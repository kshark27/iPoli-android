package io.ipoli.android.repeatingquest.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.common.datetime.datesBetween
import io.ipoli.android.common.datetime.isBetween
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.RepeatingQuest
import io.ipoli.android.quest.data.persistence.QuestRepository
import io.ipoli.android.repeatingquest.entity.RepeatPattern
import io.ipoli.android.repeatingquest.persistence.RepeatingQuestRepository
import org.threeten.bp.LocalDate

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 3/3/18.
 */
class CreatePlaceholderQuestsForRepeatingQuestsUseCase(
    private val questRepository: QuestRepository,
    private val repeatingQuestRepository: RepeatingQuestRepository
) : UseCase<CreatePlaceholderQuestsForRepeatingQuestsUseCase.Params, List<Quest>> {


    override fun execute(parameters: Params): List<Quest> {
        val start = parameters.startDate
        val end = parameters.endDate
        val currentDate = parameters.currentDate

        if (currentDate.isAfter(end)) {
            return listOf()
        }

        val rqs = repeatingQuestRepository.findAllActive(currentDate)

        return rqs.filter { it.isFixed }.map {
            val rqStart = it.start
            if (end.isBefore(rqStart)) {
                return@map listOf<Quest>()
            }

            val rqEnd = it.end
            if (rqEnd != null && start.isAfter(rqEnd)) {
                return@map listOf<Quest>()
            }

            val currStart = if (start.isBefore(rqStart)) rqStart else start
            val currEnd = if (rqEnd != null && rqEnd.isBefore(end)) rqEnd else end

            val scheduleDates = when (it.repeatPattern) {

                is RepeatPattern.Daily -> currStart.datesBetween(currEnd).toSet()

                is RepeatPattern.Weekly ->
                    weeklyDatesToScheduleInPeriod(
                        it.repeatPattern,
                        currStart,
                        currEnd
                    )

                is RepeatPattern.Monthly ->
                    monthlyDatesToScheduleInPeriod(
                        it.repeatPattern,
                        currStart,
                        currEnd
                    )


                is RepeatPattern.Yearly ->
                    yearlyDatesToScheduleInPeriod(
                        it.repeatPattern,
                        currStart,
                        currEnd
                    )

                is RepeatPattern.EveryXDays ->
                    RepeatPattern.everyXDatesToScheduleInPeriod(
                        it.repeatPattern,
                        currStart,
                        currEnd
                    )


                else -> throw IllegalArgumentException("Cannot create placeholders for $it")
            }

            if (scheduleDates.isNotEmpty()) {
                val scheduledQuests =
                    questRepository.findScheduledForRepeatingQuestBetween(it.id, currStart, currEnd)

                val (removed, existing) = scheduledQuests.partition { it.isRemoved }
                val scheduledDateToQuest =
                    existing.associateBy({ it.originalScheduledDate!! }, { it })
                val removedDates = removed.map { it.originalScheduledDate!! }
                val resultDates = scheduleDates - removedDates

                resultDates.filter { !scheduledDateToQuest.containsKey(it) }.map { date ->
                    createQuest(it, date)
                }
            } else {
                listOf()
            }
        }.flatten()
    }

    private fun createQuest(
        rq: RepeatingQuest,
        scheduleDate: LocalDate
    ) =
        Quest(
            name = rq.name,
            color = rq.color,
            icon = rq.icon,
            startTime = rq.startTime,
            duration = rq.duration,
            scheduledDate = scheduleDate,
            reminders = rq.reminders,
            repeatingQuestId = rq.id,
            note = rq.note
        )

    private fun monthlyDatesToScheduleInPeriod(
        repeatPattern: RepeatPattern.Monthly,
        start: LocalDate,
        end: LocalDate
    ): List<LocalDate> {

        var date = start
        val dates = mutableListOf<LocalDate>()
        while (date.isBefore(end.plusDays(1))) {
            if (date.dayOfMonth in repeatPattern.daysOfMonth) {
                dates.add(date)
            }
            date = date.plusDays(1)
        }
        return dates

    }

    private fun weeklyDatesToScheduleInPeriod(
        repeatPattern: RepeatPattern.Weekly,
        start: LocalDate,
        end: LocalDate
    ): List<LocalDate> {

        var date = start
        val dates = mutableListOf<LocalDate>()
        while (date.isBefore(end.plusDays(1))) {
            if (date.dayOfWeek in repeatPattern.daysOfWeek) {
                dates.add(date)
            }
            date = date.plusDays(1)
        }
        return dates

    }

    private fun yearlyDatesToScheduleInPeriod(
        repeatPattern: RepeatPattern.Yearly,
        start: LocalDate,
        end: LocalDate
    ): List<LocalDate> {
        if (start.year == end.year) {
            val date = LocalDate.of(
                start.year,
                repeatPattern.month,
                repeatPattern.dayOfMonth
            )
            return listOf(date).filter { it.isBetween(start, end) }
        }

        var startPeriodDate = start
        val dates = mutableListOf<LocalDate>()
        while (startPeriodDate <= end) {
            val lastDayOfYear = LocalDate.of(startPeriodDate.year, 12, 31)
            val date = LocalDate.of(
                startPeriodDate.year,
                repeatPattern.month,
                repeatPattern.dayOfMonth
            )
            val endPeriodDate = if (end.isBefore(lastDayOfYear)) end else lastDayOfYear
            if (date.isBetween(startPeriodDate, endPeriodDate)) {
                dates.add(date)
            }
            startPeriodDate = LocalDate.of(startPeriodDate.year + 1, 1, 1)
        }
        return dates

    }


    data class Params(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val currentDate: LocalDate = LocalDate.now()
    )
}