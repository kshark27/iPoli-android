package io.ipoli.android.repeatingquest.add

import io.ipoli.android.Constants
import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.Validator
import io.ipoli.android.common.datetime.Duration
import io.ipoli.android.common.datetime.Minute
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.datetime.minutes
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import io.ipoli.android.quest.Reminder
import io.ipoli.android.quest.reminder.picker.ReminderViewModel
import io.ipoli.android.quest.subquest.SubQuest
import io.ipoli.android.repeatingquest.add.EditRepeatingQuestViewState.DurationOption.*
import io.ipoli.android.repeatingquest.add.EditRepeatingQuestViewState.RepeatPatternOption.*
import io.ipoli.android.repeatingquest.add.EditRepeatingQuestViewState.StateType.*
import io.ipoli.android.repeatingquest.entity.RepeatPattern
import io.ipoli.android.tag.Tag
import org.threeten.bp.DayOfWeek
import java.util.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 04/09/2018.
 */
sealed class EditRepeatingQuestAction : Action {
    data class ChangeColor(val color: Color) : EditRepeatingQuestAction() {
        override fun toMap() = mapOf("color" to color.name)
    }

    data class ChangeIcon(val icon: Icon?) : EditRepeatingQuestAction() {
        override fun toMap() = mapOf("icon" to icon?.name)
    }

    data class ShowRepeatPatternPicker(val name: String) : EditRepeatingQuestAction() {
        override fun toMap() = mapOf("name" to name)
    }

    data class PickRepeatPattern(val repeatPatternOption: EditRepeatingQuestViewState.RepeatPatternOption) :
        EditRepeatingQuestAction() {
        override fun toMap() = mapOf("repeatPatternOption" to repeatPatternOption)
    }

    data class RepeatPatternPicked(val repeatPattern: RepeatPattern) :
        EditRepeatingQuestAction() {
        override fun toMap() = mapOf("repeatPattern" to repeatPattern)
    }

    data class PickDuration(val durationOption: EditRepeatingQuestViewState.DurationOption) :
        EditRepeatingQuestAction() {
        override fun toMap() = mapOf("durationOption" to durationOption)
    }

    data class DurationPicked(val minutes: Int) : EditRepeatingQuestAction() {
        override fun toMap() = mapOf("minutes" to minutes)
    }

    data class AddSubQuest(val name: String) : EditRepeatingQuestAction() {
        override fun toMap() = mapOf("name" to name)
    }

    data class ChangeChallenge(val challenge: Challenge?) : EditRepeatingQuestAction() {
        override fun toMap() = mapOf("challenge" to challenge)
    }

    data class ChangeReminder(val reminder: ReminderViewModel?) : EditRepeatingQuestAction() {
        override fun toMap() = mapOf("reminder" to reminder)
    }

    data class ChangeRepeatPattern(val repeatPattern: RepeatPattern?) : EditRepeatingQuestAction() {
        override fun toMap() = mapOf("repeatPattern" to repeatPattern)
    }

    data class ChangeStartTime(val time: Time?) : EditRepeatingQuestAction() {
        override fun toMap() = mapOf("time" to time)
    }

    data class ChangeDuration(val minutes: Int) : EditRepeatingQuestAction() {
        override fun toMap() = mapOf("minutes" to minutes)
    }

    data class ChangeNote(val text: String) : EditRepeatingQuestAction() {
        override fun toMap() = mapOf("text" to text)
    }

    data class RemoveTag(val tag: Tag) : EditRepeatingQuestAction() {
        override fun toMap() = mapOf("tag" to tag)
    }

    data class AddTag(val tagName: String) : EditRepeatingQuestAction() {
        override fun toMap() = mapOf("tagName" to tagName)
    }

    data class ValidateName(val name: String) : EditRepeatingQuestAction() {
        override fun toMap() = mapOf("name" to name)
    }

    data class Load(val repeatingQuestId: String) : EditRepeatingQuestAction() {
        override fun toMap() = mapOf(
            "mode" to if (repeatingQuestId.isBlank()) "add" else "edit",
            "repeatingQuestId" to repeatingQuestId
        )
    }

    object Back : EditRepeatingQuestAction()

    data class Save(val newSubQuestNames: List<String>) : EditRepeatingQuestAction()

    object LoadName : EditRepeatingQuestAction()
    object LoadSummary : EditRepeatingQuestAction()

    data class SaveNew(val newSubQuestNames: List<String>) : EditRepeatingQuestAction()
}

object EditRepeatingQuestReducer : BaseViewStateReducer<EditRepeatingQuestViewState>() {

    override val stateKey = key<EditRepeatingQuestViewState>()

    override fun reduce(
        state: AppState,
        subState: EditRepeatingQuestViewState,
        action: Action
    ) =
        when (action) {

            is EditRepeatingQuestAction.ShowRepeatPatternPicker -> {
                val name = action.name
                val errors = Validator.validate(action).check<ValidationError> {
                    "name" {
                        given { name.isBlank() } addError ValidationError.EMPTY_NAME
                    }
                }

                if (errors.isEmpty()) {
                    subState.copy(
                        type = NEXT_PAGE,
                        name = action.name,
                        adapterPosition = subState.adapterPosition + 1
                    )
                } else {
                    subState.copy(
                        type = VALIDATION_ERROR_EMPTY_NAME
                    )
                }
            }

            is EditRepeatingQuestAction.ValidateName -> {
                val name = action.name
                val errors = Validator.validate(action).check<ValidationError> {
                    "name" {
                        given { name.isBlank() } addError ValidationError.EMPTY_NAME
                    }
                }
                if (errors.isEmpty()) {
                    subState.copy(
                        type = VALID_NAME,
                        name = name
                    )
                } else {
                    subState.copy(
                        type = VALIDATION_ERROR_EMPTY_NAME
                    )
                }
            }

            is EditRepeatingQuestAction.ChangeIcon -> {
                subState.copy(
                    type = ICON_CHANGED,
                    icon = action.icon
                )
            }

            is EditRepeatingQuestAction.ChangeColor -> {
                subState.copy(
                    type = COLOR_CHANGED,
                    color = action.color
                )
            }

            is EditRepeatingQuestAction.PickDuration ->
                subState.copy(
                    type = NEXT_PAGE,
                    duration = createDurationFromOption(action.durationOption),
                    adapterPosition = subState.adapterPosition + 1
                )

            is EditRepeatingQuestAction.DurationPicked ->
                subState.copy(
                    type = NEXT_PAGE,
                    duration = action.minutes.minutes,
                    adapterPosition = subState.adapterPosition + 1
                )

            is EditRepeatingQuestAction.PickRepeatPattern ->
                subState.copy(
                    type = NEXT_PAGE,
                    repeatPattern = createRepeatingPatternFromOption(action.repeatPatternOption),
                    adapterPosition = subState.adapterPosition + 1
                )

            is EditRepeatingQuestAction.RepeatPatternPicked ->
                subState.copy(
                    type = NEXT_PAGE,
                    repeatPattern = action.repeatPattern,
                    adapterPosition = subState.adapterPosition + 1
                )

            EditRepeatingQuestAction.Back -> {
                val adapterPosition = subState.adapterPosition - 1
                if (adapterPosition < 0) {
                    subState.copy(
                        type = CLOSE
                    )
                } else {
                    subState.copy(
                        type = PREVIOUS_PAGE,
                        adapterPosition = adapterPosition
                    )
                }
            }

            EditRepeatingQuestAction.LoadSummary ->
                subState.copy(
                    type = SUMMARY_DATA_LOADED
                )

            is EditRepeatingQuestAction.Load -> {
                val dataState = state.dataState
                val rq = dataState.repeatingQuests!!.first { it.id == action.repeatingQuestId }

                val challenge = rq.challengeId?.let { challengeId ->
                    state.dataState.challenges?.first { it.id == challengeId }
                }

                subState.copy(
                    type = SUMMARY_DATA_LOADED,
                    id = rq.id,
                    name = rq.name,
                    questTags = rq.tags,
                    tags = state.dataState.tags - rq.tags,
//                    subQuestNames = rq.subQuests.map { it.name },
                    subQuests = rq.subQuests.map {
                        UUID.randomUUID().toString() to it
                    }.toMap(),
                    startTime = rq.startTime,
                    repeatPattern = rq.repeatPattern,
                    duration = rq.duration.minutes,
                    reminder = rq.reminders.firstOrNull()?.let {
                        if (it is Reminder.Relative) {
                            ReminderViewModel(
                                it.message,
                                it.minutesFromStart
                            )
                        } else null
                    },
                    icon = rq.icon,
                    color = rq.color,
                    challenge = challenge,
                    note = rq.note,
                    maxTagsReached = rq.tags.size >= Constants.MAX_TAGS_PER_ITEM
                )
            }

            EditRepeatingQuestAction.LoadName ->
                subState.copy(
                    type = NAME_DATA_LOADED,
                    tags = state.dataState.tags
                )

            is EditRepeatingQuestAction.AddSubQuest ->
                subState.copy(
                    type = SUB_QUEST_ADDED,
//                    subQuestNames = subState.subQuestNames + action.name,
                    newSubQuestName = action.name
                )

            is EditRepeatingQuestAction.ChangeRepeatPattern ->
                subState.copy(
                    type = REPEAT_PATTERN_CHANGED,
                    repeatPattern = action.repeatPattern
                )

            is EditRepeatingQuestAction.ChangeStartTime ->
                subState.copy(
                    type = START_TIME_CHANGED,
                    startTime = action.time
                )

            is EditRepeatingQuestAction.ChangeDuration ->
                subState.copy(
                    type = DURATION_CHANGED,
                    duration = action.minutes.minutes
                )

            is EditRepeatingQuestAction.ChangeReminder ->
                subState.copy(
                    type = REMINDER_CHANGED,
                    reminder = action.reminder
                )

            is EditRepeatingQuestAction.ChangeChallenge ->
                subState.copy(
                    type = CHALLENGE_CHANGED,
                    challenge = action.challenge
                )

            is EditRepeatingQuestAction.ChangeNote -> {
                val note = action.text.trim()
                subState.copy(
                    type = NOTE_CHANGED,
                    note = note
                )
            }

            is DataLoadedAction.TagsChanged ->
                subState.copy(
                    type = TAGS_CHANGED,
                    tags = action.tags - subState.questTags
                )

            is EditRepeatingQuestAction.RemoveTag -> {
                val questTags = subState.questTags - action.tag
                subState.copy(
                    type = TAGS_CHANGED,
                    questTags = questTags,
                    tags = subState.tags + action.tag,
                    maxTagsReached = questTags.size >= Constants.MAX_TAGS_PER_ITEM
                )
            }

            is EditRepeatingQuestAction.AddTag -> {
                val tag = subState.tags.first { it.name == action.tagName }
                val questTags = subState.questTags + tag
                subState.copy(
                    type = TAGS_CHANGED,
                    questTags = questTags,
                    tags = subState.tags - tag,
                    maxTagsReached = questTags.size >= Constants.MAX_TAGS_PER_ITEM
                )
            }

            is EditRepeatingQuestAction.SaveNew,
            is EditRepeatingQuestAction.Save ->
                subState.copy(
                    type = CLOSE
                )

            else -> subState
        }

    private fun createRepeatingPatternFromOption(
        option: EditRepeatingQuestViewState.RepeatPatternOption
    ) =
        when (option) {
            EVERY_DAY -> RepeatPattern.Daily()
            ONCE_PER_WEEK -> RepeatPattern.Flexible.Weekly(1)
            THREE_PER_WEEK -> RepeatPattern.Flexible.Weekly(3)
            FIVE_PER_WEEK -> RepeatPattern.Flexible.Weekly(5)
            WORK_DAYS -> RepeatPattern.Weekly(
                setOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY
                )
            )
            WEEKEND_DAYS -> RepeatPattern.Weekly(
                setOf(
                    DayOfWeek.SATURDAY,
                    DayOfWeek.SUNDAY
                )
            )

            else -> throw IllegalArgumentException("Unknown repeat option $option")
        }

    private fun createDurationFromOption(option: EditRepeatingQuestViewState.DurationOption) =
        when (option) {
            TEN_MINUTES ->
                10.minutes
            FIFTEEN_MINUTES -> 15.minutes
            TWENTY_FIVE_MINUTES -> 25.minutes
            THIRTY_MINUTES -> 30.minutes
            ONE_HOUR -> 60.minutes
            TWO_HOURS -> 120.minutes
            else -> throw IllegalArgumentException("Unknown duration option $option")
        }

    override fun defaultState() =
        EditRepeatingQuestViewState(
            type = INITIAL,
            id = "",
            adapterPosition = 0,
            name = "",
            tags = emptyList(),
            questTags = emptyList(),
            subQuests = emptyMap(),
            newSubQuestName = "",
            startTime = null,
            color = Color.GREEN,
            icon = null,
            repeatPattern = null,
            reminder = ReminderViewModel(
                message = "",
                minutesFromStart = Constants.DEFAULT_RELATIVE_REMINDER_MINUTES_FROM_START
            ),
            challenge = null,
            note = "",
            maxTagsReached = false
        )

    enum class ValidationError {
        EMPTY_NAME
    }
}

data class EditRepeatingQuestViewState(
    val type: StateType,
    val id: String,
    val adapterPosition: Int,
    val name: String,
    val tags: List<Tag>,
    val questTags: List<Tag>,
    val subQuests: Map<String, SubQuest>,
//    val subQuestNames: List<String>,
    val newSubQuestName: String,
    val startTime: Time?,
    val color: Color,
    val icon: Icon?,
    val repeatPattern: RepeatPattern?,
    val reminder: ReminderViewModel?,
    val challenge: Challenge?,
    val duration: Duration<Minute> = Constants.DEFAULT_QUEST_DURATION.minutes,
    val note: String,
    val maxTagsReached: Boolean
) : BaseViewState() {

    enum class StateType {
        INITIAL,
        VALID_NAME,
        VALIDATION_ERROR_EMPTY_NAME,
        TAGS_CHANGED,
        NEXT_PAGE,
        PREVIOUS_PAGE,
        SUB_QUEST_ADDED,
        CLOSE,
        REPEAT_PATTERN_CHANGED,
        START_TIME_CHANGED,
        DURATION_CHANGED,
        REMINDER_CHANGED,
        CHALLENGE_CHANGED,
        COLOR_CHANGED,
        ICON_CHANGED,
        NOTE_CHANGED,
        SUMMARY_DATA_LOADED,
        NAME_DATA_LOADED
    }

    enum class RepeatPatternOption {
        EVERY_DAY,
        ONCE_PER_WEEK,
        THREE_PER_WEEK,
        FIVE_PER_WEEK,
        WORK_DAYS,
        WEEKEND_DAYS,
        EVERY_X_DAYS,
        MORE_OPTIONS
    }

    enum class DurationOption {
        TEN_MINUTES,
        FIFTEEN_MINUTES,
        TWENTY_FIVE_MINUTES,
        THIRTY_MINUTES,
        ONE_HOUR,
        TWO_HOURS,
        MORE_OPTIONS
    }
}