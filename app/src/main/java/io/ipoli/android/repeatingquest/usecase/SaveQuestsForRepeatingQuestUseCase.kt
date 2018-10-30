package io.ipoli.android.repeatingquest.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.common.datetime.datesBetween
import io.ipoli.android.common.datetime.isBetween
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.RepeatingQuest
import io.ipoli.android.quest.data.persistence.QuestRepository
import io.ipoli.android.quest.job.ReminderScheduler
import io.ipoli.android.repeatingquest.entity.Period
import io.ipoli.android.repeatingquest.entity.RepeatPattern
import io.ipoli.android.repeatingquest.entity.RepeatPattern.Companion.everyXDatesToScheduleInPeriod
import io.ipoli.android.repeatingquest.entity.RepeatPattern.Companion.findMonthlyPeriods
import io.ipoli.android.repeatingquest.entity.RepeatPattern.Companion.findWeeklyPeriods
import io.ipoli.android.repeatingquest.entity.RepeatPattern.Companion.monthlyDatesToScheduleInPeriod
import io.ipoli.android.repeatingquest.entity.RepeatPattern.Companion.weeklyDatesToScheduleInPeriod
import io.ipoli.android.repeatingquest.entity.RepeatPattern.Companion.yearlyDatesToScheduleInPeriod
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate
import org.threeten.bp.temporal.TemporalAdjusters.nextOrSame

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 02/14/2018.
 */
class SaveQuestsForRepeatingQuestUseCase(
    private val questRepository: QuestRepository,
    private val reminderScheduler: ReminderScheduler
) : UseCase<SaveQuestsForRepeatingQuestUseCase.Params, SaveQuestsForRepeatingQuestUseCase.Result> {

    override fun execute(parameters: Params): SaveQuestsForRepeatingQuestUseCase.Result {
        val rq = parameters.repeatingQuest

        if (parameters.end.isBefore(rq.start)) {
            return Result(listOf(), rq)
        }

        val rqEnd = rq.end
        if (rqEnd != null && parameters.start.isAfter(rqEnd)) {
            return Result(listOf(), rq)
        }

        val start = if (parameters.start.isBefore(rq.start)) rq.start else parameters.start
        val end = if (rqEnd != null && rqEnd.isBefore(parameters.end)) rqEnd else parameters.end

        var newRQ: RepeatingQuest? = null

        val scheduleDates = when (rq.repeatPattern) {

            is RepeatPattern.Daily -> start.datesBetween(end).toSet()


            is RepeatPattern.Weekly ->
                weeklyDatesToScheduleInPeriod(
                    rq.repeatPattern,
                    start,
                    end
                )

            is RepeatPattern.Monthly ->
                monthlyDatesToScheduleInPeriod(
                    rq.repeatPattern,
                    start,
                    end
                )


            is RepeatPattern.Yearly ->
                yearlyDatesToScheduleInPeriod(
                    rq.repeatPattern,
                    start,
                    end
                )

            is RepeatPattern.EveryXDays -> {
                everyXDatesToScheduleInPeriod(
                    rq.repeatPattern,
                    start,
                    end
                )
            }

            is RepeatPattern.Flexible.Weekly -> {
                val scheduledPeriods = rq.repeatPattern.scheduledPeriods.toMutableMap()
                val periods = findWeeklyPeriods(start, end)
                periods.forEach {
                    if (!scheduledPeriods.containsKey(it.start)) {
                        scheduledPeriods[it.start] =
                            generateWeeklyFlexibleDates(rq.repeatPattern, it)
                    }
                }
                val pattern = rq.repeatPattern.copy(
                    scheduledPeriods = scheduledPeriods
                )

                newRQ = rq.copy(
                    repeatPattern = pattern
                )

                flexibleWeeklyToScheduleInPeriod(
                    periods,
                    pattern,
                    start,
                    end
                )
            }


            is RepeatPattern.Flexible.Monthly -> {
                val scheduledPeriods = rq.repeatPattern.scheduledPeriods.toMutableMap()
                val periods = findMonthlyPeriods(start, end)
                periods.forEach {
                    if (!scheduledPeriods.containsKey(it.start)) {
                        scheduledPeriods[it.start] =
                            generateMonthlyFlexibleDates(rq.repeatPattern, it)
                    }
                }

                val pattern = rq.repeatPattern.copy(
                    scheduledPeriods = scheduledPeriods
                )

                newRQ = rq.copy(
                    repeatPattern = pattern
                )

                flexibleMonthlyToScheduleInPeriod(
                    periods,
                    pattern,
                    start,
                    end
                )
            }
        }


        if (scheduleDates.isEmpty()) {
            return Result(listOf(), rq)
        }

        val scheduledQuests =
            questRepository.findScheduledForRepeatingQuestBetween(rq.id, start, end)

        val (removed, existing) = scheduledQuests.partition { it.isRemoved }
        val scheduledDateToQuest = existing.associateBy({ it.originalScheduledDate }, { it })
        val removedDates = removed.map { it.originalScheduledDate!! }

        val resultDates = scheduleDates - removedDates

        val questsToSave = mutableListOf<Quest>()

        val quests = resultDates.map {

            if (scheduledDateToQuest.containsKey(it)) {
                scheduledDateToQuest[it]!!
            } else {
                val q = createQuest(rq, it)
                questsToSave.add(q)
                q
            }
        }

        questRepository.save(questsToSave)
        reminderScheduler.schedule()
        return Result(quests, newRQ ?: rq)
    }

    private fun flexibleMonthlyToScheduleInPeriod(
        periods: List<Period>,
        pattern: RepeatPattern.Flexible.Monthly,
        start: LocalDate,
        end: LocalDate
    ): List<LocalDate> {
        val preferredDays = pattern.preferredDays
        val timesPerMonth = pattern.timesPerMonth
        require(timesPerMonth != preferredDays.size)
        require(timesPerMonth >= 1)
        require(timesPerMonth <= 31)

        val result = mutableListOf<LocalDate>()

        for (p in periods) {
            result.addAll(
                pattern.scheduledPeriods[p.start]!!
                    .filter { it.isBetween(start, end) }
            )
        }

        return result
    }

    private fun generateMonthlyFlexibleDates(
        pattern: RepeatPattern.Flexible.Monthly,
        period: Period
    ): List<LocalDate> {
        val timesPerMonth = pattern.timesPerMonth
        val preferredDays = pattern.preferredDays

        val daysOfMonth =
            (1..period.start.lengthOfMonth()).map { it }.shuffled().take(timesPerMonth)

        val days = if (preferredDays.isNotEmpty()) {
            val scheduledMonthDays = preferredDays.shuffled().take(timesPerMonth)
            val remainingMonthDays =
                (daysOfMonth - scheduledMonthDays).shuffled()
                    .take(timesPerMonth - scheduledMonthDays.size)
            scheduledMonthDays + remainingMonthDays
        } else {
            daysOfMonth.shuffled().take(timesPerMonth)
        }

        return days.map { period.start.withDayOfMonth(it) }
    }

    private fun flexibleWeeklyToScheduleInPeriod(
        periods: List<Period>,
        pattern: RepeatPattern.Flexible.Weekly,
        start: LocalDate,
        end: LocalDate
    ): List<LocalDate> {

        val preferredDays = pattern.preferredDays
        val timesPerWeek = pattern.timesPerWeek
        require(timesPerWeek != preferredDays.size)
        require(timesPerWeek >= 1)
        require(timesPerWeek <= 7)

        val result = mutableListOf<LocalDate>()

        for (p in periods) {
            result.addAll(
                pattern.scheduledPeriods[p.start]!!
                    .filter { it.isBetween(start, end) }
            )
        }

        return result
    }

    private fun generateWeeklyFlexibleDates(
        pattern: RepeatPattern.Flexible.Weekly,
        period: Period
    ): List<LocalDate> {
        val preferredDays = pattern.preferredDays
        val timesPerWeek = pattern.timesPerWeek

        val daysOfWeek = DayOfWeek.values().toList()
        val days = if (preferredDays.isNotEmpty()) {
            val scheduledWeekDays = preferredDays.shuffled().take(timesPerWeek)
            val remainingWeekDays =
                (daysOfWeek - scheduledWeekDays).shuffled()
                    .take(timesPerWeek - scheduledWeekDays.size)
            scheduledWeekDays + remainingWeekDays
        } else {
            daysOfWeek.shuffled().take(timesPerWeek)
        }
        return days.map { period.start.with(nextOrSame(it)) }

    }

    private fun createQuest(
        rq: RepeatingQuest,
        scheduleDate: LocalDate
    ) =
        Quest(
            name = rq.name,
            subQuests = rq.subQuests,
            color = rq.color,
            icon = rq.icon,
            startTime = rq.startTime,
            duration = rq.duration,
            priority = rq.priority,
            preferredStartTime = rq.preferredStartTime,
            scheduledDate = scheduleDate,
            reminders = rq.reminders,
            repeatingQuestId = rq.id,
            challengeId = rq.challengeId,
            tags = rq.tags
        )

    data class Result(val quests: List<Quest>, val repeatingQuest: RepeatingQuest)

    /**
     * @startDate inclusive
     * @end inclusive
     */
    data class Params(
        val repeatingQuest: RepeatingQuest,
        val start: LocalDate,
        val end: LocalDate
    )
}