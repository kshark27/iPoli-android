package io.ipoli.android.planday.review

import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.datetime.Duration
import io.ipoli.android.common.datetime.Minute
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.quest.Quest
import org.threeten.bp.LocalDate

sealed class ReviewDayAction : Action {
    data class ApplyDamage(
        val allQuestIds: Set<String>,
        val habits: List<Habit>,
        val date: LocalDate
    ) :
        ReviewDayAction()

    data class RescheduleQuest(
        val questId: String,
        val date: LocalDate?,
        val time: Time?,
        val duration: Duration<Minute>?
    ) : ReviewDayAction()

    data class CompleteQuest(val questId: String) : ReviewDayAction()
    data class UndoCompleteQuest(val questId: String) : ReviewDayAction()
    data class CompleteHabit(val habitId: String) : ReviewDayAction()

    object Load : ReviewDayAction()
    object Done : ReviewDayAction()
}

object ReviewDayReducer : BaseViewStateReducer<ReviewDayViewState>() {

    override fun reduce(
        state: AppState,
        subState: ReviewDayViewState,
        action: Action
    ) =
        when (action) {

            is DataLoadedAction.ReviewDayQuestsChanged ->
                subState.copy(
                    type = ReviewDayViewState.StateType.QUESTS_CHANGED,
                    quests = action.quests,
                    allQuestIds = subState.allQuestIds + action.quests.map { it.id }.toSet()
                )


            is DataLoadedAction.ReviewDayHabitsChanged ->
                subState.copy(
                    type = ReviewDayViewState.StateType.HABITS_CHANGED,
                    habits = action.habits,
                    date = action.date
                )

            is ReviewDayAction.Done ->
                subState.copy(
                    type = ReviewDayViewState.StateType.DONE
                )

            else -> subState
        }

    override fun defaultState() =
        ReviewDayViewState(
            type = ReviewDayViewState.StateType.LOADING,
            quests = emptyList(),
            habits = emptyList(),
            date = null,
            allQuestIds = emptySet()
        )

    override val stateKey = key<ReviewDayViewState>()
}

data class ReviewDayViewState(
    val type: StateType,
    val quests: List<Quest>,
    val habits: List<Habit>,
    val date: LocalDate?,
    val allQuestIds: Set<String>
) : BaseViewState() {
    enum class StateType {
        LOADING,
        QUESTS_CHANGED,
        HABITS_CHANGED,
        DONE
    }
}