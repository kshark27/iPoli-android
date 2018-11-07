package io.ipoli.android.quest.schedule

import android.content.Context
import io.ipoli.android.common.AppDataState
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.common.text.CalendarFormatter
import io.ipoli.android.player.data.Player
import io.ipoli.android.quest.schedule.agenda.view.AgendaAction
import io.ipoli.android.quest.schedule.calendar.CalendarAction
import org.threeten.bp.LocalDate

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 10/21/17.
 */

sealed class ScheduleAction : Action {

    object ToggleViewMode : ScheduleAction()
    object ToggleAgendaPreviewMode : ScheduleAction()

    data class Load(val viewMode: Player.Preferences.AgendaScreen) : ScheduleAction()
    object GoToToday : ScheduleAction()
    object ResetAgendaDate : ScheduleAction()
}

object ScheduleReducer : BaseViewStateReducer<ScheduleViewState>() {

    override val stateKey = key<ScheduleViewState>()

    override fun defaultState() =
        ScheduleViewState(
            type = ScheduleViewState.StateType.LOADING,
            currentDate = LocalDate.now(),
            viewMode = Player.Preferences.AgendaScreen.AGENDA
        )

    override fun reduce(state: AppState, subState: ScheduleViewState, action: Action) =
        when (action) {

            is ScheduleAction ->
                reduceCalendarAction(
                    state.dataState,
                    subState,
                    action
                )

            is CalendarAction.ChangeDate ->
                subState.copy(
                    type = ScheduleViewState.StateType.SWIPE_DATE_CHANGED,
                    currentDate = action.date
                )

            is AgendaAction.AutoChangeDate ->
                autoChangeDate(subState, action.date)

            is AgendaAction.ChangePreviewDate ->
                autoChangeDate(subState, action.date)

            else -> subState
        }

    private fun autoChangeDate(
        subState: ScheduleViewState,
        date: LocalDate
    ) =
        if (subState.currentDate.isEqual(date)) {
            subState.copy(
                type = ScheduleViewState.StateType.IDLE
            )
        } else {
            subState.copy(
                type = ScheduleViewState.StateType.DATE_AUTO_CHANGED,
                currentDate = date
            )
        }

    private fun reduceCalendarAction(
        dataState: AppDataState,
        state: ScheduleViewState,
        action: ScheduleAction
    ) =
        when (action) {
            is ScheduleAction.Load -> {
                if (state.type != ScheduleViewState.StateType.LOADING) {
                    state.copy(
                        currentDate = dataState.agendaDate
                    )
                } else {
                    state.copy(
                        type = ScheduleViewState.StateType.INITIAL,
                        viewMode = action.viewMode,
                        currentDate = dataState.agendaDate
                    )
                }
            }

            is ScheduleAction.ToggleViewMode -> {
                state.copy(
                    type = ScheduleViewState.StateType.VIEW_MODE_CHANGED,
                    viewMode = if (state.viewMode == Player.Preferences.AgendaScreen.DAY)
                        Player.Preferences.AgendaScreen.AGENDA
                    else
                        Player.Preferences.AgendaScreen.DAY
                )
            }

            is ScheduleAction.GoToToday -> {
                state.copy(
                    type = ScheduleViewState.StateType.DATE_AUTO_CHANGED,
                    currentDate = LocalDate.now()
                )
            }


            else -> state
        }
}

data class ScheduleViewState(
    val type: StateType,
    val currentDate: LocalDate,
    val viewMode: Player.Preferences.AgendaScreen
) : BaseViewState() {

    enum class StateType {
        LOADING, INITIAL, IDLE,
        CALENDAR_DATE_CHANGED,
        SWIPE_DATE_CHANGED,
        DATE_PICKER_CHANGED,
        VIEW_MODE_CHANGED,
        DATE_AUTO_CHANGED
    }
}


val ScheduleViewState.viewModeTitle
    get() = if (viewMode == Player.Preferences.AgendaScreen.DAY) "Agenda" else "Calendar"

fun ScheduleViewState.dayText(context: Context) =
    CalendarFormatter(context).day(currentDate)

fun ScheduleViewState.dateText(context: Context) =
    CalendarFormatter(context).date(currentDate)