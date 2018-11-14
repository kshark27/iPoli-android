package io.ipoli.android.quest.reminder

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnticipateInterpolator
import io.ipoli.android.R
import io.ipoli.android.common.view.Popup
import io.ipoli.android.common.view.gone
import io.ipoli.android.common.view.views
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.pet.PetState
import kotlinx.android.synthetic.main.popup_pet_notification.view.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 10/13/17.
 */

class PetNotificationPopup(
    private val viewModel: PetNotificationPopup.ViewModel,
    private val onDo: () -> Unit = {},
    private val onSnooze: () -> Unit = {},
    private val onDismiss: () -> Unit = {}
) {

    data class ViewModel(
        val headline: String,
        val title: String?,
        val body: String?,
        val petAvatar: PetAvatar,
        val petState: PetState,
        @StringRes val doTextRes: Int,
        @DrawableRes val doImageRes: Int
    )

    private lateinit var overlayView: ViewGroup
    private lateinit var windowManager: WindowManager

    @SuppressLint("InflateParams")
    fun show(context: Context) {
        overlayView =
            LayoutInflater.from(context).inflate(R.layout.popup_pet_notification, null) as ViewGroup
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addViewToWindowManager(overlayView)

        initUI()
        show()
    }

    private fun initUI() {
        with(overlayView) {
            startHint.setText(viewModel.doTextRes)
            start.setImageResource(viewModel.doImageRes)
            headline.text = viewModel.headline
            if (viewModel.title != null) {
                title.text = viewModel.title
            } else {
                title.gone()
            }
            if (viewModel.body != null) {
                body.text = viewModel.body
            } else {
                body.height = 0
            }
            val petAvatar = AndroidPetAvatar.valueOf(viewModel.petAvatar.name)
            pet.setImageResource(petAvatar.image)
            petState.setImageResource(petAvatar.stateImage[viewModel.petState]!!)

            reminderAnimation.setAnimation("bell.json")
            reminderAnimation.playAnimation()
        }
        initButtons()
    }

    private fun show() {
        overlayView.post {
            startShowAnimation(overlayView)
        }
    }

    private fun initButtons() {
        with(overlayView) {
            dismiss.setOnClickListener {
                dismiss.isClickable = false
                hide {
                    onDismiss()
                }
            }

            snooze.setOnClickListener {
                snooze.isClickable = false
                hide {
                    onSnooze()
                }
            }

            start.setOnClickListener {
                start.isClickable = false
                hide {
                    onDo()
                }
            }
        }

    }

    private fun addViewToWindowManager(view: ViewGroup) {
        @Suppress("DEPRECATION")
        val focusable =
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                .or(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
                .or(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                .or(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                .or(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                .or(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
                .or(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            Popup.WindowOverlayCompat.TYPE_SYSTEM_ERROR,
            focusable,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(view, layoutParams)
    }

    private fun removeViewFromWindowManager(view: ViewGroup) {
        windowManager.removeViewImmediate(view)
    }

    private fun hide(onEnd: () -> Unit) {
        startHideAnimation(overlayView, object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                onEnd()
                removeViewFromWindowManager(overlayView)
            }
        })
    }

    private fun startShowAnimation(view: ViewGroup) {
        playShowBackgroundAnimation(view)
        playShowPetAnimation(view)
    }

    private fun playShowPetAnimation(view: ViewGroup) {
        val duration = view.resources.getInteger(android.R.integer.config_longAnimTime).toLong()
        val petSet = AnimatorSet()
        val petAnimator = createShowPetAnimator(view.pet)
        val petStateAnimator = createShowPetAnimator(view.petState)
        petSet.playTogether(petAnimator, petStateAnimator)
        petSet.startDelay = (duration / 1.2).toLong()
        petSet.start()
    }

    private fun playShowBackgroundAnimation(view: ViewGroup) {
        val props = calculateRevealAnimationProperties(view.backgroundView)

        val backgroundAnim = ViewAnimationUtils.createCircularReveal(
            view.backgroundView,
            props.centerX, props.centerY,
            0f, props.radius
        )
        backgroundAnim.interpolator = AnticipateInterpolator(2.0f)
        val duration = view.resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
        backgroundAnim.duration = duration

        val animators = mutableListOf(backgroundAnim)

        val alphaAnimators =
            contentViews(view).map {
                it.alpha = 0f
                ObjectAnimator.ofFloat(it, "alpha", 0f, 1f).apply {
                    this.duration = (duration * 4 / 5)
                    startDelay = duration * 4 / 5
                }
            }
        animators.addAll(alphaAnimators)

        val set = AnimatorSet()
        set.playTogether(animators)
        set.start()
    }

    private fun startHideAnimation(view: ViewGroup, listener: AnimatorListenerAdapter) {
        view.pet.visibility = View.VISIBLE
        view.petState.visibility = View.VISIBLE

        playHidePetAnimation(view)
        playHideBackgroundAnimation(view, listener)
    }

    private fun playHideBackgroundAnimation(view: ViewGroup, listener: AnimatorListenerAdapter) {
        val props = calculateRevealAnimationProperties(view.backgroundView)
        val backgroundAnim = ViewAnimationUtils.createCircularReveal(
            view.backgroundView,
            props.centerX, props.centerY,
            props.radius, 0f
        )
        backgroundAnim.interpolator = AccelerateDecelerateInterpolator()
        val duration = view.resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()
        backgroundAnim.duration = duration
        backgroundAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                view.backgroundView.visibility = View.INVISIBLE
            }
        })

        val animators = mutableListOf(backgroundAnim)

        val alphaAnimators = contentViews(view).map {
            ObjectAnimator.ofFloat(it, "alpha", 1f, 0f).setDuration((duration / 1.5).toLong())
        }
        animators.addAll(alphaAnimators)

        val set = AnimatorSet()
        set.playTogether(animators)
        set.startDelay = duration
        set.addListener(listener)
        set.start()
    }

    private fun contentViews(view: ViewGroup): List<View> {
        val vs = view.group.views().toMutableList()
        vs.add(view.title)
        vs.add(view.body)
        return vs
    }

    private fun playHidePetAnimation(view: ViewGroup) {
        val petSet = AnimatorSet()
        val petAnimator = createHidePetAnimator(view.pet)
        val petStateAnimator = createHidePetAnimator(view.petState)
        petSet.playTogether(petAnimator, petStateAnimator)
        petSet.start()
    }

    private fun createShowPetAnimator(view: View): ObjectAnimator {
        val animator = ObjectAnimator.ofFloat(view, "y", view.y + view.height, view.y)
        animator.duration =
            view.context.resources.getInteger(android.R.integer.config_longAnimTime).toLong()
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addListener(object : AnimatorListenerAdapter() {

            override fun onAnimationStart(animation: Animator?) {
                view.visibility = View.VISIBLE
            }
        })

        return animator
    }

    private fun createHidePetAnimator(view: View): ObjectAnimator {
        val animator = ObjectAnimator.ofFloat(view, "y", view.y, view.y + view.height)
        animator.duration =
            view.context.resources.getInteger(android.R.integer.config_longAnimTime).toLong()
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addListener(object : AnimatorListenerAdapter() {

            override fun onAnimationEnd(animation: Animator?) {
                view.visibility = View.INVISIBLE
            }
        })

        return animator
    }

    private fun calculateRevealAnimationProperties(view: View): RevealAnimationProperties {
        val bounds = Rect()
        view.getDrawingRect(bounds)

        val location = IntArray(2)
        view.getLocationOnScreen(location)

        val radius = (getScreenHeight(view.context) - location[1]).toFloat()
        val centerX = bounds.centerX()
        val centerY = radius.toInt() / 2

        return RevealAnimationProperties(centerX, centerY, radius)
    }

    private fun getScreenHeight(context: Context): Int {
        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getMetrics(metrics)
        return metrics.heightPixels
    }

    data class RevealAnimationProperties(val centerX: Int, val centerY: Int, val radius: Float)
}