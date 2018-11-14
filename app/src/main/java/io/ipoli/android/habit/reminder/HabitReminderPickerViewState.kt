package io.ipoli.android.habit.reminder

import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.habit.reminder.HabitReminderPickerViewState.StateType.*
import io.ipoli.android.pet.PetAvatar

sealed class HabitReminderPickerAction : Action {

    data class Load(val reminder: Habit.Reminder? = null) : HabitReminderPickerAction()
    data class Save(val message: String) : HabitReminderPickerAction()
    data class ChangeTime(val time: Time) : HabitReminderPickerAction()
}

object HabitReminderReducer : BaseViewStateReducer<HabitReminderPickerViewState>() {
    override fun reduce(
        state: AppState,
        subState: HabitReminderPickerViewState,
        action: Action
    ) =
        when (action) {

            is HabitReminderPickerAction.Load -> {
                val r = action.reminder

                val newState = subState.copy(
                    message = r?.message ?: subState.message,
                    time = r?.time ?: subState.time
                )

                if (state.dataState.player == null)
                    newState.copy(
                        type = LOADING
                    )
                else
                    newState.copy(
                        type = DATA_CHANGED,
                        petAvatar = state.dataState.player.pet.avatar
                    )
            }

            is HabitReminderPickerAction.ChangeTime ->
                subState.copy(
                    type = DATA_CHANGED,
                    time = action.time,
                    reminder = subState.reminder.copy(
                        time = action.time
                    )
                )

            is HabitReminderPickerAction.Save ->
                subState.copy(
                    type = DONE,
                    reminder = subState.reminder.copy(
                        message = action.message
                    )
                )

            else -> subState
        }

    override fun defaultState() =
        HabitReminderPickerViewState(
            type = LOADING,
            petAvatar = PetAvatar.BEAR,
            message = "",
            time = Time.now(),
            reminder = Habit.Reminder("", Time.now())
        )

    override val stateKey = key<HabitReminderPickerViewState>()

}

data class HabitReminderPickerViewState(
    val type: StateType,
    val petAvatar: PetAvatar,
    val message: String,
    val time: Time,
    val reminder: Habit.Reminder
) : BaseViewState() {
    enum class StateType {
        LOADING,
        DATA_CHANGED,
        DONE
    }
}