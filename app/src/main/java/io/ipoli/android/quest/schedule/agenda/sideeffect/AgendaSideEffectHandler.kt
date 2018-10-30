package io.ipoli.android.quest.schedule.agenda.sideeffect

import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.LoadDataAction
import io.ipoli.android.common.datetime.DateUtils
import io.ipoli.android.common.datetime.daysBetween
import io.ipoli.android.common.redux.Action
import io.ipoli.android.event.usecase.FindEventsBetweenDatesUseCase
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.schedule.ScheduleAction
import io.ipoli.android.quest.schedule.agenda.usecase.CreateAgendaItemsUseCase
import io.ipoli.android.quest.schedule.agenda.usecase.CreateAgendaPreviewItemsUseCase
import io.ipoli.android.quest.schedule.agenda.usecase.FindAgendaDatesUseCase
import io.ipoli.android.quest.schedule.agenda.view.AgendaAction
import io.ipoli.android.quest.schedule.agenda.view.AgendaReducer
import io.ipoli.android.quest.schedule.agenda.view.AgendaViewState
import io.ipoli.android.repeatingquest.usecase.CreatePlaceholderQuestsForRepeatingQuestsUseCase
import kotlinx.coroutines.experimental.channels.Channel
import org.threeten.bp.LocalDate
import org.threeten.bp.temporal.TemporalAdjusters
import space.traversal.kapsule.required

object AgendaSideEffectHandler : AppSideEffectHandler() {

    private val findAgendaDatesUseCase by required { findAgendaDatesUseCase }
    private val createAgendaItemsUseCase by required { createAgendaItemsUseCase }
    private val questRepository by required { questRepository }
    private val findEventsBetweenDatesUseCase by required { findEventsBetweenDatesUseCase }
    private val createPlaceholderQuestsForRepeatingQuestsUseCase by required { createPlaceholderQuestsForRepeatingQuestsUseCase }
    private val createAgendaPreviewItemsUseCase by required { createAgendaPreviewItemsUseCase }

    private var agendaItemsChannel: Channel<List<Quest>>? = null
    private var agendaPreviewItemsChannel: Channel<List<Quest>>? = null

    override suspend fun doExecute(action: Action, state: AppState) {

        when (action) {

            is AgendaAction.Load -> {
                listenForAgendaItemsAtDate(state.dataState.agendaDate)
                listenForPreviewItems(state.dataState.agendaDate)
            }

            is AgendaAction.LoadBefore -> {
                val agendaItems = state.stateFor(AgendaViewState::class.java).agendaItems
                val position = action.itemPosition
                val agendaItem = agendaItems[position]
                val agendaDate = agendaItem.startDate()
                val result = findAgendaDatesUseCase.execute(
                    FindAgendaDatesUseCase.Params.Before(
                        agendaDate,
                        AgendaReducer.ITEMS_BEFORE_COUNT
                    )
                )
                var startDate = agendaDate.minusMonths(3)
                (result as FindAgendaDatesUseCase.Result.Before).date?.let {
                    startDate = it
                }
                val endDate =
                    agendaItems[position + AgendaReducer.ITEMS_AFTER_COUNT - 1].startDate()
                listenForAgendaItems(
                    startDate = startDate,
                    endDate = endDate,
                    currentDate = agendaDate,
                    changeCurrentAgendaItem = false
                )
            }
            is AgendaAction.LoadAfter -> {
                val agendaItems = state.stateFor(AgendaViewState::class.java).agendaItems
                val position = action.itemPosition
                val agendaItem = agendaItems[position]
                val agendaDate = agendaItem.startDate()
                val result = findAgendaDatesUseCase.execute(
                    FindAgendaDatesUseCase.Params.After(
                        agendaDate,
                        AgendaReducer.ITEMS_AFTER_COUNT
                    )
                )
                val startDate = agendaItems[position - AgendaReducer.ITEMS_BEFORE_COUNT].startDate()
                var endDate = agendaDate.plusMonths(3)
                (result as FindAgendaDatesUseCase.Result.After).date?.let {
                    endDate = it
                }
                listenForAgendaItems(
                    startDate = startDate,
                    endDate = endDate,
                    currentDate = agendaDate,
                    changeCurrentAgendaItem = false
                )
            }

            is AgendaAction.FirstVisibleItemChanged -> {
                if (!state.hasState(AgendaViewState::class.java)) {
                    return
                }
                val agendaItems = state.stateFor(AgendaViewState::class.java).agendaItems
                val date = agendaItems[action.itemPosition].startDate()
                dispatch(AgendaAction.AutoChangeDate(date))
            }

            is AgendaAction.ChangePreviewMonth ->
                listenForPreviewItems(
                    LocalDate.of(
                        action.yearMonth.year,
                        action.yearMonth.monthValue,
                        1
                    )
                )

            is AgendaAction.ChangePreviewDate ->
                listenForAgendaItemsAtDate(action.date)

            is ScheduleAction.GoToToday -> {
                listenForAgendaItemsAtDate(LocalDate.now())
                listenForPreviewItems(LocalDate.now())
            }

        }
    }

    private fun listenForAgendaItemsAtDate(date: LocalDate) {
        val pair = findAllAgendaDates(date)
        listenForAgendaItems(
            startDate = pair.first,
            endDate = pair.second,
            currentDate = date,
            changeCurrentAgendaItem = true
        )
    }

    private fun listenForPreviewItems(currentDate: LocalDate) {
        val startDate = currentDate.with(TemporalAdjusters.firstDayOfMonth())
            .with(TemporalAdjusters.previousOrSame(DateUtils.firstDayOfWeek))

        val lastWeekEndDate = currentDate.with(TemporalAdjusters.lastDayOfMonth())
            .with(TemporalAdjusters.nextOrSame(DateUtils.lastDayOfWeek))

        val daysBetween = startDate.daysBetween(lastWeekEndDate).toInt()

        val endDate =
            if (daysBetween / 7 == 6) lastWeekEndDate else lastWeekEndDate.plusWeeks(1)

        listenForAgendaPreviewItems(
            startDate = startDate,
            endDate = endDate
        )
    }

    private fun listenForAgendaItems(
        startDate: LocalDate,
        endDate: LocalDate,
        currentDate: LocalDate,
        changeCurrentAgendaItem: Boolean
    ) {

        listenForChanges(
            oldChannel = agendaItemsChannel,
            channelCreator = {
                agendaItemsChannel = questRepository.listenForScheduledBetween(
                    startDate = startDate,
                    endDate = endDate
                )
                agendaItemsChannel!!
            },
            onResult = { quests ->
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

                val agendaItems = createAgendaItemsUseCase.execute(
                    CreateAgendaItemsUseCase.Params(
                        date = currentDate,
                        scheduledQuests = quests + placeholderQuests,
                        events = events,
                        itemsBefore = AgendaReducer.ITEMS_BEFORE_COUNT,
                        itemsAfter = AgendaReducer.ITEMS_AFTER_COUNT
                    )
                )

                dispatch(
                    DataLoadedAction.AgendaItemsChanged(
                        start = startDate,
                        end = endDate,
                        agendaItems = agendaItems,
                        currentAgendaItemDate = if (changeCurrentAgendaItem) currentDate else null
                    )
                )
            }
        )
    }

    private fun findAllAgendaDates(
        agendaDate: LocalDate
    ): Pair<LocalDate, LocalDate> {
        val result = findAgendaDatesUseCase.execute(
            FindAgendaDatesUseCase.Params.All(
                agendaDate,
                AgendaReducer.ITEMS_BEFORE_COUNT,
                AgendaReducer.ITEMS_AFTER_COUNT
            )
        ) as FindAgendaDatesUseCase.Result.All
        val start = result.start ?: agendaDate.minusMonths(3)
        val end = result.end ?: agendaDate.plusMonths(3)
        return Pair(start, end)
    }


    private fun listenForAgendaPreviewItems(
        startDate: LocalDate,
        endDate: LocalDate
    ) {

        listenForChanges(
            oldChannel = agendaPreviewItemsChannel,
            channelCreator = {
                agendaPreviewItemsChannel = questRepository.listenForScheduledBetween(
                    startDate = startDate,
                    endDate = endDate
                )
                agendaPreviewItemsChannel!!
            },
            onResult = { quests ->
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

                val pItems = createAgendaPreviewItemsUseCase.execute(
                    CreateAgendaPreviewItemsUseCase.Params(
                        startDate = startDate,
                        endDate = endDate,
                        quests = quests + placeholderQuests,
                        events = events
                    )
                )

                dispatch(DataLoadedAction.AgendaPreviewItemsChanged(pItems))
            }
        )
    }

    override fun canHandle(action: Action) =
        action == LoadDataAction.All
            || action is AgendaAction
            || action is ScheduleAction
}