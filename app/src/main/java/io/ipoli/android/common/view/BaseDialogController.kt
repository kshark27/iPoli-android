package io.ipoli.android.common.view

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.bluelinelabs.conductor.RestoreViewOnCreateController
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.SimpleSwapChangeHandler
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.ViewState
import io.ipoli.android.common.redux.ViewStateReducer
import io.ipoli.android.common.redux.android.ReduxViewController

/**
 * A controller that displays a dialog window, floating on top of its activity's window.
 * This is a wrapper over [Dialog] object like [android.app.DialogFragment].
 *
 *
 * Implementations should override this class and implement [.onCreateDialog] to create a custom dialog, such as an [android.app.AlertDialog]
 */
abstract class BaseDialogController : RestoreViewOnCreateController {

    protected lateinit var dialog: Dialog
    private var dismissed: Boolean = false

    /**
     * Convenience constructor for use when no arguments are needed.
     */
    protected constructor() : super()

    /**
     * Constructor that takes arguments that need to be retained across restarts.
     *
     * @param args Any arguments that need to be retained.
     */
    protected constructor(args: Bundle?) : super(args)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val headerView = createHeaderView(inflater)
        onHeaderViewCreated(headerView)
        val contentView = onCreateContentView(inflater, savedViewState)

        val dialogBuilder = AlertDialog.Builder(activity!!)
            .setView(contentView)

        headerView?.let {
            dialogBuilder.setCustomTitle(headerView)
        }
        dialog = onCreateDialog(dialogBuilder, contentView, savedViewState)
        dialog.window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        dialog.ownerActivity = activity!!
        dialog.setOnDismissListener {
            dismiss()
            onDismiss()
        }
        if (savedViewState != null) {
            val dialogState = savedViewState.getBundle(SAVED_DIALOG_STATE_TAG)
            if (dialogState != null) {
                dialog.onRestoreInstanceState(dialogState)
            }
        }
        return View(activity)
    }

    @SuppressLint("InflateParams")
    protected open fun createHeaderView(inflater: LayoutInflater): View? {
        return inflater.inflate(R.layout.view_dialog_header, null)
    }

    protected open fun onHeaderViewCreated(headerView: View?) {

    }

    protected abstract fun onCreateContentView(
        inflater: LayoutInflater,
        savedViewState: Bundle?
    ): View

    protected abstract fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog

    override fun onSaveViewState(view: View, outState: Bundle) {
        super.onSaveViewState(view, outState)
        val dialogState = dialog.onSaveInstanceState()
        outState.putBundle(SAVED_DIALOG_STATE_TAG, dialogState)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        dialog.show()
        dialog.window.decorView.systemUiVisibility =
            dialog.ownerActivity.window.decorView.systemUiVisibility

        dialog.window.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        onShow(dialog.window.decorView)
    }

    protected open fun onShow(contentView: View) {}

    override fun onDetach(view: View) {
        onHide(dialog.window.decorView)
        dialog.hide()
        super.onDetach(view)
    }

    protected open fun onHide(contentView: View) {}

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        dialog.setOnDismissListener(null)
        dialog.dismiss()
    }

    /**
     * Display the dialog, create a transaction and pushing the controller.
     * @param router The router on which the transaction will be applied
     * @param tag The tag for this controller
     */
    fun show(router: Router, tag: String? = null) {
        dismissed = false
        router.pushController(
            RouterTransaction.with(this)
                .pushChangeHandler(SimpleSwapChangeHandler(false))
                .popChangeHandler(SimpleSwapChangeHandler(false))
                .tag(tag)
        )
    }

    /**
     * Dismiss the dialog and pop this controller
     */
    fun dismiss() {
        if (dismissed) {
            return
        }
        router.popController(this)
        dismissed = true
    }

    protected open fun onDismiss() {

    }

    companion object {

        private val SAVED_DIALOG_STATE_TAG = "android:savedDialogState"
    }
}

abstract class ReduxDialogController<A : Action, VS : ViewState, out R : ViewStateReducer<AppState, VS>>(
    args: Bundle? = null
) : ReduxViewController<A, VS, R>(args) {

    protected lateinit var dialog: AlertDialog
    private lateinit var contentView: View
    private var dismissed: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {

        val headerView = createHeaderView(inflater)
        onHeaderViewCreated(headerView)
        contentView = onCreateContentView(inflater, savedViewState)

        val dialogBuilder = AlertDialog.Builder(contentView.context)
            .setView(contentView)
            .setCustomTitle(headerView)
        dialog = onCreateDialog(dialogBuilder, contentView, savedViewState)

        dialog.window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        onDialogCreated(dialog, contentView)

        dialog.setCanceledOnTouchOutside(false)
        dialog.ownerActivity = activity!!
        dialog.setOnDismissListener { dismiss() }
        if (savedViewState != null) {
            val dialogState = savedViewState.getBundle(SAVED_DIALOG_STATE_TAG)
            if (dialogState != null) {
                dialog.onRestoreInstanceState(dialogState)
            }
        }
        return View(activity)
    }

    protected open fun createHeaderView(inflater: LayoutInflater): View =
        inflater.inflate(io.ipoli.android.R.layout.view_dialog_header, null)

    protected open fun onHeaderViewCreated(headerView: View) {

    }

    protected abstract fun onCreateContentView(
        inflater: LayoutInflater,
        savedViewState: Bundle?
    ): View

    protected abstract fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog

    protected open fun onDialogCreated(dialog: AlertDialog, contentView: View) {

    }

    override fun onSaveViewState(view: View, outState: Bundle) {
        super.onSaveViewState(view, outState)
        val dialogState = dialog.onSaveInstanceState()
        outState.putBundle(SAVED_DIALOG_STATE_TAG, dialogState)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        dialog.show()
        dialog.window.decorView.systemUiVisibility =
            dialog.ownerActivity.window.decorView.systemUiVisibility

        dialog.window.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        onShow(dialog.window.decorView)
    }

    protected open fun onShow(contentView: View) {}

    override fun onDetach(view: View) {
        onHide(dialog.window.decorView)
        dialog.hide()
        super.onDetach(view)
    }

    protected open fun onHide(contentView: View) {}

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        dialog.setOnDismissListener(null)
        dialog.dismiss()
    }

    override fun colorStatusBars() {

    }

    /**
     * Display the dialog, create a transaction and pushing the controller.
     * @param router The router on which the transaction will be applied
     * @param tag The tag for this controller
     */
    fun show(router: Router, tag: String? = null) {
        dismissed = false
        router.pushController(
            RouterTransaction.with(this)
                .pushChangeHandler(SimpleSwapChangeHandler(false))
                .popChangeHandler(SimpleSwapChangeHandler(false))
                .tag(tag)
        )
    }

    /**
     * Dismiss the dialog and pop this controller
     */
    fun dismiss() {
        if (dismissed) {
            return
        }
        router.popController(this)
        dismissed = true
    }

    override fun onRenderViewState(state: VS) {
        render(state, contentView)
    }

    protected fun changeIcon(@DrawableRes icon: Int) {
        val headerIcon = dialog.findViewById<ImageView>(io.ipoli.android.R.id.dialogHeaderIcon)
        headerIcon?.setImageResource(icon)
    }

    protected fun changeTitle(@StringRes title: Int) {
        val headerTitle = dialog.findViewById<TextView>(io.ipoli.android.R.id.dialogHeaderTitle)
        headerTitle?.setText(title)
    }

    protected fun changeLifeCoins(lifeCoins: Int) {
        val headerLifeCoins =
            dialog.findViewById<TextView>(io.ipoli.android.R.id.dialogHeaderLifeCoins)
        headerLifeCoins?.visible()
        headerLifeCoins?.text = lifeCoins.toString()
    }

    protected fun changeNeutralButtonText(@StringRes text: Int) {
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setText(text)
    }

    protected fun changePositiveButtonText(@StringRes text: Int) {
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setText(text)
    }

    protected fun changeNegativeButtonText(@StringRes text: Int) {
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setText(text)
    }

    protected fun setNeutralButtonListener(listener: (() -> Unit)?) {
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).onDebounceClick {
            if (listener != null) listener()
            else dismiss()
        }
    }

    protected fun setPositiveButtonListener(listener: (() -> Unit)?) {
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).onDebounceClick {
            if (listener != null) listener()
            else dismiss()
        }
    }

    protected fun setNegativeButtonListener(listener: (() -> Unit)?) {
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).onDebounceClick {
            if (listener != null) listener()
            else dismiss()
        }
    }

    companion object {
        private const val SAVED_DIALOG_STATE_TAG = "android:savedDialogState"
    }
}