package io.ipoli.android.achievement

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import io.ipoli.android.TestUtil
import io.ipoli.android.achievement.usecase.UpdateAchievementProgressUseCase
import io.ipoli.android.common.datetime.hours
import io.ipoli.android.common.datetime.minutes
import io.ipoli.android.pet.Pet
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.pet.PetState
import io.ipoli.android.planday.usecase.CalculateAwesomenessScoreUseCase
import io.ipoli.android.planday.usecase.CalculateFocusDurationUseCase
import io.ipoli.android.player.data.Statistics
import io.ipoli.android.player.persistence.PlayerRepository
import org.amshove.kluent.`should equal`
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate

class UpdateAchievementProgressUseCaseSpek : Spek({

    describe("UpdateAchievementProgressUseCase") {

        fun executeUseCase(
            statistics: Statistics,
            calculateAwesomenessScoreUseCase: CalculateAwesomenessScoreUseCase = mock(),
            calculateFocusDurationUseCase: CalculateFocusDurationUseCase = mock {
                on { execute(any()) } doReturn 1.minutes
            },
            currentDate: LocalDate = LocalDate.now(),
            planDays: Set<DayOfWeek> = emptySet(),
            pet: Pet = Pet("A", PetAvatar.BEAR)
        ): Statistics {

            val p = TestUtil.player
            val player = p.copy(
                statistics = statistics,
                preferences = p.preferences.copy(
                    planDays = planDays
                ),
                pet = pet
            )

            val playerRepoMock = mock<PlayerRepository> {
                on { find() } doReturn player
                on { saveStatistics(any()) } doAnswer { invocation ->
                    invocation.getArgument(0)
                }
            }

            return UpdateAchievementProgressUseCase(
                playerRepoMock,
                calculateAwesomenessScoreUseCase,
                calculateFocusDurationUseCase,
                mock()
            ).execute(
                UpdateAchievementProgressUseCase.Params(currentDate = currentDate)
            )
        }

        it("should reset complete quests for 100 days in a row") {
            val stats = executeUseCase(
                Statistics(
                    questCompletedStreak = Statistics.StreakStatistic(
                        count = 1,
                        lastDate = LocalDate.now().minusDays(2)
                    )
                )
            )
            stats.questCompletedStreak.`should equal`(
                Statistics.StreakStatistic(
                    count = 0,
                    lastDate = null
                )
            )
        }

        it("should not reset complete quests for 100 days in a row") {
            val streakStat = Statistics.StreakStatistic(
                count = 1,
                lastDate = LocalDate.now().minusDays(1)
            )
            val stats = executeUseCase(
                Statistics(
                    questCompletedStreak = streakStat
                )
            )
            stats.questCompletedStreak.`should equal`(streakStat)
        }

        it("should reset complete daily challenge count") {
            val today = LocalDate.now().with(DayOfWeek.SATURDAY)
            val streakStat = Statistics.StreakStatistic(
                count = 5,
                lastDate = today.minusDays(2)
            )
            val stats = executeUseCase(
                statistics = Statistics(
                    dailyChallengeCompleteStreak = streakStat
                ),
                currentDate = today,
                planDays = setOf(DayOfWeek.FRIDAY)
            )
            stats.dailyChallengeCompleteStreak.`should equal`(
                Statistics.StreakStatistic(
                    count = 0,
                    lastDate = null
                )
            )
        }

        it("should not reset complete daily challenge count") {
            val today = LocalDate.now().with(DayOfWeek.SATURDAY)
            val streakStat = Statistics.StreakStatistic(
                count = 5,
                lastDate = today.minusDays(1)
            )
            val stats = executeUseCase(
                statistics = Statistics(
                    dailyChallengeCompleteStreak = streakStat
                ),
                currentDate = today,
                planDays = setOf(DayOfWeek.FRIDAY)
            )
            stats.dailyChallengeCompleteStreak.`should equal`(streakStat)
        }

        it("should not reset complete daily challenge count") {
            val today = LocalDate.now().with(DayOfWeek.SATURDAY)
            val streakStat = Statistics.StreakStatistic(
                count = 5,
                lastDate = today.minusDays(1)
            )
            val stats = executeUseCase(
                statistics = Statistics(
                    dailyChallengeCompleteStreak = streakStat
                ),
                currentDate = today,
                planDays = setOf(DayOfWeek.FRIDAY)
            )
            stats.dailyChallengeCompleteStreak.`should equal`(streakStat)
        }

        it("should increment pet happy streak") {
            val stats = executeUseCase(
                statistics = Statistics(
                    petHappyStateStreak = 0
                ),
                pet = Pet(name = "", avatar = PetAvatar.BEAR, state = PetState.AWESOME)
            )
            stats.petHappyStateStreak.`should equal`(1)
        }

        it("should reset pet happy streak") {
            val stats = executeUseCase(
                statistics = Statistics(
                    petHappyStateStreak = 4
                ),
                pet = Pet(name = "", avatar = PetAvatar.BEAR, state = PetState.SAD)
            )
            stats.petHappyStateStreak.`should equal`(0)
        }

        it("should increment awesomeness score streak") {
            val stats = executeUseCase(
                statistics = Statistics(
                    awesomenessScoreStreak = 4
                ),
                calculateAwesomenessScoreUseCase = mock() {
                    on { execute(any()) } doReturn 4.0
                }
            )
            stats.awesomenessScoreStreak.`should equal`(5)
        }

        it("should reset awesomeness score streak") {
            val stats = executeUseCase(
                statistics = Statistics(
                    awesomenessScoreStreak = 4
                ),
                calculateAwesomenessScoreUseCase = mock() {
                    on { execute(any()) } doReturn 3.99
                }
            )
            stats.awesomenessScoreStreak.`should equal`(0)
        }

        it("should increment focus hours streak") {
            val today = LocalDate.now().with(DayOfWeek.SATURDAY)
            val stats = executeUseCase(
                statistics = Statistics(
                    focusHoursStreak = 4
                ),
                calculateFocusDurationUseCase = mock() {
                    on { execute(any()) } doReturn 5.hours.asMinutes
                },
                currentDate = today,
                planDays = setOf(DayOfWeek.FRIDAY)
            )
            stats.focusHoursStreak.`should equal`(5)
        }

        it("should reset focus hours streak") {
            val today = LocalDate.now().with(DayOfWeek.SATURDAY)
            val stats = executeUseCase(
                statistics = Statistics(
                    focusHoursStreak = 4
                ),
                calculateFocusDurationUseCase = mock() {
                    on { execute(any()) } doReturn 4.hours.asMinutes
                },
                currentDate = today,
                planDays = setOf(DayOfWeek.FRIDAY)
            )
            stats.focusHoursStreak.`should equal`(0)
        }

        it("should reset plan day streak") {
            val today = LocalDate.now().with(DayOfWeek.SATURDAY)
            val streakStat = Statistics.StreakStatistic(
                count = 5,
                lastDate = today.minusDays(2)
            )
            val stats = executeUseCase(
                statistics = Statistics(
                    planDayStreak = streakStat
                ),
                currentDate = today,
                planDays = setOf(DayOfWeek.FRIDAY)
            )
            stats.planDayStreak.`should equal`(
                Statistics.StreakStatistic(
                    count = 0,
                    lastDate = null
                )
            )
        }

        it("should not reset plan day streak") {
            val today = LocalDate.now().with(DayOfWeek.SATURDAY)
            val streakStat = Statistics.StreakStatistic(
                count = 5,
                lastDate = today.minusDays(1)
            )
            val stats = executeUseCase(
                statistics = Statistics(
                    planDayStreak = streakStat
                ),
                currentDate = today,
                planDays = setOf(DayOfWeek.FRIDAY)
            )
            stats.planDayStreak.`should equal`(streakStat)
        }

        it("should not reset plan day streak") {
            val today = LocalDate.now().with(DayOfWeek.SATURDAY)
            val streakStat = Statistics.StreakStatistic(
                count = 5,
                lastDate = today.minusDays(1)
            )
            val stats = executeUseCase(
                statistics = Statistics(
                    planDayStreak = streakStat
                ),
                currentDate = today,
                planDays = setOf(DayOfWeek.FRIDAY)
            )
            stats.planDayStreak.`should equal`(streakStat)
        }
    }

})