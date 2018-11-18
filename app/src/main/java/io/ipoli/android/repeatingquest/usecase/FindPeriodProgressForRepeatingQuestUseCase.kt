package io.ipoli.android.repeatingquest.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.quest.RepeatingQuest
import io.ipoli.android.quest.data.persistence.QuestRepository
import io.ipoli.android.repeatingquest.entity.PeriodProgress
import org.threeten.bp.LocalDate

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 2/26/18.
 */
class FindPeriodProgressForRepeatingQuestUseCase(
    private val questRepository: QuestRepository
) : UseCase<FindPeriodProgressForRepeatingQuestUseCase.Params, RepeatingQuest> {

    override fun execute(parameters: Params): RepeatingQuest {
        val rq = parameters.repeatingQuest
        val periodRange = rq.repeatPattern.periodRangeFor(parameters.currentDate)
        return rq.copy(
            periodProgress = PeriodProgress(
                completedCount = questRepository.findCompletedCountForRepeatingQuestInPeriod(
                    rq.id,
                    periodRange.start,
                    periodRange.end
                ),
                scheduledCount = questRepository.findCountForRepeatingQuestInPeriod(
                    rq.id,
                    periodRange.start,
                    periodRange.end
                )
            )
        )
    }

    data class Params(
        val repeatingQuest: RepeatingQuest,
        val currentDate: LocalDate = LocalDate.now()
    )
}