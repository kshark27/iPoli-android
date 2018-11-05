package io.ipoli.android.quest.edit

import android.app.DatePickerDialog
import android.app.Dialog
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import io.ipoli.android.R
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.datetime.minutes
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.text.DateFormatter
import io.ipoli.android.common.text.DurationFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import io.ipoli.android.quest.edit.EditQuestViewState.StateType.*
import io.ipoli.android.quest.reminder.formatter.ReminderTimeFormatter
import io.ipoli.android.quest.reminder.picker.ReminderPickerDialogController
import io.ipoli.android.quest.reminder.picker.ReminderViewModel
import io.ipoli.android.quest.subquest.view.ReadOnlySubQuestAdapter
import io.ipoli.android.tag.widget.EditItemAutocompleteTagAdapter
import io.ipoli.android.tag.widget.EditItemTagAdapter
import kotlinx.android.synthetic.main.controller_edit_quest.view.*
import kotlinx.android.synthetic.main.item_edit_repeating_quest_sub_quest.view.*
import kotlinx.android.synthetic.main.view_no_elevation_toolbar.view.*
import org.threeten.bp.LocalDate
import java.util.*


/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 4/10/18.
 */
class EditQuestViewController(args: Bundle? = null) :
    ReduxViewController<EditQuestAction, EditQuestViewState, EditQuestReducer>(args) {

    override val reducer = EditQuestReducer

    private var questId: String? = null
    private var params: Params? = null

    private lateinit var newSubQuestWatcher: TextWatcher


    data class Params(
        val name: String?,
        val scheduleDate: LocalDate?,
        val startTime: Time?,
        val duration: Int?,
        val icon: Icon?,
        val color: Color?,
        val reminderViewModel: ReminderViewModel?
    )


    constructor(
        questId: String?,
        params: Params? = null
    ) : this() {
        this.questId = questId
        this.params = params
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        applyStatusBarColors = false
        val view = inflater.inflate(R.layout.controller_edit_quest, container, false)
        setToolbar(view.toolbar)

        initSubQuests(view)

        view.questTagList.layoutManager = LinearLayoutManager(activity!!)
        view.questTagList.adapter = EditItemTagAdapter(removeTagCallback = {
            dispatch(EditQuestAction.RemoveTag(it))
        })

        return view
    }

    private fun initSubQuests(view: View) {
        val adapter = ReadOnlySubQuestAdapter(view.questSubQuestList, useLightTheme = true)
        view.questSubQuestList.layoutManager = LinearLayoutManager(activity!!)
        view.questSubQuestList.adapter = adapter

        newSubQuestWatcher = object : TextWatcher {
            override fun afterTextChanged(editable: Editable) {
                if (editable.isBlank()) {
                    view.questAddSubQuest.invisible()
                } else {
                    view.questAddSubQuest.visible()
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

        view.questSubQuestName.addTextChangedListener(newSubQuestWatcher)

        view.questAddSubQuest.onDebounceClick {
            addSubQuest(view)
        }

        view.questSubQuestName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addSubQuest(view)
            }
            true
        }
    }

    override fun onCreateLoadAction(): EditQuestAction? {
        return if (questId.isNullOrEmpty()) {
            EditQuestAction.StartAdd(params)
        } else {
            EditQuestAction.Load(questId!!, params)
        }
    }

    override fun onAttach(view: View) {
            super.onAttach(view)
            showBackButton()
            exitFullScreen()
    }

    private fun addSubQuest(view: View) {
        val name = view.questSubQuestName.text.toString()
        dispatch(EditQuestAction.AddSubQuest(name))
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.edit_quest_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            android.R.id.home -> {
                router.handleBack()
            }

            R.id.actionSave -> {
                dispatch(
                    EditQuestAction.Validate(
                        view!!.questName.text.toString()
                    )
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun render(state: EditQuestViewState, view: View) {
        when (state.type) {
            DATA_LOADED -> {
                toolbarTitle = state.toolbarTitle

                view.questName.setText(state.name)
                renderScheduledDate(view, state)
                renderStartTime(view, state)
                renderDuration(view, state)
                renderReminder(view, state)
                renderIcon(view, state)
                renderColor(view, state)

                renderSubQuests(view, state)
                renderChallenge(view, state)
                renderNote(view, state)

                renderTags(view, state)

            }

            TAGS_CHANGED -> {
                renderTags(view, state)
            }

            SCHEDULE_DATE_CHANGED -> {
                renderScheduledDate(view, state)
            }

            DURATION_CHANGED -> {
                renderDuration(view, state)
            }

            START_TIME_CHANGED -> {
                renderStartTime(view, state)
            }

            ICON_CHANGED -> {
                renderIcon(view, state)
            }

            COLOR_CHANGED -> {
                renderColor(view, state)
            }

            REMINDER_CHANGED -> {
                renderReminder(view, state)
            }

            CHALLENGE_CHANGED -> {
                renderChallenge(view, state)
            }

            NOTE_CHANGED -> {
                renderNote(view, state)
            }

            SUB_QUEST_ADDED -> {
                (view.questSubQuestList.adapter as ReadOnlySubQuestAdapter).add(
                    ReadOnlySubQuestAdapter.ReadOnlySubQuestViewModel(
                        UUID.randomUUID().toString(),
                        state.newSubQuestName
                    )
                )
                view.questSubQuestName.setText("")
                view.questSubQuestName.requestFocus()
                view.questAddSubQuest.invisible()
            }

            VALIDATION_ERROR_EMPTY_NAME -> {
                view.questName.error = stringRes(R.string.think_of_a_name)
            }

            VALIDATION_SUCCESSFUL -> {
                val newSubQuestNames = view.questSubQuestList.children.map {
                    val v = it.editSubQuestName
                    v.tag.toString() to v.text.toString()
                }.toMap()
                dispatch(EditQuestAction.Save(newSubQuestNames))
                router.handleBack()
            }
            else -> {
            }
        }
    }

    private fun renderTags(
        view: View,
        state: EditQuestViewState
    ) {
        (view.questTagList.adapter as EditItemTagAdapter).updateAll(state.tagViewModels)
        val add = view.addQuestTag
        if (state.maxTagsReached) {
            add.gone()
            view.maxTagsMessage.visible()
        } else {
            add.visible()
            view.maxTagsMessage.gone()

            val adapter = EditItemAutocompleteTagAdapter(state.tags, activity!!)
            add.setAdapter(adapter)
            add.setOnItemClickListener { _, _, position, _ ->
                dispatch(EditQuestAction.AddTag(adapter.getItem(position).name))
                add.setText("")
            }
            add.threshold = 0
            add.setOnTouchListener { _, _ ->
                add.showDropDown()
                false
            }
        }

    }

    private fun renderSubQuests(view: View, state: EditQuestViewState) {
        (view.questSubQuestList.adapter as ReadOnlySubQuestAdapter).updateAll(state.subQuestViewModels)
    }

    override fun onDestroyView(view: View) {
        view.questSubQuestName.removeTextChangedListener(newSubQuestWatcher)
        super.onDestroyView(view)
    }

    private fun renderChallenge(
        view: View,
        state: EditQuestViewState
    ) {
        view.questChallenge.text = state.challengeText
        view.questChallenge.onDebounceClick {
            navigate().toChallengePicker(state.challenge) { challenge ->
                dispatch(EditQuestAction.ChangeChallenge(challenge))
            }
        }
    }

    private fun renderScheduledDate(
        view: View,
        state: EditQuestViewState
    ) {
        view.questScheduleDate.text = state.scheduleDateText
        view.questScheduleDate.onDebounceClick {
            val date = state.scheduleDate ?: LocalDate.now()
            val datePickerDialog = DatePickerDialog(
                view.context,
                DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                    dispatch(EditQuestAction.ChangeDate(LocalDate.of(year, month + 1, dayOfMonth)))
                }, date.year, date.month.value - 1, date.dayOfMonth
            )
            datePickerDialog.setButton(
                Dialog.BUTTON_NEUTRAL,
                view.context.getString(R.string.do_not_know)
            ) { _, _ -> dispatch(EditQuestAction.ChangeDate(null)) }
            datePickerDialog.show()
        }
    }

    private fun renderDuration(
        view: View,
        state: EditQuestViewState
    ) {
        view.questDuration.text = state.durationText
        view.questDuration.onDebounceClick {
            navigate()
                .toDurationPicker(
                    state.duration.minutes
                ) { dispatch(EditQuestAction.ChangeDuration(it.intValue)) }
        }
    }

    private fun renderStartTime(
        view: View,
        state: EditQuestViewState
    ) {
        view.questStartTime.text = state.startTimeText
        view.questStartTime.onDebounceClick {
            val startTime = state.startTime ?: Time.now()
            createTimePickerDialog(
                startTime = startTime,
                onTimePicked = {
                    dispatch(EditQuestAction.ChangeStartTime(it))
                }
            ).show(router)
        }
    }

    private fun renderColor(
        view: View,
        state: EditQuestViewState
    ) {
        colorLayout(view, state)
        view.questColor.onDebounceClick {
            navigate().toColorPicker(
                {
                    dispatch(EditQuestAction.ChangeColor(it))
                },
                state.color
            )
        }
    }

    private fun renderIcon(
        view: View,
        state: EditQuestViewState
    ) {
        view.questSelectedIcon.setImageDrawable(state.iconDrawable)
        view.questIcon.onDebounceClick {
            navigate()
                .toIconPicker({ icon ->
                    dispatch(EditQuestAction.ChangeIcon(icon))
                }, state.icon)
        }
    }

    private fun colorLayout(
        view: View,
        state: EditQuestViewState
    ) {
        val color500 = colorRes(state.color500)
        val color700 = colorRes(state.color700)
        view.appbar.setBackgroundColor(color500)
        view.toolbar.setBackgroundColor(color500)
        view.rootContainer.setBackgroundColor(color500)
        activity?.window?.navigationBarColor = color500
        activity?.window?.statusBarColor = color700
    }

    private fun renderReminder(
        view: View,
        state: EditQuestViewState
    ) {
        view.questReminder.text = state.reminderText
        view.questReminder.onDebounceClick {
            navigate()
                .toReminderPicker(
                    object :
                        ReminderPickerDialogController.ReminderPickedListener {
                        override fun onReminderPicked(reminder: ReminderViewModel?) {
                            dispatch(EditQuestAction.ChangeReminder(reminder))
                        }
                    }, state.reminder
                )
        }

    }


    private fun renderNote(view: View, state: EditQuestViewState) {
        view.questNote.text = state.noteText
        view.questNote.onDebounceClick {
            navigate()
                .toNotePicker(
                    note = state.note,
                    resultListener = { note ->
                        dispatch(EditQuestAction.ChangeNote(note))
                    }
                )
        }
    }

    private val EditQuestViewState.scheduleDateText: String
        get() = DateFormatter.formatWithoutYear(view!!.context, scheduleDate)

    private val EditQuestViewState.color500: Int
        get() = color.androidColor.color500

    private val EditQuestViewState.color700: Int
        get() = color.androidColor.color700

    private val EditQuestViewState.iconDrawable: Drawable
        get() =
            if (icon == null) {
                ContextCompat.getDrawable(view!!.context, R.drawable.ic_icon_white_24dp)!!
            } else {
                val androidIcon = icon.androidIcon
                IconicsDrawable(view!!.context)
                    .largeIcon(androidIcon.icon)
            }

    private val EditQuestViewState.noteText: String
        get() = if (note.isBlank()) stringRes(R.string.tap_to_add_note) else note

    private val EditQuestViewState.startTimeText: String
        get() = startTime?.let { "At ${it.toString(shouldUse24HourFormat)}" }
            ?: stringRes(R.string.unscheduled)

    private val EditQuestViewState.durationText: String
        get() = "For ${DurationFormatter.formatReadable(activity!!, duration)}"

    private val EditQuestViewState.reminderText: String
        get() = reminder?.let {
            ReminderTimeFormatter.format(
                it.minutesFromStart.toInt(),
                activity!!
            )
        }
            ?: stringRes(R.string.do_not_remind)

    private val EditQuestViewState.challengeText: String
        get() = challenge?.name ?: stringRes(R.string.add_to_challenge)

    private val EditQuestViewState.subQuestViewModels: List<ReadOnlySubQuestAdapter.ReadOnlySubQuestViewModel>
        get() = subQuests.entries.map {
            ReadOnlySubQuestAdapter.ReadOnlySubQuestViewModel(
                id = it.key,
                name = it.value.name
            )
        }

    private val EditQuestViewState.tagViewModels: List<EditItemTagAdapter.TagViewModel>
        get() = questTags.map {
            EditItemTagAdapter.TagViewModel(
                name = it.name,
                icon = it.icon?.androidIcon?.icon ?: MaterialDesignIconic.Icon.gmi_label,
                tag = it
            )
        }

    private val EditQuestViewState.toolbarTitle: String
        get() = stringRes(if (id.isEmpty()) R.string.title_add_quest else R.string.title_edit_quest)
}
