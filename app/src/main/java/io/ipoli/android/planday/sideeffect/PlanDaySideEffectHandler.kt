package io.ipoli.android.planday.sideeffect

import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.redux.Action
import io.ipoli.android.planday.PlanDayAction
import io.ipoli.android.planday.PlanDayViewState
import io.ipoli.android.planday.usecase.CalculateAwesomenessScoreUseCase
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.usecase.RescheduleQuestUseCase
import kotlinx.coroutines.experimental.channels.Channel
import org.threeten.bp.LocalDate
import space.traversal.kapsule.required

object PlanDaySideEffectHandler : AppSideEffectHandler() {

    private val questRepository by required { questRepository }
    private val quoteRepository by required { quoteRepository }
    private val motivationalImageRepository by required { motivationalImageRepository }
    private val weatherRepository by required { weatherRepository }
    private val calculateAwesomenessScoreUseCase by required { calculateAwesomenessScoreUseCase }
    private val rescheduleQuestUseCase by required { rescheduleQuestUseCase }

    private var yesterdayQuestsChannel: Channel<List<Quest>>? = null

    override suspend fun doExecute(action: Action, state: AppState) {
        when (action) {
            is PlanDayAction.Load -> {
                val vs = state.stateFor(PlanDayViewState::class.java)

                listenForChanges(
                    oldChannel = yesterdayQuestsChannel,
                    channelCreator = {
                        yesterdayQuestsChannel =
                            questRepository.listenForScheduledAt(LocalDate.now().minusDays(1))
                        yesterdayQuestsChannel!!
                    },
                    onResult = { qs ->
                        dispatch(
                            DataLoadedAction.ReviewDayQuestsChanged(
                                quests = qs,
                                awesomenessScore = calculateAwesomenessScoreUseCase.execute(
                                    CalculateAwesomenessScoreUseCase.Params.WithQuests(qs)
                                )
                            )
                        )
                    }
                )

                if (vs.suggestedQuests == null) {
                    dispatch(
                        DataLoadedAction.SuggestionsChanged(
                            questRepository.findRandomUnscheduledAndUncompleted(
                                3
                            )
                        )
                    )
                }
                if (!vs.quoteLoaded) {
                    dispatch(DataLoadedAction.QuoteChanged(quoteRepository.findRandomQuote()))
                }
                if (!vs.imageLoaded) {
                    dispatch(DataLoadedAction.MotivationalImageChanged(motivationalImageRepository.findRandomImage()))
                }
            }

            is PlanDayAction.GetWeather ->
                try {
                    dispatch(DataLoadedAction.WeatherChanged(weatherRepository.getCurrentWeather()))
                } catch (e: Throwable) {
                    dispatch(DataLoadedAction.WeatherChanged(null))
                }

            is PlanDayAction.Done -> {
                val yesterday = LocalDate.now().minusDays(1)
                val yesterdayNotCompletedQuests =
                    questRepository.findScheduledAt(yesterday).filter { !it.isCompleted }
                yesterdayNotCompletedQuests.forEach {
                    rescheduleQuestUseCase.execute(RescheduleQuestUseCase.Params(it.id, null, null, null))
                }
            }
        }
    }

    override fun canHandle(action: Action) = action is PlanDayAction

}