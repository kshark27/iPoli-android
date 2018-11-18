package io.ipoli.android.habit.usecase

import com.nhaarman.mockito_kotlin.mock
import io.ipoli.android.TestUtil
import io.ipoli.android.common.Reward
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.habit.data.CompletedEntry
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.player.usecase.RemoveRewardFromPlayerUseCase
import io.ipoli.android.quest.Quest
import org.amshove.kluent.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.threeten.bp.LocalDate

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 6/17/18.
 */
class UndoCompleteHabitUseCaseSpek : Spek({


    describe("UndoCompleteHabitUseCase") {
        val completedEntry =
            CompletedEntry(
                completedAtTimes = listOf(Time.now()),
                reward = Reward(emptyMap(), 0, 1, 1, Quest.Bounty.None)
            )

        fun executeUseCase(
            habit: Habit,
            date: LocalDate = LocalDate.now(),
            removeRewardFromPlayerUseCase: RemoveRewardFromPlayerUseCase = mock()
        ) =
            UndoCompleteHabitUseCase(
                TestUtil.habitRepoMock(
                    habit
                ),
                removeRewardFromPlayerUseCase
            ).execute(
                UndoCompleteHabitUseCase.Params(habit.id, date)
            )

        it("should not remove from empty history") {
            val habit = executeUseCase(
                TestUtil.habit
            )
            habit.history.`should be empty`()
        }

        it("should remove time") {
            val today = LocalDate.now()
            val habit = executeUseCase(
                TestUtil.habit.copy(
                    history = mapOf(today to completedEntry)
                ),
                LocalDate.now()
            )
            habit.history[today]!!.completedAtTimes.`should be empty`()
        }

        it("should remove time with more than 1 times a day") {
            val today = LocalDate.now()
            val now = Time.now()

            val habit = executeUseCase(
                TestUtil.habit.copy(
                    timesADay = 2,
                    history = mapOf(
                        today to completedEntry.copy(
                            completedAtTimes = listOf(
                                now,
                                now.plus(30)
                            )
                        )
                    )
                ),
                LocalDate.now()
            )
            val completedAtTimes = habit.history[today]!!.completedAtTimes
            completedAtTimes.size.`should be equal to`(1)
            completedAtTimes.first().`should equal`(now)
        }

        describe("Rewards") {
            it("should not remove rewards") {
                val today = LocalDate.now()
                val habit = executeUseCase(
                    TestUtil.habit.copy(
                        history = mapOf(
                            today to CompletedEntry(
                                completedAtTimes = listOf(Time.now()),
                                reward = completedEntry.reward
                            )
                        )
                    ),
                    today
                )
                habit.history[today]!!.reward.`should not be null`()
            }
        }

        it("should remove bounty when was already completed") {

            val removeRewardFromPlayerUseCaseMock = mock<RemoveRewardFromPlayerUseCase>()
            val today = LocalDate.now()
            executeUseCase(
                TestUtil.habit.copy(
                    history = mapOf(
                        today to completedEntry
                    )
                ),
                today,
                removeRewardFromPlayerUseCaseMock
            )
            Verify on removeRewardFromPlayerUseCaseMock that removeRewardFromPlayerUseCaseMock.execute(
                RemoveRewardFromPlayerUseCase.Params(
                    RemoveRewardFromPlayerUseCase.Params.RewardType.GOOD_HABIT,
                    completedEntry.reward!!
                )
            ) was called
        }

        it("should not remove bounty when was not already completed") {

            val removeRewardFromPlayerUseCaseMock = mock<RemoveRewardFromPlayerUseCase>()
            val today = LocalDate.now()
            executeUseCase(
                TestUtil.habit.copy(
                    history = mapOf(
                        today to completedEntry
                    ),
                    timesADay = 2
                ),
                today,
                removeRewardFromPlayerUseCaseMock
            )
            `Verify not called` on removeRewardFromPlayerUseCaseMock that removeRewardFromPlayerUseCaseMock.execute(
                RemoveRewardFromPlayerUseCase.Params(
                    RemoveRewardFromPlayerUseCase.Params.RewardType.GOOD_HABIT,
                    completedEntry.reward!!
                )
            )
        }

        it("should not remove bounty on negative habit") {

            val removeRewardFromPlayerUseCaseMock = mock<RemoveRewardFromPlayerUseCase>()
            val today = LocalDate.now()
            executeUseCase(
                TestUtil.habit.copy(
                    history = mapOf(
                        today to completedEntry
                    ),
                    isGood = false
                ),
                today,
                removeRewardFromPlayerUseCaseMock
            )
            `Verify not called` on removeRewardFromPlayerUseCaseMock that removeRewardFromPlayerUseCaseMock.execute(
                RemoveRewardFromPlayerUseCase.Params(
                    RemoveRewardFromPlayerUseCase.Params.RewardType.GOOD_HABIT,
                    completedEntry.reward!!
                )
            )
        }

        it("should remove time with more than 1 times a day") {
            val today = LocalDate.now()
            val now = Time.now()

            val habit = executeUseCase(
                TestUtil.habit.copy(
                    timesADay = 2,
                    history = mapOf(
                        today to completedEntry.copy(
                            completedAtTimes = listOf(
                                now,
                                now.plus(30)
                            )
                        )
                    )
                ),
                LocalDate.now()
            )
            val completedAtTimes = habit.history[today]!!.completedAtTimes
            completedAtTimes.size.`should be equal to`(1)
            completedAtTimes.first().`should equal`(now)
        }

    }
})