package io.ipoli.android.repeatingquest.usecase

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import io.ipoli.android.TestUtil
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.RepeatingQuest
import io.ipoli.android.quest.data.persistence.QuestRepository
import io.ipoli.android.repeatingquest.entity.RepeatPattern
import org.amshove.kluent.`should equal`
import org.amshove.kluent.shouldThrow
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.xit
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate
import org.threeten.bp.temporal.TemporalAdjusters

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 3/2/18.
 */
class CreateRepeatingQuestHistoryUseCaseSpek : Spek({

    describe("CreateRepeatingQuestHistoryUseCase") {

        fun executeUseCase(
            questRepo: QuestRepository,
            repeatingQuest: RepeatingQuest,
            start: LocalDate,
            end: LocalDate,
            currentDate: LocalDate = LocalDate.now()
        ) =
            CreateRepeatingQuestHistoryUseCase(questRepo,
                mock {
                    on { findById(any()) } doReturn repeatingQuest
                }).execute(
                CreateRepeatingQuestHistoryUseCase.Params(
                    "",
                    start,
                    end,
                    currentDate
                )
            )

        it("should require end to be after startDate") {
            val exec = {
                executeUseCase(
                    TestUtil.questRepoMock(),
                    TestUtil.repeatingQuest,
                    LocalDate.now(),
                    LocalDate.now().minusDays(1)
                )
            }
            exec shouldThrow IllegalArgumentException::class
        }

        it("should return completed") {
            val date = LocalDate.now()
            val questRepoMock = mock<QuestRepository> {
                on {
                    findScheduledForRepeatingQuestInPeriod(
                        any(),
                        any(),
                        any()
                    )
                } doReturn listOf(
                    TestUtil.quest.copy(
                        completedAtDate = date
                    )
                )
            }
            val result = executeUseCase(
                questRepoMock, TestUtil.repeatingQuest.copy(
                    repeatPattern = RepeatPattern.Daily()
                ), date, date
            ).data
            result[date].`should equal`(CreateRepeatingQuestHistoryUseCase.DateHistory.DONE_ON_SCHEDULE)
        }

        it("should return not completed") {
            val date = LocalDate.now().minusDays(1)
            val questRepoMock = mock<QuestRepository> {
                on {
                    findScheduledForRepeatingQuestInPeriod(
                        any(),
                        any(),
                        any()
                    )
                } doReturn listOf<Quest>()
            }
            val result = executeUseCase(
                questRepoMock, TestUtil.repeatingQuest.copy(
                    repeatPattern = RepeatPattern.Daily(
                        startDate = date,
                        endDate = date.plusDays(1)
                    )
                ), date, date.plusDays(1)
            ).data
            result[date].`should equal`(CreateRepeatingQuestHistoryUseCase.DateHistory.FAILED)
        }

        xit("should return empty") {
            val date = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))
            val questRepoMock = mock<QuestRepository> {
                on {
                    findScheduledForRepeatingQuestInPeriod(
                        any(),
                        any(),
                        any()
                    )
                } doReturn listOf<Quest>()
            }
            val result = executeUseCase(
                questRepoMock, TestUtil.repeatingQuest.copy(
                    repeatPattern = RepeatPattern.Weekly(
                        setOf(DayOfWeek.MONDAY),
                        date.minusDays(1)
                    )
                ), date, date, date.minusDays(1)
            ).data
            result[date].`should equal`(CreateRepeatingQuestHistoryUseCase.DateHistory.EMPTY)
        }

        xit("should return completed not on schedule") {
            val date = LocalDate.now().with(DayOfWeek.TUESDAY)
            val questRepoMock = mock<QuestRepository> {
                on {
                    findScheduledForRepeatingQuestInPeriod(
                        any(),
                        any(),
                        any()
                    )
                } doReturn listOf(
                    TestUtil.quest.copy(
                        completedAtDate = date
                    )
                )
            }
            val result = executeUseCase(
                questRepoMock, TestUtil.repeatingQuest.copy(
                    repeatPattern = RepeatPattern.Weekly(setOf(DayOfWeek.MONDAY))
                ), date, date
            ).data
            result[date].`should equal`(CreateRepeatingQuestHistoryUseCase.DateHistory.DONE_NOT_ON_SCHEDULE)
        }

        xit("should return completed not on schedule after repeating quest end") {
            val today = LocalDate.now()
            val tomorrow = LocalDate.now().plusDays(1)
            val questRepoMock = mock<QuestRepository> {
                on {
                    findScheduledForRepeatingQuestInPeriod(
                        any(),
                        any(),
                        any()
                    )
                } doReturn listOf(
                    TestUtil.quest.copy(
                        completedAtDate = tomorrow
                    )
                )
            }
            val result = executeUseCase(
                questRepoMock, TestUtil.repeatingQuest.copy(
                    repeatPattern = RepeatPattern.Daily(today.minusDays(1), today)
                ), tomorrow, tomorrow
            ).data
            result[tomorrow].`should equal`(CreateRepeatingQuestHistoryUseCase.DateHistory.DONE_NOT_ON_SCHEDULE)
        }

        it("should return states for 2 dates") {
            val today = LocalDate.now()
            val tomorrow = LocalDate.now().plusDays(1)
            val questRepoMock = mock<QuestRepository> {
                on {
                    findScheduledForRepeatingQuestInPeriod(
                        any(),
                        any(),
                        any()
                    )
                } doReturn listOf(
                    TestUtil.quest.copy(
                        completedAtDate = tomorrow
                    )
                )
            }
            val result = executeUseCase(
                questRepo = questRepoMock,
                repeatingQuest = TestUtil.repeatingQuest.copy(
                    repeatPattern = RepeatPattern.Daily()
                ),
                start = today,
                end = tomorrow,
                currentDate = tomorrow.plusDays(1)
            ).data
            result.size.`should equal`(2)
            result[today].`should equal`(CreateRepeatingQuestHistoryUseCase.DateHistory.FAILED)
            result[tomorrow].`should equal`(CreateRepeatingQuestHistoryUseCase.DateHistory.DONE_ON_SCHEDULE)
        }
    }
})