package io.ipoli.android.quest.view

import android.animation.*
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.support.annotation.DrawableRes
import android.support.v4.content.ContextCompat
import android.support.v7.widget.GridLayoutManager
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.view.anim.TypewriterTextAnimator
import io.ipoli.android.common.view.gone
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.common.view.visible
import io.ipoli.android.pet.Food
import io.ipoli.android.player.data.AndroidAttribute
import io.ipoli.android.player.data.Player
import kotlinx.android.synthetic.main.popup_reward.view.*
import java.util.*

class RewardPopup(
    @DrawableRes private val petHeadImage: Int,
    private val earnedXP: Int,
    private val earnedCoins: Int,
    private val attributes: Map<Player.AttributeType, Int> = emptyMap(),
    private val bounty: Food? = null,
    private val undoListener: () -> Unit = {},
    private val isPositive: Boolean = true,
    private val showUndoAction: Boolean = true
) : ToastOverlay() {


    @SuppressLint("InflateParams")
    override fun createView(inflater: LayoutInflater): View {
        val view = inflater.inflate(R.layout.popup_reward, null)
        view.attributes.layoutManager = GridLayoutManager(view.context, 3)
        val adapter = AttributeRewardAdapter()
        view.attributes.adapter = adapter

        adapter.updateAll(attributes.map {
            val attr = AndroidAttribute.valueOf(it.key.name)
            AttributeRewardViewModel(it.key.name, it.value.toString(), attr.whiteIcon)
        })

        if (!showUndoAction) {
            view.petAction.gone()
        }
        return view
    }

    data class AttributeRewardViewModel(
        override val id: String,
        val text: String,
        val icon: Int
    ) : RecyclerViewViewModel

    inner class AttributeRewardAdapter :
        BaseRecyclerViewAdapter<AttributeRewardViewModel>(R.layout.item_reward_popup_attribute) {

        override fun onBindViewModel(
            vm: AttributeRewardViewModel,
            view: View,
            holder: SimpleViewHolder
        ) {
            val textView = view as TextView
            textView.text = vm.text
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                ContextCompat.getDrawable(view.context, vm.icon), null, null, null
            )
        }
    }

    override fun onViewShown(contentView: View) {
        contentView.pet.setImageResource(petHeadImage)
        bounty?.let {
            contentView.bounty.setImageResource(it.image)
        }
        startTypingAnimation(contentView)

        contentView.petAction.setOnClickListener {
            contentView.petAction.gone()
            undoListener()
            hide()
        }
    }

    private fun startTypingAnimation(contentView: View) {
        val title = contentView.message
        val messages = contentView.resources.getStringArray(
            if (isPositive) R.array.reward_positive_message
            else R.array.reward_negative_message
        )
        val message = messages[Random().nextInt(messages.size)]
        val typewriterAnim = TypewriterTextAnimator.of(
            title,
            message,
            typeSpeed = TypewriterTextAnimator.DEFAULT_TYPE_SPEED - 5
        )
        typewriterAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (earnedXP <= 0 && earnedCoins <= 0) {
                    val messageAnim = ObjectAnimator.ofFloat(contentView.message, "alpha", 1f, 0f)
                    messageAnim.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator?) {
                            if (attributes.isEmpty() && bounty == null) {
                                hide()
                            } else {
                                playAttributesAnimation(contentView)
                            }
                        }
                    })
                    messageAnim.startDelay = 500
                    messageAnim.duration = 300
                    messageAnim.start()
                } else {
                    startEarnedRewardAnimation(contentView)
                }
            }
        })
        typewriterAnim.start()
    }

    private fun startEarnedRewardAnimation(contentView: View) {
        val messageAnim = ObjectAnimator.ofFloat(contentView.message, "alpha", 1f, 0f)

        val earnedXP = contentView.earnedXP
        val earnedCoins = contentView.earnedCoins

        val xpAnim = ValueAnimator.ofInt(0, this.earnedXP)
        xpAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                earnedXP.visible = true
            }
        })
        xpAnim.addUpdateListener {
            earnedXP.text = "${it.animatedValue}"
        }

        val coinsAnim = ValueAnimator.ofInt(0, this.earnedCoins)

        coinsAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                earnedCoins.visible = true
            }
        })

        coinsAnim.addUpdateListener {
            earnedCoins.text = "${it.animatedValue}"
        }

        val anim = AnimatorSet()
        anim.startDelay = 500
        anim.duration = 300
        anim.playSequentially(messageAnim, xpAnim, coinsAnim)

        anim.addListener(object : AnimatorListenerAdapter() {

            override fun onAnimationEnd(animation: Animator) {

                if (attributes.isNotEmpty() || bounty != null) {
                    playRewardAnimation(contentView)
                } else autoHideAfter(700)
            }
        })

        anim.start()
    }

    private fun playRewardAnimation(contentView: View) {

        val alphaSet = AnimatorSet()
        alphaSet.playTogether(
            ObjectAnimator.ofFloat(contentView.earnedCoins, "alpha", 1f, 0f),
            ObjectAnimator.ofFloat(contentView.earnedXP, "alpha", 1f, 0f)
        )
        alphaSet.startDelay = 500

        alphaSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                if (attributes.isNotEmpty()) {
                    playAttributesAnimation(contentView)
                } else {
                    playBountyAnimation(contentView)
                }
            }
        })

        alphaSet.start()
    }

    private fun playAttributesAnimation(contentView: View) {

        val fadeAnim = ObjectAnimator.ofFloat(contentView.attributes, "alpha", 0f, 1f)

        fadeAnim.addListener(object : AnimatorListenerAdapter() {

            override fun onAnimationStart(animation: Animator?) {
                contentView.attributes.visible()
            }

            override fun onAnimationEnd(animation: Animator?) {
                if (bounty != null) playBountyAnimationAfterAttribute(contentView)
                else autoHideAfter(700)
            }
        })

        fadeAnim.duration = 500
        fadeAnim.start()
    }

    private fun playBountyAnimationAfterAttribute(contentView: View) {
        val fadeAnim = ObjectAnimator.ofFloat(contentView.attributes, "alpha", 1f, 0f)

        fadeAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                playBountyAnimation(contentView)
            }
        })

        fadeAnim.startDelay = 500

        fadeAnim.start()
    }

    private fun playBountyAnimation(contentView: View) {
        val qAnim = ObjectAnimator.ofFloat(contentView.bountyQuantity, "alpha", 0f, 1f)
        val xAnim = ObjectAnimator.ofFloat(contentView.bounty, "scaleX", 0f, 1f)
        val yAnim = ObjectAnimator.ofFloat(contentView.bounty, "scaleY", 0f, 1f)
        val bountyAnim = AnimatorSet()
        bountyAnim.interpolator = OvershootInterpolator()
        bountyAnim.playTogether(qAnim, xAnim, yAnim)

        bountyAnim.addListener(object : AnimatorListenerAdapter() {

            override fun onAnimationStart(animation: Animator?) {
                contentView.bounty.visible = true
                contentView.bountyQuantity.visible = true
            }

            override fun onAnimationEnd(animation: Animator?) {
                autoHideAfter(700)
            }
        })
        bountyAnim.start()
    }
}


abstract class ToastOverlay {

    private lateinit var contentView: ViewGroup
    private lateinit var windowManager: WindowManager
    private val displayMetrics = DisplayMetrics()
    private val autoHideHandler = Handler(Looper.getMainLooper())

    private val autoHideRunnable = {
        hide()
    }

    abstract fun createView(inflater: LayoutInflater): View

    fun show(context: Context) {

        contentView = createView(LayoutInflater.from(context)) as ViewGroup
        contentView.visibility = View.INVISIBLE

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        windowManager.defaultDisplay.getRealMetrics(displayMetrics)

        addViewToWindowManager(contentView)
        contentView.post {
            playEnterAnimation(contentView)
        }
    }

    protected open fun playEnterAnimation(contentView: View) {
        val transAnim = ObjectAnimator.ofFloat(
            contentView,
            "y",
            displayMetrics.heightPixels.toFloat(),
            contentView.y
        )
        val fadeAnim = ObjectAnimator.ofFloat(contentView, "alpha", 0f, 1f)
        transAnim.duration =
            contentView.context.resources.getInteger(android.R.integer.config_shortAnimTime)
                .toLong()
        fadeAnim.duration =
            contentView.context.resources.getInteger(android.R.integer.config_mediumAnimTime)
                .toLong()
        val animSet = AnimatorSet()
        animSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                contentView.visible = true
            }

            override fun onAnimationEnd(animation: Animator) {
                onViewShown(contentView)
            }
        })
        animSet.playTogether(transAnim, fadeAnim)
        animSet.start()
    }

    protected open fun onViewShown(contentView: View) {

    }

    protected open fun playExitAnimation(contentView: View) {
        val transAnim = ObjectAnimator.ofFloat(
            contentView,
            "y",
            contentView.y,
            displayMetrics.heightPixels.toFloat()
        )
        val fadeAnim = ObjectAnimator.ofFloat(contentView, "alpha", 1f, 0f)
        transAnim.duration =
            contentView.context.resources.getInteger(android.R.integer.config_shortAnimTime)
                .toLong()
        fadeAnim.duration =
            contentView.context.resources.getInteger(android.R.integer.config_mediumAnimTime)
                .toLong()
        val animSet = AnimatorSet()
        animSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onDestroy()
            }
        })
        animSet.playTogether(transAnim, fadeAnim)
        animSet.start()
    }

    private fun onDestroy() {
        if (contentView.windowToken != null) {
            windowManager.removeViewImmediate(contentView)
        }
    }

    private fun addViewToWindowManager(view: ViewGroup) {
        val focusable =
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED.or(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
                .or(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

        val width = displayMetrics.widthPixels - (ViewUtils.dpToPx(32f, view.context).toInt())

        val layoutParams = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowOverlayCompat.TYPE_SYSTEM_ERROR,
            focusable,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.CENTER_HORIZONTAL.or(Gravity.BOTTOM)

        layoutParams.y += ViewUtils.dpToPx(24f, view.context).toInt()

        windowManager.addView(view, layoutParams)
    }

    protected fun autoHideAfter(millis: Long) {
        autoHideHandler.postDelayed(autoHideRunnable, millis)
    }

    fun hide() {
        autoHideHandler.removeCallbacksAndMessages(null)
        contentView.setOnClickListener(null)
        playExitAnimation(contentView)
    }

    internal object WindowOverlayCompat {
        private const val ANDROID_OREO = 26
        private const val TYPE_APPLICATION_OVERLAY = 2038

        @Suppress("DEPRECATION")
        val TYPE_SYSTEM_ERROR =
            if (Build.VERSION.SDK_INT < ANDROID_OREO) WindowManager.LayoutParams.TYPE_SYSTEM_ERROR else TYPE_APPLICATION_OVERLAY
    }
}