package io.ipoli.android.challenge.preset.add.quest

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
import io.ipoli.android.challenge.preset.add.quest.AddPresetChallengeQuestViewState.StateType.*
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.datetime.*
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.common.text.DurationFormatter
import io.ipoli.android.common.view.ReduxDialogController
import io.ipoli.android.common.view.colorRes
import io.ipoli.android.common.view.navigate
import io.ipoli.android.common.view.stringRes
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import kotlinx.android.synthetic.main.dialog_add_preset_challenge_quest.view.*
import kotlinx.android.synthetic.main.view_dialog_header.view.*

sealed class AddPresetChallengeQuestAction : Action {
    data class ChangeDuration(val duration: Duration<Minute>) : AddPresetChallengeQuestAction()
    data class ChangeColor(val color: Color) : AddPresetChallengeQuestAction()
    data class ChangeIcon(val icon: Icon) : AddPresetChallengeQuestAction()
    data class ChangeDay(val day: Int) : AddPresetChallengeQuestAction()
    data class ChangeIsRepeating(val isRepeating: Boolean) : AddPresetChallengeQuestAction()

    data class Load(
        val quest: PresetChallenge.Quest?,
        val isRepeating: Boolean
    ) : AddPresetChallengeQuestAction()

    data class Validate(val name: String) : AddPresetChallengeQuestAction()
}

object AddPresetChallengeQuestReducer : BaseViewStateReducer<AddPresetChallengeQuestViewState>() {
    override val stateKey = key<AddPresetChallengeQuestViewState>()

    override fun reduce(
        state: AppState,
        subState: AddPresetChallengeQuestViewState,
        action: Action
    ) =
        when (action) {

            is AddPresetChallengeQuestAction.Load -> {

                val q = action.quest

                val newState = subState.copy(
                    name = q?.name ?: subState.name,
                    color = q?.color ?: subState.color,
                    icon = q?.icon ?: subState.icon,
                    duration = q?.duration ?: subState.duration,
                    day = q?.day ?: subState.day,
                    isRepeating = action.isRepeating
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

            is AddPresetChallengeQuestAction.ChangeDuration ->
                subState.copy(
                    type = DURATION_CHANGED,
                    duration = action.duration
                )

            is AddPresetChallengeQuestAction.ChangeColor ->
                subState.copy(
                    type = COLOR_CHANGED,
                    color = action.color
                )
            is AddPresetChallengeQuestAction.ChangeIcon ->
                subState.copy(
                    type = ICON_CHANGED,
                    icon = action.icon
                )

            is AddPresetChallengeQuestAction.ChangeDay ->
                subState.copy(
                    type = DAY_CHANGED,
                    day = action.day
                )

            is AddPresetChallengeQuestAction.ChangeIsRepeating ->
                subState.copy(
                    type = EVERY_DAY_CHANGED,
                    isRepeating = action.isRepeating
                )

            is AddPresetChallengeQuestAction.Validate -> {
                if (action.name.isBlank()) {
                    subState.copy(
                        type = ERROR_EMPTY_NAME
                    )
                } else {
                    subState.copy(
                        type = QUEST_PICKED,
                        quest = PresetChallenge.Quest(
                            name = action.name,
                            color = subState.color,
                            icon = subState.icon,
                            duration = subState.duration,
                            day = subState.day,
                            subQuests = emptyList(),
                            note = ""
                        )
                    )
                }
            }

            else -> subState
        }

    override fun defaultState() =
        AddPresetChallengeQuestViewState(
            type = LOADING,
            petAvatar = PetAvatar.BEAR,
            name = "",
            color = Color.GREEN,
            icon = Icon.FLOWER,
            duration = 30.minutes,
            day = 1,
            isRepeating = false,
            quest = null
        )

}

data class AddPresetChallengeQuestViewState(
    val type: StateType,
    val petAvatar: PetAvatar,
    val name: String,
    val color: Color,
    val icon: Icon,
    val duration: Duration<Minute>,
    val isRepeating: Boolean,
    val day: Int,
    val quest: PresetChallenge.Quest?
) : BaseViewState() {
    enum class StateType {
        LOADING,
        DATA_LOADED,
        DURATION_CHANGED,
        ICON_CHANGED,
        COLOR_CHANGED,
        DAY_CHANGED,
        EVERY_DAY_CHANGED,
        ERROR_EMPTY_NAME,
        QUEST_PICKED
    }
}

class AddPresetChallengeQuestDialogController(args: Bundle? = null) :
    ReduxDialogController<AddPresetChallengeQuestAction, AddPresetChallengeQuestViewState, AddPresetChallengeQuestReducer>(
        args
    ) {

    override val reducer =
        AddPresetChallengeQuestReducer

    private var challengeDuration: Duration<Day> = 0.days
    private var quest: PresetChallenge.Quest? = null
    private var isRepeating = false
    private var listener: (PresetChallenge.Quest, Boolean) -> Unit = { _, _ -> }

    constructor(
        challengeDuration: Duration<Day>,
        quest: PresetChallenge.Quest? = null,
        isRepeating: Boolean = false,
        listener: (PresetChallenge.Quest, Boolean) -> Unit = { _, _ -> }
    ) : this() {
        this.challengeDuration = challengeDuration
        this.quest = quest
        this.isRepeating = isRepeating
        this.listener = listener
    }

    override fun onHeaderViewCreated(headerView: View) {
        headerView.dialogHeaderTitle.setText(R.string.quest_for_preset_challenge_title)
    }

    @SuppressLint("InflateParams")
    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View =
        inflater.inflate(R.layout.dialog_add_preset_challenge_quest, null)

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
        AddPresetChallengeQuestAction.Load(
            quest,
            isRepeating
        )

    override fun onDialogCreated(dialog: AlertDialog, contentView: View) {
        dialog.setOnShowListener {
            setPositiveButtonListener {
                dispatch(
                    AddPresetChallengeQuestAction.Validate(
                        contentView.questName.text.toString()
                    )
                )
            }
        }
    }

    override fun render(state: AddPresetChallengeQuestViewState, view: View) {
        when (state.type) {
            DATA_LOADED -> {
                changeIcon(AndroidPetAvatar.valueOf(state.petAvatar.name).headImage)

                view.questName.setText(state.name)

                renderDuration(view, state)
                renderColor(view, state)
                renderIcon(view, state)
                renderDay(view)
                renderEveryDay(view, state)
            }

            DURATION_CHANGED ->
                renderDuration(view, state)

            COLOR_CHANGED ->
                renderColor(view, state)

            ICON_CHANGED ->
                renderIcon(view, state)

            EVERY_DAY_CHANGED ->
                renderEveryDay(view, state)

            ERROR_EMPTY_NAME ->
                view.questName.error = stringRes(R.string.think_of_a_name)

            QUEST_PICKED -> {
                listener(state.quest!!, state.isRepeating)
                dismiss()
            }

            else -> {
            }
        }
    }

    private fun renderEveryDay(
        view: View,
        state: AddPresetChallengeQuestViewState
    ) {
        view.questEveryDay.setOnCheckedChangeListener(null)
        view.questEveryDay.isChecked = state.isRepeating
        view.questDay.isEnabled = !state.isRepeating
        view.questEveryDay.setOnCheckedChangeListener { _, isChecked ->
            dispatch(
                AddPresetChallengeQuestAction.ChangeIsRepeating(
                    isChecked
                )
            )
        }
    }

    private fun renderDay(
        view: View
    ) {
        val days = (1..challengeDuration.intValue).toList()
        view.questDay.adapter = ArrayAdapter<Int>(
            view.context,
            R.layout.item_dropdown_number_spinner,
            days
        )

        view.questDay.onItemSelectedListener =
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
                        AddPresetChallengeQuestAction.ChangeDay(
                            days[position]
                        )
                    )
                }
            }
    }

    private fun renderIcon(
        view: View,
        state: AddPresetChallengeQuestViewState
    ) {
        val icon =
            listItemIcon(state.icon.androidIcon.icon)
                .colorRes(state.color.androidColor.color500).paddingDp(0)
        view.questIcon.setCompoundDrawablesRelativeWithIntrinsicBounds(
            icon,
            null,
            null,
            null
        )
        view.questIcon.onDebounceClick {
            navigate().toIconPicker({ i ->
                if (i != null) dispatch(
                    AddPresetChallengeQuestAction.ChangeIcon(
                        i
                    )
                )
            })
        }
    }

    private fun renderColor(
        view: View,
        state: AddPresetChallengeQuestViewState
    ) {
        val allDrawables = view.questColor.compoundDrawablesRelative
        val drawableStart = allDrawables[0] as GradientDrawable
        val size = ViewUtils.dpToPx(24f, view.context).toInt()
        drawableStart.setSize(size, size)
        drawableStart.setColor(colorRes(state.color.androidColor.color500))
        view.questColor.setCompoundDrawablesRelativeWithIntrinsicBounds(
            drawableStart,
            null,
            null,
            null
        )
        view.questColor.onDebounceClick {
            navigate().toColorPicker({ c -> dispatch(
                AddPresetChallengeQuestAction.ChangeColor(
                    c
                )
            ) })
        }
    }

    private fun renderDuration(
        view: View,
        state: AddPresetChallengeQuestViewState
    ) {
        view.questDuration.text = DurationFormatter.formatShort(state.duration.intValue)
        view.questDuration.onDebounceClick {
            navigate().toDurationPicker(state.duration) { d ->
                dispatch(
                    AddPresetChallengeQuestAction.ChangeDuration(
                        d
                    )
                )
            }
        }
    }

}