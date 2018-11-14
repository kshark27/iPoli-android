package io.ipoli.android.habit.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.habit.persistence.HabitRepository
import org.threeten.bp.LocalDateTime

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 10/27/17.
 */
class FindHabitsToRemindUseCase(private val habitRepository: HabitRepository) :
    UseCase<LocalDateTime, List<Habit>> {

    override fun execute(parameters: LocalDateTime) =
        habitRepository.findHabitsToRemind(parameters)
}