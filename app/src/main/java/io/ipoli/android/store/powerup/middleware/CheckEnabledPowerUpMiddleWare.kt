package io.ipoli.android.store.powerup.middleware

import io.ipoli.android.Constants
import io.ipoli.android.challenge.add.EditChallengeAction
import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.common.AppState
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.Dispatcher
import io.ipoli.android.common.redux.MiddleWare
import io.ipoli.android.common.view.DurationPickerDialogAction
import io.ipoli.android.event.calendar.picker.CalendarPickerAction
import io.ipoli.android.event.calendar.picker.CalendarPickerViewState
import io.ipoli.android.growth.GrowthAction
import io.ipoli.android.habit.list.HabitListAction
import io.ipoli.android.player.data.Player
import io.ipoli.android.store.powerup.PowerUp
import io.ipoli.android.tag.list.TagListAction

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 03/20/2018.
 */
data class ShowBuyPowerUpAction(val powerUp: PowerUp.Type) : Action

object CheckEnabledPowerUpMiddleWare : MiddleWare<AppState> {

    override fun execute(
        state: AppState,
        dispatcher: Dispatcher,
        action: Action
    ): MiddleWare.Result {
        val p = state.dataState.player ?: return MiddleWare.Result.Continue

        return when (action) {

            is GrowthAction.ShowWeek,
            is GrowthAction.ShowMonth ->
                checkForAvailablePowerUp(PowerUp.Type.GROWTH, p, dispatcher)

            is HabitListAction.AddPreset,
            is HabitListAction.Add -> {
                val habits = state.dataState.habits
                if (habits == null || habits.size < Constants.MAX_FREE_HABITS) {
                    MiddleWare.Result.Continue
                } else {
                    checkForAvailablePowerUp(PowerUp.Type.HABITS, p, dispatcher)
                }
            }

            is TagListAction.AddTag ->
                if (state.dataState.tags.size < Constants.MAX_FREE_TAGS)
                    MiddleWare.Result.Continue
                else
                    checkForAvailablePowerUp(PowerUp.Type.TAGS, p, dispatcher)

            is DurationPickerDialogAction.ShowCustom ->
                checkForAvailablePowerUp(PowerUp.Type.CUSTOM_DURATION, p, dispatcher)

            is CalendarPickerAction.SyncSelectedCalendars -> {
                val s = state.stateFor(CalendarPickerViewState::class.java)
                if (s.syncCalendars.size <= Constants.MAX_FREE_SYNC_CALENDARS)
                    MiddleWare.Result.Continue
                else
                    checkForAvailablePowerUp(PowerUp.Type.CALENDAR_SYNC, p, dispatcher)
            }


            is EditChallengeAction.ShowTargetTrackedValuePicker -> {
                checkForAddChallengeValue(action.trackedValues, p, dispatcher)
            }

            is EditChallengeAction.ShowAverageTrackedValuePicker -> {
                checkForAddChallengeValue(action.trackedValues, p, dispatcher)
            }

            else -> MiddleWare.Result.Continue
        }
    }

    private fun checkForAddChallengeValue(
        trackedValues: List<Challenge.TrackedValue>,
        player: Player,
        dispatcher: Dispatcher
    ) =
        if (trackedValues.any { it !is Challenge.TrackedValue.Progress }) {
            checkForAvailablePowerUp(
                PowerUp.Type.TRACK_CHALLENGE_VALUES,
                player,
                dispatcher
            )
        } else {
            MiddleWare.Result.Continue
        }


    private fun checkForAvailablePowerUp(
        powerUp: PowerUp.Type,
        player: Player,
        dispatcher: Dispatcher
    ) =
        when {
            player.isMember -> MiddleWare.Result.Continue
            else -> {
                dispatcher.dispatch(ShowBuyPowerUpAction(powerUp))
                MiddleWare.Result.Stop
            }
        }

}