package io.ipoli.android.common.view

import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import io.ipoli.android.Constants
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.datetime.Duration
import io.ipoli.android.common.datetime.Minute
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.datetime.minutes
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.common.text.DurationFormatter
import io.ipoli.android.common.view.DurationPickerDialogViewState.StateType.*
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.player.data.Player
import io.ipoli.android.store.powerup.PowerUp
import kotlinx.android.synthetic.main.dialog_duration_picker.view.*
import kotlinx.android.synthetic.main.view_dialog_header.view.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 9/2/17.
 */

sealed class DurationPickerDialogAction : Action {
    object ShowPreset : DurationPickerDialogAction()
    object ShowCustom : DurationPickerDialogAction()
    data class Load(val selectedDuration: Duration<Minute>?) : DurationPickerDialogAction()
    data class Validate(val pickerIndex: Int, val hoursIndex: Int, val minutesIndex: Int) :
        DurationPickerDialogAction()
}

object DurationPickerDialogReducer :
    BaseViewStateReducer<DurationPickerDialogViewState>() {
    override val stateKey = key<DurationPickerDialogViewState>()

    override fun reduce(
        state: AppState,
        subState: DurationPickerDialogViewState,
        action: Action
    ) =
        when (action) {
            is DurationPickerDialogAction.Load -> {
                state.dataState.player?.let {
                    val selectedDuration = action.selectedDuration
                    createLoadedState(subState, it, selectedDuration)
                } ?: subState.copy(
                    type = LOADING
                )
            }

            is DataLoadedAction.PlayerChanged ->
                if (subState.type == LOADING)
                    createLoadedState(subState, action.player, subState.duration)
                else subState.copy(
                    type = PET_CHANGED,
                    petAvatar = action.player.pet.avatar
                )

            is DurationPickerDialogAction.ShowCustom ->
                subState.copy(
                    type = VIEW_SWITCHED,
                    isCustom = true
                )

            is DurationPickerDialogAction.ShowPreset ->
                subState.copy(
                    type = VIEW_SWITCHED,
                    isCustom = false
                )

            is DurationPickerDialogAction.Validate -> {
                if (!subState.isCustom)
                    subState.copy(
                        type = VALIDATION_SUCCESSFUL,
                        duration = subState.durations[action.pickerIndex]
                    )
                else {
                    val d =
                        subState.hours[action.hoursIndex] * Time.MINUTES_IN_AN_HOUR + subState.minutes[action.minutesIndex]

                    if (d >= Constants.QUEST_MIN_DURATION && d <= Constants.MAX_QUEST_DURATION_HOURS * Time.MINUTES_IN_AN_HOUR) {
                        subState.copy(
                            type = VALIDATION_SUCCESSFUL,
                            duration = d.minutes
                        )
                    } else subState.copy(
                        type = VALIDATION_ERROR
                    )
                }
            }

            else -> subState
        }

    private fun createLoadedState(
        subState: DurationPickerDialogViewState,
        player: Player,
        selectedDuration: Duration<Minute>?
    ): DurationPickerDialogViewState {
        val isCustom = selectedDuration != null && !subState.durations.contains(selectedDuration)
            && player.isMember
        val duration: Duration<Minute> =
            selectedDuration ?: Constants.DEFAULT_QUEST_DURATION.minutes

        val pickerDuration = if(isCustom) Constants.DEFAULT_QUEST_DURATION.minutes else duration

        val minutes = duration.intValue % Time.MINUTES_IN_AN_HOUR
        val hours = duration.intValue / Time.MINUTES_IN_AN_HOUR

        return subState.copy(
            type = DATA_LOADED,
            petAvatar = player.pet.avatar,
            duration = duration,
            isCustom = isCustom,
            selectedDurationIndex = subState.durations.indexOfFirst { it == pickerDuration },
            selectedHourIndex = subState.hours.indexOfFirst { it == hours },
            selectedMinuteIndex = subState.minutes.indexOfFirst { it == minutes }
        )
    }

    override fun defaultState() = DurationPickerDialogViewState(
        type = LOADING,
        petAvatar = null,
        hours = (0..Constants.MAX_QUEST_DURATION_HOURS).toList(),
        minutes = 0.until(Time.MINUTES_IN_AN_HOUR).toList(),
        duration = null,
        durations = Constants.DURATIONS.map { it.minutes },
        isCustom = false,
        selectedDurationIndex = null,
        selectedMinuteIndex = null,
        selectedHourIndex = null
    )


}

data class DurationPickerDialogViewState(
    val type: StateType,
    val petAvatar: PetAvatar? = null,
    val duration: Duration<Minute>?,
    val hours: List<Int>,
    val minutes: List<Int>,
    val durations: List<Duration<Minute>>,
    val isCustom: Boolean,
    val selectedDurationIndex: Int?,
    val selectedHourIndex: Int?,
    val selectedMinuteIndex: Int?
) : BaseViewState() {

    enum class StateType {
        LOADING,
        DATA_LOADED,
        PET_CHANGED,
        VIEW_SWITCHED,
        VALIDATION_SUCCESSFUL,
        VALIDATION_ERROR
    }
}

class DurationPickerDialogController :
    ReduxDialogController<DurationPickerDialogAction, DurationPickerDialogViewState, DurationPickerDialogReducer> {

    override val reducer = DurationPickerDialogReducer

    private var listener: (Duration<Minute>) -> Unit = {}
    private var selectedDuration: Duration<Minute>? = null

    constructor(
        selectedDuration: Duration<Minute>? = null,
        listener: (Duration<Minute>) -> Unit
    ) : this() {
        this.listener = listener
        this.selectedDuration = selectedDuration
    }

    constructor(args: Bundle? = null) : super(args)

    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View {
        val contentView = inflater.inflate(R.layout.dialog_duration_picker, null)
        val durationPicker = contentView.durationPicker
        durationPicker.setItems(Constants.DURATIONS
            .map {
                DurationFormatter.formatReadable(
                    contentView.context,
                    it
                )
            })

        durationPicker.setSelectedItem(Constants.DURATIONS.indexOfFirst {
            it == selectedDuration?.intValue ?: Constants.DEFAULT_QUEST_DURATION
        })
        return contentView
    }

    override fun onHeaderViewCreated(headerView: View) {
        headerView.dialogHeaderTitle.setText(R.string.quest_duration_question)
    }

    override fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog =
        dialogBuilder
            .setPositiveButton(R.string.dialog_ok, null)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.custom, null)
            .create()

    override fun onDialogCreated(dialog: AlertDialog, contentView: View) {
        dialog.setOnShowListener {
            setPositiveButtonListener {
                dispatch(
                    DurationPickerDialogAction.Validate(
                        contentView.durationPicker.selectedItemIndex,
                        contentView.hours.selectedItemIndex,
                        contentView.minutes.selectedItemIndex
                    )
                )
            }

            setNeutralButtonListener {
                if(contentView.viewSwitcher.displayedChild == 0) {
                    dispatch(DurationPickerDialogAction.ShowCustom)
                } else {
                    dispatch(DurationPickerDialogAction.ShowPreset)
                }
            }
        }
    }

    override fun onCreateLoadAction() = DurationPickerDialogAction.Load(selectedDuration)

    override fun render(state: DurationPickerDialogViewState, view: View) {
        when (state.type) {
            DATA_LOADED -> {
                if(state.isCustom) {
                    view.viewSwitcher.showNext()
                    changeNeutralButtonText(R.string.back)
                }

                state.petAvatar?.let {
                    changeIcon(AndroidPetAvatar.valueOf(it.name).headImage)
                }

                val durationPicker = view.durationPicker
                durationPicker.setItems(state.durations
                    .map {
                        DurationFormatter.formatReadable(
                            view.context,
                            it.intValue
                        )
                    })

                durationPicker.setSelectedItem(state.selectedDurationIndex!!)

                view.hours.setItems(
                    state.hours.map {
                        it.toString()
                    }
                )

                view.hours.setSelectedItem(state.selectedHourIndex!!)

                view.minutes.setItems(
                    state.minutes.map {
                        it.toString()
                    }
                )

                view.minutes.setSelectedItem(state.selectedMinuteIndex!!)

            }

            PET_CHANGED -> changeIcon(AndroidPetAvatar.valueOf(state.petAvatar!!.name).headImage)

            VIEW_SWITCHED -> {
                view.viewSwitcher.showNext()
                if(state.isCustom) {
                    view.hours.setSelectedItem(state.selectedHourIndex!!)
                    view.minutes.setSelectedItem(state.selectedMinuteIndex!!)
                }
                changeNeutralButtonText(if (state.isCustom) R.string.back else R.string.custom)
            }

            VALIDATION_SUCCESSFUL -> {
                listener(state.duration!!)
                dismiss()
            }

            VALIDATION_ERROR ->
                showShortToast(R.string.duration_error_message)

            else -> {}
        }
    }
}