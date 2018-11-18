package io.ipoli.android.repeatingquest.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.RepeatingQuest
import io.ipoli.android.quest.data.persistence.QuestRepository
import io.ipoli.android.quest.job.ReminderScheduler
import org.threeten.bp.LocalDate

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

        val rqEnd = rq.end
        if (rqEnd != null && parameters.start.isAfter(rqEnd)) {
            return Result(listOf(), rq)
        }

        val start = if (parameters.start.isBefore(rq.start)) rq.start else parameters.start

        val (scheduleDates, newRepeatPattern) = rq.repeatPattern.createSchedule(
            start,
            rqEnd
        )

        if (scheduleDates.isEmpty()) {
            return Result(listOf(), rq)
        }

        val quests = scheduleDates.map {
            Quest.createFromRepeatingQuest(repeatingQuest = rq, scheduleDate = it)
        }
        questRepository.save(quests)
        reminderScheduler.schedule()
        return Result(quests, rq.copy(repeatPattern = newRepeatPattern))
    }

    data class Result(val quests: List<Quest>, val repeatingQuest: RepeatingQuest)

    /**
     * @startDate inclusive
     * @end inclusive
     */
    data class Params(
        val repeatingQuest: RepeatingQuest,
        val start: LocalDate
    )
}