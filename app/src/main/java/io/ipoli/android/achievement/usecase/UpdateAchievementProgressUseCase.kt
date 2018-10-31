package io.ipoli.android.achievement.usecase

import io.ipoli.android.Constants
import io.ipoli.android.common.UseCase
import io.ipoli.android.friends.persistence.FriendRepository
import io.ipoli.android.pet.PetState
import io.ipoli.android.planday.usecase.CalculateAwesomenessScoreUseCase
import io.ipoli.android.planday.usecase.CalculateFocusDurationUseCase
import io.ipoli.android.player.data.Statistics
import io.ipoli.android.player.persistence.PlayerRepository
import org.threeten.bp.LocalDate

class UpdateAchievementProgressUseCase(
    private val playerRepository: PlayerRepository,
    private val calculateAwesomenessScoreUseCase: CalculateAwesomenessScoreUseCase,
    private val calculateFocusDurationUseCase: CalculateFocusDurationUseCase,
    private val friendRepository: FriendRepository
) :
    UseCase<UpdateAchievementProgressUseCase.Params, Statistics> {

    override fun execute(parameters: Params): Statistics {
        val player = playerRepository.find()
        requireNotNull(player)
        val stats = player!!.statistics

        val yesterday = parameters.currentDate.minusDays(1)

        val newQuestCompleteStreak = if (yesterday == stats.questCompletedStreak.lastDate) {
            stats.questCompletedStreak
        } else {
            Statistics.StreakStatistic()
        }

        val newDailyChallengeCompleteStreak =
            if (player.preferences.planDays.contains(yesterday.dayOfWeek) && yesterday != stats.dailyChallengeCompleteStreak.lastDate) {
                Statistics.StreakStatistic()
            } else {
                stats.dailyChallengeCompleteStreak
            }

        val petHappyStreak =
            if (player.pet.state == PetState.HAPPY || player.pet.state == PetState.AWESOME) {
                stats.petHappyStateStreak + 1
            } else {
                0
            }

        val aScore =
            calculateAwesomenessScoreUseCase.execute(CalculateAwesomenessScoreUseCase.Params.WithoutQuests())

        val awesomenessScoreStreak = if (aScore >= 4.0) {
            stats.awesomenessScoreStreak + 1
        } else {
            0
        }

        val focusHoursStreak = if (player.preferences.planDays.contains(yesterday.dayOfWeek)) {
            val focusDuration =
                calculateFocusDurationUseCase.execute(CalculateFocusDurationUseCase.Params.WithoutQuests())

            if (focusDuration.asHours.intValue >= Constants.DAILY_FOCUS_HOURS_GOAL) {
                stats.focusHoursStreak + 1
            } else {
                0
            }
        } else {
            stats.focusHoursStreak
        }

        val newPlanDayStreak =
            if (player.preferences.planDays.contains(yesterday.dayOfWeek) && yesterday != stats.planDayStreak.lastDate) {
                Statistics.StreakStatistic()
            } else {
                stats.planDayStreak
            }

        val friendCount = try {
            friendRepository.count().toLong()
        } catch (e: Throwable) {
            stats.friendInvitedCount
        }

        val newStats = stats.copy(
            friendInvitedCount = friendCount,
            questCompletedStreak = newQuestCompleteStreak,
            dailyChallengeCompleteStreak = newDailyChallengeCompleteStreak,
            petHappyStateStreak = petHappyStreak,
            awesomenessScoreStreak = awesomenessScoreStreak,
            focusHoursStreak = focusHoursStreak,
            planDayStreak = newPlanDayStreak,
            experienceForToday = 0,
            petDiedCount = if (player.pet.isDead) stats.petDiedCount + 1 else stats.petDiedCount
        )
        return playerRepository.saveStatistics(newStats)
    }

    data class Params(val currentDate: LocalDate = LocalDate.now())
}