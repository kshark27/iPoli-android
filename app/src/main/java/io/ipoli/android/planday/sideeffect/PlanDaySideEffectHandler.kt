package io.ipoli.android.planday.sideeffect

import io.ipoli.android.Constants
import io.ipoli.android.MyPoliApp
import io.ipoli.android.achievement.usecase.UpdatePlayerStatsUseCase
import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.notification.QuickDoNotificationUtil
import io.ipoli.android.common.redux.Action
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.pet.usecase.ApplyDamageToPlayerUseCase
import io.ipoli.android.planday.PlanDayAction
import io.ipoli.android.planday.PlanDayViewState
import io.ipoli.android.planday.review.ReviewDayAction
import io.ipoli.android.player.usecase.FindIncompleteItemsUseCase
import io.ipoli.android.quest.Quest
import kotlinx.coroutines.experimental.channels.Channel
import org.threeten.bp.LocalDate
import space.traversal.kapsule.required

object PlanDaySideEffectHandler : AppSideEffectHandler() {

    private val questRepository by required { questRepository }
    private val playerRepository by required { playerRepository }
    private val habitRepository by required { habitRepository }
    private val quoteRepository by required { quoteRepository }
    private val motivationalImageRepository by required { motivationalImageRepository }
    private val weatherRepository by required { weatherRepository }
    private val findIncompleteItemsUseCase by required { findIncompleteItemsUseCase }
    private val applyDamageToPlayerUseCase by required { applyDamageToPlayerUseCase }
    private val updatePlayerStatsUseCase by required { updatePlayerStatsUseCase }
    private val sharedPreferences by required { sharedPreferences }

    private var reviewQuestsChannel: Channel<List<Quest>>? = null
    private var reviewHabitsChannel: Channel<List<Habit>>? = null

    override suspend fun doExecute(action: Action, state: AppState) {
        when (action) {

            is ReviewDayAction.Load -> {
                val r =
                    findIncompleteItemsUseCase.execute(FindIncompleteItemsUseCase.Params())

                listenForChanges(
                    oldChannel = reviewQuestsChannel,
                    channelCreator = {
                        reviewQuestsChannel =
                            questRepository.listenForScheduledAt(r.date)
                        reviewQuestsChannel!!
                    },
                    onResult = { qs ->
                        dispatch(
                            DataLoadedAction.ReviewDayQuestsChanged(
                                quests = qs
                            )
                        )
                    }
                )

                listenForChanges(
                    oldChannel = reviewHabitsChannel,
                    channelCreator = {
                        reviewHabitsChannel =
                            habitRepository.listenForAll(r.habits.map { it.id })
                        reviewHabitsChannel!!
                    },
                    onResult = { hs ->
                        dispatch(
                            DataLoadedAction.ReviewDayHabitsChanged(
                                habits = hs,
                                date = r.date
                            )
                        )
                    }
                )
            }

            is ReviewDayAction.ApplyDamage -> {

                val player = playerRepository.find()!!

                val oldPet = player.pet

                val quests = questRepository
                    .findAll(action.allQuestIds.toList())
                    .filter { !it.isCompleted }

                val newPlayer =
                    applyDamageToPlayerUseCase.execute(
                        ApplyDamageToPlayerUseCase.Params(
                            quests = quests,
                            habits = action.habits.filter { !it.isUnlimited },
                            date = action.date
                        )
                    ).player
                val newPet = newPlayer.pet

                if (oldPet.isDead != newPet.isDead) {
                    updatePlayerStatsUseCase.execute(
                        UpdatePlayerStatsUseCase.Params(
                            player = playerRepository.find()!!,
                            eventType = UpdatePlayerStatsUseCase.Params.EventType.PetDied
                        )
                    )
                }

                if (newPlayer.isDead) {
                    sharedPreferences.edit().putBoolean(Constants.KEY_PLAYER_DEAD, true).apply()
                }

                if (newPlayer.preferences.isQuickDoNotificationEnabled && newPlayer.isDead) {
                    QuickDoNotificationUtil.showDefeated(MyPoliApp.instance)
                }

                sharedPreferences.edit().putBoolean(Constants.KEY_SHOULD_REVIEW_DAY, false).apply()
            }

            is PlanDayAction.Load -> {
                val vs = state.stateFor(PlanDayViewState::class.java)

                if (vs.suggestedQuests == null) {
                    dispatch(
                        DataLoadedAction.SuggestionsChanged(
                            questRepository
                                .findRandomUnscheduledAndUncompleted(10)
                                .distinctBy { it.name }
                                .take(3)
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

            is PlanDayAction.MoveBucketListQuestsToToday -> {
                val today = LocalDate.now()
                questRepository.save(action.quests.map {
                    it.copy(
                        scheduledDate = today,
                        originalScheduledDate = it.originalScheduledDate ?: today
                    )
                })
            }
        }
    }

    override fun canHandle(action: Action) = action is PlanDayAction || action is ReviewDayAction

}