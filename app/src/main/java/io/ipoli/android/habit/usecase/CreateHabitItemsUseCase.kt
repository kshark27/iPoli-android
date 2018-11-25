package io.ipoli.android.habit.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.habit.data.Habit
import org.threeten.bp.LocalDate

class CreateHabitItemsUseCase :
    UseCase<CreateHabitItemsUseCase.Params, List<CreateHabitItemsUseCase.HabitItem>> {

    override fun execute(parameters: Params): List<HabitItem> {
        val currentDate = parameters.currentDate

        val habits = parameters.habits.sortedWith(
            compareByDescending<Habit> { it.shouldBeDoneOn(currentDate) }
                .thenBy { it.isCompletedForDate(currentDate) }
                .thenByDescending { if (it.isUnlimited) 1 else it.timesADay }
                .thenByDescending { it.isGood }
        )

        val (todayHabits, otherDayHabits) = habits.partition { it.shouldBeDoneOn(currentDate) }

        return when {
            todayHabits.isNotEmpty() && otherDayHabits.isNotEmpty() ->
                listOf(HabitItem.TodaySection) +
                    todayHabits.map {
                        val isCompleted = it.isCompletedForDate(currentDate)
                        HabitItem.Today(
                            habit = it,
                            isCompleted = if (it.isGood) isCompleted else !isCompleted,
                            completedCount = it.completedCountForDate(currentDate),
                            canBeCompletedMoreTimes = it.canCompleteMoreForDate(currentDate),
                            isBestStreak = it.streak.best != 0 && it.streak.best == it.streak.current
                        )
                    } +
                    HabitItem.AnyOtherDaySection +
                    otherDayHabits.map {
                        HabitItem.OtherDay(
                            habit = it,
                            isBestStreak = it.streak.best != 0 && it.streak.best == it.streak.current
                        )
                    }

            todayHabits.isNotEmpty() && otherDayHabits.isEmpty() ->
                listOf(HabitItem.TodaySection) +
                    todayHabits.map {
                        val isCompleted = it.isCompletedForDate(currentDate)
                        HabitItem.Today(
                            habit = it,
                            isCompleted = if (it.isGood) isCompleted else !isCompleted,
                            completedCount = it.completedCountForDate(currentDate),
                            canBeCompletedMoreTimes = it.canCompleteMoreForDate(currentDate),
                            isBestStreak = it.streak.best != 0 && it.streak.best == it.streak.current
                        )
                    }

            otherDayHabits.isNotEmpty() && todayHabits.isEmpty() ->
                listOf(HabitItem.AnyOtherDaySection) +
                    otherDayHabits.map {
                        HabitItem.OtherDay(
                            habit = it,
                            isBestStreak = it.streak.best != 0 && it.streak.best == it.streak.current
                        )
                    }

            else -> emptyList()
        }
    }

    data class Params(val habits: List<Habit>, val currentDate: LocalDate = LocalDate.now())

    sealed class HabitItem {
        object TodaySection : HabitItem()
        data class Today(
            val habit: Habit,
            val isCompleted: Boolean,
            val canBeCompletedMoreTimes: Boolean,
            val completedCount: Int,
            val isBestStreak: Boolean
        ) : HabitItem()

        object AnyOtherDaySection : HabitItem()
        data class OtherDay(val habit: Habit, val isBestStreak: Boolean) : HabitItem()
    }
}