package io.ipoli.android.quest.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.common.datetime.Duration
import io.ipoli.android.common.datetime.Minute
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.data.persistence.QuestRepository
import io.ipoli.android.quest.job.ReminderScheduler
import org.threeten.bp.LocalDate

class RescheduleQuestUseCase(
    private val questRepository: QuestRepository,
    private val reminderScheduler: ReminderScheduler
) : UseCase<RescheduleQuestUseCase.Params, Quest> {

    override fun execute(parameters: Params): Quest {
        val quest = questRepository.findById(parameters.questId)
        requireNotNull(quest)

        val shouldMoveToBucket =
            parameters.scheduledDate == null && parameters.startTime == null && parameters.duration == null
        return if (shouldMoveToBucket || (parameters.scheduledDate != null && parameters.startTime == null)) {
            changeDate(quest!!, parameters.scheduledDate)
        } else if (parameters.startTime != null) {
            changeTime(quest!!, parameters.scheduledDate, parameters.startTime)
        } else changeDuration(quest!!, parameters.duration!!)
    }

    private fun changeDuration(quest: Quest, duration: Duration<Minute>) = questRepository.save(
        quest.copy(
            duration = duration.intValue
        )
    )

    private fun changeTime(quest: Quest, scheduledDate: LocalDate?, startTime: Time): Quest {
        val newQuest = questRepository.save(
            quest.copy(
                startTime = startTime,
                scheduledDate = scheduledDate ?: quest.scheduledDate,
                originalScheduledDate = quest.originalScheduledDate ?: scheduledDate
            )
        )
        reminderScheduler.schedule()
        return newQuest
    }

    private fun changeDate(
        quest: Quest,
        scheduledDate: LocalDate?
    ): Quest {
        val newQuest = questRepository.save(
            quest.copy(
                scheduledDate = scheduledDate,
                originalScheduledDate = quest.originalScheduledDate ?: scheduledDate
            )
        )
        reminderScheduler.schedule()
        return newQuest
    }

    data class Params(
        val questId: String,
        val scheduledDate: LocalDate?,
        val startTime: Time?,
        val duration: Duration<Minute>?
    )
}