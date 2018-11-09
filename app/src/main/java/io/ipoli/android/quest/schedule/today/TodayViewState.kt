package io.ipoli.android.quest.schedule.today

import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.datetime.Duration
import io.ipoli.android.common.datetime.Minute
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.dailychallenge.usecase.CheckDailyChallengeProgressUseCase
import io.ipoli.android.habit.usecase.CreateHabitItemsUseCase
import io.ipoli.android.pet.Pet
import io.ipoli.android.player.data.Avatar
import io.ipoli.android.player.data.Player
import io.ipoli.android.quest.schedule.today.TodayViewState.StateType.*
import io.ipoli.android.quest.schedule.today.usecase.CreateTodayItemsUseCase
import org.threeten.bp.LocalDate

sealed class TodayAction : Action {
    data class Load(val today: LocalDate, val showDataAfterStats: Boolean) : TodayAction()

    object ImageLoaded : TodayAction()
    object StatsShown : TodayAction()

    data class CompleteHabit(val habitId: String) : TodayAction() {
        override fun toMap() = mapOf("habitId" to habitId)
    }

    data class UndoCompleteHabit(val habitId: String) : TodayAction() {
        override fun toMap() = mapOf("habitId" to habitId)
    }

    data class RescheduleQuest(val questId: String, val date: LocalDate?) : TodayAction() {
        override fun toMap() = mapOf("questId" to questId, "date" to date)
    }

    data class CompleteQuest(val questId: String) : TodayAction() {
        override fun toMap() = mapOf("questId" to questId)
    }

    data class UndoCompleteQuest(val questId: String) : TodayAction() {
        override fun toMap() = mapOf("questId" to questId)
    }

    data class RemoveQuest(val questId: String) : TodayAction() {
        override fun toMap() = mapOf("questId" to questId)
    }

    data class UndoRemoveQuest(val questId: String) : TodayAction() {
        override fun toMap() = mapOf("questId" to questId)
    }
}

object TodayReducer : BaseViewStateReducer<TodayViewState>() {

    override val stateKey = key<TodayViewState>()

    override fun reduce(state: AppState, subState: TodayViewState, action: Action) =
        when (action) {

            is TodayAction.Load -> {
                val dataState = state.dataState

                val type =
                    if (dataState.todayImage != null && dataState.player != null)
                        TodayViewState.StateType.SHOW_IMAGE
                    else
                        LOADING

                val newState = dataState.player?.let {
                    updateStateFromPlayer(it, subState)
                } ?: subState

                newState.copy(
                    type = type,
                    showDataAfterStats = action.showDataAfterStats,
                    todayImageUrl = dataState.todayImage,
                    focusDuration = dataState.focusDuration,
                    dailyChallengeProgress = dataState.dailyChallengeProgress
                )
            }

            is DataLoadedAction.PlayerChanged -> {
                val stateType = when {
                    subState.pet == null && state.dataState.todayImage != null -> SHOW_IMAGE
                    subState.pet == null -> LOADING
                    else -> PLAYER_STATS_CHANGED
                }
                updateStateFromPlayer(action.player, subState)
                    .copy(type = stateType)
            }

            is DataLoadedAction.TodayImageChanged -> {
                subState.copy(
                    type = TodayViewState.StateType.SHOW_IMAGE,
                    todayImageUrl = action.imageUrl
                )
            }

            is DataLoadedAction.TodayQuestItemsChanged -> {
                val type =
                    when {
                        subState.todayHabitItems == null -> LOADING
                        subState.quests == null -> DATA_CHANGED
                        else -> QUESTS_CHANGED
                    }
                val questItems = action.questItems
                subState.copy(
                    type = type,
                    quests = questItems,
                    questCompleteCount = questItems.complete.size,
                    questCount = questItems.complete.size + questItems.incomplete.size
                )
            }

            is DataLoadedAction.HabitItemsChanged -> {

                val type =
                    when {
                        subState.quests == null -> LOADING
                        subState.todayHabitItems == null -> DATA_CHANGED
                        else -> HABITS_CHANGED
                    }

                val todayHabitItems = action.habitItems
                    .filterIsInstance(CreateHabitItemsUseCase.HabitItem.Today::class.java)
                val goodHabits = todayHabitItems.filter { it.habit.isGood }
                subState.copy(
                    type = type,
                    todayHabitItems = todayHabitItems,
                    habitCompleteCount = goodHabits.count { it.isCompleted },
                    habitCount = goodHabits.size
                )
            }

            is TodayAction.ImageLoaded ->
                if (subState.focusDuration == null)
                    subState
                else
                    subState.copy(
                        type = TodayViewState.StateType.SHOW_SUMMARY_STATS
                    )

            is TodayAction.StatsShown ->
                if (subState.showDataAfterStats)
                    subState.copy(
                        type = TodayViewState.StateType.SHOW_DATA
                    ) else subState

            is DataLoadedAction.TodaySummaryStatsChanged ->
                subState.copy(
                    type = SUMMARY_STATS_CHANGED,
                    focusDuration = action.focusDuration,
                    dailyChallengeProgress = action.dailyChallengeProgress
                )

            else -> subState
        }

    private fun updateStateFromPlayer(
        player: Player,
        subState: TodayViewState
    ) =
        subState.copy(
            avatar = player.avatar,
            level = player.level,
            levelXpProgress = player.experienceProgressForLevel,
            levelXpMaxProgress = player.experienceForNextLevel,
            coins = player.coins,
            gems = player.gems,
            pet = player.pet,
            attributes = player.attributes.map { it.value },
            health = player.health.current,
            maxHealth = player.health.max
        )

    override fun defaultState() =
        TodayViewState(
            type = LOADING,
            avatar = Avatar.AVATAR_00,
            pet = null,
            level = -1,
            levelXpProgress = -1,
            levelXpMaxProgress = -1,
            coins = -1,
            gems = -1,
            health = -1,
            maxHealth = -1,
            attributes = emptyList(),
            questCount = 0,
            questCompleteCount = 0,
            habitCount = 0,
            habitCompleteCount = 0,
            quests = null,
            todayHabitItems = null,
            todayImageUrl = null,
            focusDuration = null,
            dailyChallengeProgress = null,
            showDataAfterStats = false
        )
}

data class TodayViewState(
    val type: StateType,
    val avatar: Avatar,
    val pet: Pet?,
    val level: Int,
    val levelXpProgress: Int,
    val levelXpMaxProgress: Int,
    val coins: Int,
    val gems: Int,
    val health: Int,
    val maxHealth: Int,
    val attributes: List<Player.Attribute>,
    val quests: CreateTodayItemsUseCase.Result?,
    val questCount: Int,
    val questCompleteCount: Int,
    val habitCount: Int,
    val habitCompleteCount: Int,
    val todayHabitItems: List<CreateHabitItemsUseCase.HabitItem.Today>?,
    val todayImageUrl: String?,
    val focusDuration: Duration<Minute>?,
    val dailyChallengeProgress: CheckDailyChallengeProgressUseCase.Result?,
    val showDataAfterStats: Boolean
) :
    BaseViewState() {

    enum class StateType {
        LOADING,
        SUMMARY_STATS_CHANGED,
        PLAYER_STATS_CHANGED,
        SHOW_SUMMARY_STATS,
        SHOW_DATA,
        HABITS_CHANGED,
        QUESTS_CHANGED,
        SHOW_IMAGE,
        DATA_CHANGED
    }
}