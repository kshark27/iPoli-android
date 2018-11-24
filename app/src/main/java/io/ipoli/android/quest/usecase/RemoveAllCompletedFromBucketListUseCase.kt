package io.ipoli.android.quest.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.quest.data.persistence.QuestRepository

class RemoveAllCompletedFromBucketListUseCase(private val questRepository: QuestRepository) :
    UseCase<Unit, Unit> {

    override fun execute(parameters: Unit) {
        questRepository.removeAllUnscheduledCompleted()
    }

}