package io.ipoli.android.quest.schedule.calendar

import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.quest.schedule.ScheduleAction
import io.ipoli.android.quest.schedule.calendar.CalendarViewState.StateType.*
import org.threeten.bp.LocalDate

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 01/31/2018.
 */

object CalendarReducer : BaseViewStateReducer<CalendarViewState>() {

    override val stateKey = key<CalendarViewState>()

    override fun reduce(
        state: AppState,
        subState: CalendarViewState,
        action: Action
    ) =
        when (action) {

            is CalendarAction.Load -> {
                if (subState.type == LOADING) {
                    subState.copy(
                        type = INITIAL,
                        currentDate = state.dataState.agendaDate
                    )
                } else {
                    subState.copy(
                        currentDate = state.dataState.agendaDate
                    )
                }
            }

            is CalendarAction.SwipePage -> {

                val currentPos = subState.adapterPosition
                val newPos = action.adapterPosition
                val currentDate = subState.currentDate
                val newDate = if (newPos < currentPos)
                    currentDate.minusDays(1)
                else
                    currentDate.plusDays(1)

                subState.copy(
                    type = SWIPE_DATE_CHANGED,
                    adapterPosition = action.adapterPosition,
                    currentDate = newDate
                )
            }

            is ScheduleAction.GoToToday -> {
                subState.copy(
                    type = CALENDAR_DATE_CHANGED,
                    adapterMidPosition = MID_POSITION,
                    adapterPosition = MID_POSITION,
                    currentDate = LocalDate.now()
                )
            }

            else -> subState
        }

    override fun defaultState() =
        CalendarViewState(
            type = LOADING,
            currentDate = LocalDate.now(),
            adapterPosition = MID_POSITION,
            adapterMidPosition = MID_POSITION
        )

    private const val MID_POSITION = 49
}

sealed class CalendarAction : Action {
    data class SwipePage(val adapterPosition: Int) : CalendarAction() {
        override fun toMap() = mapOf("adapterPosition" to adapterPosition)
    }

    data class ChangeDate(val date: LocalDate) : CalendarAction() {
        override fun toMap() = mapOf("date" to date)
    }

    object Load : CalendarAction()
}

data class CalendarViewState(
    val type: CalendarViewState.StateType,
    val currentDate: LocalDate,
    val adapterPosition: Int,
    val adapterMidPosition: Int
) : BaseViewState() {
    enum class StateType {
        INITIAL, CALENDAR_DATE_CHANGED, SWIPE_DATE_CHANGED, LOADING
    }
}