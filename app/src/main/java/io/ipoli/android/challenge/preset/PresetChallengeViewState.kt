package io.ipoli.android.challenge.preset

import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.challenge.preset.PresetChallengeViewState.StateType.*
import io.ipoli.android.challenge.usecase.CreateChallengeFromPresetUseCase
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.datetime.*
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.tag.Tag

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 9/29/18.
 */
sealed class PresetChallengeAction : Action {
    data class Load(val challenge: PresetChallenge) : PresetChallengeAction() {
        override fun toMap() = mapOf(
            "id" to challenge.id,
            "name" to challenge.name,
            "isPaid" to (challenge.gemPrice > 0),
            "gemPrice" to challenge.gemPrice
        )
    }

    data class Accept(
        val challenge: PresetChallenge,
        val tags: List<Tag>,
        val startTime: Time?,
        val schedule: PresetChallenge.Schedule,
        val physicalCharacteristics: CreateChallengeFromPresetUseCase.PhysicalCharacteristics?
    ) : PresetChallengeAction() {
        override fun toMap() = mapOf(
            "id" to challenge.id,
            "name" to challenge.name,
            "tags" to tags.joinToString(",") { it.name },
            "startTime" to startTime,
            "isPaid" to (challenge.gemPrice > 0),
            "gemPrice" to challenge.gemPrice
        )
    }

    data class ChangeStartTime(val time: Time?) : PresetChallengeAction()
    data class AddTag(val tag: Tag) : PresetChallengeAction()
    data class RemoveTag(val tag: Tag) : PresetChallengeAction()
    data class ToggleSelectedHabit(val habitName: String, val isSelected: Boolean) :
        PresetChallengeAction()

    object Validate : PresetChallengeAction()
    object Unlocked : PresetChallengeAction()
    object ChallengeTooExpensive : PresetChallengeAction()
    object ChallengeAlreadyAccepted : PresetChallengeAction()

    data class Unlock(val challenge: PresetChallenge) : PresetChallengeAction()
    data class PhysicalCharacteristicsPicked(
        val physicalCharacteristics: CreateChallengeFromPresetUseCase.PhysicalCharacteristics?
    ) : PresetChallengeAction()

    data class Accepted(val challengeId: String) : PresetChallengeAction()
}

object PresetChallengeReducer : BaseViewStateReducer<PresetChallengeViewState>() {

    override val stateKey = key<PresetChallengeViewState>()

    override fun reduce(
        state: AppState,
        subState: PresetChallengeViewState,
        action: Action
    ) = when (action) {
        is PresetChallengeAction.Load -> {
            val c = action.challenge
            subState.copy(
                type = DATA_CHANGED,
                name = c.name,
                duration = c.duration,
                busynessPerWeek = c.busynessPerWeek.asMinutes,
                difficulty = c.difficulty,
                level = c.level,
                description = c.description,
                requirements = c.requirements,
                expectedResults = c.expectedResults,
                showStartTime = c.config.defaultStartTime != null,
                startTime = c.config.defaultStartTime,
                schedule = c.schedule,
                challenge = c,
                showAddTag = true,
                challengeTags = emptyList(),
                tags = state.dataState.tags,
                gemPrice = c.gemPrice,
                isUnlocked = c.gemPrice == 0 || state.dataState.player!!.hasChallenge(c),
                author = c.author,
                participantCount = c.participantCount
            )
        }

        is DataLoadedAction.PlayerChanged -> {
            if (subState.challenge != null) {
                val challenge = subState.challenge
                subState.copy(
                    type = DATA_CHANGED,
                    isUnlocked = challenge.gemPrice == 0 || action.player.hasChallenge(challenge)
                )
            } else {
                subState.copy(
                    type = LOADING
                )
            }
        }

        is PresetChallengeAction.ChangeStartTime ->
            subState.copy(
                type = START_TIME_CHANGED,
                startTime = action.time
            )

        is DataLoadedAction.TagsChanged ->
            subState.copy(
                type = TAGS_CHANGED,
                tags = action.tags
            )

        is PresetChallengeAction.AddTag -> {
            val challengeTags = subState.challengeTags!! + action.tag
            subState.copy(
                type = TAGS_CHANGED,
                showAddTag = challengeTags.size < 3,
                challengeTags = challengeTags
            )
        }

        is PresetChallengeAction.RemoveTag -> {
            val challengeTags = subState.challengeTags!! - action.tag
            subState.copy(
                type = TAGS_CHANGED,
                showAddTag = challengeTags.size < 3,
                challengeTags = challengeTags
            )
        }

        is PresetChallengeAction.Validate -> {
            val newType = if (subState.challengeTags!!.isEmpty())
                EMPTY_TAGS
            else if (subState.schedule!!.quests.isEmpty() && subState.schedule.habits.none { it.isSelected })
                EMPTY_SCHEDULE
            else if (subState.challenge!!.config.nutritionMacros != null)
                SHOW_CHARACTERISTICS_PICKER
            else
                CHALLENGE_VALID
            subState.copy(
                type = newType
            )
        }

        is PresetChallengeAction.ToggleSelectedHabit -> {
            val schedule = subState.schedule!!
            subState.copy(
                type = HABITS_CHANGED,
                schedule = schedule.copy(
                    habits = schedule.habits.map {
                        if (it.name == action.habitName) {
                            it.copy(
                                isSelected = action.isSelected
                            )
                        } else it
                    }
                )
            )
        }

        is PresetChallengeAction.Unlocked ->
            subState.copy(
                type = UNLOCKED
            )

        is PresetChallengeAction.ChallengeTooExpensive ->
            subState.copy(
                type = TOO_EXPENSIVE
            )

        is PresetChallengeAction.PhysicalCharacteristicsPicked ->
            subState.copy(
                type = if (action.physicalCharacteristics == null) CHARACTERISTICS_PICKER_CANCELED else CHALLENGE_VALID,
                physicalCharacteristics = action.physicalCharacteristics
            )

        is PresetChallengeAction.ChallengeAlreadyAccepted ->
            subState.copy(
                type = ALREADY_ACCEPTED
            )

        is PresetChallengeAction.Accept ->
            subState.copy(
                type = LOADING
            )

        is PresetChallengeAction.Accepted ->
            subState.copy(
                type = ACCEPTED,
                challengeId = action.challengeId
            )

        else -> subState
    }

    override fun defaultState() = PresetChallengeViewState(
        type = LOADING,
        name = "",
        duration = 0.days,
        busynessPerWeek = 0.minutes,
        difficulty = Challenge.Difficulty.EASY,
        level = null,
        description = "",
        requirements = emptyList(),
        expectedResults = emptyList(),
        showStartTime = null,
        startTime = null,
        schedule = null,
        challenge = null,
        showAddTag = null,
        challengeTags = emptyList(),
        tags = null,
        gemPrice = null,
        isUnlocked = false,
        physicalCharacteristics = null,
        challengeId = null,
        author = null,
        participantCount = 0
    )

}

data class PresetChallengeViewState(
    val type: StateType,
    val name: String,
    val duration: Duration<Day>,
    val busynessPerWeek: Duration<Minute>,
    val difficulty: Challenge.Difficulty,
    val level: Int?,
    val description: String,
    val requirements: List<String>,
    val expectedResults: List<String>,
    val showStartTime: Boolean?,
    val startTime: Time?,
    val schedule: PresetChallenge.Schedule?,
    val challenge: PresetChallenge?,
    val showAddTag: Boolean?,
    val challengeTags: List<Tag>?,
    val tags: List<Tag>?,
    val gemPrice: Int?,
    val isUnlocked: Boolean?,
    val physicalCharacteristics: CreateChallengeFromPresetUseCase.PhysicalCharacteristics?,
    val challengeId: String?,
    val author: PresetChallenge.Author?,
    val participantCount: Int
) : BaseViewState(

) {
    enum class StateType {
        LOADING,
        DATA_CHANGED,
        START_TIME_CHANGED,
        TAGS_CHANGED,
        CHALLENGE_VALID,
        EMPTY_TAGS,
        EMPTY_SCHEDULE,
        HABITS_CHANGED,
        UNLOCKED,
        TOO_EXPENSIVE,
        SHOW_CHARACTERISTICS_PICKER,
        CHARACTERISTICS_PICKER_CANCELED,
        ALREADY_ACCEPTED,
        ACCEPTED
    }
}