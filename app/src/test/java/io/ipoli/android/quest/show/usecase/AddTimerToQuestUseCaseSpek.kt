package io.ipoli.android.quest.show.usecase

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import io.ipoli.android.Constants
import io.ipoli.android.TestUtil
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.TimeRange
import io.ipoli.android.quest.data.persistence.QuestRepository
import io.ipoli.android.quest.show.pomodoros
import org.amshove.kluent.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.threeten.bp.Instant

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 1/22/18.
 */
class AddTimerToQuestUseCaseSpek : Spek({

    describe("AddTimerToQuestUseCase") {

        fun executeUseCase(
            quest: Quest,
            isPomodoro: Boolean,
            time: Instant = Instant.now(),
            startedQuests: List<Quest> = listOf()
        ): AddTimerToQuestUseCase.Result {
            val questRepoMock = mock<QuestRepository> {
                on { findById(any()) } doReturn quest
                on { save(any<Quest>()) } doAnswer { invocation ->
                    invocation.getArgument(0)
                }
                on { findStartedQuests() } doReturn startedQuests
            }
            return AddTimerToQuestUseCase(questRepoMock, mock(), mock(), mock())
                .execute(AddTimerToQuestUseCase.Params(quest.id, isPomodoro, time))
        }

        val quest = TestUtil.quest

        val now = Instant.now()

        it("should save countdown timer") {
            val result = executeUseCase(quest, false, now)
            result.otherTimerStopped.`should be false`()
            val timeRanges = result.quest.timeRanges
            timeRanges.size.`should be equal to`(1)
            timeRanges.first().type.`should be`(TimeRange.Type.COUNTDOWN)
            timeRanges.first().start.`should equal`(now)
        }

        it("should add the first time range") {
            val result = executeUseCase(quest, true)
            result.otherTimerStopped.`should be false`()
            result.quest.timeRanges.size `should be equal to` (1)
            val range = result.quest.timeRanges.first()
            range.start.`should not be null`()
            range.end.`should be null`()
        }

        it("should require not starter countdown timer") {
            val exec = {
                executeUseCase(
                    quest.copy(
                        timeRanges = listOf(
                            TimeRange(
                                TimeRange.Type.COUNTDOWN,
                                quest.duration,
                                now
                            )
                        )
                    ),
                    false,
                    now
                )
            }
            exec shouldThrow IllegalArgumentException::class
        }

        it("should require not starter pomodoro timer") {
            val exec = {
                executeUseCase(
                    quest.copy(
                        timeRanges = listOf(
                            TimeRange(
                                TimeRange.Type.POMODORO_WORK,
                                Constants.DEFAULT_POMODORO_WORK_DURATION,
                                now
                            )
                        )
                    ),
                    true,
                    now
                )
            }
            exec shouldThrow IllegalArgumentException::class
        }

        it("should stop other countdown timer") {
            val result = executeUseCase(
                quest = quest,
                isPomodoro = false,
                time = now,
                startedQuests = listOf(
                    quest.copy(
                        timeRanges = listOf(
                            TimeRange(
                                TimeRange.Type.COUNTDOWN,
                                quest.duration,
                                now
                            )
                        )
                    )
                )
            )
            result.otherTimerStopped.`should be true`()
            result.quest.hasCountDownTimer.`should be true`()
        }

        it("should stop other pomodoro timer") {
            val result = executeUseCase(
                quest = quest,
                isPomodoro = true,
                time = now,
                startedQuests = listOf(
                    quest.copy(
                        timeRanges = listOf(
                            TimeRange(
                                TimeRange.Type.POMODORO_WORK,
                                1.pomodoros(),
                                now
                            )
                        )
                    )
                )
            )
            result.otherTimerStopped.`should be true`()
            result.quest.hasPomodoroTimer.`should be true`()
        }
    }
})