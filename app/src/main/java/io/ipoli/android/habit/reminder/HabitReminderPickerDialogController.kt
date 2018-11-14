package io.ipoli.android.habit.reminder

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import io.ipoli.android.R
import io.ipoli.android.common.view.ReduxDialogController
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.habit.reminder.HabitReminderPickerViewState.StateType.DATA_CHANGED
import io.ipoli.android.habit.reminder.HabitReminderPickerViewState.StateType.DONE
import io.ipoli.android.pet.AndroidPetAvatar
import kotlinx.android.synthetic.main.dialog_habit_reminder_picker.view.*
import kotlinx.android.synthetic.main.view_dialog_header.view.*

class HabitReminderPickerDialogController :
    ReduxDialogController<HabitReminderPickerAction, HabitReminderPickerViewState, HabitReminderReducer> {

    override val reducer = HabitReminderReducer

    private var listener: (Habit.Reminder) -> Unit = {}
    private var reminder: Habit.Reminder? = null

    constructor(
        selectedReminder: Habit.Reminder? = null,
        listener: (Habit.Reminder) -> Unit
    ) : this() {
        this.listener = listener
        this.reminder = selectedReminder
    }

    constructor(args: Bundle? = null) : super(args)

    override fun onHeaderViewCreated(headerView: View) {
        headerView.dialogHeaderTitle.setText(R.string.reminder_dialog_title)
    }

    @SuppressLint("InflateParams")
    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_habit_reminder_picker, null)
        return view
    }

    override fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog =
        dialogBuilder
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.dialog_ok, null)
            .create()

    override fun onCreateLoadAction() = HabitReminderPickerAction.Load(reminder)

    override fun onDialogCreated(dialog: AlertDialog, contentView: View) {
        dialog.setOnShowListener {
            setPositiveButtonListener {
                dispatch(
                    HabitReminderPickerAction.Save(contentView.message.text.toString())
                )
            }
        }
    }

    override fun render(state: HabitReminderPickerViewState, view: View) {

        when (state.type) {
            DATA_CHANGED -> {
                changeIcon(AndroidPetAvatar.valueOf(state.petAvatar.name).headImage)

                view.message.setText(state.message)
                view.message.setSelection(view.message.text.length)

                view.remindStartTime.text = state.time.toString(shouldUse24HourFormat)

                view.remindStartTime.onDebounceClick {
                    createTimePickerDialog(
                        startTime = state.time,
                        showNeutral = false,
                        onTimePicked = { t ->
                            dispatch(HabitReminderPickerAction.ChangeTime(t!!))
                        }).show(router)
                }
            }

            DONE -> {
                listener(state.reminder)
                dismiss()
            }

            else -> {
            }
        }
    }
}