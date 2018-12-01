package io.ipoli.android.challenge.add

import io.ipoli.android.Constants
import io.ipoli.android.challenge.QuestPickerAction
import io.ipoli.android.challenge.QuestPickerViewState
import io.ipoli.android.challenge.add.EditChallengeViewState.StateType.*
import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.Validator
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.quest.BaseQuest
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import io.ipoli.android.tag.Tag
import org.threeten.bp.LocalDate

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 3/8/18.
 */
sealed class EditChallengeAction : Action {
    data class ChangeColor(val color: Color) : EditChallengeAction() {
        override fun toMap() = mapOf("color" to color.name)
    }

    data class ChangeIcon(val icon: Icon?) : EditChallengeAction() {
        override fun toMap() = mapOf("icon" to icon?.name)
    }

    data class ChangeDifficulty(val position: Int) : EditChallengeAction() {
        override fun toMap() = mapOf("position" to position)
    }

    data class ValidateName(val name: String, val motivation: String? = null) :
        EditChallengeAction() {
        override fun toMap() = mapOf("name" to name)
    }

    data class ValidateMotivation(val motivationList: List<String>) : EditChallengeAction() {
        override fun toMap() = mapOf("motivationList" to motivationList.joinToString(","))
    }

    data class SelectDate(val date: LocalDate) : EditChallengeAction() {
        override fun toMap() = mapOf("date" to date)
    }

    data class ChangeNote(val note: String) : EditChallengeAction() {
        override fun toMap() = mapOf("note" to note)
    }

    data class RemoveTag(val tag: Tag) : EditChallengeAction() {
        override fun toMap() = mapOf("tag" to tag)
    }

    data class AddTag(val tagName: String) : EditChallengeAction() {
        override fun toMap() = mapOf("tagName" to tagName)
    }

    data class Load(val challengeId: String) : EditChallengeAction() {
        override fun toMap() = mapOf("challengeId" to challengeId)
    }

    data class ChangeEndDate(val date: LocalDate) : EditChallengeAction() {
        override fun toMap() = mapOf("date" to date)
    }

    data class ChangeMotivations(
        val motivation1: String,
        val motivation2: String,
        val motivation3: String
    ) : EditChallengeAction() {

        override fun toMap() = mapOf(
            "motivation1" to motivation1,
            "motivation2" to motivation2,
            "motivation3" to motivation3
        )

    }

    data class AddAverageTrackedValue(val trackedValue: Challenge.TrackedValue.Average) :
        EditChallengeAction() {

        override fun toMap() = mapOf(
            "name" to trackedValue.name,
            "targetValue" to trackedValue.targetValue
        )
    }

    data class AddTargetTrackedValue(val trackedValue: Challenge.TrackedValue.Target) :
        EditChallengeAction() {

        override fun toMap() = mapOf(
            "name" to trackedValue.name,
            "targetValue" to trackedValue.targetValue,
            "isCumulative" to trackedValue.isCumulative
        )
    }

    data class RemoveTrackedValue(val trackedValueId: String) : EditChallengeAction()

    data class UpdateTrackedValue(val trackedValue: Challenge.TrackedValue) :
        EditChallengeAction() {

        override fun toMap() =
            when (trackedValue) {
                is Challenge.TrackedValue.Target ->
                    mapOf(
                        "type" to "Target",
                        "name" to trackedValue.name,
                        "targetValue" to trackedValue.targetValue,
                        "isCumulative" to trackedValue.isCumulative
                    )

                is Challenge.TrackedValue.Average ->
                    mapOf(
                        "type" to "Average",
                        "name" to trackedValue.name,
                        "targetValue" to trackedValue.targetValue
                    )

                else -> mapOf(
                    "type" to "Progress"
                )
            }
    }

    object ShowNext : EditChallengeAction()
    object UpdateSummary : EditChallengeAction()
    object LoadSummary : EditChallengeAction()
    object LoadTags : EditChallengeAction()

    object Back : EditChallengeAction()
    object Save : EditChallengeAction()
    object LoadFirstPage : EditChallengeAction()
    object SaveNew : EditChallengeAction()

    data class ShowTargetTrackedValuePicker(val trackedValues: List<Challenge.TrackedValue>) :
        EditChallengeAction()

    data class ShowAverageTrackedValuePicker(val trackedValues: List<Challenge.TrackedValue>) :
        EditChallengeAction()
}

object EditChallengeReducer : BaseViewStateReducer<EditChallengeViewState>() {

    override val stateKey = key<EditChallengeViewState>()


    override fun reduce(
        state: AppState,
        subState: EditChallengeViewState,
        action: Action
    ) =
        when (action) {
            EditChallengeAction.LoadFirstPage -> {
                subState.copy(
                    type = LOADING
                )
            }

            is EditChallengeAction.Load -> {
                val dataState = state.dataState
                val c = dataState.challenges!!.first { it.id == action.challengeId }
                subState.copy(
                    type = DATA_CHANGED,
                    id = action.challengeId,
                    name = c.name,
                    selectedTags = c.tags,
                    tags = state.dataState.tags,
                    icon = c.icon,
                    color = c.color,
                    difficulty = c.difficulty,
                    end = c.endDate,
                    motivation1 = c.motivation1,
                    motivation2 = c.motivation2,
                    motivation3 = c.motivation3,
                    note = c.note,
                    quests = c.baseQuests,
                    maxTagsReached = c.tags.size >= Constants.MAX_TAGS_PER_ITEM,
                    trackedValues = c.trackedValues,
                    hasChangedColor = true,
                    hasChangedIcon = true
                )
            }

            is EditChallengeAction.ShowNext ->
                if (subState.adapterPosition + 1 > AddChallengeViewController.SUMMARY_INDEX)
                    subState
                else
                    subState.copy(
                        type = NEXT_PAGE,
                        adapterPosition = subState.adapterPosition + 1
                    )

            is EditChallengeAction.LoadTags -> {
                subState.copy(
                    type = TAGS_CHANGED,
                    tags = state.dataState.tags,
                    hasChangedColor = false,
                    hasChangedIcon = false
                )
            }

            is DataLoadedAction.TagsChanged -> {
                subState.copy(
                    type = TAGS_CHANGED,
                    tags = action.tags
                )
            }

            is EditChallengeAction.RemoveTag -> {
                subState.copy(
                    type = TAGS_CHANGED,
                    tags = subState.tags,
                    selectedTags = subState.selectedTags - action.tag,
                    maxTagsReached = false
                )
            }

            is EditChallengeAction.AddTag -> {
                val tag = subState.tags.first { it.name == action.tagName }
                val selectedTags = subState.selectedTags + tag

                val color = if (!subState.hasChangedColor && subState.selectedTags.isEmpty())
                    tag.color
                else subState.color

                val icon =
                    if (!subState.hasChangedIcon && subState.selectedTags.isEmpty() && tag.icon != null)
                        tag.icon
                    else subState.icon

                subState.copy(
                    type = TAGS_CHANGED,
                    tags = subState.tags,
                    selectedTags = selectedTags,
                    maxTagsReached = selectedTags.size >= Constants.MAX_TAGS_PER_ITEM,
                    color = color,
                    icon = icon
                )
            }

            is EditChallengeAction.ChangeColor -> {
                subState.copy(
                    type = COLOR_CHANGED,
                    color = action.color,
                    hasChangedColor = true
                )
            }

            is EditChallengeAction.ChangeIcon ->
                subState.copy(
                    type = ICON_CHANGED,
                    icon = action.icon,
                    hasChangedIcon = true
                )

            is EditChallengeAction.ChangeDifficulty ->
                subState.copy(
                    type = DIFFICULTY_CHANGED,
                    difficulty = Challenge.Difficulty.values()[action.position]
                )

            is EditChallengeAction.ValidateName -> {
                val errors = Validator.validate(action).check<ValidationError> {
                    "name" {
                        given { name.isEmpty() } addError ValidationError.EMPTY_NAME
                    }
                }

                subState.copy(
                    type = if (errors.isEmpty()) {
                        VALIDATION_NAME_SUCCESSFUL
                    } else {
                        VALIDATION_ERROR_EMPTY_NAME
                    },
                    name = action.name,
                    motivation1 = if (action.motivation != null && action.motivation.isNotBlank())
                        action.motivation
                    else
                        subState.motivation1
                )
            }

            is EditChallengeAction.ValidateMotivation -> {
                val errors = Validator.validate(action).check<ValidationError> {
                    "name" {
                        given {
                            motivationList.isEmpty()
                                || motivationList.none { it.isNotBlank() }
                        } addError ValidationError.EMPTY_MOTIVATION
                    }
                }
                val motivationList = action.motivationList
                subState.copy(
                    type = if (errors.isEmpty()) {
                        VALIDATION_MOTIVATION_SUCCESSFUL
                    } else {
                        VALIDATION_ERROR_EMPTY_MOTIVATION
                    },
                    motivation1 = if (motivationList.isNotEmpty()) motivationList[0] else "",
                    motivation2 = if (motivationList.size > 1) motivationList[1] else "",
                    motivation3 = if (motivationList.size > 2) motivationList[2] else ""
                )
            }

            is EditChallengeAction.SelectDate -> {
                subState.copy(
                    type = NEXT_PAGE,
                    adapterPosition = subState.adapterPosition + 1,
                    end = action.date
                )
            }

            is QuestPickerAction.Next -> {
                if (subState.adapterPosition + 1 > AddChallengeViewController.SUMMARY_INDEX) {
                    subState
                } else {
                    val s = state.stateFor(QuestPickerViewState::class.java)
                    subState.copy(
                        type = NEXT_PAGE,
                        adapterPosition = subState.adapterPosition + 1,
                        allQuests = s.allQuests.map {
                            it.baseQuest
                        },
                        selectedQuestIds = s.selectedQuests
                    )
                }
            }

            EditChallengeAction.Back -> {
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

            is EditChallengeAction.LoadSummary -> {
                subState.copy(
                    type = SUMMARY_DATA_LOADED,
                    quests = subState.allQuests.filter { subState.selectedQuestIds.contains(it.id) }
                )
            }

            is EditChallengeAction.ChangeNote ->
                subState.copy(
                    type = NOTE_CHANGED,
                    note = action.note.trim()
                )

            is EditChallengeAction.ChangeEndDate -> {
                subState.copy(
                    type = END_DATE_CHANGED,
                    end = action.date
                )
            }

            is EditChallengeAction.ChangeMotivations -> {
                if (action.motivation1.isEmpty() && action.motivation2.isEmpty() && action.motivation3.isEmpty()) {
                    subState
                } else {
                    subState.copy(
                        type = MOTIVATIONS_CHANGED,
                        motivation1 = action.motivation1,
                        motivation2 = action.motivation2,
                        motivation3 = action.motivation3
                    )
                }
            }

            is EditChallengeAction.ShowTargetTrackedValuePicker ->
                subState.copy(
                    type = SHOW_TARGET_TRACKED_VALUE_PICKER
                )

            is EditChallengeAction.ShowAverageTrackedValuePicker ->
                subState.copy(
                    type = SHOW_AVERAGE_TRACKED_VALUE_PICKER
                )

            is EditChallengeAction.AddTargetTrackedValue ->
                subState.copy(
                    type = TRACKED_VALUES_CHANGED,
                    trackedValues = subState.trackedValues + action.trackedValue
                )

            is EditChallengeAction.AddAverageTrackedValue ->
                subState.copy(
                    type = TRACKED_VALUES_CHANGED,
                    trackedValues = subState.trackedValues + action.trackedValue
                )

            is EditChallengeAction.RemoveTrackedValue ->
                subState.copy(
                    type = TRACKED_VALUES_CHANGED,
                    trackedValues = subState.trackedValues
                        .filter { it.id != action.trackedValueId }
                )

            is EditChallengeAction.UpdateTrackedValue ->
                subState.copy(
                    type = TRACKED_VALUES_CHANGED,
                    trackedValues = subState.trackedValues.map {
                        if (it.id == action.trackedValue.id)
                            action.trackedValue
                        else
                            it
                    }
                )

            EditChallengeAction.SaveNew,
            EditChallengeAction.Save ->
                subState.copy(
                    type = CLOSE
                )


            else -> subState
        }

    override fun defaultState() =
        EditChallengeViewState(
            type = INITIAL,
            adapterPosition = 0,
            id = "",
            name = "",
            selectedTags = emptyList(),
            tags = emptyList(),
            color = Color.GREEN,
            icon = null,
            difficulty = Challenge.Difficulty.NORMAL,
            end = LocalDate.now(),
            motivation1 = "",
            motivation2 = "",
            motivation3 = "",
            allQuests = emptyList(),
            trackedValues = emptyList(),
            quests = emptyList(),
            selectedQuestIds = emptySet(),
            note = "",
            maxTagsReached = false,
            hasChangedColor = false,
            hasChangedIcon = false
        )

    enum class ValidationError {
        EMPTY_NAME, EMPTY_MOTIVATION
    }
}

data class EditChallengeViewState(
    val type: EditChallengeViewState.StateType,
    val adapterPosition: Int,
    val id: String,
    val name: String,
    val selectedTags: List<Tag>,
    val tags: List<Tag>,
    val color: Color,
    val icon: Icon?,
    val difficulty: Challenge.Difficulty,
    val end: LocalDate,
    val motivation1: String,
    val motivation2: String,
    val motivation3: String,
    val allQuests: List<BaseQuest>,
    val trackedValues: List<Challenge.TrackedValue>,
    val quests: List<BaseQuest>,
    val selectedQuestIds: Set<String>,
    val note: String,
    val maxTagsReached: Boolean,
    val hasChangedColor: Boolean,
    val hasChangedIcon: Boolean
) : BaseViewState() {
    enum class StateType {
        INITIAL,
        LOADING,
        DATA_CHANGED,
        NEXT_PAGE,
        PREVIOUS_PAGE,
        CLOSE,
        COLOR_CHANGED,
        ICON_CHANGED,
        NOTE_CHANGED,
        DIFFICULTY_CHANGED,
        VALIDATION_ERROR_EMPTY_NAME,
        VALIDATION_ERROR_EMPTY_MOTIVATION,
        VALIDATION_NAME_SUCCESSFUL,
        VALIDATION_MOTIVATION_SUCCESSFUL,
        SUMMARY_DATA_LOADED,
        TAGS_CHANGED,
        END_DATE_CHANGED,
        MOTIVATIONS_CHANGED,
        TRACKED_VALUES_CHANGED,
        SHOW_TARGET_TRACKED_VALUE_PICKER,
        SHOW_AVERAGE_TRACKED_VALUE_PICKER
    }
}