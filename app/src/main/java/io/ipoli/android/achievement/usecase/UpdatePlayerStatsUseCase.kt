package io.ipoli.android.achievement.usecase

import io.ipoli.android.achievement.usecase.UpdatePlayerStatsUseCase.Params.EventType.*
import io.ipoli.android.common.UseCase
import io.ipoli.android.pet.Food
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.persistence.PlayerRepository
import org.threeten.bp.LocalDate

class UpdatePlayerStatsUseCase(
    private val playerRepository: PlayerRepository
) : UseCase<UpdatePlayerStatsUseCase.Params, Player> {

    override fun execute(parameters: Params): Player {
        val player = parameters.player
        val stats = player.statistics
        val currentDate = parameters.currentDate

        val newStats = when (parameters.eventType) {

            DailyChallengeCompleted -> {
                val dcCompleteStreak = stats.dailyChallengeCompleteStreak
                val currentDcStreak = if (currentDate != dcCompleteStreak.lastDate)
                    dcCompleteStreak.copy(
                        count = dcCompleteStreak.count + 1,
                        lastDate = currentDate
                    )
                else dcCompleteStreak

                stats.copy(
                    dailyChallengeCompleteStreak = currentDcStreak,
                    dailyChallengeBestStreak =
                    if (stats.dailyChallengeBestStreak < dcCompleteStreak.count)
                        dcCompleteStreak.count
                    else stats.dailyChallengeBestStreak
                )
            }

            DayPlanned -> {
                val dayPlanStreak = stats.planDayStreak
                stats.copy(
                    planDayStreak = if (currentDate != dayPlanStreak.lastDate) {
                        dayPlanStreak.copy(
                            count = dayPlanStreak.count + 1,
                            lastDate = currentDate
                        )
                    } else {
                        dayPlanStreak
                    }
                )
            }

            RepeatingQuestCreated ->
                stats.copy(
                    repeatingQuestCreatedCount = stats.repeatingQuestCreatedCount + 1
                )

            ChallengeCompleted ->
                stats.copy(
                    challengeCompletedCount = stats.challengeCompletedCount + 1
                )

            ChallengeCreated ->
                stats.copy(
                    challengeCreatedCount = stats.challengeCreatedCount + 1
                )

            is GemsConverted ->
                stats.copy(
                    gemConvertedCount = stats.gemConvertedCount + parameters.eventType.gems
                )

            is FriendshipAccepted ->
                stats.copy(
                    friendInvitedCount = stats.friendInvitedCount + 1
                )

            is ExperienceIncreased ->
                stats.copy(
                    experienceForToday = stats.experienceForToday + parameters.eventType.value
                )

            is ExperienceDecreased ->
                stats.copy(
                    experienceForToday = Math.max(
                        stats.experienceForToday - parameters.eventType.value,
                        0L
                    )
                )

            PetItemEquipped ->
                stats.copy(
                    petItemEquippedCount = stats.petItemEquippedCount + 1
                )

            AvatarChanged ->
                stats.copy(
                    avatarChangeCount = stats.avatarChangeCount + 1
                )

            PetChanged ->
                stats.copy(
                    petChangeCount = stats.petChangeCount + 1
                )

            is PetFed ->
                stats.copy(
                    petFedCount = stats.petFedCount + 1,
                    petFedWithPoopCount = if (parameters.eventType.food == Food.POOP)
                        stats.petFedWithPoopCount + 1
                    else stats.petFedWithPoopCount
                )

            FeedbackSent ->
                stats.copy(
                    feedbackSentCount = stats.feedbackSentCount + 1
                )

            BecomeMember ->
                stats.copy(
                    joinMembershipCount = stats.joinMembershipCount + 1
                )

            PowerUpActivated ->
                stats.copy(
                    powerUpActivatedCount = stats.powerUpActivatedCount + 1
                )

            PetRevived ->
                stats.copy(
                    petRevivedCount = stats.petRevivedCount + 1
                )

            PetDied ->
                stats.copy(
                    petDiedCount = stats.petDiedCount + 1
                )

            else -> stats
        }

        return if (newStats != stats) {
            player.copy(
                statistics = playerRepository.saveStatistics(newStats)
            )
        } else player
    }

    data class Params(
        val player: Player,
        val eventType: EventType? = null,
        val currentDate: LocalDate = LocalDate.now()
    ) {
        sealed class EventType(val count: Int = 1) {
            object DailyChallengeCompleted : EventType()
            object RepeatingQuestCreated : EventType()
            object ChallengeCompleted : EventType()
            object ChallengeCreated : EventType()
            object DayPlanned : EventType()
            data class GemsConverted(val gems: Int) : EventType(gems)
            object FriendshipAccepted : EventType()
            data class ExperienceIncreased(val value: Long) : EventType()
            data class ExperienceDecreased(val value: Long) : EventType()
            object PetItemEquipped : EventType()
            object AvatarChanged : EventType()
            object PetChanged : EventType()
            data class PetFed(val food: Food) : EventType()
            object FeedbackSent : EventType()
            object BecomeMember : EventType()
            object PowerUpActivated : EventType()
            object PetRevived : EventType()
            object PetDied : EventType()
        }
    }
}