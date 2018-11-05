package io.ipoli.android.challenge.preset.add.habit

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import io.ipoli.android.R
import io.ipoli.android.challenge.preset.PresetChallenge
import io.ipoli.android.challenge.preset.add.habit.AddPresetChallengeHabitViewState.StateType.*
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.common.view.ReduxDialogController
import io.ipoli.android.common.view.colorRes
import io.ipoli.android.common.view.navigate
import io.ipoli.android.common.view.stringRes
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import kotlinx.android.synthetic.main.dialog_add_preset_challenge_habit.view.*
import kotlinx.android.synthetic.main.view_dialog_header.view.*

sealed class AddPresetChallengeHabitAction : Action {
    data class ChangeIcon(val icon: Icon) : AddPresetChallengeHabitAction()
    data class ChangeColor(val color: Color) : AddPresetChallengeHabitAction()
    data class ChangeTimesADay(val timesADay: Int) : AddPresetChallengeHabitAction()
    data class ChangeType(val isGood: Boolean) : AddPresetChallengeHabitAction()
    data class Validate(val name: String) : AddPresetChallengeHabitAction()

    data class Load(val habit: PresetChallenge.Habit?) : AddPresetChallengeHabitAction()
}

object AddPresetChallengeHabitReducer : BaseViewStateReducer<AddPresetChallengeHabitViewState>() {
    override fun reduce(
        state: AppState,
        subState: AddPresetChallengeHabitViewState,
        action: Action
    ) =
        when (action) {

            is AddPresetChallengeHabitAction.Load -> {

                val h = action.habit

                val newState = subState.copy(
                    name = h?.name ?: subState.name,
                    color = h?.color ?: subState.color,
                    icon = h?.icon ?: subState.icon,
                    isGood = h?.isGood ?: subState.isGood,
                    timesADay = h?.timesADay ?: subState.timesADay
                )

                if (state.dataState.player == null)
                    newState.copy(
                        type = LOADING
                    )
                else
                    newState.copy(
                        type = DATA_LOADED,
                        petAvatar = state.dataState.player.pet.avatar
                    )
            }

            is DataLoadedAction.PlayerChanged -> {
                if (subState.type == LOADING)
                    subState.copy(
                        type = DATA_LOADED,
                        petAvatar = action.player.pet.avatar
                    )
                else subState
            }

            is AddPresetChallengeHabitAction.ChangeColor ->
                subState.copy(
                    type = COLOR_CHANGED,
                    color = action.color
                )
            is AddPresetChallengeHabitAction.ChangeIcon ->
                subState.copy(
                    type = ICON_CHANGED,
                    icon = action.icon
                )

            is AddPresetChallengeHabitAction.ChangeTimesADay ->
                subState.copy(
                    type = TIMES_A_DAY_CHANGED,
                    timesADay = action.timesADay
                )

            is AddPresetChallengeHabitAction.ChangeType ->
                subState.copy(
                    type = TYPE_CHANGED,
                    isGood = action.isGood
                )

            is AddPresetChallengeHabitAction.Validate -> {
                if (action.name.isBlank()) {
                    subState.copy(
                        type = ERROR_EMPTY_NAME
                    )
                } else {
                    subState.copy(
                        type = HABIT_PICKED,
                        habit = PresetChallenge.Habit(
                            name = action.name,
                            color = subState.color,
                            icon = subState.icon,
                            timesADay = if (subState.isGood) subState.timesADay else 1,
                            isGood = subState.isGood,
                            isSelected = true
                        )
                    )
                }
            }

            else -> subState
        }

    override fun defaultState() =
        AddPresetChallengeHabitViewState(
            type = LOADING,
            petAvatar = PetAvatar.BEAR,
            name = "",
            color = Color.GREEN,
            icon = Icon.FLOWER,
            timesADay = 1,
            isGood = true,
            habit = null
        )

    override val stateKey = key<AddPresetChallengeHabitViewState>()

}

data class AddPresetChallengeHabitViewState(
    val type: StateType,
    val petAvatar: PetAvatar,
    val name: String,
    val color: Color,
    val icon: Icon,
    val timesADay: Int,
    val isGood: Boolean,
    val habit: PresetChallenge.Habit?
) : BaseViewState() {
    enum class StateType {
        LOADING,
        DATA_LOADED,
        ICON_CHANGED,
        COLOR_CHANGED,
        TIMES_A_DAY_CHANGED,
        TYPE_CHANGED,
        ERROR_EMPTY_NAME,
        HABIT_PICKED
    }
}

class AddPresetChallengeHabitDialogController(args: Bundle? = null) :
    ReduxDialogController<AddPresetChallengeHabitAction, AddPresetChallengeHabitViewState, AddPresetChallengeHabitReducer>(
        args
    ) {

    override val reducer = AddPresetChallengeHabitReducer

    private var habit: PresetChallenge.Habit? = null

    private var listener: (PresetChallenge.Habit) -> Unit = { _ -> }

    constructor(
        habit: PresetChallenge.Habit? = null,
        listener: (PresetChallenge.Habit) -> Unit = { _ -> }
    ) : this() {
        this.habit = habit
        this.listener = listener
    }

    override fun onHeaderViewCreated(headerView: View) {
        headerView.dialogHeaderTitle.setText(R.string.habit_for_preset_challenge_title)
    }

    @SuppressLint("InflateParams")
    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View =
        inflater.inflate(R.layout.dialog_add_preset_challenge_habit, null)

    override fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog {
        return dialogBuilder
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.dialog_ok, null)
            .create()
    }

    override fun onCreateLoadAction() =
        AddPresetChallengeHabitAction.Load(habit)

    override fun onDialogCreated(dialog: AlertDialog, contentView: View) {
        dialog.setOnShowListener {
            setPositiveButtonListener {
                dispatch(
                    AddPresetChallengeHabitAction.Validate(
                        contentView.habitName.text.toString()
                    )
                )
            }
        }
    }

    override fun render(state: AddPresetChallengeHabitViewState, view: View) {
        when (state.type) {
            DATA_LOADED -> {
                changeIcon(AndroidPetAvatar.valueOf(state.petAvatar.name).headImage)

                view.habitName.setText(state.name)

                renderColor(view, state)
                renderIcon(view, state)
                renderTimesADay(view)
                renderType(view, state)
            }

            COLOR_CHANGED ->
                renderColor(view, state)

            ICON_CHANGED ->
                renderIcon(view, state)

            TYPE_CHANGED ->
                renderType(view, state)

            ERROR_EMPTY_NAME ->
                view.habitName.error = stringRes(R.string.think_of_a_name)

            HABIT_PICKED -> {
                listener(state.habit!!)
                dismiss()
            }

            else -> {
            }
        }
    }

    private fun renderType(
        view: View,
        state: AddPresetChallengeHabitViewState
    ) {
        view.habitType.setOnCheckedChangeListener(null)
        view.habitType.isChecked = state.isGood
        view.habitTimesADay.isEnabled = state.isGood
        view.habitType.setOnCheckedChangeListener { _, isChecked ->
            dispatch(
                AddPresetChallengeHabitAction.ChangeType(
                    isChecked
                )
            )
        }
    }

    private fun renderTimesADay(
        view: View
    ) {
        val timesADay = (1..8).toList()
        view.habitTimesADay.adapter = ArrayAdapter<Int>(
            view.context,
            R.layout.item_dropdown_number_spinner,
            timesADay
        )

        view.habitTimesADay.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    v: View?,
                    position: Int,
                    id: Long
                ) {
                    dispatch(
                        AddPresetChallengeHabitAction.ChangeTimesADay(
                            timesADay[position]
                        )
                    )
                }
            }
    }

    private fun renderIcon(
        view: View,
        state: AddPresetChallengeHabitViewState
    ) {
        val icon =
            listItemIcon(state.icon.androidIcon.icon)
                .colorRes(state.color.androidColor.color500).paddingDp(0)
        view.habitIcon.setCompoundDrawablesRelativeWithIntrinsicBounds(
            icon,
            null,
            null,
            null
        )
        view.habitIcon.onDebounceClick {
            navigate().toIconPicker({ i ->
                if (i != null) dispatch(
                    AddPresetChallengeHabitAction.ChangeIcon(
                        i
                    )
                )
            })
        }
    }

    private fun renderColor(
        view: View,
        state: AddPresetChallengeHabitViewState
    ) {
        val allDrawables = view.habitColor.compoundDrawablesRelative
        val drawableStart = allDrawables[0] as GradientDrawable
        val size = ViewUtils.dpToPx(24f, view.context).toInt()
        drawableStart.setSize(size, size)
        drawableStart.setColor(colorRes(state.color.androidColor.color500))
        view.habitColor.setCompoundDrawablesRelativeWithIntrinsicBounds(
            drawableStart,
            null,
            null,
            null
        )
        view.habitColor.onDebounceClick {
            navigate().toColorPicker({ c ->
                dispatch(
                    AddPresetChallengeHabitAction.ChangeColor(
                        c
                    )
                )
            })
        }
    }

}