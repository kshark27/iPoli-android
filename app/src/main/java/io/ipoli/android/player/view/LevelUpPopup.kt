package io.ipoli.android.player.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.support.v4.view.animation.LinearOutSlowInInterpolator
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import io.ipoli.android.Constants
import io.ipoli.android.MyPoliApp
import io.ipoli.android.R
import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.notification.QuickDoNotificationUtil
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.common.view.AppWidgetUtil
import io.ipoli.android.common.view.Debounce
import io.ipoli.android.common.view.ReduxPopup
import io.ipoli.android.common.view.anim.TypewriterTextAnimator
import io.ipoli.android.common.view.visible
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.player.data.Player
import kotlinx.android.synthetic.main.popup_level_up.view.*
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.launch
import space.traversal.kapsule.required


data class LevelUpViewState(
    val level: Int?,
    val avatar: PetAvatar?
) : BaseViewState()

sealed class LevelUpAction : Action {
    data class Load(val newLevel: Int) : LevelUpAction() {
        override fun toMap() = mapOf("newLevel" to newLevel)
    }

    data class PlayerLoaded(val player: Player) : LevelUpAction() {
        override fun toMap() = mapOf("player" to player)
    }
}

object PlayerSideEffectHandler : AppSideEffectHandler() {

    private val playerRepository by required { playerRepository }
    private val sharedPreferences by required { sharedPreferences }

    override suspend fun doExecute(action: Action, state: AppState) {
        when (action) {
            is LevelUpAction.Load ->
                dispatch(LevelUpAction.PlayerLoaded(playerRepository.find()!!))

            is ReviveAction -> {
                val p = playerRepository.find()!!
                if (!p.isDead) {
                    return
                }
                playerRepository.save(p.revive())

                sharedPreferences.edit().putBoolean(Constants.KEY_PLAYER_DEAD, false).commit()

                GlobalScope.launch(Dispatchers.Main) {
                    state.dataState.todayQuests?.let {
                        if (p.preferences.isQuickDoNotificationEnabled) {
                            QuickDoNotificationUtil.update(MyPoliApp.instance, it)
                        }
                    }

                    AppWidgetUtil.updateAgendaWidget(MyPoliApp.instance)
                    AppWidgetUtil.updateHabitWidget(MyPoliApp.instance)
                }
            }
        }
    }

    override fun canHandle(action: Action) = action is LevelUpAction.Load || action is ReviveAction

}

object LevelUpReducer : BaseViewStateReducer<LevelUpViewState>() {
    override fun reduce(
        state: AppState,
        subState: LevelUpViewState,
        action: Action
    ) =
        when (action) {
            is LevelUpAction.Load ->
                subState.copy(level = action.newLevel)

            is LevelUpAction.PlayerLoaded ->
                subState.copy(avatar = action.player.pet.avatar)

            else -> subState
        }

    override fun defaultState() = LevelUpViewState(level = null, avatar = null)

    override val stateKey = key<LevelUpViewState>()
}

class LevelUpPopup(private val newLevel: Int) :
    ReduxPopup<LevelUpAction, LevelUpViewState, LevelUpReducer>() {

    override val reducer = LevelUpReducer

    private val eventLogger by required { eventLogger }

    override fun render(state: LevelUpViewState, view: View) {
        state.avatar?.let {
            val androidAvatar = AndroidPetAvatar.valueOf(it.name)
            view.pet.setImageResource(androidAvatar.headImage)
            val params = mutableMapOf<String, Any>()
            params["level"] = state.level!!
            view.positive.setOnClickListener(Debounce.clickListener { _ ->
                params["sentiment"] = "positive"
                eventLogger.logEvent("reward_response", params)
                hide()
            })
            view.negative.setOnClickListener(Debounce.clickListener { _ ->
                params["sentiment"] = "negative"
                eventLogger.logEvent("reward_response", params)
                hide()
            })
            startTypingAnimation(view, state.level)
        }
    }

    @SuppressLint("InflateParams")
    override fun createView(inflater: LayoutInflater): View =
        inflater.inflate(R.layout.popup_level_up, null)

    override fun onCreateLoadAction() = LevelUpAction.Load(newLevel)

    private fun startTypingAnimation(contentView: View, level: Int) {
        val title = contentView.title

        val typewriterTitleAnim = TypewriterTextAnimator.of(title, "You reached new level")
        val animSet = AnimatorSet()
        animSet.playSequentially(
            typewriterTitleAnim, levelBadgeAnimation(contentView, level),
            claimRewardAnimation(contentView)
        )
        animSet.start()

        val rewardUrl = Constants.LEVEL_UP_REWARDS[(level - 2) % Constants.LEVEL_UP_REWARDS.size]
        contentView.claimReward.setOnClickListener {
            val reward = contentView.bounty as ImageView
            Glide.with(contentView.context)
                .load(rewardUrl)
                .into(reward)
            contentView.container.showNext()
        }
    }

    private fun levelBadgeAnimation(contentView: View, level: Int): Animator {
        val badge = contentView.badge
        val levelView = contentView.playerLevel
        levelView.text = level.toString()

        val fadeIn = ObjectAnimator.ofFloat(badge, "alpha", 0f, 1f)
        val scaleLevelX = ObjectAnimator.ofFloat(levelView, "scaleX", 1.5f, 1f)
        val scaleLevelY = ObjectAnimator.ofFloat(levelView, "scaleY", 1.5f, 1f)

        val scaleAnim = AnimatorSet()
        scaleAnim.playTogether(scaleLevelX, scaleLevelY)

        val anim = AnimatorSet()
        anim.interpolator = LinearOutSlowInInterpolator()
        anim.playSequentially(fadeIn, scaleAnim)
        fadeIn.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                badge.visible = true
            }

            override fun onAnimationEnd(animation: Animator?) {
                levelView.visible = true
            }
        })

        return anim
    }

    private fun claimRewardAnimation(contentView: View): Animator {
        val anim = ObjectAnimator.ofFloat(contentView.claimReward, "alpha", 0f, 1f)
        anim.duration = 300
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                contentView.claimReward.visible = true
            }
        })
        return anim
    }
}