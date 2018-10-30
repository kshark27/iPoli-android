package io.ipoli.android.repeatingquest.entity

import io.ipoli.android.common.datetime.datesBetween
import io.ipoli.android.common.datetime.daysUntil
import io.ipoli.android.common.datetime.isBetween
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate
import org.threeten.bp.Month
import org.threeten.bp.temporal.TemporalAdjusters

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 02/14/2018.
 */

sealed class RepeatPattern(
    open val startDate: LocalDate,
    open val endDate: LocalDate?
) {

    data class Daily(
        override val startDate: LocalDate = LocalDate.now(),
        override val endDate: LocalDate? = null
    ) : RepeatPattern(startDate, endDate) {
        override fun periodRangeFor(date: LocalDate) =
            PeriodRange(
                start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                end = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            )

        override val periodCount get() = DayOfWeek.values().size

        override fun nextDateWithoutRange(from: LocalDate) = from
    }

    data class Yearly(
        val dayOfMonth: Int,
        val month: Month,
        override val startDate: LocalDate = LocalDate.now(),
        override val endDate: LocalDate? = null
    ) : RepeatPattern(startDate, endDate) {
        override fun periodRangeFor(date: LocalDate) =
            PeriodRange(
                start = date.with(TemporalAdjusters.firstDayOfYear()),
                end = date.with(TemporalAdjusters.lastDayOfYear())
            )

        override val periodCount get() = 1

        override fun nextDateWithoutRange(from: LocalDate): LocalDate =
            LocalDate.of(from.year, month, dayOfMonth).let {
                when {
                    it.isBefore(from) -> it.plusYears(1)
                    else -> it
                }
            }
    }

    data class Weekly(
        val daysOfWeek: Set<DayOfWeek>,
        override val startDate: LocalDate = LocalDate.now(),
        override val endDate: LocalDate? = null
    ) : RepeatPattern(startDate, endDate) {
        override fun periodRangeFor(date: LocalDate) =
            PeriodRange(
                start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                end = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            )

        override val periodCount get() = daysOfWeek.size

        override fun nextDateWithoutRange(from: LocalDate): LocalDate? {
            require(daysOfWeek.isNotEmpty())
            var nextDate = from
            while (true) {
                if (daysOfWeek.contains(nextDate.dayOfWeek)) {
                    return nextDate
                }
                nextDate = nextDate.plusDays(1)
            }
        }
    }

    data class Monthly(
        val daysOfMonth: Set<Int>,
        override val startDate: LocalDate = LocalDate.now(),
        override val endDate: LocalDate? = null
    ) : RepeatPattern(startDate, endDate) {

        override fun periodRangeFor(date: LocalDate) =
            PeriodRange(
                start = date.with(TemporalAdjusters.firstDayOfMonth()),
                end = date.with(TemporalAdjusters.lastDayOfMonth())
            )

        override val periodCount get() = daysOfMonth.size

        override fun nextDateWithoutRange(from: LocalDate): LocalDate? {
            require(daysOfMonth.isNotEmpty())
            var nextDate = from

            while (true) {
                if (daysOfMonth.contains(nextDate.dayOfMonth)) {
                    return nextDate
                }
                nextDate = nextDate.plusDays(1)
            }
        }
    }

    data class EveryXDays(
        val xDays: Int,
        override val startDate: LocalDate = LocalDate.now(),
        override val endDate: LocalDate? = null
    ) : RepeatPattern(
        startDate, endDate
    ) {
        override val periodCount: Int
            get() {
                val today = LocalDate.now()
                val periodStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val periodEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                return periodStart.datesBetween(periodEnd).sumBy { date ->
                    if (shouldBeDoneOn(date)) 1
                    else 0
                }
            }

        override fun periodRangeFor(date: LocalDate) = PeriodRange(
            start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            end = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        )

        override fun nextDateWithoutRange(from: LocalDate): LocalDate? {
            var nextDate = from
            while (true) {
                if (shouldBeDoneOn(nextDate)) {
                    return nextDate
                }
                nextDate = nextDate.plusDays(1)
            }
        }

        fun shouldBeDoneOn(date: LocalDate) =
            startDate.daysUntil(date) % xDays == 0L
    }

    sealed class Flexible(
        override val startDate: LocalDate,
        override val endDate: LocalDate?,
        open val scheduledPeriods: Map<LocalDate, List<LocalDate>> = mapOf()
    ) : RepeatPattern(startDate, endDate) {

        data class Weekly(
            val timesPerWeek: Int,
            val preferredDays: Set<DayOfWeek> = DayOfWeek.values().toSet(),
            override val scheduledPeriods: Map<LocalDate, List<LocalDate>> = mapOf(),
            override val startDate: LocalDate = LocalDate.now(),
            override val endDate: LocalDate? = null
        ) : Flexible(startDate, endDate) {

            override fun periodRangeFor(date: LocalDate) =
                PeriodRange(
                    start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    end = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                )

            override val periodCount get() = timesPerWeek

            override fun nextDateWithoutRange(from: LocalDate): LocalDate? {
                require(scheduledPeriods.isNotEmpty())

                val periodStart =
                    from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                if (!scheduledPeriods.containsKey(periodStart)) {
                    return null
                }

                val nextDate =
                    scheduledPeriods[periodStart]!!.sorted().firstOrNull { !it.isBefore(from) }
                return nextDate ?: firstDateForNextPeriod(periodStart)
            }

            private fun firstDateForNextPeriod(periodStart: LocalDate): LocalDate? {
                val nextPeriodStart = periodStart.plusWeeks(1)
                if (!scheduledPeriods.containsKey(nextPeriodStart)) {
                    return null
                }
                return scheduledPeriods[nextPeriodStart]!!.firstOrNull()
            }
        }

        data class Monthly(
            val timesPerMonth: Int,
            val preferredDays: Set<Int>,
            override val scheduledPeriods: Map<LocalDate, List<LocalDate>> = mapOf(),
            override val startDate: LocalDate = LocalDate.now(),
            override val endDate: LocalDate? = null
        ) : Flexible(startDate, endDate) {
            override fun periodRangeFor(date: LocalDate) =
                PeriodRange(
                    start = date.with(TemporalAdjusters.firstDayOfMonth()),
                    end = date.with(TemporalAdjusters.lastDayOfMonth())
                )

            override val periodCount get() = timesPerMonth

            override fun nextDateWithoutRange(from: LocalDate): LocalDate? {
                require(scheduledPeriods.isNotEmpty())
                val periodStart = from.with(TemporalAdjusters.firstDayOfMonth())

                if (!scheduledPeriods.containsKey(periodStart)) {
                    return null
                }

                val nextDate = scheduledPeriods[periodStart]!!.firstOrNull { !it.isBefore(from) }
                return nextDate ?: firstDateFromNextPeriod(periodStart)
            }

            private fun firstDateFromNextPeriod(periodStart: LocalDate): LocalDate? {
                val nextPeriodStart = periodStart.plusMonths(1)
                if (!scheduledPeriods.containsKey(nextPeriodStart)) {
                    return null
                }
                return scheduledPeriods[nextPeriodStart]!!.firstOrNull()
            }
        }
    }

    abstract val periodCount: Int
    abstract fun periodRangeFor(date: LocalDate): PeriodRange
    protected abstract fun nextDateWithoutRange(from: LocalDate): LocalDate?

    fun nextDate(from: LocalDate) =
        when {
            endDate != null && from.isAfter(endDate) -> null
            from.isBefore(startDate) -> nextDateWithoutRange(startDate)
            else -> nextDateWithoutRange(from)
        }

    fun shouldScheduleOn(date: LocalDate): Boolean {
        val nextDate = nextDate(date) ?: return false
        return date.isEqual(nextDate)
    }

    companion object {
        fun findWeeklyPeriods(
            start: LocalDate,
            end: LocalDate
        ): List<Period> {

            val periods = mutableListOf<Period>()
            val firstDayOfWeek = DayOfWeek.MONDAY

            var periodStart = start.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            val dayAfterEnd = end.plusDays(1)
            while (periodStart.isBefore(dayAfterEnd)) {
                val periodEnd = periodStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                periods.add(Period(periodStart, periodEnd))
                periodStart = periodEnd.plusDays(1)
            }

            return periods
        }

        fun findMonthlyPeriods(
            start: LocalDate,
            end: LocalDate
        ): List<Period> {
            val periods = mutableListOf<Period>()

            var periodStart = start.with(TemporalAdjusters.firstDayOfMonth())
            val dayAfterEnd = end.plusDays(1)
            while (periodStart.isBefore(dayAfterEnd)) {
                val periodEnd = periodStart.with(TemporalAdjusters.lastDayOfMonth())
                periods.add(Period(periodStart, periodEnd))
                periodStart = periodEnd.plusDays(1)
            }

            return periods
        }

        fun monthlyDatesToScheduleInPeriod(
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

        fun weeklyDatesToScheduleInPeriod(
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

        fun everyXDatesToScheduleInPeriod(
            repeatPattern: RepeatPattern.EveryXDays,
            start: LocalDate,
            end: LocalDate
        ): List<LocalDate> {

            var date = start
            val dates = mutableListOf<LocalDate>()
            while (date.isBefore(end.plusDays(1))) {
                if (repeatPattern.shouldBeDoneOn(date)) {
                    dates.add(date)
                }
                date = date.plusDays(1)
            }
            return dates

        }

        fun yearlyDatesToScheduleInPeriod(
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
    }
}

data class PeriodRange(val start: LocalDate, val end: LocalDate)

data class PeriodProgress(val completedCount: Int, val allCount: Int)

data class Period(val start: LocalDate, val end: LocalDate)

enum class RepeatType {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    EVERY_X_DAYS
}

val RepeatPattern.repeatType: RepeatType
    get() = when (this) {
        is RepeatPattern.Daily -> RepeatType.DAILY
        is RepeatPattern.Weekly -> RepeatType.WEEKLY
        is RepeatPattern.Flexible.Weekly -> RepeatType.WEEKLY
        is RepeatPattern.Monthly -> RepeatType.MONTHLY
        is RepeatPattern.Flexible.Monthly -> RepeatType.MONTHLY
        is RepeatPattern.Yearly -> RepeatType.YEARLY
        is RepeatPattern.EveryXDays -> RepeatType.EVERY_X_DAYS
    }