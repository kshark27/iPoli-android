package io.ipoli.android.quest.show.sideeffect

import io.ipoli.android.Constants
import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.redux.Action
import io.ipoli.android.event.usecase.FindEventsBetweenDatesUseCase
import io.ipoli.android.note.usecase.SaveQuestNoteUseCase
import io.ipoli.android.quest.CompletedQuestAction
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.schedule.agenda.view.AgendaAction
import io.ipoli.android.quest.schedule.summary.ScheduleSummaryAction
import io.ipoli.android.quest.schedule.today.TodayAction
import io.ipoli.android.quest.schedule.today.usecase.CreateTodayItemsUseCase
import io.ipoli.android.quest.show.QuestAction
import io.ipoli.android.quest.show.QuestReducer
import io.ipoli.android.quest.show.QuestViewState
import io.ipoli.android.quest.show.usecase.*
import io.ipoli.android.quest.subquest.usecase.*
import kotlinx.coroutines.experimental.channels.Channel
import org.threeten.bp.LocalDate
import space.traversal.kapsule.required

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 03/22/2018.
 */

data class TimerStartedAction(val otherTimerStopped: Boolean = false) : Action

object QuestSideEffectHandler : AppSideEffectHandler() {

    private val questRepository by required { questRepository }

    private val splitDurationForPomodoroTimerUseCase by required { splitDurationForPomodoroTimerUseCase }
    private val addTimerToQuestUseCase by required { addTimerToQuestUseCase }
    private val completeTimeRangeUseCase by required { completeTimeRangeUseCase }
    private val cancelTimerUseCase by required { cancelTimerUseCase }
    private val addPomodoroUseCase by required { addPomodoroUseCase }
    private val removePomodoroUseCase by required { removePomodoroUseCase }
    private val completeSubQuestUseCase by required { completeSubQuestUseCase }
    private val undoCompletedSubQuestUseCase by required { undoCompletedSubQuestUseCase }
    private val saveSubQuestNameUseCase by required { saveSubQuestNameUseCase }
    private val addSubQuestUseCase by required { addSubQuestUseCase }
    private val removeSubQuestUseCase by required { removeSubQuestUseCase }
    private val reorderSubQuestUseCase by required { reorderSubQuestUseCase }
    private val saveQuestNoteUseCase by required { saveQuestNoteUseCase }
    private val removeQuestUseCase by required { removeQuestUseCase }
    private val undoRemoveQuestUseCase by required { undoRemoveQuestUseCase }
    private val notificationManager by required { notificationManager }
    private val createTodayItemsUseCase by required { createTodayItemsUseCase }
    private val findEventsBetweenDatesUseCase by required { findEventsBetweenDatesUseCase }

    private var questChannel: Channel<Quest?>? = null
    private var completedQuestChannel: Channel<Quest?>? = null

    override suspend fun doExecute(action: Action, state: AppState) {
        when (action) {

            is QuestAction.Load ->
                listenForQuest(action)

            is CompletedQuestAction.Load ->
                listenForCompletedQuest(action)

            QuestAction.Start -> {
                removeTimerNotification()
                val timerState = questState(state)
                val q = timerState.quest!!

                if (q.hasTimer) {
                    completeTimeRangeUseCase.execute(
                        CompleteTimeRangeUseCase.Params(
                            questId = q.id
                        )
                    )
                    dispatch(TimerStartedAction())
                } else {
                    val result = addTimerToQuestUseCase.execute(
                        AddTimerToQuestUseCase.Params(
                            questId = q.id,
                            isPomodoro = timerState.timerType == QuestViewState.TimerType.POMODORO
                        )
                    )

                    dispatch(TimerStartedAction(result.otherTimerStopped))
                }
            }

            QuestAction.Stop -> {
                removeTimerNotification()
                cancelTimerUseCase.execute(CancelTimerUseCase.Params(questId(state)))
            }

            QuestAction.CompletePomodoro -> {
                removeTimerNotification()
                completeTimeRangeUseCase.execute(
                    CompleteTimeRangeUseCase.Params(
                        questId = questId(state)
                    )
                )
            }

            QuestAction.CompleteQuest -> {
                removeTimerNotification()
                completeTimeRangeUseCase.execute(CompleteTimeRangeUseCase.Params(questId(state)))
            }

            QuestAction.AddPomodoro ->
                if (questState(state).pomodoroCount < Constants.MAX_POMODORO_COUNT) {
                    addPomodoroUseCase.execute(AddPomodoroUseCase.Params(questId(state)))
                }

            QuestAction.RemovePomodoro ->
                if (questState(state).pomodoroCount > 1)
                    removePomodoroUseCase.execute(
                        RemovePomodoroUseCase.Params(
                            questId = questId(state)
                        )
                    )

            is QuestAction.CompleteSubQuest ->
                completeSubQuestUseCase.execute(
                    CompleteSubQuestUseCase.Params(
                        subQuestIndex = action.position,
                        questId = questId(state)
                    )
                )

            is QuestAction.UndoCompletedSubQuest ->
                undoCompletedSubQuestUseCase.execute(
                    UndoCompletedSubQuestUseCase.Params(
                        subQuestIndex = action.position,
                        questId = questId(state)
                    )
                )

            is QuestAction.SaveSubQuestName ->
                saveSubQuestNameUseCase.execute(
                    SaveSubQuestNameUseCase.Params(
                        newName = action.name,
                        questId = questId(state),
                        index = action.position
                    )
                )

            is QuestAction.AddSubQuest ->
                addSubQuestUseCase.execute(
                    AddSubQuestUseCase.Params(
                        name = action.name,
                        questId = questId(state)
                    )
                )

            is QuestAction.RemoveSubQuest ->
                removeSubQuestUseCase.execute(
                    RemoveSubQuestUseCase.Params(
                        subQuestIndex = action.position,
                        questId = questId(state)
                    )
                )

            is QuestAction.ReorderSubQuest ->
                reorderSubQuestUseCase.execute(
                    ReorderSubQuestUseCase.Params(
                        oldPosition = action.oldPosition,
                        newPosition = action.newPosition,
                        questId = questId(state)
                    )
                )

            is QuestAction.SaveNote ->
                saveQuestNoteUseCase.execute(
                    SaveQuestNoteUseCase.Params(
                        questId = questId(state),
                        note = action.note
                    )
                )

            is QuestAction.Remove ->
                removeQuestUseCase.execute(action.questId)

            is QuestAction.UndoRemove ->
                undoRemoveQuestUseCase.execute(action.questId)

            is TodayAction.Load ->
                state.dataState.todayQuests?.let {

                    val events = findEventsBetweenDatesUseCase.execute(
                        FindEventsBetweenDatesUseCase.Params(
                            startDate = action.today,
                            endDate = action.today
                        )
                    )

                    dispatch(
                        DataLoadedAction.TodayQuestItemsChanged(
                            questItems = createTodayItemsUseCase.execute(
                                CreateTodayItemsUseCase.Params(quests = it, events = events)
                            )
                        )
                    )
                }

            is DataLoadedAction.TodayQuestsChanged -> {

                val events = findEventsBetweenDatesUseCase.execute(
                    FindEventsBetweenDatesUseCase.Params(
                        startDate = LocalDate.now(),
                        endDate = LocalDate.now()
                    )
                )

                val quests = action.quests
                dispatch(
                    DataLoadedAction.TodayQuestItemsChanged(
                        questItems = createTodayItemsUseCase.execute(
                            CreateTodayItemsUseCase.Params(quests = quests, events = events)
                        )
                    )
                )
            }

        }
    }

    private fun removeTimerNotification() {
        notificationManager.removeTimerNotification()
    }

    private fun listenForCompletedQuest(action: CompletedQuestAction.Load) {
        listenForChanges(
            oldChannel = completedQuestChannel,
            channelCreator = {
                completedQuestChannel = questRepository.listenById(action.questId)
                completedQuestChannel!!
            },
            onResult = { q ->

                val quest = q!!

                val splitResult = splitDurationForPomodoroTimerUseCase.execute(
                    SplitDurationForPomodoroTimerUseCase.Params(quest)
                )
                val totalPomodoros =
                    if (splitResult == SplitDurationForPomodoroTimerUseCase.Result.DurationNotSplit) {
                        quest.timeRanges.size / 2
                    } else {
                        (splitResult as SplitDurationForPomodoroTimerUseCase.Result.DurationSplit).timeRanges.size / 2
                    }
                dispatch(DataLoadedAction.QuestChanged(quest.copy(totalPomodoros = totalPomodoros)))
            }
        )
    }

    private fun listenForQuest(action: QuestAction.Load) {
        listenForChanges(
            oldChannel = questChannel,
            channelCreator = {
                questChannel = questRepository.listenById(action.questId)
                questChannel!!
            },
            onResult = { q ->
                val quest = if (isPomodoroQuest(q!!)) {
                    val timeRanges = q.timeRanges

                    val result = splitDurationForPomodoroTimerUseCase.execute(
                        SplitDurationForPomodoroTimerUseCase.Params(q)
                    )
                    val timeRangesToComplete =
                        if (result == SplitDurationForPomodoroTimerUseCase.Result.DurationNotSplit) {
                            timeRanges
                        } else {
                            (result as SplitDurationForPomodoroTimerUseCase.Result.DurationSplit).timeRanges
                        }
                    q.copy(
                        timeRangesToComplete = timeRangesToComplete
                    )
                } else q
                dispatch(DataLoadedAction.QuestChanged(quest))
            }
        )
    }

    private fun isPomodoroQuest(q: Quest) =
        q.hasPomodoroTimer || q.duration >= QuestReducer.MIN_POMODORO_TIMER_DURATION

    private fun questId(state: AppState) =
        questState(state).quest!!.id

    private fun questState(state: AppState) =
        state.stateFor(QuestViewState::class.java)

    override fun canHandle(action: Action) =
        action is QuestAction || action is CompletedQuestAction || action is ScheduleSummaryAction || action is TodayAction || action is DataLoadedAction || action is AgendaAction
}