package io.ipoli.android.quest.bucketlist

import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.datetime.Duration
import io.ipoli.android.common.datetime.Minute
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.quest.bucketlist.usecase.CreateBucketListItemsUseCase
import org.threeten.bp.LocalDate

sealed class BucketListAction : Action {
    data class ItemsChanged(val items: List<CreateBucketListItemsUseCase.BucketListItem>) :
        BucketListAction() {
        override fun toMap() = mapOf("items" to items)
    }

    data class CompleteQuest(val questId: String) : BucketListAction() {
        override fun toMap() = mapOf("questId" to questId)
    }

    data class UndoCompleteQuest(val questId: String) : BucketListAction() {
        override fun toMap() = mapOf("questId" to questId)
    }

    data class RescheduleQuest(
        val questId: String,
        val date: LocalDate?,
        val time: Time?,
        val duration: Duration<Minute>?
    ) : BucketListAction() {
        override fun toMap() = mapOf(
            "questId" to questId,
            "date" to date,
            "time" to time,
            "duration" to duration?.intValue
        )
    }

    data class RemoveQuest(val questId: String) : BucketListAction() {
        override fun toMap() = mapOf("questId" to questId)
    }

    data class UndoRemoveQuest(val questId: String) : BucketListAction() {
        override fun toMap() = mapOf("questId" to questId)
    }

    object Load : BucketListAction()
}

object BucketListReducer : BaseViewStateReducer<BucketListViewState>() {

    override val stateKey = key<BucketListViewState>()

    override fun reduce(
        state: AppState,
        subState: BucketListViewState,
        action: Action
    ) = when (action) {

        is BucketListAction.Load ->
            subState.copy(type = BucketListViewState.StateType.LOADING)

        is BucketListAction.ItemsChanged ->
            if (action.items.isEmpty()) {
                subState.copy(type = BucketListViewState.StateType.EMPTY)
            } else {
                subState.copy(
                    type = BucketListViewState.StateType.DATA_CHANGED,
                    items = action.items
                )
            }

        else -> subState
    }

    override fun defaultState() =
        BucketListViewState(type = BucketListViewState.StateType.LOADING, items = emptyList())
}

data class BucketListViewState(
    val type: StateType,
    val items: List<CreateBucketListItemsUseCase.BucketListItem>
) : BaseViewState() {

    enum class StateType { LOADING, DATA_CHANGED, EMPTY }
}