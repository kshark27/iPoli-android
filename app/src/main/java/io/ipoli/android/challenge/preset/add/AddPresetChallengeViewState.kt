package io.ipoli.android.challenge.preset.add

import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.challenge.preset.PresetChallenge
import io.ipoli.android.challenge.preset.add.AddPresetChallengeViewState.StateType.*
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.datetime.Day
import io.ipoli.android.common.datetime.Duration
import io.ipoli.android.common.datetime.days
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 11/1/18.
 */
sealed class AddPresetChallengeAction : Action {
    object LoadItems : AddPresetChallengeAction()
    object NextPage : AddPresetChallengeAction()
    object Back : AddPresetChallengeAction()
    data class Save(
        val name: String,
        val shortDescription: String,
        val description: String,
        val icon: Icon,
        val color: Color,
        val duration: Duration<Day>,
        val difficulty: Challenge.Difficulty,
        val category: PresetChallenge.Category,
        val expectedResults: List<String>,
        val requirements: List<String>,
        val quests: List<Pair<PresetChallenge.Quest, Boolean>>,
        val habits: List<PresetChallenge.Habit>
    ) : AddPresetChallengeAction() {
        override fun toMap() = mapOf(
            "name" to name,
            "category" to category.name,
            "questCount" to quests.size,
            "habitCount" to habits.size,
            "duration" to duration.intValue
        )
    }

    data class Load(
        val category: PresetChallenge.Category?,
        val color: Color?
    ) : AddPresetChallengeAction()

    data class AddQuest(val quest: PresetChallenge.Quest, val isRepeating: Boolean) :
        AddPresetChallengeAction()

    data class UpdateQuest(
        val position: Int,
        val quest: PresetChallenge.Quest,
        val repeating: Boolean
    ) : AddPresetChallengeAction()

    data class AddHabit(val habit: PresetChallenge.Habit) : AddPresetChallengeAction()
    data class UpdateHabit(val position: Int, val habit: PresetChallenge.Habit) :
        AddPresetChallengeAction()

    data class ChangeColor(val color: Color) : AddPresetChallengeAction()
    data class ChangeIcon(val icon: Icon) : AddPresetChallengeAction()
    data class ChangeCategory(val category: PresetChallenge.Category) : AddPresetChallengeAction()
    data class ChangeDifficulty(val difficulty: Challenge.Difficulty) : AddPresetChallengeAction()
    data class ChangeDuration(val duration: Int) : AddPresetChallengeAction()
    data class ValidateInfo(
        val name: String,
        val shortDescription: String,
        val expectedResults: List<String>,
        val requirements: List<String>
    ) : AddPresetChallengeAction()

    data class ChangeDescription(val description: String) : AddPresetChallengeAction()
    data class RemoveQuest(val position: Int) : AddPresetChallengeAction()
    data class RemoveHabit(val position: Int) : AddPresetChallengeAction()

    object Saved : AddPresetChallengeAction()
    object SaveError : AddPresetChallengeAction()
    object LoadInfo : AddPresetChallengeAction()
}

object AddPresetChallengeReducer : BaseViewStateReducer<AddPresetChallengeViewState>() {
    override val stateKey = key<AddPresetChallengeViewState>()

    override fun reduce(
        state: AppState,
        subState: AddPresetChallengeViewState,
        action: Action
    ) =
        when (action) {

            is AddPresetChallengeAction.Load -> {
                val newState = subState.copy(
                    category = action.category ?: subState.category,
                    color = action.color ?: subState.color
                )
                if (subState.page == AddPresetChallengeViewState.Page.NONE)
                    newState.copy(
                        type = INITIAL,
                        page = AddPresetChallengeViewState.Page.INFO
                    )
                else
                    newState.copy(
                        type = DATA_LOADED
                    )
            }

            is AddPresetChallengeAction.LoadInfo ->
                subState.copy(
                    type = DATA_LOADED
                )

            is AddPresetChallengeAction.NextPage ->
                when {
                    subState.page == AddPresetChallengeViewState.Page.INFO ->
                        subState.copy(
                            type = VALIDATE_TEXTS
                        )
                    subState.quests.isEmpty() && subState.habits.isEmpty() ->
                        subState.copy(
                            type = ERROR_ITEMS
                        )
                    else ->
                        subState.copy(
                            type = SAVING
                        )
                }

            is AddPresetChallengeAction.Back -> {
                val currentPage = subState.page
                if (currentPage == AddPresetChallengeViewState.Page.INFO) {
                    subState.copy(
                        type = EXIT
                    )
                } else {
                    subState.copy(
                        type = PAGE_CHANGED,
                        page = AddPresetChallengeViewState.Page.INFO
                    )
                }
            }

            is AddPresetChallengeAction.LoadItems ->
                subState.copy(
                    type = DATA_LOADED
                )

            is AddPresetChallengeAction.AddQuest -> {
                subState.copy(
                    type = QUESTS_CHANGED,
                    quests = sorted(subState.quests + Pair(action.quest, action.isRepeating))
                )
            }

            is AddPresetChallengeAction.UpdateQuest ->
                subState.copy(
                    type = QUESTS_CHANGED,
                    quests = sorted(
                        subState.quests.mapIndexed { index, pair ->
                            if (index == action.position)
                                Pair(action.quest, action.repeating)
                            else pair
                        }
                    ))

            is AddPresetChallengeAction.RemoveQuest ->
                subState.copy(
                    type = QUESTS_CHANGED,
                    quests = sorted(
                        subState.quests.mapIndexedNotNull { index, pair ->
                            if (index == action.position) {
                                null
                            } else pair
                        }
                    )
                )

            is AddPresetChallengeAction.AddHabit -> {
                subState.copy(
                    type = HABITS_CHANGED,
                    habits = subState.habits + action.habit
                )
            }

            is AddPresetChallengeAction.UpdateHabit ->
                subState.copy(
                    type = HABITS_CHANGED,
                    habits = subState.habits.mapIndexed { index, habit ->
                        if (index == action.position)
                            action.habit
                        else habit
                    }
                )

            is AddPresetChallengeAction.RemoveHabit ->
                subState.copy(
                    type = HABITS_CHANGED,
                    habits = subState.habits.mapIndexedNotNull { index, h ->
                        if (index == action.position) {
                            null
                        } else h
                    }
                )

            is AddPresetChallengeAction.ChangeColor ->
                subState.copy(
                    type = COLOR_CHANGED,
                    color = action.color
                )

            is AddPresetChallengeAction.ChangeIcon ->
                subState.copy(
                    type = ICON_CHANGED,
                    icon = action.icon
                )

            is AddPresetChallengeAction.ChangeCategory ->
                subState.copy(
                    type = CATEGORY_CHANGED,
                    category = action.category
                )

            is AddPresetChallengeAction.ChangeDifficulty ->
                subState.copy(
                    type = DIFFICULTY_CHANGED,
                    difficulty = action.difficulty
                )

            is AddPresetChallengeAction.ChangeDuration ->
                subState.copy(
                    type = DURATION_CHANGED,
                    duration = action.duration.days
                )

            is AddPresetChallengeAction.ChangeDescription ->
                subState.copy(
                    type = DESCRIPTION_CHANGED,
                    description = action.description
                )

            is AddPresetChallengeAction.ValidateInfo -> {

                val errors = mutableSetOf<AddPresetChallengeViewState.InfoError>()

                if (action.name.isBlank()) {
                    errors.add(AddPresetChallengeViewState.InfoError.EMPTY_NAME)
                }

                if (action.shortDescription.isBlank()) {
                    errors.add(AddPresetChallengeViewState.InfoError.EMPTY_SHORT_DESCRIPTION)
                }

                if (subState.description.isBlank()) {
                    errors.add(AddPresetChallengeViewState.InfoError.EMPTY_DESCRIPTION)
                }

                if (action.expectedResults.isEmpty()) {
                    errors.add(AddPresetChallengeViewState.InfoError.EMPTY_EXPECTED_RESULTS)
                }

                if (errors.isEmpty())
                    subState.copy(
                        type = PAGE_CHANGED,
                        name = action.name,
                        shortDescription = action.shortDescription,
                        expectedResults = action.expectedResults,
                        requirements = action.requirements,
                        page = AddPresetChallengeViewState.Page.ITEMS
                    )
                else
                    subState.copy(
                        type = ERROR_INFO,
                        infoErrors = errors
                    )
            }

            is AddPresetChallengeAction.Saved ->
                subState.copy(
                    type = DONE
                )

            is AddPresetChallengeAction.SaveError ->
                subState.copy(
                    type = SAVE_ERROR
                )

            else -> subState
        }

    private fun sorted(quests: List<Pair<PresetChallenge.Quest, Boolean>>) =
        quests.sortedWith(
            compareByDescending<Pair<PresetChallenge.Quest, Boolean>> { it.second }
                .thenBy { it.first.day }
        )

    override fun defaultState() = AddPresetChallengeViewState(
        type = LOADING,
        name = "",
        shortDescription = "",
        color = Color.ORANGE,
        icon = Icon.FLOWER,
        category = PresetChallenge.Category.HEALTH,
        duration = 7.days,
        difficulty = Challenge.Difficulty.NORMAL,
        expectedResults = emptyList(),
        requirements = emptyList(),
        description = "",
        quests = emptyList(),
        habits = emptyList(),
        page = AddPresetChallengeViewState.Page.NONE,
        infoErrors = emptySet()
    )
}


data class AddPresetChallengeViewState(
    val type: StateType,
    val name: String,
    val shortDescription: String,
    val color: Color,
    val icon: Icon,
    val category: PresetChallenge.Category,
    val duration: Duration<Day>,
    val difficulty: Challenge.Difficulty,
    val expectedResults: List<String>,
    val requirements: List<String>,
    val description: String,
    val quests: List<Pair<PresetChallenge.Quest, Boolean>>,
    val habits: List<PresetChallenge.Habit>,
    val page: Page,
    val infoErrors: Set<InfoError>
) : BaseViewState() {
    enum class StateType {
        LOADING,
        INITIAL,
        DATA_LOADED,
        QUESTS_CHANGED,
        HABITS_CHANGED,
        COLOR_CHANGED,
        ICON_CHANGED,
        CATEGORY_CHANGED,
        DIFFICULTY_CHANGED,
        DURATION_CHANGED,
        VALIDATE_TEXTS,
        PAGE_CHANGED,
        ERROR_INFO,
        ERROR_ITEMS,
        DESCRIPTION_CHANGED,
        SAVE_ERROR,
        EXIT,
        SAVING,
        DONE
    }

    enum class Page {
        NONE, INFO, ITEMS
    }

    enum class InfoError {
        EMPTY_NAME, EMPTY_SHORT_DESCRIPTION, EMPTY_DESCRIPTION, EMPTY_EXPECTED_RESULTS
    }
}