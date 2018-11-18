package io.ipoli.android.repeatingquest.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.data.persistence.QuestRepository
import io.ipoli.android.repeatingquest.persistence.RepeatingQuestRepository
import org.threeten.bp.LocalDate

class AddQuestToRepeatingQuestUseCase(
    private val repeatingQuestRepository: RepeatingQuestRepository,
    private val questRepository: QuestRepository
) : UseCase<AddQuestToRepeatingQuestUseCase.Params, Unit> {

    override fun execute(parameters: Params) {
        val rq = repeatingQuestRepository.findById(parameters.repeatingQuestId)!!
        questRepository.save(
            Quest.createFromRepeatingQuest(
                repeatingQuest = rq,
                scheduleDate = parameters.date,
                startTime = parameters.time
            )
        )
    }

    data class Params(
        val repeatingQuestId: String,
        val date: LocalDate?,
        val time: Time?
    )
}