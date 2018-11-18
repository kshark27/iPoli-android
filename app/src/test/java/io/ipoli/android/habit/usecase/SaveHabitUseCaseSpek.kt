package io.ipoli.android.habit.usecase

import com.nhaarman.mockito_kotlin.mock
import io.ipoli.android.TestUtil
import io.ipoli.android.common.Reward
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.habit.data.CompletedEntry
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.usecase.RemoveRewardFromPlayerUseCase
import io.ipoli.android.player.usecase.RewardPlayerUseCase
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import io.ipoli.android.quest.Quest
import org.amshove.kluent.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 8/21/18.
 */
class SaveHabitUseCaseSpek : Spek({

    describe("SaveHabitUseCase") {

        fun executeUseCase(
            params: SaveHabitUseCase.Params,
            habit: Habit,
            rewardPlayerUseCase: RewardPlayerUseCase = mock(),
            removeRewardFromPlayerUseCase: RemoveRewardFromPlayerUseCase = mock(),
            player: Player = TestUtil.player
        ) =
            SaveHabitUseCase(
                TestUtil.habitRepoMock(
                    habit
                ),
                TestUtil.playerRepoMock(player),
                rewardPlayerUseCase,
                removeRewardFromPlayerUseCase,
                mock()
            ).execute(
                params
            )

        it("should remove reward from player") {
            val removeRewardFromPlayerUseCaseMock = mock<RemoveRewardFromPlayerUseCase>()
            val r = Reward(
                attributePoints = emptyMap(),
                healthPoints = 0,
                experience = 10,
                coins = 1,
                bounty = Quest.Bounty.None
            )
            executeUseCase(
                params = SaveHabitUseCase.Params(
                    id = "AAA",
                    timesADay = 2,
                    name = "",
                    color = Color.LIME,
                    icon = Icon.DROP,
                    days = DayOfWeek.values().toSet(),
                    isGood = true,
                    reminders = emptyList()
                ),
                habit = TestUtil.habit.copy(
                    id = "AAA",
                    days = DayOfWeek.values().toSet(),
                    timesADay = 1,
                    history = mapOf(
                        LocalDate.now() to
                            CompletedEntry().copy(
                                completedAtTimes = listOf(Time.at(12, 45)),
                                reward = r
                            )
                    )
                ),
                player = TestUtil.player.copy(
                    preferences = TestUtil.player.preferences.copy(
                        resetDayTime = Time.at(12, 30)
                    )
                ),
                removeRewardFromPlayerUseCase = removeRewardFromPlayerUseCaseMock
            )

            Verify on removeRewardFromPlayerUseCaseMock that removeRewardFromPlayerUseCaseMock.execute(
                RemoveRewardFromPlayerUseCase.Params(
                    RemoveRewardFromPlayerUseCase.Params.RewardType.GOOD_HABIT,
                    r
                )
            ) was called
        }

        it("should not remove reward from player") {
            val removeRewardFromPlayerUseCaseMock = mock<RemoveRewardFromPlayerUseCase>()
            executeUseCase(
                params = SaveHabitUseCase.Params(
                    id = "AAA",
                    timesADay = 3,
                    name = "",
                    color = Color.LIME,
                    icon = Icon.DROP,
                    days = DayOfWeek.values().toSet(),
                    isGood = true,
                    reminders = emptyList()
                ),
                habit = TestUtil.habit.copy(
                    id = "AAA",
                    days = DayOfWeek.values().toSet(),
                    timesADay = 8,
                    history = mapOf()
                ),
                player = TestUtil.player.copy(
                    preferences = TestUtil.player.preferences.copy(
                        resetDayTime = Time.at(12, 30)
                    )
                ),
                removeRewardFromPlayerUseCase = removeRewardFromPlayerUseCaseMock
            )

            val expectedReward =
                Reward(emptyMap(), 0, 10, 1, Quest.Bounty.None)
            `Verify not called` on removeRewardFromPlayerUseCaseMock that removeRewardFromPlayerUseCaseMock.execute(
                RemoveRewardFromPlayerUseCase.Params(
                    RemoveRewardFromPlayerUseCase.Params.RewardType.GOOD_HABIT,
                    expectedReward
                )
            )
        }

        it("should not change reward for player") {
            val removeRewardFromPlayerUseCaseMock = mock<RemoveRewardFromPlayerUseCase>()
            val rewardPlayerUseCaseMock = mock<RewardPlayerUseCase>()
            executeUseCase(
                params = SaveHabitUseCase.Params(
                    id = "AAA",
                    timesADay = 2,
                    name = "",
                    color = Color.LIME,
                    icon = Icon.DROP,
                    days = DayOfWeek.values().toSet(),
                    isGood = true,
                    reminders = emptyList()
                ),
                habit = TestUtil.habit.copy(
                    id = "AAA",
                    days = DayOfWeek.values().toSet(),
                    timesADay = 3,
                    history = mapOf(
                        LocalDate.now() to CompletedEntry(listOf(Time.atHours(12)))
                    )
                ),
                player = TestUtil.player.copy(
                    preferences = TestUtil.player.preferences.copy(
                        resetDayTime = Time.at(0, 30)
                    )
                ),
                removeRewardFromPlayerUseCase = removeRewardFromPlayerUseCaseMock,
                rewardPlayerUseCase = rewardPlayerUseCaseMock
            )

            val expectedReward =
                Reward(emptyMap(), 0, 10, 1, Quest.Bounty.None)
            `Verify not called` on removeRewardFromPlayerUseCaseMock that removeRewardFromPlayerUseCaseMock.execute(
                RemoveRewardFromPlayerUseCase.Params(
                    RemoveRewardFromPlayerUseCase.Params.RewardType.GOOD_HABIT,
                    expectedReward
                )
            )
//            `Verify not called` on rewardPlayerUseCaseMock that rewardPlayerUseCaseMock.execute(
//                expectedReward
//            )
        }

        it("should update preferenceHistory when timesADay changes") {
            val newTimesADay = 2
            val h = executeUseCase(
                params = SaveHabitUseCase.Params(
                    id = "AAA",
                    timesADay = newTimesADay,
                    name = "",
                    color = Color.LIME,
                    icon = Icon.DROP,
                    days = DayOfWeek.values().toSet(),
                    isGood = true,
                    reminders = emptyList()
                ),
                habit = TestUtil.habit.copy(
                    id = "AAA",
                    days = DayOfWeek.values().toSet(),
                    timesADay = 3,
                    preferenceHistory = TestUtil.habit.preferenceHistory.copy(
                        timesADay = sortedMapOf(LocalDate.now().minusDays(1) to 3)
                    )
                )
            )

            h.preferenceHistory.timesADay.size.`should be equal to`(2)
            val expectedTimesADayHistory = sortedMapOf(
                LocalDate.now().minusDays(1) to 3,
                LocalDate.now() to newTimesADay
            )
            h.preferenceHistory.timesADay.`should equal`(expectedTimesADayHistory)
        }

        it("should update preferenceHistory when days changes") {
            val newDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
            val h = executeUseCase(
                params = SaveHabitUseCase.Params(
                    id = "AAA",
                    timesADay = 2,
                    name = "",
                    color = Color.LIME,
                    icon = Icon.DROP,
                    days = newDays,
                    isGood = true,
                    reminders = emptyList()
                ),
                habit = TestUtil.habit.copy(
                    id = "AAA",
                    days = DayOfWeek.values().toSet(),
                    preferenceHistory = TestUtil.habit.preferenceHistory.copy(
                        days = sortedMapOf(LocalDate.now().minusDays(1) to DayOfWeek.values().toSet())
                    )
                )
            )

            h.preferenceHistory.days.size.`should be equal to`(2)
            val expectedDayHistory = sortedMapOf(
                LocalDate.now().minusDays(1) to DayOfWeek.values().toSet(),
                LocalDate.now() to newDays
            )
            h.preferenceHistory.days.`should equal`(expectedDayHistory)
        }
    }

})