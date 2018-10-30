package io.ipoli.android.quest.schedule.agenda.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.datetime.datesBetween
import io.ipoli.android.event.Event
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Quest
import org.threeten.bp.LocalDate

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 10/26/18.
 */
class CreateAgendaPreviewItemsUseCase :
    UseCase<CreateAgendaPreviewItemsUseCase.Params, List<CreateAgendaPreviewItemsUseCase.PreviewItem>> {

    override fun execute(parameters: Params): List<CreateAgendaPreviewItemsUseCase.PreviewItem> {
        val dayQuests = mapQuestsToDays(parameters.quests)
        val dayEvents = mapEventsToDays(parameters.events)

        return parameters.startDate.datesBetween(parameters.endDate).map {
            PreviewItem(
                date = it,
                weekIndicators = createWeekIndicators(it, dayQuests, dayEvents),
                monthIndicators = createMonthIndicators(it, dayQuests, dayEvents)
            )
        }
    }

    private fun mapEventsToDays(events: List<Event>): Map<LocalDate, List<Event>> {
        val dayEvents = mutableMapOf<LocalDate, List<Event>>()
        events.forEach {
            val date = it.startDate
            val list =
                if (!dayEvents.containsKey(date)) listOf()
                else dayEvents[date]!!
            dayEvents[date] = list + it
        }
        return dayEvents
    }

    private fun mapQuestsToDays(quests: List<Quest>): Map<LocalDate, List<Quest>> {
        val dayQuests = mutableMapOf<LocalDate, List<Quest>>()
        quests.forEach {
            val date = it.scheduledDate!!
            val list =
                if (!dayQuests.containsKey(date)) listOf()
                else dayQuests[date]!!
            dayQuests[date] = list + it
        }
        return dayQuests
    }

    private fun createMonthIndicators(
        date: LocalDate,
        quests: Map<LocalDate, List<Quest>>,
        events: Map<LocalDate, List<Event>>
    ): List<PreviewItem.MonthIndicator> {
        val indicators = mutableListOf<PreviewItem.MonthIndicator>()
        events[date]?.forEach { e ->
            val d = if (e.isAllDay) Time.MINUTES_IN_A_DAY else e.duration.intValue
            indicators.add(PreviewItem.MonthIndicator.Event(d, e.color))
        }
        quests[date]?.forEach { q ->
            indicators.add(PreviewItem.MonthIndicator.Quest(q.duration, q.color))
        }
        return indicators.sortedByDescending { i -> i.duration }
    }

    private fun createWeekIndicators(
        date: LocalDate,
        quests: Map<LocalDate, List<Quest>>,
        events: Map<LocalDate, List<Event>>
    ): List<PreviewItem.WeekIndicator> {

        val indicators = mutableListOf<PreviewItem.WeekIndicator>()
        quests[date]?.forEach { q ->
            val startTime: Time? = q.startTime

            if (startTime == null) {
                indicators.add(
                    PreviewItem.WeekIndicator.Quest(
                        startMinute = 0,
                        duration = MIN_TIME.minutesTo(MAX_TIME),
                        color = q.color
                    )
                )
            } else if (!(startTime < MIN_TIME && q.endTime!! < MIN_TIME)) {

                val (startMinute, duration) =
                    when {
                        startTime < MIN_TIME -> Pair(
                            MIN_TIME.toMinuteOfDay(),
                            MIN_TIME.minutesTo(q.endTime!!)
                        )
                        q.endTime!! < MIN_TIME -> Pair(
                            startTime.toMinuteOfDay(),
                            startTime.minutesTo(MAX_TIME)
                        )
                        else -> Pair(startTime.toMinuteOfDay(), q.duration)
                    }
                indicators.add(
                    PreviewItem.WeekIndicator.Quest(
                        startMinute = startMinute - MINUTES_OFFSET,
                        duration = duration,
                        color = q.color
                    )
                )
            }
        }

        events[date]?.forEach { e ->
            val startTime: Time = e.startTime
            if (!(startTime < MIN_TIME && e.endTime < MIN_TIME)) {

                val (startMinute, duration) =
                    when {
                        e.isAllDay -> Pair(
                            MIN_TIME.toMinuteOfDay(),
                            MIN_TIME.minutesTo(MAX_TIME)
                        )
                        startTime < MIN_TIME -> Pair(
                            MIN_TIME.toMinuteOfDay(),
                            MIN_TIME.minutesTo(e.endTime)
                        )
                        e.endTime < MIN_TIME -> Pair(
                            startTime.toMinuteOfDay(),
                            startTime.minutesTo(MAX_TIME)
                        )
                        else -> Pair(startTime.toMinuteOfDay(), e.duration.intValue)
                    }
                indicators.add(
                    PreviewItem.WeekIndicator.Event(
                        startMinute = startMinute - MINUTES_OFFSET,
                        duration = duration,
                        color = e.color
                    )
                )
            }
        }

        return indicators.sortedWith(
            compareBy<PreviewItem.WeekIndicator> { i -> i.startMinute }
                .thenByDescending { i -> i.duration }
        )
    }


    companion object {
        const val MINUTES_OFFSET = 8 * 60
        val MIN_TIME = Time.atHours(8)
        val MAX_TIME = Time.at(23, 59)
    }

    data class PreviewItem(
        val date: LocalDate,
        val weekIndicators: List<WeekIndicator>,
        val monthIndicators: List<MonthIndicator>
    ) {
        sealed class WeekIndicator {
            abstract val duration: Int
            abstract val startMinute: Int

            data class Quest(
                val color: Color,
                override val duration: Int,
                override val startMinute: Int
            ) : WeekIndicator()

            data class Event(
                val color: Int,
                override val duration: Int,
                override val startMinute: Int
            ) : WeekIndicator()
        }

        sealed class MonthIndicator {

            abstract val duration: Int

            data class Quest(override val duration: Int, val color: Color) : MonthIndicator()
            data class Event(override val duration: Int, val color: Int) : MonthIndicator()
        }
    }

    data class Params(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val quests: List<Quest>,
        val events: List<Event>
    )
}