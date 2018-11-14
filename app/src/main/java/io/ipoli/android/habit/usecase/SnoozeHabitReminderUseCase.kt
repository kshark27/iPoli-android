package io.ipoli.android.habit.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.common.datetime.Duration
import io.ipoli.android.common.datetime.Minute
import io.ipoli.android.common.datetime.toTime
import io.ipoli.android.common.persistence.EntityReminder
import io.ipoli.android.common.persistence.EntityReminderRepository
import io.ipoli.android.quest.job.ReminderScheduler
import org.threeten.bp.LocalDateTime

class SnoozeHabitReminderUseCase(
    private val entityReminderRepository: EntityReminderRepository,
    private val reminderScheduler: ReminderScheduler
) : UseCase<SnoozeHabitReminderUseCase.Params, Unit> {

    override fun execute(parameters: SnoozeHabitReminderUseCase.Params) {
        val date = parameters.remindTime.toLocalDate()
        val time = parameters.remindTime.toTime()

        entityReminderRepository.snooze(
            date = date,
            time = time,
            entityId = parameters.habitId,
            entityType = EntityReminder.EntityType.HABIT,
            duration = parameters.snoozeDuration
        )
        reminderScheduler.schedule()
    }

    data class Params(
        val habitId: String,
        val remindTime: LocalDateTime,
        val snoozeDuration: Duration<Minute>
    )
}