package io.ipoli.android.achievement

import io.ipoli.android.TestUtil
import io.ipoli.android.achievement.usecase.UnlockAchievementsUseCase
import io.ipoli.android.achievement.usecase.UpdatePlayerStatsUseCase
import io.ipoli.android.achievement.usecase.UpdatePlayerStatsUseCase.Params
import io.ipoli.android.pet.Food
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.data.Statistics
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should contain`
import org.amshove.kluent.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.threeten.bp.LocalDate

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 6/7/18.
 */

class UnlockAchievementsUseCaseSpek : Spek({

    describe("UnlockAchievementsUseCase") {

        fun executeUseCase(player: Player, eventType: Params.EventType? = null) =
            UnlockAchievementsUseCase(
                TestUtil.playerRepoMock(
                    player
                ),
                mock(),
                UpdatePlayerStatsUseCase(
                    TestUtil.playerRepoMock(
                        player
                    )
                ),
                mock()
            ).execute(
                UnlockAchievementsUseCase.Params(player, eventType)
            )

        it("should unlock Daily Challenge for 30 days streak") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        dailyChallengeCompleteStreak = Statistics.StreakStatistic(
                            count = 9,
                            lastDate = LocalDate.now().minusDays(1)
                        )
                    )
                ),
                Params.EventType.DailyChallengeCompleted
            )

            achievements.size.`should be equal to`(2)
            achievements.`should contain`(Achievement.COMPLETE_DAILY_CHALLENGE_10_DAY_STREAK)
        }

        it("should unlock keep pet happy for 5 days in a row") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        petHappyStateStreak = 5
                    )
                )
            )

            achievements.size.`should be equal to`(1)
            achievements.`should contain`(Achievement.KEEP_PET_HAPPY_5_DAY_STREAK)
        }

        it("should unlock awesomeness score for 5 days streak") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        awesomenessScoreStreak = 5
                    )
                )
            )
            achievements.size.`should be equal to`(1)
            achievements.`should contain`(Achievement.AWESOMENESS_SCORE_5_DAY_STREAK)
        }

        it("should unlock focus hours for 5 days streak") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        focusHoursStreak = 5
                    )
                )
            )
            achievements.size.`should be equal to`(1)
            achievements.`should contain`(Achievement.FOCUS_HOURS_5_DAY_STREAK)
        }

        it("should unlock plan day for 5 days streak") {
            val today = LocalDate.now()
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        planDayStreak = Statistics.StreakStatistic(4, today.minusDays(1))
                    )
                ),
                Params.EventType.DayPlanned
            )
            achievements.size.`should be equal to`(1)
            achievements.`should contain`(Achievement.PLAN_DAY_5_DAY_STREAK)
        }

        it("should not increment plan day streak") {
            val today = LocalDate.now()
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        planDayStreak = Statistics.StreakStatistic(4, today)
                    )
                ),
                Params.EventType.DayPlanned
            )
            achievements.size.`should be equal to`(0)
        }

        it("should unlock first repeating quest created") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        repeatingQuestCreatedCount = 0
                    )
                ),
                eventType = Params.EventType.RepeatingQuestCreated
            )
            achievements.size.`should be equal to`(1)
            achievements.`should contain`(Achievement.FIRST_REPEATING_QUEST_CREATED)
        }

        it("should unlock 5 completed challenges") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        challengeCompletedCount = 4
                    )
                ),
                eventType = Params.EventType.ChallengeCompleted
            )
            achievements.size.`should be equal to`(2)
            achievements.`should contain`(Achievement.COMPLETE_5_CHALLENGES)
        }

        it("should unlock first challenge created") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        challengeCreatedCount = 0
                    )
                ),
                eventType = Params.EventType.ChallengeCreated
            )
            achievements.size.`should be equal to`(1)
            achievements.`should contain`(Achievement.FIRST_CHALLENGE_CREATED)
        }

        it("should unlock 20 gems converted") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        gemConvertedCount = 15
                    )
                ),
                eventType = Params.EventType.GemsConverted(6)
            )
            achievements.size.`should be equal to`(2)
            achievements.`should contain`(Achievement.CONVERT_20_GEMS)
        }

        it("should unlock invite friend") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        friendInvitedCount = 0
                    )
                ),
                eventType = Params.EventType.FriendshipAccepted
            )
            achievements.size.`should be equal to`(1)
            achievements.`should contain`(Achievement.INVITE_1_FRIEND)
        }

        it("should unlock gain 999 xp in a day") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        experienceForToday = 998
                    )
                ),
                eventType = Params.EventType.ExperienceIncreased(1)
            )
            achievements.size.`should be equal to`(1)
            achievements.`should contain`(Achievement.GAIN_999_XP_IN_A_DAY)
        }

        it("should unlock first pet item equipped") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        petItemEquippedCount = 0
                    )
                ),
                eventType = Params.EventType.PetItemEquipped
            )
            achievements.size.`should be equal to`(1)
            achievements.`should contain`(Achievement.FIRST_PET_ITEM_EQUIPPED)
        }

        it("should unlock first avatar changed") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        avatarChangeCount = 0
                    )
                ),
                eventType = Params.EventType.AvatarChanged
            )
            achievements.size.`should be equal to`(1)
            achievements.`should contain`(Achievement.FIRST_AVATAR_CHANGED)
        }

        it("should unlock first pet changed") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        petChangeCount = 0
                    )
                ),
                eventType = Params.EventType.PetChanged
            )
            achievements.size.`should be equal to`(1)
            achievements.`should contain`(Achievement.FIRST_PET_CHANGED)
        }

        it("should unlock pet fed with poop") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        petFedWithPoopCount = 0
                    )
                ),
                eventType = Params.EventType.PetFed(Food.POOP)
            )
            achievements.size.`should be equal to`(2)
            achievements.`should contain`(Achievement.PET_FED_WITH_POOP)
        }

        it("should unlock pet fed") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        petFedCount = 0
                    )
                ),
                eventType = Params.EventType.PetFed(Food.APPLE)
            )
            achievements.size.`should be equal to`(1)
            achievements.`should contain`(Achievement.PET_FED)
        }

        it("should unlock feedback sent") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        feedbackSentCount = 0
                    )
                ),
                eventType = Params.EventType.FeedbackSent
            )
            achievements.size.`should be equal to`(1)
            achievements.`should contain`(Achievement.FEEDBACK_SENT)
        }

        it("should unlock become member") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        joinMembershipCount = 0
                    )
                ),
                eventType = Params.EventType.BecomeMember
            )
            achievements.size.`should be equal to`(1)
            achievements.`should contain`(Achievement.BECAME_PRO)
        }

        it("should unlock first power up activated") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        powerUpActivatedCount = 0
                    )
                ),
                eventType = Params.EventType.PowerUpActivated
            )
            achievements.size.`should be equal to`(1)
            achievements.`should contain`(Achievement.FIRST_POWER_UP_ACTIVATED)
        }

        it("should unlock pet revived") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        petRevivedCount = 0
                    )
                ),
                eventType = Params.EventType.PetRevived
            )
            achievements.size.`should be equal to`(1)
            achievements.`should contain`(Achievement.PET_REVIVED)
        }

        it("should unlock pet died") {
            val achievements = executeUseCase(
                TestUtil.player.copy(
                    achievements = emptyList(),
                    statistics = Statistics(
                        petDiedCount = 1
                    )
                )
            )
            achievements.size.`should be equal to`(1)
            achievements.`should contain`(Achievement.PET_DIED)
        }
    }
})