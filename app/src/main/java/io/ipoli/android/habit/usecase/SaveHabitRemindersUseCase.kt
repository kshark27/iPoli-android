package io.ipoli.android.habit.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.common.persistence.EntityReminder
import io.ipoli.android.common.persistence.EntityReminderRepository
import io.ipoli.android.habit.persistence.HabitRepository
import io.ipoli.android.quest.job.ReminderScheduler
import org.threeten.bp.LocalDate
import java.util.*

class SaveHabitRemindersUseCase(
    private val habitRepository: HabitRepository,
    private val entityReminderRepository: EntityReminderRepository,
    private val reminderScheduler: ReminderScheduler
) : UseCase<SaveHabitRemindersUseCase.Params, Unit> {

    override fun execute(parameters: Params) {
        val today = parameters.today
        val dayOfWeek = today.dayOfWeek
        val habits = habitRepository.findAllNotRemoved()
            .filter { it.isGood && it.reminders.isNotEmpty() && it.days.contains(dayOfWeek) }

        val entityReminders = habits.flatMap { h ->
            h.reminders.map { r ->
                EntityReminder(
                    id = UUID.randomUUID().toString(),
                    date = today,
                    time = r.time,
                    entityType = EntityReminder.EntityType.HABIT,
                    entityId = h.id
                )
            }
        }

        entityReminderRepository.save(entityReminders)
        reminderScheduler.schedule()
    }

    data class Params(val today: LocalDate = LocalDate.now())
}