package io.ipoli.android.quest.bucketlist.sideeffect

import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.redux.Action
import io.ipoli.android.quest.bucketlist.BucketListAction
import io.ipoli.android.quest.bucketlist.usecase.CreateBucketListItemsUseCase
import space.traversal.kapsule.required

object BucketListSideEffectHandler : AppSideEffectHandler() {

    private val createBucketListItemsUseCase by required { createBucketListItemsUseCase }

    override suspend fun doExecute(action: Action, state: AppState) {
        when (action) {
            is BucketListAction.Load ->
                dispatch(
                    BucketListAction.ItemsChanged(
                        createBucketListItemsUseCase.execute(
                            CreateBucketListItemsUseCase.Params(state.dataState.unscheduledQuests)
                        )
                    )
                )

            is DataLoadedAction.UnscheduledQuestsChanged ->
                dispatch(
                    BucketListAction.ItemsChanged(
                        createBucketListItemsUseCase.execute(
                            CreateBucketListItemsUseCase.Params(action.quests)
                        )
                    )
                )
        }
    }

    override fun canHandle(action: Action) =
        action is BucketListAction || action is DataLoadedAction.UnscheduledQuestsChanged
}