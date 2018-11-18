package io.ipoli.android.habit.usecase

import com.nhaarman.mockito_kotlin.mock
import io.ipoli.android.TestUtil
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.habit.data.CompletedEntry
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.player.attribute.usecase.CheckForOneTimeBoostUseCase
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.usecase.RemoveRewardFromPlayerUseCase
import io.ipoli.android.player.usecase.RewardPlayerUseCase
import org.amshove.kluent.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 6/17/18.
 */
class CompleteHabitUseCaseSpek : Spek({

    describe("CompleteHabitUseCase") {

        val completedEntry = CompletedEntry()

        fun executeUseCase(
            habit: Habit,
            date: LocalDate = LocalDate.now(),
            player: Player = TestUtil.player
        ): Habit {

            val playerRepository = TestUtil.playerRepoMock(TestUtil.player)
            val rewardPlayerUseCase =
                RewardPlayerUseCase(
                    playerRepository,
                    mock(),
                    mock(),
                    CheckForOneTimeBoostUseCase(mock()),
                    RemoveRewardFromPlayerUseCase(playerRepository, mock(), mock())
                )

            return CompleteHabitUseCase(
                TestUtil.habitRepoMock(
                    habit
                ),
                TestUtil.playerRepoMock(player),
                rewardPlayerUseCase,
                mock(),
                mock(),
                mock(),
                mock(),
                mock()
            ).execute(
                CompleteHabitUseCase.Params(habit.id, date)
            )
        }

        it("should add entry in history") {
            val habit = executeUseCase(
                TestUtil.habit
            )
            habit.history.size.`should be`(1)
            habit.history.keys.`should contain`(LocalDate.now())
        }

        it("should check if already complete today") {
            val exec = {
                executeUseCase(
                    TestUtil.habit.copy(
                        days = setOf(DayOfWeek.SUNDAY),
                        history = mapOf(
                            LocalDate.now() to
                                completedEntry.copy(completedAtTimes = listOf(Time.now()))
                        )
                    ),
                    LocalDate.now()
                )
            }
            exec shouldThrow IllegalArgumentException::class
        }

        it("should add entry in existing history") {
            val today = LocalDate.now()
            val habit = executeUseCase(
                TestUtil.habit.copy(
                    history = mapOf(
                        today.minusDays(1) to completedEntry
                    )
                ),
                LocalDate.now()
            )
            habit.history.size.`should be`(2)
            habit.history.keys.`should contain all`(listOf(today.minusDays(1), today))
            habit.history[today]!!.completedCount.`should be`(1)
        }

        it("should add entry with 2 times a day") {
            val today = LocalDate.now()
            val habit = executeUseCase(
                TestUtil.habit.copy(
                    timesADay = 2,
                    history = mapOf(
                        today to completedEntry.copy(completedAtTimes = listOf(Time.now()))
                    )
                ),
                LocalDate.now()
            )
            habit.history[today]!!.completedCount.`should be`(2)
        }

        it("should not add entry when all times a day are completed") {
            val today = LocalDate.now()
            val habit = executeUseCase(
                TestUtil.habit.copy(
                    timesADay = 1,
                    history = mapOf(
                        today to completedEntry
                    )
                ),
                LocalDate.now()
            )
            habit.history[today]!!.completedCount.`should be`(1)
        }

        describe("Rewards") {

            it("should give rewards") {
                val today = LocalDate.now()
                val habit = executeUseCase(
                    TestUtil.habit.copy(
                        isGood = true
                    ),
                    LocalDate.now()
                )
                val ce = habit.history[today]!!
                val r = ce.reward!!
                r.coins.`should be greater than`(0)
                r.experience.`should be greater than`(0)
            }

            it("should not give rewards if not completed") {
                val today = LocalDate.now()
                val habit = executeUseCase(
                    TestUtil.habit.copy(
                        isGood = true,
                        timesADay = 2
                    ),
                    LocalDate.now()
                )
                val ce = habit.history[today]!!
                ce.reward.`should be null`()
            }

            it("should assign rewards if negative") {
                val today = LocalDate.now()
                val habit = executeUseCase(
                    TestUtil.habit.copy(
                        isGood = false
                    ),
                    LocalDate.now()
                )
                val ce = habit.history[today]!!
                val r = ce.reward!!
                r.coins.`should be greater than`(0)
                r.experience.`should be greater than`(0)
            }
        }
    }
})