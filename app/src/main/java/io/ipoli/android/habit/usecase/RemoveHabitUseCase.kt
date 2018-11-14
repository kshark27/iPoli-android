package io.ipoli.android.habit.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.habit.persistence.HabitRepository
import io.ipoli.android.quest.job.ReminderScheduler

class RemoveHabitUseCase(
    private val habitRepository: HabitRepository,
    private val reminderScheduler: ReminderScheduler
) : UseCase<RemoveHabitUseCase.Params, Unit> {

    override fun execute(parameters: Params) {
        habitRepository.remove(parameters.habitId)
        reminderScheduler.schedule()
    }

    data class Params(val habitId: String)
}