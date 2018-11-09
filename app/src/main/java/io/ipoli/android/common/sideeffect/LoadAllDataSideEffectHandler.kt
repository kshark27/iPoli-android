package io.ipoli.android.common.sideeffect

import io.ipoli.android.Constants
import io.ipoli.android.MyPoliApp
import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.challenge.usecase.FindChallengeProgressUseCase
import io.ipoli.android.challenge.usecase.FindHabitsForChallengeUseCase
import io.ipoli.android.challenge.usecase.FindNextDateForChallengeUseCase
import io.ipoli.android.challenge.usecase.FindQuestsForChallengeUseCase
import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.LoadDataAction
import io.ipoli.android.common.notification.QuickDoNotificationUtil
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.view.AppWidgetUtil
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.planday.usecase.CalculateFocusDurationUseCase
import io.ipoli.android.player.data.Player
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.RepeatingQuest
import io.ipoli.android.repeatingquest.usecase.FindNextDateForRepeatingQuestUseCase
import io.ipoli.android.repeatingquest.usecase.FindPeriodProgressForRepeatingQuestUseCase
import io.ipoli.android.tag.Tag
import io.ipoli.android.tag.usecase.AddQuestCountToTagUseCase
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.withContext
import org.threeten.bp.LocalDate
import space.traversal.kapsule.required

object LoadAllDataSideEffectHandler : AppSideEffectHandler() {

    private val playerRepository by required { playerRepository }
    private val questRepository by required { questRepository }
    private val challengeRepository by required { challengeRepository }
    private val repeatingQuestRepository by required { repeatingQuestRepository }
    private val tagRepository by required { tagRepository }
    private val habitRepository by required { habitRepository }
    private val todayImageRepository by required { todayImageRepository }
    private val findNextDateForRepeatingQuestUseCase by required { findNextDateForRepeatingQuestUseCase }
    private val findPeriodProgressForRepeatingQuestUseCase by required { findPeriodProgressForRepeatingQuestUseCase }
    private val findQuestsForChallengeUseCase by required { findQuestsForChallengeUseCase }
    private val findHabitsForChallengeUseCase by required { findHabitsForChallengeUseCase }
    private val findNextDateForChallengeUseCase by required { findNextDateForChallengeUseCase }
    private val findChallengeProgressUseCase by required { findChallengeProgressUseCase }
    private val addQuestCountToTagUseCase by required { addQuestCountToTagUseCase }
    private val reminderScheduler by required { reminderScheduler }
    private val sharedPreferences by required { sharedPreferences }

    private val calculateFocusDurationUseCase by required { calculateFocusDurationUseCase }
    private val checkDailyChallengeProgressUseCase by required { checkDailyChallengeProgressUseCase }

    private var playerChannel: Channel<Player?>? = null
    private var todayQuestsChannel: Channel<List<Quest>>? = null
    private var tagsChannel: Channel<List<Tag>>? = null
    private var repeatingQuestsChannel: Channel<List<RepeatingQuest>>? = null
    private var habitsChannel: Channel<List<Habit>>? = null
    private var challengesChannel: Channel<List<Challenge>>? = null
    private var unscheduledQuestsChannel: Channel<List<Quest>>? = null

    override suspend fun doExecute(action: Action, state: AppState) {

        if (action is DataLoadedAction.TodayQuestsChanged) {
            withContext(Dispatchers.Main) {
                updateQuestWidgets()
            }

            if (sharedPreferences.getBoolean(Constants.KEY_PLAYER_DEAD, false)) {
                QuickDoNotificationUtil.showDefeated(MyPoliApp.instance)
            } else if (sharedPreferences.getBoolean(
                    Constants.KEY_QUICK_DO_NOTIFICATION_ENABLED,
                    Constants.DEFAULT_QUICK_DO_NOTIFICATION_ENABLED
                )
            ) {

                QuickDoNotificationUtil.update(
                    MyPoliApp.instance,
                    action.quests
                )
            }

            val focusDuration = calculateFocusDurationUseCase.execute(
                CalculateFocusDurationUseCase.Params.WithQuests(action.quests)
            )
            val dcProgress = checkDailyChallengeProgressUseCase.execute(Unit)

            dispatch(
                DataLoadedAction.TodaySummaryStatsChanged(
                    focusDuration,
                    dcProgress
                )
            )
        }

        if (action is DataLoadedAction.HabitsChanged) {
            withContext(Dispatchers.Main) {
                AppWidgetUtil.updateHabitWidget(MyPoliApp.instance)
            }
        }

        if (action == LoadDataAction.All) {
            listenForPlayerData()
            dispatch(DataLoadedAction.TodayImageChanged(todayImageRepository.findForDay(LocalDate.now())))
        }
    }

    private fun listenForPlayerData() {

        listenForPlayer()
        listenForTodayQuests()
        listenForTags()
        listenForRepeatingQuests()
        listenForHabits()
        listenForChallenges()
        listenForUnscheduledQuests()

        reminderScheduler.schedule()
    }

    private fun listenForUnscheduledQuests() {
        listenForChanges(
            oldChannel = unscheduledQuestsChannel,
            channelCreator = {
                unscheduledQuestsChannel = questRepository.listenForAllUnscheduled()
                unscheduledQuestsChannel!!
            },
            onResult = { qs ->
                dispatch(DataLoadedAction.UnscheduledQuestsChanged(qs))
            }
        )
    }

    private fun listenForChallenges() {
        listenForChanges(
            oldChannel = challengesChannel,
            channelCreator = {
                challengesChannel = challengeRepository.listenForAll()
                challengesChannel!!
            },
            onResult = { cs ->
                val challenges = cs.map {
                    findQuestsForChallengeUseCase.execute(
                        FindQuestsForChallengeUseCase.Params(it)
                    )
                }.map {
                    findNextDateForChallengeUseCase.execute(
                        FindNextDateForChallengeUseCase.Params(it)
                    )
                }.map {
                    findChallengeProgressUseCase.execute(
                        FindChallengeProgressUseCase.Params(it)
                    )
                }.map {
                    findHabitsForChallengeUseCase.execute(FindHabitsForChallengeUseCase.Params(it))
                }
                dispatch(DataLoadedAction.ChallengesChanged(challenges))
            }
        )
    }

    private fun listenForHabits() {
        listenForChanges(
            oldChannel = habitsChannel,
            channelCreator = {
                habitsChannel = habitRepository.listenForAll()
                habitsChannel!!
            },
            onResult = { hs ->
                dispatch(DataLoadedAction.HabitsChanged(hs))
            }
        )
    }

    private fun listenForRepeatingQuests() {
        listenForChanges(
            oldChannel = repeatingQuestsChannel,
            channelCreator = {
                repeatingQuestsChannel = repeatingQuestRepository.listenForAll()
                repeatingQuestsChannel!!
            },
            onResult = { rqs ->
                val repeatingQuests = rqs.map {
                    findNextDateForRepeatingQuestUseCase.execute(
                        FindNextDateForRepeatingQuestUseCase.Params(
                            it
                        )
                    )
                }.map {
                    findPeriodProgressForRepeatingQuestUseCase.execute(
                        FindPeriodProgressForRepeatingQuestUseCase.Params(
                            it
                        )
                    )
                }
                dispatch(
                    DataLoadedAction.RepeatingQuestsChanged(
                        repeatingQuests
                    )
                )
            }
        )
    }

    private fun listenForTags() {
        listenForChanges(
            oldChannel = tagsChannel,
            channelCreator = {
                tagsChannel = tagRepository.listenForAll()
                tagsChannel!!
            },
            onResult = { ts ->
                val tags = ts
                    .map {
                        addQuestCountToTagUseCase.execute(
                            AddQuestCountToTagUseCase.Params(
                                it
                            )
                        )
                    }
                dispatch(DataLoadedAction.TagsChanged(tags))
            }
        )
    }

    private fun listenForTodayQuests() {
        listenForChanges(
            oldChannel = todayQuestsChannel,
            channelCreator = {
                todayQuestsChannel = questRepository.listenForScheduledAt(LocalDate.now())
                todayQuestsChannel!!
            },
            onResult = { qs -> dispatch(DataLoadedAction.TodayQuestsChanged(qs)) }
        )
    }

    private fun listenForPlayer() {
        listenForChanges(
            oldChannel = playerChannel,
            channelCreator = {
                playerChannel = playerRepository.listen()
                playerChannel!!
            },
            onResult = { p ->
                dispatch(DataLoadedAction.PlayerChanged(p!!))
            }
        )
    }

    private fun updateQuestWidgets() {
        AppWidgetUtil.updateAgendaWidget(MyPoliApp.instance)
    }

    override fun canHandle(action: Action) =
        action == LoadDataAction.All
            || action is DataLoadedAction.TodayQuestsChanged
            || action is DataLoadedAction.HabitsChanged
}