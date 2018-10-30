package io.ipoli.android.quest.schedule.agenda.view

import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.datetime.isBetween
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.quest.schedule.ScheduleAction
import io.ipoli.android.quest.schedule.agenda.usecase.CreateAgendaItemsUseCase.AgendaItem
import io.ipoli.android.quest.schedule.agenda.usecase.CreateAgendaPreviewItemsUseCase
import io.ipoli.android.quest.schedule.agenda.view.AgendaViewState.PreviewMode.MONTH
import io.ipoli.android.quest.schedule.agenda.view.AgendaViewState.PreviewMode.WEEK
import io.ipoli.android.quest.schedule.agenda.view.AgendaViewState.StateType.*
import org.threeten.bp.LocalDate
import org.threeten.bp.YearMonth

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 01/26/2018.
 */

sealed class AgendaAction : Action {
    object Load : AgendaAction()

    data class LoadBefore(val itemPosition: Int) : AgendaAction() {
        override fun toMap() = mapOf("itemPosition" to itemPosition)
    }

    data class LoadAfter(val itemPosition: Int) : AgendaAction() {
        override fun toMap() = mapOf("itemPosition" to itemPosition)
    }

    data class CompleteQuest(val questId: String) : AgendaAction() {
        override fun toMap() = mapOf("questId" to questId)
    }

    data class UndoCompleteQuest(val questId: String) : AgendaAction() {
        override fun toMap() = mapOf("questId" to questId)
    }

    data class RescheduleQuest(val questId: String, val date: LocalDate?) : AgendaAction() {
        override fun toMap() = mapOf("questId" to questId, "date" to date)
    }

    data class RemoveQuest(val questId: String) : AgendaAction() {
        override fun toMap() = mapOf("questId" to questId)
    }

    data class UndoRemoveQuest(val questId: String) : AgendaAction() {
        override fun toMap() = mapOf("questId" to questId)
    }

    data class FirstVisibleItemChanged(val itemPosition: Int) : AgendaAction() {
        override fun toMap() = mapOf("itemPosition" to itemPosition)
    }

    data class AutoChangeDate(val date: LocalDate) : AgendaAction()
    data class ChangePreviewMonth(val yearMonth: YearMonth) : AgendaAction()
    data class ChangePreviewDate(val date: LocalDate) : AgendaAction()
}

object AgendaReducer : BaseViewStateReducer<AgendaViewState>() {

    override val stateKey = key<AgendaViewState>()

    override fun reduce(
        state: AppState,
        subState: AgendaViewState,
        action: Action
    ) =
        when (action) {

            is DataLoadedAction.AgendaItemsChanged -> {
                subState.copy(
                    type = DATA_CHANGED,
                    agendaItems = action.agendaItems,
                    scrollToPosition = findItemPositionToScrollTo(
                        action.currentAgendaItemDate,
                        action.agendaItems
                    )
                )
            }

            is DataLoadedAction.AgendaPreviewItemsChanged -> {
                subState.copy(
                    type = CALENDAR_DATA_CHANGED,
                    previewItems = action.previewItems
                )
            }

            is AgendaAction.LoadBefore -> {
                subState.copy(
                    type = SHOW_TOP_LOADER
                )
            }
            is AgendaAction.LoadAfter -> {
                subState.copy(
                    type = SHOW_BOTTOM_LOADER
                )
            }

            is ScheduleAction.ToggleAgendaPreviewMode -> {
                subState.copy(
                    type = PREVIEW_MODE_CHANGED,
                    previewMode = if (subState.previewMode == WEEK) MONTH else WEEK
                )
            }

            is AgendaAction.AutoChangeDate -> {
                subState.copy(
                    type = VISIBLE_DATE_CHANGED,
                    currentDate = action.date
                )
            }

            is AgendaAction.ChangePreviewDate -> {
                subState.copy(
                    type = IDLE,
                    currentDate = action.date
                )
            }

            is ScheduleAction.GoToToday -> {
                subState.copy(
                    type = SHOW_TODAY,
                    currentDate = LocalDate.now()
                )
            }

            else -> subState

        }

    private fun findItemPositionToScrollTo(
        date: LocalDate?,
        agendaItems: List<AgendaItem>
    ) = date?.let {
        val currentAgendaItemDate = it
        val index = agendaItems.indexOfLast { item ->
            when (item) {
                is AgendaItem.Date ->
                    item.startDate() == currentAgendaItemDate
                is AgendaItem.Week ->
                    currentAgendaItemDate.isBetween(
                        item.start,
                        item.end
                    )
                else -> false
            }
        }
        if (index < 0) null
        else index
    }

    override fun defaultState() = AgendaViewState(
        type = LOADING,
        agendaItems = listOf(),
        scrollToPosition = null,
        currentDate = LocalDate.now(),
        previewItems = null,
        previewMode = WEEK
    )

    const val ITEMS_BEFORE_COUNT = 25
    const val ITEMS_AFTER_COUNT = 35
}

data class AgendaViewState(
    val type: StateType,
    val currentDate: LocalDate?,
    val scrollToPosition: Int?,
    val agendaItems: List<AgendaItem>,
    val previewItems: List<CreateAgendaPreviewItemsUseCase.PreviewItem>?,
    val previewMode: PreviewMode
) : BaseViewState() {

    enum class StateType {
        LOADING,
        DATA_CHANGED,
        SHOW_TOP_LOADER,
        SHOW_BOTTOM_LOADER,
        IDLE,
        PREVIEW_MODE_CHANGED,
        CALENDAR_DATA_CHANGED,
        SHOW_TODAY,
        VISIBLE_DATE_CHANGED
    }

    enum class PreviewMode {
        WEEK, MONTH
    }

}