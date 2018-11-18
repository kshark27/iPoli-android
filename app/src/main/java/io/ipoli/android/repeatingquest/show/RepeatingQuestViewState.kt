package io.ipoli.android.repeatingquest.show

import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.datetime.Duration
import io.ipoli.android.common.datetime.Minute
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.datetime.minutes

import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.RepeatingQuest
import io.ipoli.android.repeatingquest.entity.PeriodProgress
import io.ipoli.android.repeatingquest.entity.RepeatPattern
import io.ipoli.android.repeatingquest.show.RepeatingQuestViewState.StateType.*
import io.ipoli.android.repeatingquest.usecase.CreateRepeatingQuestHistoryUseCase
import io.ipoli.android.tag.Tag
import org.threeten.bp.LocalDate

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 02/21/2018.
 */

sealed class RepeatingQuestAction : Action {
    data class Load(val repeatingQuestId: String) : RepeatingQuestAction() {
        override fun toMap() = mapOf("repeatingQuestId" to repeatingQuestId)
    }

    data class Remove(val repeatingQuestId: String) : RepeatingQuestAction() {
        override fun toMap() = mapOf("repeatingQuestId" to repeatingQuestId)
    }

    data class AddQuest(val repeatingQuestId: String, val date: LocalDate?, val time: Time?) :
        RepeatingQuestAction()
}

data class RepeatingQuestViewState(
    val type: StateType, val id: String,
    val name: String,
    val tags: List<Tag>,
    val subQuestNames: List<String>,
    val color: Color,
    val nextScheduledDate: LocalDate?,
    val lastCompletedDate: LocalDate?,
    val totalDuration: Duration<Minute>,
    val currentStreak: Int,
    val repeat: RepeatType,
    val progress: List<ProgressModel>,
    val startTime: Time?,
    val endTime: Time?,
    val duration: Int,
    val isCompleted: Boolean,
    val note: String?,
    val history: CreateRepeatingQuestHistoryUseCase.History?
) : BaseViewState() {

    enum class StateType {
        LOADING, HISTORY_CHANGED, REPEATING_QUEST_CHANGED
    }

    enum class ProgressModel {
        COMPLETE, INCOMPLETE
    }

    sealed class RepeatType {
        object Daily : RepeatType()
        data class Weekly(val frequency: Int) : RepeatType()
        data class Monthly(val frequency: Int) : RepeatType()
        object Yearly : RepeatType()
        object Manual : RepeatType()
    }
}

object RepeatingQuestReducer : BaseViewStateReducer<RepeatingQuestViewState>() {

    override val stateKey = key<RepeatingQuestViewState>()

    override fun reduce(state: AppState, subState: RepeatingQuestViewState, action: Action) =
        when (action) {
            is RepeatingQuestAction.Load -> {

                val dataState = state.dataState
                val rq =
                    dataState.repeatingQuests!!.firstOrNull { it.id == action.repeatingQuestId }

                rq?.let {
                    createChangedState(it, subState)
                } ?: subState.copy(type = LOADING)
            }

            is DataLoadedAction.RepeatingQuestsChanged -> {

                val rq = action.repeatingQuests.firstOrNull { it.id == subState.id }
                rq?.let {
                    createChangedState(it, subState)
                } ?: subState
            }

            is DataLoadedAction.RepeatingQuestHistoryChanged -> {
                subState.copy(
                    type = HISTORY_CHANGED,
                    history = action.history
                )
            }

            else -> subState
        }

    private fun createChangedState(
        rq: RepeatingQuest,
        state: RepeatingQuestViewState
    ) =
        state.copy(
            type = REPEATING_QUEST_CHANGED,
            id = rq.id,
            name = rq.name,
            tags = rq.tags,
            subQuestNames = rq.subQuests.map { it.name },
            color = rq.color,
            nextScheduledDate = rq.nextDate,
            lastCompletedDate = rq.lastCompletedDate,
            totalDuration = 180.minutes,
            currentStreak = 10,
            repeat = repeatTypeFor(rq.repeatPattern),
            progress = progressFor(rq.periodProgress!!),
            startTime = rq.startTime,
            endTime = rq.endTime,
            duration = rq.duration,
            isCompleted = rq.isCompleted,
            note = rq.note
        )

    private fun progressFor(progress: PeriodProgress): List<RepeatingQuestViewState.ProgressModel> {
        val complete = (0 until progress.completedCount).map {
            RepeatingQuestViewState.ProgressModel.COMPLETE
        }
        val incomplete = (progress.completedCount until progress.scheduledCount).map {
            RepeatingQuestViewState.ProgressModel.INCOMPLETE
        }
        return complete + incomplete
    }

    private fun repeatTypeFor(repeatPattern: RepeatPattern) =
        when (repeatPattern) {
            is RepeatPattern.Daily -> RepeatingQuestViewState.RepeatType.Daily
            is RepeatPattern.Weekly, is RepeatPattern.Flexible.Weekly -> RepeatingQuestViewState.RepeatType.Weekly(
                repeatPattern.countForCurrentPeriod
            )

            is RepeatPattern.Monthly, is RepeatPattern.Flexible.Monthly -> RepeatingQuestViewState.RepeatType.Monthly(
                repeatPattern.countForCurrentPeriod
            )

            is RepeatPattern.Yearly ->
                RepeatingQuestViewState.RepeatType.Yearly

            is RepeatPattern.Manual -> RepeatingQuestViewState.RepeatType.Manual
        }

    override fun defaultState() =
        RepeatingQuestViewState(
            type = LOADING,
            id = "",
            name = "",
            tags = emptyList(),
            subQuestNames = emptyList(),
            color = Color.PINK,
            nextScheduledDate = null,
            lastCompletedDate = null,
            totalDuration = 0.minutes,
            currentStreak = -1,
            repeat = RepeatingQuestViewState.RepeatType.Daily,
            progress = emptyList(),
            startTime = null,
            endTime = null,
            duration = -1,
            isCompleted = false,
            note = null,
            history = null
        )
}