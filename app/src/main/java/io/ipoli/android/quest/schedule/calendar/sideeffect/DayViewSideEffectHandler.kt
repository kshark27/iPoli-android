package io.ipoli.android.quest.schedule.calendar.sideeffect

import io.ipoli.android.Constants
import io.ipoli.android.common.*
import io.ipoli.android.common.redux.Action
import io.ipoli.android.event.usecase.FindEventsBetweenDatesUseCase
import io.ipoli.android.player.data.Player
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.Reminder
import io.ipoli.android.quest.schedule.ScheduleAction
import io.ipoli.android.quest.schedule.ScheduleViewState
import io.ipoli.android.quest.schedule.calendar.CalendarAction
import io.ipoli.android.quest.schedule.calendar.dayview.view.DayViewAction
import io.ipoli.android.quest.schedule.calendar.dayview.view.DayViewState
import io.ipoli.android.quest.show.usecase.CompleteTimeRangeUseCase
import io.ipoli.android.quest.usecase.CompleteQuestUseCase
import io.ipoli.android.quest.usecase.LoadScheduleForDateUseCase
import io.ipoli.android.quest.usecase.Result
import io.ipoli.android.quest.usecase.SaveQuestUseCase
import io.ipoli.android.repeatingquest.usecase.CreatePlaceholderQuestsForRepeatingQuestsUseCase
import kotlinx.coroutines.experimental.channels.Channel
import org.threeten.bp.LocalDate
import space.traversal.kapsule.required

object DayViewSideEffectHandler : AppSideEffectHandler() {
    private val saveQuestUseCase by required { saveQuestUseCase }
    private val questRepository by required { questRepository }
    private val findEventsBetweenDatesUseCase by required { findEventsBetweenDatesUseCase }
    private val removeQuestUseCase by required { removeQuestUseCase }
    private val undoRemoveQuestUseCase by required { undoRemoveQuestUseCase }
    private val loadScheduleForDateUseCase by required { loadScheduleForDateUseCase }
    private val createPlaceholderQuestsForRepeatingQuestsUseCase by required { createPlaceholderQuestsForRepeatingQuestsUseCase }
    private val completeTimeRangeUseCase by required { completeTimeRangeUseCase }
    private val completeQuestUseCase by required { completeQuestUseCase }
    private val undoCompletedQuestUseCase by required { undoCompletedQuestUseCase }

    private var scheduledQuestsChannel: Channel<List<Quest>>? = null

    override suspend fun doExecute(action: Action, state: AppState) {
        val a = (action as? NamespaceAction)?.source ?: action
        when (a) {

            is ScheduleAction.Load ->
                startListenForCalendarQuests(state.dataState.agendaDate)

            is ScheduleAction.GoToToday ->
                startListenForCalendarQuests(LocalDate.now())

            DayViewAction.AddQuest ->
                saveQuest(state, action)

            DayViewAction.EditQuest ->
                saveQuest(state, action)

            DayViewAction.EditUnscheduledQuest ->
                saveQuest(state, action)

            is DayViewAction.RemoveQuest ->
                removeQuestUseCase.execute(a.questId)

            is DayViewAction.UndoRemoveQuest ->
                undoRemoveQuestUseCase.execute(a.questId)

            is CalendarAction.ChangeDate ->
                startListenForCalendarQuests(a.date)

            is ScheduleAction.ToggleViewMode -> {
                val scheduleState = state.stateFor(ScheduleViewState::class.java)

                if (scheduleState.viewMode == Player.Preferences.AgendaScreen.CALENDAR) {
                    startListenForCalendarQuests(scheduleState.currentDate)
                }
            }

            is DayViewAction.CompleteQuest ->
                if (a.isStarted) {
                    completeTimeRangeUseCase.execute(
                        CompleteTimeRangeUseCase.Params(
                            a.questId
                        )
                    )
                } else {
                    completeQuestUseCase.execute(
                        CompleteQuestUseCase.Params.WithQuestId(
                            a.questId
                        )
                    )
                }

            is DayViewAction.UndoCompleteQuest ->
                undoCompletedQuestUseCase.execute(a.questId)
        }
    }

    private fun startListenForCalendarQuests(
        currentDate: LocalDate
    ) {

        val startDate = currentDate.minusDays(1)
        val endDate = currentDate.plusDays(1)

        listenForChanges(
            oldChannel = scheduledQuestsChannel,
            channelCreator = {
                scheduledQuestsChannel = questRepository
                    .listenForScheduledBetween(
                        startDate = startDate,
                        endDate = endDate
                    )
                scheduledQuestsChannel!!
            },
            onResult = { qs ->
                val placeholderQuests =
                    createPlaceholderQuestsForRepeatingQuestsUseCase.execute(
                        CreatePlaceholderQuestsForRepeatingQuestsUseCase.Params(
                            startDate = startDate,
                            endDate = endDate
                        )
                    )

                val events = findEventsBetweenDatesUseCase.execute(
                    FindEventsBetweenDatesUseCase.Params(
                        startDate = startDate,
                        endDate = endDate
                    )
                )

                val schedule =
                    loadScheduleForDateUseCase.execute(
                        LoadScheduleForDateUseCase.Params(
                            startDate = startDate,
                            endDate = endDate,
                            quests = qs + placeholderQuests,
                            events = events
                        )
                    )
                dispatch(DataLoadedAction.CalendarScheduleChanged(schedule))
            }
        )
    }

    private fun saveQuest(
        state: AppState,
        action: Action
    ) {
        val dayViewState: DayViewState = state.stateFor(
            "${(action as NamespaceAction).namespace}/${DayViewState::class.java.simpleName}"
        )

        val errors = Validator.validate(dayViewState).check<ValidationError> {
            "name" {
                given { name.isBlank() } addError ValidationError.EMPTY_NAME
            }
        }

        if (errors.isNotEmpty()) {
            dispatch(DayViewAction.SaveInvalidQuestName)
            return
        }

        dispatch(DayViewAction.QuestSaved)

        val scheduledDate = dayViewState.scheduledDate ?: dayViewState.currentDate
        val r = dayViewState.reminder
        val reminder = when {
            dayViewState.editId.isEmpty() && r == null ->
                Reminder.Relative(
                    "",
                    Constants.DEFAULT_RELATIVE_REMINDER_MINUTES_FROM_START
                )
            dayViewState.editId.isEmpty() && r != null ->
                Reminder.Relative(r.message, r.minutesFromStart)
            r != null -> Reminder.Relative(r.message, r.minutesFromStart)
            else -> null
        }

        val questParams = SaveQuestUseCase.Parameters(
            id = dayViewState.editId,
            name = dayViewState.name,
            subQuests = null,
            color = dayViewState.color!!,
            icon = dayViewState.icon,
            scheduledDate = scheduledDate,
            startTime = dayViewState.startTime,
            duration = dayViewState.duration!!,
            reminders = reminder?.let { listOf(it) },
            repeatingQuestId = dayViewState.repeatingQuestId,
            tags = dayViewState.tags,
            challengeId = dayViewState.challengeId
        )
        val result = saveQuestUseCase.execute(questParams)
        if (result is Result.Invalid) {
            dispatch(DayViewAction.SaveInvalidQuest(result))
        }
    }

    enum class ValidationError {
        EMPTY_NAME
    }

    override fun canHandle(action: Action): Boolean {
        val a = (action as? NamespaceAction)?.source ?: action
        return a is DayViewAction
            || a is CalendarAction.ChangeDate
            || a is ScheduleAction.ToggleViewMode
            || a is ScheduleAction.Load
            || a is ScheduleAction.GoToToday
    }
}