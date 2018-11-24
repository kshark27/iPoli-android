package io.ipoli.android.quest.bucketlist

import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.datetime.Duration
import io.ipoli.android.common.datetime.Minute
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.quest.bucketlist.BucketListViewState.StateType.*
import io.ipoli.android.quest.bucketlist.usecase.CreateBucketListItemsUseCase
import io.ipoli.android.tag.Tag
import org.threeten.bp.LocalDate

sealed class BucketListAction : Action {
    object RemoveAllCompleted : BucketListAction()

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

    data class Filter(val showCompleted: Boolean, val selectedTags: Set<Tag>) : BucketListAction()

    data class Load(val showCompleted: Boolean) : BucketListAction()
}

object BucketListReducer : BaseViewStateReducer<BucketListViewState>() {

    override val stateKey = key<BucketListViewState>()

    override fun reduce(
        state: AppState,
        subState: BucketListViewState,
        action: Action
    ) = when (action) {

        is BucketListAction.Load ->
            subState.copy(type = LOADING, showCompleted = action.showCompleted)

        is BucketListAction.ItemsChanged -> {

            val shownItems =
                createShownItems(subState.showCompleted, subState.selectedTags, action.items)

            subState.copy(
                type = if (shownItems.isEmpty()) EMPTY else DATA_CHANGED,
                visibleItems = shownItems,
                items = action.items
            )
        }

        is BucketListAction.Filter -> {

            val shownItems =
                createShownItems(action.showCompleted, action.selectedTags, subState.items)

            subState.copy(
                type = if (shownItems.isEmpty()) EMPTY else DATA_CHANGED,
                visibleItems = shownItems,
                showCompleted = action.showCompleted,
                selectedTags = action.selectedTags
            )
        }

        else -> subState
    }

    private fun createShownItems(
        showCompleted: Boolean, selectedTags: Set<Tag>,
        items: List<CreateBucketListItemsUseCase.BucketListItem>
    ): List<CreateBucketListItemsUseCase.BucketListItem> {
        val vi = if (showCompleted) {
            items
        } else {
            items.filter { it !is CreateBucketListItemsUseCase.BucketListItem.Completed && (it is CreateBucketListItemsUseCase.BucketListItem.QuestItem && !it.quest.isCompleted) }
        }

        return if (selectedTags.isNotEmpty()) {
            vi.filterIsInstance(CreateBucketListItemsUseCase.BucketListItem.QuestItem::class.java)
                .filter { it.quest.tags.intersect(selectedTags).isNotEmpty() }
        } else {
            vi
        }
    }

    override fun defaultState() =
        BucketListViewState(
            type = LOADING,
            items = emptyList(),
            visibleItems = emptyList(),
            showCompleted = true,
            selectedTags = emptySet()
        )
}

data class BucketListViewState(
    val type: StateType,
    val items: List<CreateBucketListItemsUseCase.BucketListItem>,
    val visibleItems: List<CreateBucketListItemsUseCase.BucketListItem>,
    val showCompleted: Boolean,
    val selectedTags: Set<Tag>
) : BaseViewState() {

    enum class StateType { LOADING, DATA_CHANGED, EMPTY }
}