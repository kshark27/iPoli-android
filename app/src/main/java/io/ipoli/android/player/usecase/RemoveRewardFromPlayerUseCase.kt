package io.ipoli.android.player.usecase

import io.ipoli.android.achievement.usecase.UnlockAchievementsUseCase
import io.ipoli.android.achievement.usecase.UpdatePlayerStatsUseCase
import io.ipoli.android.common.Reward
import io.ipoli.android.common.UseCase
import io.ipoli.android.player.LevelDownScheduler
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.persistence.PlayerRepository

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 29.11.17.
 */
open class RemoveRewardFromPlayerUseCase(
    private val playerRepository: PlayerRepository,
    private val levelDownScheduler: LevelDownScheduler,
    private val unlockAchievementsUseCase: UnlockAchievementsUseCase
) : UseCase<RemoveRewardFromPlayerUseCase.Params, Player> {
    override fun execute(parameters: Params): Player {
        val player = parameters.player ?: playerRepository.find()
        requireNotNull(player)
        val reward = parameters.reward
        val p = removeRewardAndUpdateStats(player!!, reward, parameters.rewardType)
        val newPlayer = playerRepository.save(p)
        if (player.level != newPlayer.level) {
            levelDownScheduler.schedule()
        }
        unlockAchievementsUseCase.execute(
            UnlockAchievementsUseCase.Params(
                player = newPlayer,
                eventType = UpdatePlayerStatsUseCase.Params.EventType.ExperienceDecreased(reward.experience.toLong())
            )
        )
        return newPlayer
    }

    private fun removeRewardAndUpdateStats(
        player: Player,
        reward: Reward,
        rewardType: Params.RewardType
    ): Player {
        val newPlayer = player.removeReward(reward)

        val newStats = when (rewardType) {
            Params.RewardType.GOOD_HABIT -> newPlayer.statistics.copy(
                habitCompletedCountForDay = newPlayer.statistics.habitCompletedCountForDay.removeValue(
                    1
                )
            )
            Params.RewardType.QUEST -> newPlayer.statistics.copy(
                questCompletedCountForDay = newPlayer.statistics.questCompletedCountForDay.removeValue(
                    1
                ),
                questCompletedCount = Math.max(newPlayer.statistics.questCompletedCount - 1, 0)
            )
            else -> newPlayer.statistics
        }

        return newPlayer.copy(
            statistics = newStats
        )
    }

    data class Params(val rewardType: RewardType, val reward: Reward, val player: Player? = null) {
        enum class RewardType {
            BAD_HABIT, GOOD_HABIT, QUEST
        }
    }

}