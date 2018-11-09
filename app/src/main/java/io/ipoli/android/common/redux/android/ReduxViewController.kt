package io.ipoli.android.common.redux.android

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.annotation.IdRes
import android.support.annotation.LayoutRes
import android.support.annotation.StringRes
import android.support.v7.widget.Toolbar
import android.text.format.DateFormat
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RestoreViewOnCreateController
import com.github.florent37.tutoshowcase.TutoShowcase
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import io.ipoli.android.Constants
import io.ipoli.android.MyPoliApp
import io.ipoli.android.R
import io.ipoli.android.common.*
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.di.UIModule
import io.ipoli.android.common.navigation.Navigator
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.StateStore
import io.ipoli.android.common.redux.ViewState
import io.ipoli.android.common.redux.ViewStateReducer
import io.ipoli.android.common.view.*
import io.ipoli.android.friends.feed.data.AndroidReactionType
import io.ipoli.android.friends.feed.data.Post
import io.ipoli.android.player.data.Player
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.channels.actor
import space.traversal.kapsule.Injects
import space.traversal.kapsule.inject
import space.traversal.kapsule.required


/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 1/18/18.
 */

abstract class BaseViewController<A : Action, VS : ViewState> protected constructor(
    args: Bundle? = null
) : RestoreViewOnCreateController(args), Injects<UIModule>,
    StateStore.StateChangeSubscriber<AppState> {

    private val stateStore by required { stateStore }
    private val eventLogger by required { eventLogger }
    private val sharedPreferences by required { sharedPreferences }

    protected open var namespace: String? = null

    protected abstract val stateKey: String

    private var eventActor: SendChannel<ViewAction>? = null

    private var permissionsToRationale: Map<String, String> = mapOf()

    private var use24HourFormat: Boolean? = null

    private val runningAnimators = mutableListOf<Animator>()

    @Volatile
    private var currentState: VS? = null

    protected var applyStatusBarColors = true

    protected open var helpConfig: HelpConfig? = null

    init {
        val lifecycleListener = object : LifecycleListener() {

            override fun postAttach(controller: Controller, view: View) {
                eventLogger.logCurrentScreen(
                    activity!!,
                    this@BaseViewController.javaClass.simpleName.replace("ViewController", "")
                )
                stateStore.subscribe(this@BaseViewController)
                onSubscribedToStore()
                onCreateLoadAction()?.let {
                    dispatch(it)
                }
                eventActor = createEventActor()

            }

            override fun preDetach(controller: Controller, view: View) {
                stateStore.unsubscribe(this@BaseViewController)
                eventActor!!.close()
            }

            override fun preDestroyView(controller: Controller, view: View) {
                currentState = null
            }
        }
        addLifecycleListener(lifecycleListener)
        setHasOptionsMenu(false)
    }

    protected open fun onSubscribedToStore() {}

    override fun onContextAvailable(context: Context) {
        inject(MyPoliApp.uiModule(context))
    }

    override fun onAttach(view: View) {
        colorStatusBars()
        super.onAttach(view)
    }

    protected open fun colorStatusBars() {
        if (applyStatusBarColors) {
            activity?.window?.statusBarColor = attrData(io.ipoli.android.R.attr.colorPrimaryDark)
            activity?.window?.navigationBarColor = attrData(io.ipoli.android.R.attr.colorPrimary)
        }
    }

    override fun onDetach(view: View) {
        runningAnimators.forEach {
            it.cancel()
        }
        runningAnimators.clear()
        super.onDetach(view)
    }

    protected fun playAnimation(
        animator: Animator,
        onStart: () -> Unit = {},
        onEnd: () -> Unit = {}
    ) {
        runningAnimators.add(animator)
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                onStart()
            }

            override fun onAnimationEnd(animation: Animator?) {
                onEnd()
            }
        })
        animator.start()
    }

    private fun createEventActor() =
        GlobalScope.actor<ViewAction>(Dispatchers.Main, start = CoroutineStart.UNDISPATCHED) {
            for (va in this) {
                when (va) {
                    is ViewAction.ClickAction -> va.action(va.view)
                    is ViewAction.MenuClickAction -> va.action(va.menuItem)
                }

                delay(400)
            }
        }

    sealed class ViewAction {
        data class ClickAction(val view: View, val action: suspend (View) -> Unit) : ViewAction()
        data class MenuClickAction(val menuItem: MenuItem, val action: suspend (MenuItem) -> Unit) :
            ViewAction()
    }

    fun View.onDebounceClick(action: suspend (View) -> Unit) {
        setOnClickListener { v ->
            eventActor?.let {
                if (!it.isClosedForSend) it.offer(ViewAction.ClickAction(v, action))
            }
        }
    }

    fun Toolbar.onDebounceMenuClick(
        action: suspend (MenuItem) -> Unit,
        backAction: (suspend (View) -> Unit)? = null
    ) {
        setOnMenuItemClickListener { mi ->
            eventActor?.let {
                if (!it.isClosedForSend) it.offer(ViewAction.MenuClickAction(mi, action))
            }
            true
        }
        backAction?.let { ba ->
            setNavigationOnClickListener { v ->
                eventActor?.let {
                    if (!it.isClosedForSend) it.offer(ViewAction.ClickAction(v, ba))
                }
            }
        }
    }

    fun Toolbar.clearDebounceListeners() {
        setOnMenuItemClickListener(null)
        setNavigationOnClickListener(null)
    }

    fun dispatch(action: A) {
        val a = namespace?.let {
            NamespaceAction(action, it)
        } ?: action
        stateStore.dispatch(a)
    }

    override suspend fun onStateChanged(newState: AppState) {
        if (newState.keys.contains(stateKey)) {

            val viewState = newState.stateFor<VS>(stateKey)

            if (currentState == null || !viewState.hasSameStateId(currentState as VS)) {
                currentState = viewState
                renderViewState(viewState)
            }
        }
    }

    private suspend fun renderViewState(viewState: VS) {
        withContext(Dispatchers.Main) {
            onRenderViewState(viewState)
        }
    }

    protected open fun onRenderViewState(state: VS) {
        view?.let {
            render(state, it)
        }
    }

    protected open fun onCreateLoadAction(): A? {
        return null
    }

    abstract fun render(state: VS, view: View)

    fun View.dispatchOnClick(block: () -> A) {
        onDebounceClick {
            dispatch(block())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        if (helpConfig != null) {
            inflater.inflate(R.menu.help_menu, menu)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.actionHelp && helpConfig != null) {
            navigate().toHelp(stringRes(helpConfig!!.title), stringRes(helpConfig!!.message))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    data class HelpConfig(@StringRes val title: Int, @StringRes val message: Int)

    protected fun navigateFromRoot() = Navigator(rootRouter)

    protected fun requestPermissions(
        permissionsToRationale: Map<String, String>,
        requestCode: Int
    ) {
        this.permissionsToRationale = permissionsToRationale
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissionsToRationale.keys.toTypedArray(), requestCode)
        } else {
            onPermissionsGranted(requestCode, permissionsToRationale.keys.toList())
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = mutableListOf<String>()
        val denied = mutableListOf<String>()

        grantResults.forEachIndexed { index, res ->
            val perm = permissions[index]
            if (res == PackageManager.PERMISSION_GRANTED) {
                granted.add(perm)
            } else {
                denied.add(perm)
            }
        }
        if (granted.isNotEmpty()) {
            onPermissionsGranted(requestCode, granted)
        }

        if (denied.isNotEmpty()) {
            onPermissionsDenied(requestCode, denied)
        }
    }

    protected open fun onPermissionsDenied(requestCode: Int, permissions: List<String>) {
        permissions.forEach {
            if (shouldShowRequestPermissionRationale(it)) {
                val message = if (permissionsToRationale.containsKey(it))
                    permissionsToRationale[it]!!
                else
                    ""
                PermissionRationaleDialogController(
                    message,
                    { requestPermissions(permissions.toTypedArray(), requestCode) },
                    {}
                ).show(router)
            }
        }
    }

    protected open fun onPermissionsGranted(requestCode: Int, permissions: List<String>) {

    }

    protected fun showcaseRect(
        @LayoutRes layout: Int,
        @IdRes view: Int,
        @IdRes containerView: Int,
        onClick: (TutoShowcase) -> Unit = {}
    ): TutoShowcase {
        val showcase = TutoShowcase.from(activity!!)
        showcase
            .setContentView(layout)
            .on(view)
            .addRoundRect()
            .withBorder()
            .onClick {
                onClick(showcase)
            }
            .show()
            .onClickContentView(containerView, null)
        return showcase
    }

    protected fun showcaseRect(
        @LayoutRes layout: Int,
        view: View,
        @IdRes containerView: Int,
        onClick: (TutoShowcase) -> Unit = {}
    ): TutoShowcase {
        val showcase = TutoShowcase.from(activity!!)
        showcase
            .setContentView(layout)
            .on(view)
            .addRoundRect()
            .withBorder()
            .onClick {
                onClick(showcase)
            }
            .show()
            .onClickContentView(containerView, null)
        return showcase
    }

    protected fun showcaseCircle(
        @LayoutRes layout: Int,
        @IdRes view: Int,
        @IdRes containerView: Int,
        onClick: (TutoShowcase) -> Unit = {}
    ): TutoShowcase {
        val showcase = TutoShowcase.from(activity!!)
        showcase
            .setContentView(layout)
            .on(view)
            .addCircle()
            .withBorder()
            .onClick {
                onClick(showcase)
            }
            .show()
            .onClickContentView(containerView, null)
        return showcase
    }

    protected fun createTimePickerDialog(
        startTime: Time,
        onTimePicked: (Time?) -> Unit,
        showNeutral: Boolean = true
    ) =
        TimePickerDialogViewController(
            time = startTime,
            shouldUse24HourFormat = shouldUse24HourFormat,
            listener = { time ->
                onTimePicked(time)
            },
            showNeutral = showNeutral
        )

    protected val shouldUse24HourFormat: Boolean
        get() {
            if (use24HourFormat == null) {
                val timeFormat = Player.Preferences.TimeFormat.valueOf(
                    sharedPreferences.getString(
                        Constants.KEY_TIME_FORMAT,
                        Player.Preferences.TimeFormat.DEVICE_DEFAULT.name
                    )
                )
                use24HourFormat =
                    if (timeFormat == Player.Preferences.TimeFormat.DEVICE_DEFAULT)
                        DateFormat.is24HourFormat(activity)
                    else
                        timeFormat != Player.Preferences.TimeFormat.TWELVE_HOURS
            }
            return use24HourFormat!!
        }

    protected fun ProgressBar.animateProgressFromCurrentValue(to: Int, delay: Int = 0) {
        animateProgress(progress, to, delay)
    }

    protected fun ProgressBar.animateProgressFromZero(to: Int, delay: Int = 0) {
        animateProgress(0, to)
    }

    protected fun ProgressBar.animateProgress(
        from: Int,
        to: Int,
        delay: Int = 0,
        endListener: (() -> Unit)? = null
    ) {
        val animator = ObjectAnimator.ofInt(this, "progress", from, to)
        animator.duration = intRes(android.R.integer.config_mediumAnimTime).toLong()
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.startDelay = delay.toLong()

        endListener?.let {
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    endListener()
                }
            })
        }

        animator.start()
    }

    protected fun TextView.animateToValueFromZero(value: Int) {
        val anim = ValueAnimator.ofInt(0, value)
        anim.addUpdateListener {
            text = "${it.animatedValue}"
        }
        anim.interpolator = AccelerateDecelerateInterpolator()
        anim.duration = longAnimTime
        anim.start()
    }

    protected val Color.androidColor: AndroidColor
        get() = AndroidColor.valueOf(this.name)

    protected val AndroidColor.color: Color
        get() = Color.valueOf(this.name)

    protected val Icon.androidIcon: AndroidIcon
        get() = AndroidIcon.valueOf(this.name)

    protected val AndroidIcon.toIcon: Icon
        get() = Icon.valueOf(this.name)

    fun listItemIcon(icon: IIcon): IconicsDrawable =
        IconicsDrawable(activity!!).listItemIcon(icon)

    fun smallListItemIcon(icon: IIcon): IconicsDrawable =
        IconicsDrawable(activity!!).smallListItemIcon(icon)

    protected val Post.ReactionType.androidType: AndroidReactionType
        get() = AndroidReactionType.valueOf(this.name)
}

abstract class ReduxViewController<A : Action, VS : ViewState, out R : ViewStateReducer<AppState, VS>>
protected constructor(args: Bundle? = null) : BaseViewController<A, VS>(args) {

    private val stateStore by required { stateStore }

    protected abstract val reducer: R

    override val stateKey get() = reducer.stateKey

    init {
        val lifecycleListener = object : LifecycleListener() {

            override fun postCreateView(controller: Controller, view: View) {
                stateStore.dispatch(UiAction.Attach(reducer))
            }

            override fun preDestroyView(controller: Controller, view: View) {
                stateStore.dispatch(UiAction.Detach(reducer))
            }
        }
        addLifecycleListener(lifecycleListener)
    }

    override fun onSubscribedToStore() {
        onRenderViewState(reducer.defaultState())
    }
}