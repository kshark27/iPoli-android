package io.ipoli.android.quest.job

import android.content.Context
import io.ipoli.android.MyPoliApp
import io.ipoli.android.common.Reward
import io.ipoli.android.common.di.BackgroundModule
import io.ipoli.android.common.view.asThemedWrapper
import io.ipoli.android.habit.usecase.UndoCompleteHabitUseCase
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.view.RewardPopup
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.launch
import space.traversal.kapsule.Kapsule

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 11/15/17.
 */

interface RewardScheduler {
    fun schedule(reward: Reward, isPositive: Boolean = true, type: Type, entityId: String?)

    enum class Type { QUEST, HABIT, ADD_POST }
}

class AndroidJobRewardScheduler(private val context: Context) : RewardScheduler {
    override fun schedule(
        reward: Reward,
        isPositive: Boolean,
        type: RewardScheduler.Type,
        entityId: String?
    ) {

        val c = context.asThemedWrapper()

        val kap = Kapsule<BackgroundModule>()
        val playerRepository by kap.required { playerRepository }
        val undoCompletedQuestUseCase by kap.required { undoCompletedQuestUseCase }
        val undoCompleteHabitUseCase by kap.required { undoCompleteHabitUseCase }
        kap.inject(MyPoliApp.backgroundModule(context))

        val bounty = reward.bounty

        val petAvatar = playerRepository.find()!!.pet.avatar
        val petHeadImage = AndroidPetAvatar.valueOf(petAvatar.name).headImage
        GlobalScope.launch(Dispatchers.Main) {
            RewardPopup(
                petHeadImage = petHeadImage,
                earnedXP = reward.experience,
                earnedCoins = reward.coins,
                attributes = reward.attributePoints,
                bounty = if (bounty is Quest.Bounty.Food) {
                    bounty.food
                } else {
                    null
                },
                undoListener = {
                    GlobalScope.launch(Dispatchers.IO) {
                        when (type) {
                            RewardScheduler.Type.QUEST -> {
                                undoCompletedQuestUseCase.execute(entityId!!)
                            }
                            RewardScheduler.Type.HABIT ->
                                undoCompleteHabitUseCase.execute(
                                    UndoCompleteHabitUseCase.Params(
                                        entityId!!
                                    )
                                )

                            else -> {
                            }
                        }
                    }
                },
                isPositive = isPositive,
                showUndoAction = type != RewardScheduler.Type.ADD_POST
            ).show(c)
        }
    }

}