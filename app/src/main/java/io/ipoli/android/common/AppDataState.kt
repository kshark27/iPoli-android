package io.ipoli.android.common

import io.ipoli.android.achievement.usecase.CreateAchievementItemsUseCase
import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.challenge.preset.PresetChallenge
import io.ipoli.android.common.datetime.Duration
import io.ipoli.android.common.datetime.Minute
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.Reducer
import io.ipoli.android.common.redux.State
import io.ipoli.android.dailychallenge.usecase.CheckDailyChallengeProgressUseCase
import io.ipoli.android.event.Calendar
import io.ipoli.android.event.Event
import io.ipoli.android.friends.feed.data.Post
import io.ipoli.android.friends.persistence.Friend
import io.ipoli.android.friends.usecase.CreateReactionHistoryItemsUseCase
import io.ipoli.android.growth.usecase.CalculateGrowthStatsUseCase
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.habit.usecase.CreateHabitHistoryItemsUseCase
import io.ipoli.android.habit.usecase.CreateHabitItemsUseCase
import io.ipoli.android.planday.data.Weather
import io.ipoli.android.planday.persistence.MotivationalImage
import io.ipoli.android.planday.persistence.Quote
import io.ipoli.android.player.data.Avatar
import io.ipoli.android.player.data.Player
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.RepeatingQuest
import io.ipoli.android.quest.schedule.ScheduleAction
import io.ipoli.android.quest.schedule.agenda.usecase.CreateAgendaItemsUseCase
import io.ipoli.android.quest.schedule.agenda.usecase.CreateAgendaPreviewItemsUseCase
import io.ipoli.android.quest.schedule.agenda.view.AgendaAction
import io.ipoli.android.quest.schedule.calendar.CalendarAction
import io.ipoli.android.quest.schedule.summary.ScheduleSummaryAction
import io.ipoli.android.quest.schedule.summary.usecase.CreateScheduleSummaryItemsUseCase
import io.ipoli.android.quest.schedule.today.usecase.CreateTodayItemsUseCase
import io.ipoli.android.quest.usecase.Schedule
import io.ipoli.android.repeatingquest.usecase.CreateRepeatingQuestHistoryUseCase
import io.ipoli.android.store.gem.GemPack
import io.ipoli.android.tag.Tag
import io.ipoli.android.tag.usecase.CreateTagItemsUseCase
import org.threeten.bp.LocalDate

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 01/24/2018.
 */

sealed class DataLoadedAction : Action {

    data class PlayerChanged(val player: Player) : DataLoadedAction()
    data class TodayQuestsChanged(val quests: List<Quest>) : DataLoadedAction()
    data class RepeatingQuestsChanged(val repeatingQuests: List<RepeatingQuest>) :
        DataLoadedAction()

    data class HabitsChanged(val habits: List<Habit>) :
        DataLoadedAction()

    data class ChallengesChanged(val challenges: List<Challenge>) :
        DataLoadedAction()

    data class AgendaItemsChanged(
        val start: LocalDate,
        val end: LocalDate,
        val agendaItems: List<CreateAgendaItemsUseCase.AgendaItem>,
        val currentAgendaItemDate: LocalDate?
    ) : DataLoadedAction()

    data class AgendaPreviewItemsChanged(
        val previewItems: List<CreateAgendaPreviewItemsUseCase.PreviewItem>
    ) : DataLoadedAction()

    data class CalendarScheduleChanged(val schedule: Map<LocalDate, Schedule>) :
        DataLoadedAction()

    data class RepeatingQuestHistoryChanged(
        val repeatingQuestId: String,
        val history: CreateRepeatingQuestHistoryUseCase.History
    ) : DataLoadedAction()

    data class EventsChanged(val events: List<Event>) : DataLoadedAction()
    data class CalendarsChanged(val calendars: List<Calendar>) : DataLoadedAction()
    data class QuestChanged(val quest: Quest) : DataLoadedAction()
    data class TagsChanged(val tags: List<Tag>) : DataLoadedAction()
    data class TagItemsChanged(val tagId: String, val items: List<CreateTagItemsUseCase.TagItem>) :
        DataLoadedAction()

    data class UnscheduledQuestsChanged(val quests: List<Quest>) : DataLoadedAction()

    data class GemPacksLoaded(val gemPacks: List<GemPack>) : DataLoadedAction()
    data class ReviewDayQuestsChanged(
        val quests: List<Quest>,
        val awesomenessScore: Double
    ) : DataLoadedAction()

    data class WeatherChanged(val weather: Weather?) : DataLoadedAction()

    data class SuggestionsChanged(val quests: List<Quest>) : DataLoadedAction()
    data class MotivationalImageChanged(val motivationalImage: MotivationalImage?) :
        DataLoadedAction()

    data class QuoteChanged(val quote: Quote?) : DataLoadedAction()

    data class GrowthChanged(
        val dailyGrowth: CalculateGrowthStatsUseCase.Growth.Today,
        val weeklyGrowth: CalculateGrowthStatsUseCase.Growth.Week,
        val monthlyGrowth: CalculateGrowthStatsUseCase.Growth.Month,
        val includesAppUsageData: Boolean
    ) : DataLoadedAction()

    data class ProfileDataChanged(
        val player: Player,
        val unlockedAchievements: List<CreateAchievementItemsUseCase.AchievementItem>,
        val streak: Int,
        val averageProductiveDuration: Duration<Minute>
    ) : DataLoadedAction()

    data class FriendDataChanged(
        val player: Player,
        val unlockedAchievements: List<CreateAchievementItemsUseCase.AchievementItem>,
        val streak: Int,
        val averageProductiveDuration: Duration<Minute>,
        val isCurrentPlayerGuest: Boolean,
        val isFollowing: Boolean,
        val isFollower: Boolean
    ) : DataLoadedAction()

    data class AchievementItemsChanged(val achievementListItems: List<CreateAchievementItemsUseCase.AchievementListItem>) :
        DataLoadedAction()

    data class HabitItemsChanged(val habitItems: List<CreateHabitItemsUseCase.HabitItem>) :
        DataLoadedAction()

    data class HabitChanged(
        val habit: Habit,
        val currentDate: LocalDate,
        val habitHistory: List<CreateHabitHistoryItemsUseCase.HabitHistoryItem>
    ) :
        DataLoadedAction()

    data class ScheduleSummaryChanged(
        val currentDate: LocalDate,
        val schedules: List<CreateScheduleSummaryItemsUseCase.Schedule>
    ) : DataLoadedAction()

    data class AcceptFriendshipDataChanged(
        val avatar: Avatar,
        val displayName: String,
        val username: String
    ) : DataLoadedAction()

    data class PostsChanged(val posts: List<Post>) : DataLoadedAction()

    data class ReactionHistoryItemsChanged(val items: List<CreateReactionHistoryItemsUseCase.ReactionHistoryItem>) :
        DataLoadedAction()

    data class FollowersChanged(val friends: List<Friend>) : DataLoadedAction()

    data class FollowingChanged(val friends: List<Friend>) : DataLoadedAction()

    data class PostItemPickerItemsChanged(
        val quests: List<Quest>?,
        val habits: List<Habit>?,
        val challenges: List<Challenge>?
    ) : DataLoadedAction()

    data class FriendChallengesChanged(val challenges: List<Challenge>) : DataLoadedAction()

    data class TodayQuestItemsChanged(
        val questItems: CreateTodayItemsUseCase.Result
    ) : DataLoadedAction()

    data class TodaySummaryStatsChanged(
        val focusDuration: Duration<Minute>,
        val dailyChallengeProgress: CheckDailyChallengeProgressUseCase.Result
    ) : DataLoadedAction()

    data class PresetChallengeListForCategoryChanged(
        val category: PresetChallenge.Category,
        val challenges: List<PresetChallenge>
    ) : DataLoadedAction()

    data class ChallengeChanged(
        val challenge: Challenge,
        val currentDate: LocalDate
    ) : DataLoadedAction()

    data class PostChanged(val post: Post) : DataLoadedAction()

    data class AddPostDataChanged(
        val player: Player,
        val quest: Quest?,
        val habit: Habit?,
        val challenge: Challenge?
    ) : DataLoadedAction()

    data class TodayImageChanged(val imageUrl: String) : DataLoadedAction()
}

data class AppDataState(
    val player: Player?,
    val todayQuests: List<Quest>?,
    val unscheduledQuests: List<Quest>,
    val calendarSchedule: Map<LocalDate, Schedule>,
    val repeatingQuests: List<RepeatingQuest>?,
    val habits: List<Habit>?,
    val challenges: List<Challenge>?,
    val events: List<Event>,
    val tags: List<Tag>,
    val todayImage: String?,
    val focusDuration: Duration<Minute>?,
    val dailyChallengeProgress: CheckDailyChallengeProgressUseCase.Result?,
    val agendaDate: LocalDate
) : State

object AppDataReducer : Reducer<AppState, AppDataState> {

    override val stateKey: String = AppDataState::class.java.simpleName

    override fun reduce(state: AppState, subState: AppDataState, action: Action) =
        when (action) {

            is DataLoadedAction.PlayerChanged -> {
                subState.copy(
                    player = action.player
                )
            }

            is DataLoadedAction.TodayImageChanged ->
                subState.copy(
                    todayImage = action.imageUrl
                )

            is DataLoadedAction.TodaySummaryStatsChanged ->
                subState.copy(
                    focusDuration = action.focusDuration,
                    dailyChallengeProgress = action.dailyChallengeProgress
                )

            is DataLoadedAction.CalendarScheduleChanged ->
                subState.copy(
                    calendarSchedule = action.schedule
                )

            is DataLoadedAction.TodayQuestsChanged ->
                subState.copy(
                    todayQuests = action.quests
                )

            is DataLoadedAction.RepeatingQuestsChanged ->
                subState.copy(
                    repeatingQuests = action.repeatingQuests
                )

            is DataLoadedAction.HabitsChanged ->
                subState.copy(
                    habits = action.habits
                )

            is DataLoadedAction.ChallengesChanged ->
                subState.copy(
                    challenges = action.challenges
                )

            is DataLoadedAction.EventsChanged ->
                subState.copy(
                    events = action.events
                )

            is DataLoadedAction.TagsChanged ->
                subState.copy(
                    tags = action.tags
                )

            is DataLoadedAction.UnscheduledQuestsChanged ->
                subState.copy(
                    unscheduledQuests = action.quests
                )

            is AgendaAction.AutoChangeDate ->
                subState.copy(
                    agendaDate = action.date
                )

            is AgendaAction.ChangePreviewDate ->
                subState.copy(
                    agendaDate = action.date
                )

            is ScheduleAction.GoToToday ->
                subState.copy(
                    agendaDate = LocalDate.now()
                )

            is CalendarAction.ChangeDate ->
                subState.copy(
                    agendaDate = action.date
                )

            is ScheduleSummaryAction.ChangeDate ->
                subState.copy(
                    agendaDate = action.date
                )

            is ScheduleSummaryAction.GoToToday ->
                subState.copy(
                    agendaDate = LocalDate.now()
                )

            is ScheduleAction.ResetAgendaDate ->
                subState.copy(
                    agendaDate = LocalDate.now()
                )

            else -> subState
        }

    override fun defaultState() =
        AppDataState(
            player = null,
            todayQuests = null,
            unscheduledQuests = emptyList(),
            calendarSchedule = mapOf(),
            repeatingQuests = null,
            habits = null,
            challenges = null,
            events = listOf(),
            tags = listOf(),
            todayImage = null,
            focusDuration = null,
            dailyChallengeProgress = null,
            agendaDate = LocalDate.now()
        )
}