package io.ipoli.android.repeatingquest

import io.ipoli.android.common.datetime.DateUtils
import io.ipoli.android.repeatingquest.entity.RepeatPattern
import org.amshove.kluent.`should be null`
import org.amshove.kluent.`should be true`
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate
import org.threeten.bp.Month
import org.threeten.bp.temporal.TemporalAdjusters

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 02/25/2018.
 */

class RepeatPatternSpek : Spek({

    describe("RepeatPattern") {

        fun shouldHaveNextDate(nextDate: LocalDate?, date: LocalDate?) {

            if (date == null) {
                nextDate.`should be null`()
                return
            }

            date.isEqual(nextDate).`should be true`()
        }

        it("should not return before startDate") {
            val pattern = RepeatPattern.Daily(DateUtils.today)
            val nextDate = pattern.nextDate(DateUtils.today.minusDays(1))
            shouldHaveNextDate(nextDate!!, DateUtils.today)
        }

        it("should not return after end") {
            val pattern = RepeatPattern.Daily(DateUtils.today, DateUtils.today.plusDays(1))
            val nextDate = pattern.nextDate(DateUtils.today.plusDays(2))
            shouldHaveNextDate(nextDate, null)
        }

        describe("Daily") {

            it("should give today for next date") {
                val pattern = RepeatPattern.Daily(DateUtils.today)
                val nextDate = pattern.nextDate(DateUtils.today)
                shouldHaveNextDate(nextDate!!, DateUtils.today)
            }
        }

        describe("Weekly") {

            describe("Fixed") {

                it("should give today when today is monday and monday is chosen day") {
                    val monday = DateUtils.today.with(DayOfWeek.MONDAY)
                    val pattern = RepeatPattern.Weekly(
                        setOf(DayOfWeek.MONDAY),
                        monday
                    )
                    shouldHaveNextDate(pattern.nextDate(monday), monday)
                }

                it("should find date after 2 days when today is monday and wednesday is chosen day") {
                    val today =
                        DateUtils.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val wednesday = DateUtils.today.with(DayOfWeek.WEDNESDAY)
                    val pattern = RepeatPattern.Weekly(
                        setOf(DayOfWeek.WEDNESDAY),
                        today
                    )
                    shouldHaveNextDate(pattern.nextDate(today), wednesday)
                }

                it("should find date considering day of week ordering") {
                    val today =
                        DateUtils.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val tuesday = DateUtils.today.with(DayOfWeek.TUESDAY)
                    val pattern = RepeatPattern.Weekly(
                        setOf(DayOfWeek.WEDNESDAY, DayOfWeek.TUESDAY),
                        today
                    )
                    shouldHaveNextDate(pattern.nextDate(today), tuesday)
                }
            }

            describe("Flexible") {

                it("should give today when today is first day of week") {
                    val today =
                        DateUtils.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

                    val pattern = RepeatPattern.Flexible.Weekly(
                        timesPerWeek = 2,
                        preferredDays = setOf(),
                        scheduledPeriods = mapOf(
                            today to listOf(today)
                        ),
                        startDate = today
                    )

                    shouldHaveNextDate(pattern.nextDate(today), today)
                }

                it("should give tomorrow when today is not scheduled") {

                    val today =
                        DateUtils.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

                    val tomorrow = today.plusDays(1)

                    val pattern = RepeatPattern.Flexible.Weekly(
                        timesPerWeek = 2,
                        preferredDays = setOf(),
                        scheduledPeriods = mapOf(
                            today to listOf(tomorrow)
                        ),
                        startDate = today
                    )

                    shouldHaveNextDate(pattern.nextDate(today), tomorrow)
                }

                it("should give two days after tomorrow when from is after tomorrow") {

                    val today =
                        DateUtils.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

                    val tomorrow = today.plusDays(1)
                    val dayAfterTomorrow = tomorrow.plusDays(1)
                    val twoDaysAfterTomorrow = tomorrow.plusDays(2)

                    val pattern = RepeatPattern.Flexible.Weekly(
                        timesPerWeek = 2,
                        preferredDays = setOf(),
                        scheduledPeriods = mapOf(
                            today to listOf(tomorrow, twoDaysAfterTomorrow)
                        ),
                        startDate = today
                    )

                    shouldHaveNextDate(pattern.nextDate(dayAfterTomorrow), twoDaysAfterTomorrow)
                }

                it("should get first date from next period") {

                    val today =
                        DateUtils.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

                    val tomorrow = today.plusDays(1)
                    val dayAfterTomorrow = tomorrow.plusDays(1)

                    val nextPeriodStart = today.plusWeeks(1)

                    val pattern = RepeatPattern.Flexible.Weekly(
                        timesPerWeek = 2,
                        preferredDays = setOf(),
                        scheduledPeriods = mapOf(
                            today to listOf(tomorrow),
                            nextPeriodStart to listOf(nextPeriodStart)
                        ),
                        startDate = today
                    )

                    shouldHaveNextDate(pattern.nextDate(dayAfterTomorrow), nextPeriodStart)
                }
            }
        }

        describe("Monthly") {
            describe("Fixed") {
                it("should give today when today is 1st and 1st is chosen day") {
                    val first = DateUtils.today.withDayOfMonth(1)
                    val pattern = RepeatPattern.Monthly(
                        daysOfMonth = setOf(1),
                        startDate = first
                    )
                    shouldHaveNextDate(pattern.nextDate(first), first)
                }

                it("should find first chosen date after today when today is not chosen") {
                    val today = DateUtils.today.withDayOfMonth(10)
                    val pattern = RepeatPattern.Monthly(
                        setOf(1, 20, 15),
                        today.withDayOfMonth(1)
                    )
                    shouldHaveNextDate(pattern.nextDate(today), today.withDayOfMonth(15))
                }

                it("should find first chosen date from next month") {
                    val today = DateUtils.today.withDayOfMonth(10)
                    val first = today.withDayOfMonth(1)
                    val pattern = RepeatPattern.Monthly(
                        setOf(1),
                        first
                    )
                    shouldHaveNextDate(pattern.nextDate(today), first.plusMonths(1))
                }
            }

            describe("Flexible") {
                it("should give today when today is first day of month") {
                    val today =
                        DateUtils.today.with(TemporalAdjusters.firstDayOfMonth())

                    val pattern = RepeatPattern.Flexible.Monthly(
                        timesPerMonth = 2,
                        preferredDays = setOf(),
                        scheduledPeriods = mapOf(
                            today to listOf(today)
                        ),
                        startDate = today
                    )

                    shouldHaveNextDate(pattern.nextDate(today), today)
                }

                it("should give tomorrow when today is not scheduled") {

                    val today =
                        DateUtils.today.with(TemporalAdjusters.firstDayOfMonth())
                    val tomorrow = today.plusDays(1)

                    val pattern = RepeatPattern.Flexible.Monthly(
                        timesPerMonth = 2,
                        preferredDays = setOf(),
                        scheduledPeriods = mapOf(
                            today to listOf(tomorrow)
                        ),
                        startDate = today
                    )

                    shouldHaveNextDate(pattern.nextDate(today), tomorrow)
                }

                it("should give two days after tomorrow when from is after tomorrow") {

                    val today =
                        DateUtils.today.with(TemporalAdjusters.firstDayOfMonth())

                    val tomorrow = today.plusDays(1)
                    val dayAfterTomorrow = tomorrow.plusDays(1)
                    val twoDaysAfterTomorrow = tomorrow.plusDays(2)

                    val pattern = RepeatPattern.Flexible.Monthly(
                        timesPerMonth = 2,
                        preferredDays = setOf(),
                        scheduledPeriods = mapOf(
                            today to listOf(tomorrow, twoDaysAfterTomorrow)
                        ),
                        startDate = today
                    )

                    shouldHaveNextDate(pattern.nextDate(dayAfterTomorrow), twoDaysAfterTomorrow)
                }

                it("should get first date from next period") {

                    val today =
                        DateUtils.today.with(TemporalAdjusters.firstDayOfMonth())

                    val tomorrow = today.plusDays(1)
                    val dayAfterTomorrow = tomorrow.plusDays(1)

                    val nextPeriodStart = today.plusMonths(1)

                    val pattern = RepeatPattern.Flexible.Monthly(
                        timesPerMonth = 2,
                        preferredDays = setOf(),
                        scheduledPeriods = mapOf(
                            today to listOf(tomorrow),
                            nextPeriodStart to listOf(nextPeriodStart)
                        ),
                        startDate = today
                    )

                    shouldHaveNextDate(pattern.nextDate(dayAfterTomorrow), nextPeriodStart)
                }

            }
        }

        describe("Yearly") {

            val firstDayOfYear = DateUtils.today.with(TemporalAdjusters.firstDayOfYear())
            it("should give today") {
                val today = firstDayOfYear
                val pattern = RepeatPattern.Yearly(today.dayOfMonth, today.month, today)
                shouldHaveNextDate(pattern.nextDate(today), today)
            }

            it("should give tomorrow when starting today") {
                val today = firstDayOfYear
                val tomorrow = today.plusDays(1)
                val pattern = RepeatPattern.Yearly(tomorrow.dayOfMonth, tomorrow.month, today)
                shouldHaveNextDate(pattern.nextDate(today), tomorrow)
            }

            it("should give from the next year") {
                val today = firstDayOfYear.plusDays(1)
                val pattern = RepeatPattern.Yearly(1, Month.JANUARY, today)
                shouldHaveNextDate(pattern.nextDate(today), firstDayOfYear.plusYears(1))
            }
        }

        describe("Every x days") {

            it("should repeat every 2 days") {
                val today = LocalDate.now()
                val tommorow = today.plusDays(1)
                val pattern = RepeatPattern.EveryXDays(2)
                shouldHaveNextDate(pattern.nextDate(today), today)
                shouldHaveNextDate(pattern.nextDate(tommorow), tommorow.plusDays(1))
            }

            it("should repeat when start date is from previous month") {
                val today = LocalDate.of(2018, Month.OCTOBER, 11)
                val startDate = today.minusMonths(1)
                val pattern = RepeatPattern.EveryXDays(3, startDate)
                shouldHaveNextDate(pattern.nextDate(today), today)
            }
        }
    }
})