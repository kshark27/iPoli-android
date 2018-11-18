package io.ipoli.android.repeatingquest.add

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import io.ipoli.android.R
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.redux.android.BaseViewController
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.text.DurationFormatter
import io.ipoli.android.common.text.RepeatPatternFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.SimpleRecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.reminder.formatter.ReminderTimeFormatter
import io.ipoli.android.quest.reminder.picker.ReminderPickerDialogController
import io.ipoli.android.quest.reminder.picker.ReminderViewModel
import io.ipoli.android.quest.subquest.view.ReadOnlySubQuestAdapter
import io.ipoli.android.repeatingquest.add.EditRepeatingQuestViewState.DurationOption.*
import io.ipoli.android.repeatingquest.add.EditRepeatingQuestViewState.RepeatPatternOption.*
import io.ipoli.android.repeatingquest.add.EditRepeatingQuestViewState.RepeatPatternOption.MORE_OPTIONS
import io.ipoli.android.tag.widget.EditItemAutocompleteTagAdapter
import io.ipoli.android.tag.widget.EditItemTagAdapter
import kotlinx.android.synthetic.main.controller_add_repeating_quest.view.*
import kotlinx.android.synthetic.main.controller_add_repeating_quest_name.view.*
import kotlinx.android.synthetic.main.controller_add_repeating_quest_summary.view.*
import kotlinx.android.synthetic.main.controller_wizard_options.view.*
import kotlinx.android.synthetic.main.item_edit_repeating_quest_sub_quest.view.*
import kotlinx.android.synthetic.main.view_no_elevation_toolbar.view.*
import java.util.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 04/09/2018.
 */
class AddRepeatingQuestViewController(args: Bundle? = null) :
    ReduxViewController<EditRepeatingQuestAction, EditRepeatingQuestViewState, EditRepeatingQuestReducer>(
        args
    ) {

    override val reducer = EditRepeatingQuestReducer

    companion object {
        const val NAME_INDEX = 0
        const val PICK_RECURRENCE_INDEX = 1
        const val PICK_DURATION_INDEX = 2
        const val SUMMARY_INDEX = 3
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = container.inflate(R.layout.controller_add_repeating_quest)

        setToolbar(view.toolbar)

        return view
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        showBackButton()
        activity!!.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        view.toolbar.onDebounceMenuClick(
            {
                if (it.itemId == R.id.actionNext) {
                    dispatch(EditRepeatingQuestAction.ShowRepeatPatternPicker(view.rqName.text.toString()))
                }

                if (it.itemId == R.id.actionSave) {
                    dispatch(EditRepeatingQuestAction.ValidateName(view.summaryName.text.toString()))
                }

            }, {
                dispatch(EditRepeatingQuestAction.Back)
            })
    }

    override fun onDetach(view: View) {
        view.toolbar.clearDebounceListeners()
        activity!!.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        super.onDetach(view)
    }

    override fun handleBack(): Boolean {
        dispatch(EditRepeatingQuestAction.Back)
        return true
    }

    override fun colorStatusBars() {

    }

    override fun render(state: EditRepeatingQuestViewState, view: View) {
        when (state.type) {
            EditRepeatingQuestViewState.StateType.INITIAL -> {
                toolbarTitle = state.toolbarTitle
                colorLayout(view, state.color)
                changeChildController(
                    view = view,
                    adapterPosition = state.adapterPosition,
                    animate = false
                )
            }

            EditRepeatingQuestViewState.StateType.NEXT_PAGE -> {
                changeChildController(view = view, adapterPosition = state.adapterPosition)
                toolbarTitle = state.toolbarTitle
            }

            EditRepeatingQuestViewState.StateType.PREVIOUS_PAGE -> {
                getChildRouter(view.pager).popCurrentController()
                toolbarTitle = state.toolbarTitle
            }

            EditRepeatingQuestViewState.StateType.TAGS_CHANGED ->
                colorLayout(view, state.color)

            EditRepeatingQuestViewState.StateType.COLOR_CHANGED ->
                colorLayout(view, state.color)

            EditRepeatingQuestViewState.StateType.CLOSE ->
                router.popCurrentController()

            else -> {
            }
        }
    }

    private fun changeChildController(
        view: View,
        adapterPosition: Int,
        animate: Boolean = true
    ) {
        val childRouter = getChildRouter(view.pager)

        val changeHandler = if (animate) HorizontalChangeHandler() else null

        val transaction = RouterTransaction.with(
            createControllerForPosition(adapterPosition)
        )
            .popChangeHandler(changeHandler)
            .pushChangeHandler(changeHandler)
        childRouter.pushController(transaction)
    }

    private fun createControllerForPosition(position: Int): Controller =
        when (position) {
            NAME_INDEX -> NameViewController()
            PICK_RECURRENCE_INDEX -> PickRepeatPatternViewController()
            PICK_DURATION_INDEX -> PickDurationViewController()
            SUMMARY_INDEX -> SummaryViewController()
            else -> throw IllegalArgumentException("Unknown controller position $position")
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

    private val EditRepeatingQuestViewState.toolbarTitle: String
        get() = when (adapterPosition) {
            NAME_INDEX -> "New Repeating Quest"
            PICK_RECURRENCE_INDEX -> "How often will you do it?"
            PICK_DURATION_INDEX -> "For how long?"
            SUMMARY_INDEX -> ""
            else -> throw IllegalArgumentException("Unknown controller position $adapterPosition")
        }

    class NameViewController(args: Bundle? = null) :
        BaseViewController<EditRepeatingQuestAction, EditRepeatingQuestViewState>(
            args
        ) {

        override val stateKey = EditRepeatingQuestReducer.stateKey

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup,
            savedViewState: Bundle?
        ): View {
            setHasOptionsMenu(true)
            val view = container.inflate(R.layout.controller_add_repeating_quest_name)

            view.nameTagList.layoutManager = LinearLayoutManager(activity!!)
            view.nameTagList.adapter = EditItemTagAdapter(removeTagCallback = {
                dispatch(EditRepeatingQuestAction.RemoveTag(it))
            })

            return view
        }


        override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
            super.onCreateOptionsMenu(menu, inflater)
            inflater.inflate(R.menu.next_wizard_menu, menu)
        }

        override fun colorStatusBars() {

        }

        override fun onCreateLoadAction() = EditRepeatingQuestAction.LoadName

        override fun render(state: EditRepeatingQuestViewState, view: View) {
            when (state.type) {

                EditRepeatingQuestViewState.StateType.NAME_DATA_LOADED -> {
                    view.rqName.setText(state.name)
                    renderTags(state, view)
                    renderColor(state, view)
                    renderIcon(state, view)
                }

                EditRepeatingQuestViewState.StateType.COLOR_CHANGED ->
                    renderColor(state, view)

                EditRepeatingQuestViewState.StateType.ICON_CHANGED ->
                    renderIcon(state, view)

                EditRepeatingQuestViewState.StateType.VALIDATION_ERROR_EMPTY_NAME ->
                    view.rqName.error = stringRes(R.string.name_validation)

                EditRepeatingQuestViewState.StateType.TAGS_CHANGED -> {
                    renderTags(state, view)
                    renderColor(state, view)
                    renderIcon(state, view)
                }

                else -> {
                }
            }
        }

        private fun renderColor(
            state: EditRepeatingQuestViewState,
            view: View
        ) {
            view.rqColor.onDebounceClick {

                navigate()
                    .toColorPicker(
                        {
                            dispatch(EditRepeatingQuestAction.ChangeColor(it))
                        }, state.color
                    )
            }
        }

        private fun renderIcon(
            state: EditRepeatingQuestViewState,
            view: View
        ) {
            view.rqIconImage.setImageDrawable(
                IconicsDrawable(view.context).largeIcon(state.iicon)
            )

            view.rqIcon.onDebounceClick {
                navigate()
                    .toIconPicker({ icon ->
                        dispatch(EditRepeatingQuestAction.ChangeIcon(icon))
                    }, state.icon)
            }
        }

        private fun renderTags(
            state: EditRepeatingQuestViewState,
            view: View
        ) {
            (view.nameTagList.adapter as EditItemTagAdapter).updateAll(state.tagViewModels)
            val add = view.nameNewTag
            if (state.maxTagsReached) {
                add.gone()
                view.rqMaxTagMessage.visible()
            } else {
                add.visible()
                view.rqMaxTagMessage.gone()

                val adapter = EditItemAutocompleteTagAdapter(state.tags, activity!!)
                add.setAdapter(adapter)
                add.setOnItemClickListener { _, _, position, _ ->
                    dispatch(EditRepeatingQuestAction.AddTag(adapter.getItem(position).name))
                    add.setText("")
                }
                add.threshold = 0
                add.setOnTouchListener { _, _ ->
                    add.showDropDown()
                    false
                }
            }
        }

        private val EditRepeatingQuestViewState.tagViewModels: List<EditItemTagAdapter.TagViewModel>
            get() = questTags.map {
                EditItemTagAdapter.TagViewModel(
                    name = it.name,
                    icon = it.icon?.androidIcon?.icon ?: MaterialDesignIconic.Icon.gmi_label,
                    tag = it
                )
            }

        private val EditRepeatingQuestViewState.iicon: IIcon
            get() = icon?.androidIcon?.icon ?: GoogleMaterial.Icon.gmd_local_florist
    }

    class PickRepeatPatternViewController(args: Bundle? = null) :
        BaseViewController<EditRepeatingQuestAction, EditRepeatingQuestViewState>(
            args
        ) {

        override val stateKey = EditRepeatingQuestReducer.stateKey

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup,
            savedViewState: Bundle?
        ): View {
            setHasOptionsMenu(true)
            val view = container.inflate(R.layout.controller_wizard_options)
            view.wizardOptions.layoutManager = LinearLayoutManager(container.context)
            view.wizardOptions.setHasFixedSize(true)
            val adapter = RepeatPatternOptionAdapter()
            view.wizardOptions.adapter = adapter
            adapter.updateAll(EditRepeatingQuestViewState.RepeatPatternOption.values().map {
                SimpleRecyclerViewViewModel(
                    it
                )
            })
            return view
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            if (item.itemId == android.R.id.home) {
                dispatch(EditRepeatingQuestAction.Back)
                return true
            }
            return super.onOptionsItemSelected(item)
        }

        override fun colorStatusBars() {

        }

        override fun render(state: EditRepeatingQuestViewState, view: View) {
        }

        inner class RepeatPatternOptionAdapter :
            BaseRecyclerViewAdapter<SimpleRecyclerViewViewModel<EditRepeatingQuestViewState.RepeatPatternOption>>(
                R.layout.item_wizard_option
            ) {

            override fun onBindViewModel(
                vm: SimpleRecyclerViewViewModel<EditRepeatingQuestViewState.RepeatPatternOption>,
                view: View,
                holder: SimpleViewHolder
            ) {
                (view as TextView).text = vm.value.text
                view.onDebounceClick {
                    when (MORE_OPTIONS) {
                        vm.value -> navigate().toRepeatPatternPicker(
                            null,
                            { repeatPattern ->
                                dispatch(
                                    EditRepeatingQuestAction.RepeatPatternPicked(
                                        repeatPattern
                                    )
                                )
                            },
                            { }
                        )
                        else -> dispatch(EditRepeatingQuestAction.PickRepeatPattern(vm.value))
                    }
                }
            }
        }

        private val EditRepeatingQuestViewState.RepeatPatternOption.text: String
            get() = when (this) {
                EVERY_DAY -> stringRes(R.string.every_day)
                ONCE_PER_WEEK -> stringRes(R.string.once_a_week)
                THREE_PER_WEEK -> stringRes(R.string.three_times_a_week)
                FIVE_PER_WEEK -> stringRes(R.string.five_times_a_week)
                WORK_DAYS -> stringRes(R.string.every_work_day)
                WEEKEND_DAYS -> stringRes(R.string.every_weekend_day)
                MORE_OPTIONS -> stringRes(R.string.more_options)
            }
    }

    class PickDurationViewController(args: Bundle? = null) :
        BaseViewController<EditRepeatingQuestAction, EditRepeatingQuestViewState>(
            args
        ) {
        override val stateKey = EditRepeatingQuestReducer.stateKey

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup,
            savedViewState: Bundle?
        ): View {
            setHasOptionsMenu(true)
            val view = container.inflate(R.layout.controller_wizard_options)
            view.wizardOptions.layoutManager = LinearLayoutManager(container.context)
            view.wizardOptions.setHasFixedSize(true)
            val adapter = DurationOptionAdapter()
            view.wizardOptions.adapter = adapter
            adapter.updateAll(EditRepeatingQuestViewState.DurationOption.values().map {
                SimpleRecyclerViewViewModel(
                    it
                )
            })
            return view
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            if (item.itemId == android.R.id.home) {
                dispatch(EditRepeatingQuestAction.Back)
                return true
            }
            return super.onOptionsItemSelected(item)
        }

        override fun colorStatusBars() {

        }

        override fun render(state: EditRepeatingQuestViewState, view: View) {
        }

        inner class DurationOptionAdapter :
            BaseRecyclerViewAdapter<SimpleRecyclerViewViewModel<EditRepeatingQuestViewState.DurationOption>>(
                R.layout.item_wizard_option
            ) {

            override fun onBindViewModel(
                vm: SimpleRecyclerViewViewModel<EditRepeatingQuestViewState.DurationOption>,
                view: View,
                holder: SimpleViewHolder
            ) {
                (view as TextView).text = vm.value.text
                view.onDebounceClick {
                    if (vm.value == EditRepeatingQuestViewState.DurationOption.MORE_OPTIONS) {
                        navigate()
                            .toDurationPicker(
                                null
                            ) { dispatch(EditRepeatingQuestAction.DurationPicked(it.intValue)) }
                    } else {
                        dispatch(EditRepeatingQuestAction.PickDuration(vm.value))
                    }
                }
            }
        }

        private val EditRepeatingQuestViewState.DurationOption.text: String
            get() = when (this) {
                TEN_MINUTES -> stringRes(R.string.ten_minutes)
                FIFTEEN_MINUTES -> stringRes(R.string.fifteen_minutes)
                TWENTY_FIVE_MINUTES -> stringRes(R.string.twenty_five_minutes)
                THIRTY_MINUTES -> stringRes(R.string.thirty_minutes)
                ONE_HOUR -> stringRes(R.string.one_hour)
                TWO_HOURS -> stringRes(R.string.two_hours)
                EditRepeatingQuestViewState.DurationOption.MORE_OPTIONS -> stringRes(R.string.more_options)
            }
    }

    class SummaryViewController(args: Bundle? = null) :
        BaseViewController<EditRepeatingQuestAction, EditRepeatingQuestViewState>(
            args
        ) {
        override val stateKey = EditRepeatingQuestReducer.stateKey

        private lateinit var newSubQuestWatcher: TextWatcher

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup,
            savedViewState: Bundle?
        ): View {
            setHasOptionsMenu(true)
            val view = container.inflate(R.layout.controller_add_repeating_quest_summary)
            view.summarySubQuestList.layoutManager = LinearLayoutManager(activity!!)
            view.summarySubQuestList.adapter =
                ReadOnlySubQuestAdapter(view.summarySubQuestList, useLightTheme = true)

            newSubQuestWatcher = object : TextWatcher {
                override fun afterTextChanged(editable: Editable) {
                    if (editable.isBlank()) {
                        view.summaryAddSubQuest.invisible()
                    } else {
                        view.summaryAddSubQuest.visible()
                    }
                }

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

                }
            }

            view.summarySubQuestName.addTextChangedListener(newSubQuestWatcher)

            view.summaryAddSubQuest.onDebounceClick {
                addSubQuest(view)
            }

            view.summarySubQuestName.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    addSubQuest(view)
                }
                true
            }

            view.summaryTagList.layoutManager = LinearLayoutManager(activity!!)
            view.summaryTagList.adapter = EditItemTagAdapter(removeTagCallback = {
                dispatch(EditRepeatingQuestAction.RemoveTag(it))
            })

            return view
        }

        private fun addSubQuest(view: View) {
            val name = view.summarySubQuestName.text.toString()
            dispatch(EditRepeatingQuestAction.AddSubQuest(name))
        }

        override fun onAttach(view: View) {
            super.onAttach(view)
            view.summaryContainer.requestFocus()
        }

        override fun onDestroyView(view: View) {
            view.summarySubQuestName.removeTextChangedListener(newSubQuestWatcher)
            super.onDestroyView(view)
        }

        override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
            super.onCreateOptionsMenu(menu, inflater)
            inflater.inflate(R.menu.edit_repeating_quest_menu, menu)
        }

        override fun onCreateLoadAction() = EditRepeatingQuestAction.LoadSummary

        override fun colorStatusBars() {

        }

        override fun render(state: EditRepeatingQuestViewState, view: View) {

            when (state.type) {

                EditRepeatingQuestViewState.StateType.SUMMARY_DATA_LOADED -> {
                    view.summaryName.setText(state.name)
                    renderTags(view, state)
                    renderRepeatPattern(view, state)
                    renderStartTime(view, state)
                    renderDuration(view, state)
                    renderReminder(view, state)
                    renderIcon(view, state)
                    renderColor(view, state)
                    renderChallenge(view, state)
                    renderNote(view, state)
                }

                EditRepeatingQuestViewState.StateType.TAGS_CHANGED ->
                    renderTags(view, state)

                EditRepeatingQuestViewState.StateType.SUB_QUEST_ADDED -> {
                    (view.summarySubQuestList.adapter as ReadOnlySubQuestAdapter).add(
                        ReadOnlySubQuestAdapter.ReadOnlySubQuestViewModel(
                            UUID.randomUUID().toString(),
                            state.newSubQuestName
                        )
                    )
                    view.summarySubQuestName.setText("")
                    view.summarySubQuestName.requestFocus()
                    view.summaryAddSubQuest.invisible()
                }

                EditRepeatingQuestViewState.StateType.REPEAT_PATTERN_CHANGED ->
                    renderRepeatPattern(view, state)

                EditRepeatingQuestViewState.StateType.START_TIME_CHANGED ->
                    renderStartTime(view, state)

                EditRepeatingQuestViewState.StateType.DURATION_CHANGED ->
                    renderDuration(view, state)

                EditRepeatingQuestViewState.StateType.REMINDER_CHANGED ->
                    renderReminder(view, state)

                EditRepeatingQuestViewState.StateType.COLOR_CHANGED ->
                    renderColor(view, state)

                EditRepeatingQuestViewState.StateType.ICON_CHANGED ->
                    renderIcon(view, state)

                EditRepeatingQuestViewState.StateType.CHALLENGE_CHANGED ->
                    renderChallenge(view, state)

                EditRepeatingQuestViewState.StateType.NOTE_CHANGED ->
                    renderNote(view, state)

                EditRepeatingQuestViewState.StateType.VALIDATION_ERROR_EMPTY_NAME -> {
                    view.summaryName.error = stringRes(R.string.name_validation)
                }

                EditRepeatingQuestViewState.StateType.VALID_NAME -> {
                    val newSubQuestNames = view.summarySubQuestList.children.map {
                        it.editSubQuestName.text.toString()
                    }
                    dispatch(EditRepeatingQuestAction.SaveNew(newSubQuestNames))
                }

                else -> {
                }

            }
        }

        private fun renderTags(
            view: View,
            state: EditRepeatingQuestViewState
        ) {
            (view.summaryTagList.adapter as EditItemTagAdapter).updateAll(state.tagViewModels)

            val add = view.summaryNewTag
            if (state.maxTagsReached) {
                add.gone()
                view.maxTagsMessage.visible()
            } else {
                add.visible()
                view.maxTagsMessage.gone()
                val adapter = EditItemAutocompleteTagAdapter(state.tags, activity!!)
                add.setAdapter(adapter)
                add.setOnItemClickListener { _, _, position, _ ->
                    dispatch(EditRepeatingQuestAction.AddTag(adapter.getItem(position).name))
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
            state: EditRepeatingQuestViewState
        ) {
            view.summaryColor.onDebounceClick {
                navigate()
                    .toColorPicker(
                        { c ->
                            dispatch(EditRepeatingQuestAction.ChangeColor(c))
                        }, state.color
                    )
            }
        }

        private fun renderIcon(
            view: View,
            state: EditRepeatingQuestViewState
        ) {
            view.summarySelectedIcon.setImageDrawable(
                IconicsDrawable(view.context).largeIcon(state.iicon)
            )

            view.summaryIcon.onDebounceClick {
                navigate()
                    .toIconPicker({ icon ->
                        dispatch(EditRepeatingQuestAction.ChangeIcon(icon))
                    }, state.icon)
            }
        }

        private fun renderDuration(view: View, state: EditRepeatingQuestViewState) {

            view.summaryDuration.text = state.durationText
            view.summaryDuration.onDebounceClick {
                navigate()
                    .toDurationPicker(
                        state.duration
                    ) { dispatch(EditRepeatingQuestAction.ChangeDuration(it.intValue)) }
            }
        }

        private fun renderRepeatPattern(view: View, state: EditRepeatingQuestViewState) {
            view.summaryRepeatPattern.text = state.repeatPatternText
            view.summaryRepeatPattern.onDebounceClick {
                navigate().toRepeatPatternPicker(state.repeatPattern, { p ->
                    dispatch(EditRepeatingQuestAction.ChangeRepeatPattern(p))
                })
            }
        }

        private fun renderStartTime(
            view: View,
            state: EditRepeatingQuestViewState
        ) {
            view.summaryStartTime.text = state.startTimeText
            view.summaryStartTime.onDebounceClick {
                val startTime = state.startTime ?: Time.now()

                createTimePickerDialog(
                    startTime = startTime,
                    onTimePicked = {
                        dispatch(EditRepeatingQuestAction.ChangeStartTime(it))
                    }
                ).show(router)
            }
        }

        private fun renderReminder(view: View, state: EditRepeatingQuestViewState) {
            view.summaryReminder.text = state.reminderText
            view.summaryReminder.onDebounceClick {
                navigate()
                    .toReminderPicker(
                        object : ReminderPickerDialogController.ReminderPickedListener {
                            override fun onReminderPicked(reminder: ReminderViewModel?) {
                                dispatch(EditRepeatingQuestAction.ChangeReminder(reminder))
                            }
                        }, state.reminder
                    )
            }
        }


        private fun renderChallenge(
            view: View,
            state: EditRepeatingQuestViewState
        ) {
            view.summaryChallenge.text = state.challengeText
            view.summaryChallenge.onDebounceClick {
                navigate().toChallengePicker(state.challenge) { challenge ->
                    dispatch(EditRepeatingQuestAction.ChangeChallenge(challenge))
                }
            }
        }

        private fun renderNote(view: View, state: EditRepeatingQuestViewState) {
            view.summaryNote.text = state.noteText
            view.summaryNote.onDebounceClick {
                navigate()
                    .toNotePicker(
                        state.note
                    ) { text ->
                        dispatch(EditRepeatingQuestAction.ChangeNote(text))
                    }
            }
        }

        private val EditRepeatingQuestViewState.iicon: IIcon
            get() = icon?.androidIcon?.icon ?: GoogleMaterial.Icon.gmd_local_florist

        private val EditRepeatingQuestViewState.startTimeText: String
            get() = startTime?.let { "At ${it.toString(shouldUse24HourFormat)}" }
                ?: stringRes(R.string.unscheduled)

        private val EditRepeatingQuestViewState.durationText: String
            get() = "For ${DurationFormatter.formatReadable(activity!!, duration.intValue)}"

        private val EditRepeatingQuestViewState.repeatPatternText: String
            get() = "Repeat ${RepeatPatternFormatter.format(activity!!, repeatPattern!!)}"

        private val EditRepeatingQuestViewState.reminderText: String
            get() = reminder?.let {
                ReminderTimeFormatter.format(
                    it.minutesFromStart.toInt(),
                    activity!!
                )
            } ?: stringRes(R.string.do_not_remind)

        private val EditRepeatingQuestViewState.challengeText: String
            get() = challenge?.name ?: stringRes(R.string.add_to_challenge)

        private val EditRepeatingQuestViewState.noteText: String
            get() = if (note.isBlank()) stringRes(R.string.tap_to_add_note) else note

        private val EditRepeatingQuestViewState.tagViewModels: List<EditItemTagAdapter.TagViewModel>
            get() = questTags.map {
                EditItemTagAdapter.TagViewModel(
                    name = it.name,
                    icon = it.icon?.androidIcon?.icon ?: MaterialDesignIconic.Icon.gmi_label,
                    tag = it
                )
            }

    }
}