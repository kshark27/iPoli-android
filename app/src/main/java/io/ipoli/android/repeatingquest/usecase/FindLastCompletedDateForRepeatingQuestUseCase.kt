package io.ipoli.android.repeatingquest.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.quest.RepeatingQuest
import io.ipoli.android.quest.data.persistence.QuestRepository

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 2/25/18.
 */
class FindLastCompletedDateForRepeatingQuestUseCase(
    private val questRepository: QuestRepository
) : UseCase<FindLastCompletedDateForRepeatingQuestUseCase.Params, RepeatingQuest> {

    override fun execute(parameters: Params): RepeatingQuest {
        val rq = parameters.repeatingQuest
        val lastCompleted = questRepository.findLastCompletedForRepeatingQuest(rq.id)

        return rq.copy(
            lastCompletedDate = lastCompleted?.let {
                it.scheduledDate!!
            })
    }

    data class Params(val repeatingQuest: RepeatingQuest)
}