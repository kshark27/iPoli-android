package io.ipoli.android.habit.edit

import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.annotation.DrawableRes
import android.support.v4.widget.TextViewCompat
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import io.ipoli.android.R
import io.ipoli.android.common.datetime.DateUtils
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.habit.edit.EditHabitViewState.StateType.*
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import io.ipoli.android.tag.widget.EditItemAutocompleteTagAdapter
import io.ipoli.android.tag.widget.EditItemTagAdapter
import kotlinx.android.synthetic.main.controller_edit_habit.view.*
import kotlinx.android.synthetic.main.item_edit_habit_reminder.view.*
import kotlinx.android.synthetic.main.view_no_elevation_toolbar.view.*
import org.threeten.bp.DayOfWeek
import org.threeten.bp.format.TextStyle

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 6/16/18.
 */
class EditHabitViewController(args: Bundle? = null) :
    ReduxViewController<EditHabitAction, EditHabitViewState, EditHabitReducer>(args) {

    override val reducer = EditHabitReducer

    private var habitId: String = ""
    private var params: Params? = null

    data class Params(
        val name: String,
        val color: Color,
        val icon: Icon,
        val isGood: Boolean,
        val timesADay: Int,
        val days: Set<DayOfWeek>
    )


    constructor(habitId: String, params: Params? = null) : this() {
        this.habitId = habitId
        this.params = params
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = inflater.inflate(R.layout.controller_edit_habit, container, false)
        setToolbar(view.toolbar)
        toolbarTitle = if (habitId.isBlank()) {
            stringRes(R.string.add_habit)
        } else {
            stringRes(R.string.edit_habit)
        }

        view.habitTagList.layoutManager = LinearLayoutManager(view.context)
        view.habitTagList.adapter = EditItemTagAdapter(removeTagCallback = {
            dispatch(EditHabitAction.RemoveTag(it))
        })

        view.badHabit.dispatchOnClick {
            EditHabitAction.MakeBad
        }

        view.goodHabit.dispatchOnClick {
            EditHabitAction.MakeGood
        }

        view.habitReminderList.layoutManager = LinearLayoutManager(view.context)
        view.habitReminderList.adapter = ReminderAdapter()

        view.addHabitReminder.onDebounceClick {
            navigate().toHabitReminderPicker(null) { r ->
                dispatch(EditHabitAction.AddReminder(r))
            }
        }

        return view
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        showBackButton()
    }

    override fun onCreateLoadAction() = EditHabitAction.Load(habitId, params)

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.edit_habit_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            android.R.id.home ->
                router.handleBack()

            R.id.actionSave -> {
                dispatch(
                    EditHabitAction.Validate(
                        view!!.habitName.text.toString()
                    )
                )
                true
            }

            else -> super.onOptionsItemSelected(item)
        }

    override fun render(state: EditHabitViewState, view: View) {
        when (state.type) {

            HABIT_DATA_CHANGED -> {
                view.habitName.setText(state.name)
                renderHabitTypeSelection(view, state)
                renderDays(view, state)
                renderTags(view, state)
                renderTimesADay(view, state)
                renderReminders(view, state)
                renderColor(view, state)
                renderIcon(view, state)
                renderNote(view, state)
                renderChallenge(view, state)
            }

            HABIT_TYPE_CHANGED ->
                renderHabitTypeSelection(view, state)

            TAGS_CHANGED -> {
                renderTags(view, state)
                renderColor(view, state)
                renderIcon(view, state)
            }

            DAYS_CHANGED ->
                renderDays(view, state)

            TIMES_A_DAY_CHANGED -> {
                renderTimesADay(view, state)
                renderReminders(view, state)
            }

            REMINDERS_CHANGED ->
                renderReminders(view, state)

            COLOR_CHANGED ->
                renderColor(view, state)

            ICON_CHANGED ->
                renderIcon(view, state)

            CHALLENGE_CHANGED ->
                renderChallenge(view, state)

            NOTE_CHANGED ->
                renderNote(view, state)

            VALIDATION_ERROR_EMPTY_NAME ->
                view.habitName.error = stringRes(R.string.name_validation)

            VALIDATION_ERROR_EMPTY_DAYS ->
                showLongToast(R.string.no_days_selected)

            VALID_NAME -> {
                dispatch(EditHabitAction.Save)
                router.handleBack()
            }

            else -> {
            }

        }
    }

    private fun renderReminders(view: View, state: EditHabitViewState) {
        val rvms = state.reminderViewModels

        if (rvms.isEmpty()) {
            view.habitReminderList.gone()
        } else {
            (view.habitReminderList.adapter as ReminderAdapter).updateAll(state.reminderViewModels)
            view.habitReminderList.visible()
        }

        if (state.maxRemindersReached) {
            view.addHabitReminder.gone()
            view.addHabitMaxMessage.visible()
        } else {
            view.addHabitReminder.visible()
            view.addHabitMaxMessage.gone()
        }
    }

    private fun renderHabitTypeSelection(view: View, state: EditHabitViewState) {
        if (state.isGood) {
            renderGoodHabitSelection(view)
            if (state.isEditing) {
                view.badHabit.gone()
            }
        } else {
            renderBadHabitSelection(view)
            if (state.isEditing) {
                view.goodHabit.gone()
            }
        }
    }

    private fun renderGoodHabitSelection(view: View) {
        view.goodHabit.setImageDrawable(
            IconicsDrawable(view.context)
                .icon(GoogleMaterial.Icon.gmd_favorite)
                .color(attrData(R.attr.colorAccent))
                .paddingDp(8)
                .sizeDp(40)
        )
        view.goodHabit.setBackgroundResource(R.drawable.circle_white)

        view.badHabit.setImageDrawable(
            IconicsDrawable(view.context)
                .icon(GoogleMaterial.Icon.gmd_block)
                .colorRes(R.color.md_light_text_50)
                .paddingDp(8)
                .sizeDp(40)
        )
        view.badHabit.setBackgroundResource(R.drawable.circle_disable)

        view.habitTypeLabel.setText(R.string.good_habit)
        view.habitTypeHint.setText(R.string.good_habit_hint)

        view.habitNameLayout.hint = stringRes(R.string.good_habit_name_hint)

        view.timesADayDivider.visible()
        view.timesADayContainer.visible()

        view.habitRemindersDivider.visible()
        view.habitRemindersLabel.visible()
        view.habitRemindersContainer.visible()
    }

    private fun renderBadHabitSelection(view: View) {
        view.goodHabit.setImageDrawable(
            IconicsDrawable(view.context)
                .icon(GoogleMaterial.Icon.gmd_favorite)
                .colorRes(R.color.md_light_text_50)
                .paddingDp(8)
                .sizeDp(40)
        )
        view.goodHabit.setBackgroundResource(R.drawable.circle_disable)

        view.badHabit.setImageDrawable(
            IconicsDrawable(view.context)
                .icon(GoogleMaterial.Icon.gmd_block)
                .colorRes(R.color.md_black)
                .paddingDp(8)
                .sizeDp(40)
        )
        view.badHabit.setBackgroundResource(R.drawable.circle_white)

        view.habitTypeLabel.setText(R.string.bad_habit)
        view.habitTypeHint.setText(R.string.bad_habit_hint)

        view.habitNameLayout.hint = stringRes(R.string.bad_habit_name_hint)

        view.timesADayDivider.gone()
        view.timesADayContainer.gone()

        view.habitRemindersDivider.gone()
        view.habitRemindersLabel.gone()
        view.habitRemindersContainer.gone()
    }

    private fun renderTimesADay(
        view: View,
        state: EditHabitViewState
    ) {
        view.habitTimesADay.adapter = ArrayAdapter(
            view.context,
            R.layout.item_dropdown_number_spinner,
            state.timesADayList
        )
        view.habitTimesADay.onItemSelectedListener = null
        view.habitTimesADay.setSelection(state.timesADayIndex)
        view.habitTimesADay.post {
            styleSelectedTimesADay(view)
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
                        styleSelectedTimesADay(view)
                        dispatch(EditHabitAction.ChangeTimesADay(position))
                    }

                }
        }
    }

    private fun renderTags(
        view: View,
        state: EditHabitViewState
    ) {
        (view.habitTagList.adapter as EditItemTagAdapter).updateAll(state.tagViewModels)
        val add = view.habitNewTag
        if (state.maxTagsReached) {
            add.gone()
            view.maxTagsMessage.visible()
        } else {
            add.visible()
            view.maxTagsMessage.gone()

            val adapter = EditItemAutocompleteTagAdapter(state.tags, activity!!)
            add.setAdapter(adapter)
            add.setOnItemClickListener { _, _, position, _ ->
                dispatch(EditHabitAction.AddTag(adapter.getItem(position).name))
                add.setText("")
            }
            add.threshold = 0
            add.setOnTouchListener { _, _ ->
                add.showDropDown()
                false
            }
        }
    }

    private fun renderColor(
        view: View,
        state: EditHabitViewState
    ) {
        colorLayout(view, state.color)
        view.habitColor.onDebounceClick {
            navigate()
                .toColorPicker(
                    { c ->
                        dispatch(EditHabitAction.ChangeColor(c))
                    },
                    state.color
                )
        }
    }

    private fun renderIcon(
        view: View,
        state: EditHabitViewState
    ) {
        view.habitSelectedIcon.setImageDrawable(
            IconicsDrawable(view.context).largeIcon(state.iicon)
        )

        view.habitIcon.onDebounceClick {
            navigate()
                .toIconPicker(
                    { icon ->
                        icon?.let { i ->
                            dispatch(EditHabitAction.ChangeIcon(i))
                        }
                    }, state.icon
                )
        }
    }

    private fun renderDays(
        view: View,
        state: EditHabitViewState
    ) {
        val viewModels = state.daysViewModels()
        view.habitDaysContainer.children.forEachIndexed { i, v ->
            val b = v as Button
            val vm = viewModels[i]
            b.text = vm.text
            b.setBackgroundResource(vm.background)
            b.setTextColor(vm.textColor)
            b.onDebounceClick {
                dispatch(
                    if (vm.isSelected) EditHabitAction.DeselectDay(vm.weekDay) else EditHabitAction.SelectDay(
                        vm.weekDay
                    )
                )
            }
        }
    }

    private fun renderChallenge(
        view: View,
        state: EditHabitViewState
    ) {
        view.habitChallenge.text = state.challengeText
        view.habitChallenge.onDebounceClick {
            navigate().toChallengePicker(state.challenge) { challenge ->
                dispatch(EditHabitAction.ChangeChallenge(challenge))
            }
        }
    }

    private fun renderNote(view: View, state: EditHabitViewState) {
        view.habitNote.text = state.noteText
        view.habitNote.onDebounceClick {
            navigate()
                .toNotePicker(
                    state.note
                ) { text ->
                    dispatch(EditHabitAction.ChangeNote(text))
                }
        }
    }

    private fun colorLayout(
        view: View,
        color: Color
    ) {
        val color500 = colorRes(color.androidColor.color500)
        val color700 = colorRes(color.androidColor.color700)
        view.appbar.setBackgroundColor(color500)
        view.toolbar.setBackgroundColor(color500)
        view.rootContainer.setBackgroundColor(color500)
        activity?.window?.navigationBarColor = color500
        activity?.window?.statusBarColor = color700
    }

    override fun colorStatusBars() {

    }

    data class ReminderViewModel(val timeText: String, val reminder: Habit.Reminder) :
        RecyclerViewViewModel {
        override val id: String
            get() = timeText
    }

    inner class ReminderAdapter :
        BaseRecyclerViewAdapter<ReminderViewModel>(R.layout.item_edit_habit_reminder) {

        override fun onBindViewModel(vm: ReminderViewModel, view: View, holder: SimpleViewHolder) {

            view.reminderStartTime.text = vm.timeText
            view.reminderStartTime.onDebounceClick {
                navigateFromRoot().toHabitReminderPicker(vm.reminder) { r ->
                    dispatch(EditHabitAction.ChangeReminder(holder.adapterPosition, r))
                }
            }
            view.removeReminder.dispatchOnClick {
                EditHabitAction.RemoveReminder(holder.adapterPosition)
            }
        }

    }

    data class WeekDayViewModel(
        val text: String,
        @DrawableRes val background: Int,
        @ColorInt val textColor: Int,
        val isSelected: Boolean,
        val weekDay: DayOfWeek
    )

    private fun styleSelectedTimesADay(view: View) {
        view.habitTimesADay.selectedView?.let {
            val item = it as TextView
            TextViewCompat.setTextAppearance(item, R.style.TextAppearance_AppCompat_Subhead)
            item.setTextColor(colorRes(R.color.md_light_text_100))
            item.setPadding(0, 0, 0, 0)
        }
    }

    private val EditHabitViewState.challengeText: String
        get() = challenge?.name ?: stringRes(R.string.add_to_challenge)

    private val EditHabitViewState.noteText: String
        get() = if (note.isBlank()) stringRes(R.string.tap_to_add_note) else note

    private val EditHabitViewState.iicon: IIcon
        get() = icon.androidIcon.icon

    private val EditHabitViewState.tagViewModels: List<EditItemTagAdapter.TagViewModel>
        get() = habitTags.map {
            EditItemTagAdapter.TagViewModel(
                name = it.name,
                icon = it.icon?.androidIcon?.icon ?: MaterialDesignIconic.Icon.gmi_label,
                tag = it
            )
        }

    private val EditHabitViewState.timesADayList: List<String>
        get() = timesADayValues.map {
            if (it == Habit.UNLIMITED_TIMES_A_DAY) stringRes(R.string.unlimited_times_a_day)
            else if (it == 1) stringRes(R.string.time_a_day)
            else stringRes(R.string.times_a_day, it)
        }

    private val EditHabitViewState.timesADayIndex: Int
        get() = timesADayValues.indexOfFirst { it == timesADay }

    private val EditHabitViewState.reminderViewModels: List<ReminderViewModel>
        get() =
            reminders.map {
                ReminderViewModel(it.time.toString(shouldUse24HourFormat), it)
            }

    private fun EditHabitViewState.daysViewModels() =
        DateUtils.localeDaysOfWeek.map {
            val isSelected = days.contains(it)
            val (background, textColor) = if (isSelected)
                Pair(R.drawable.circle_white, attrData(R.attr.colorAccent))
            else
                Pair(R.drawable.circle_dark_background, colorRes(R.color.md_light_text_50))
            DateUtils.daysOfWeekText(TextStyle.SHORT_STANDALONE)

            WeekDayViewModel(
                text = DateUtils.dayOfWeekText(
                    it,
                    TextStyle.SHORT_STANDALONE
                ).first().toUpperCase().toString(),
                background = background,
                textColor = textColor,
                isSelected = isSelected,
                weekDay = it
            )
        }
}