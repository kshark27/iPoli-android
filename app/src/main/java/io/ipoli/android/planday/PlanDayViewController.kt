package io.ipoli.android.planday

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import io.ipoli.android.Constants
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.datetime.Duration
import io.ipoli.android.common.datetime.Minute
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.datetime.TimeOfDay
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.text.DateFormatter
import io.ipoli.android.common.view.enterFullScreen
import io.ipoli.android.common.view.exitFullScreen
import io.ipoli.android.common.view.inflate
import io.ipoli.android.dailychallenge.data.DailyChallenge
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.planday.PlanDayViewController.Companion.PLAN_TODAY_INDEX
import io.ipoli.android.planday.PlanDayViewState.StateType.*
import io.ipoli.android.planday.data.Weather
import io.ipoli.android.planday.persistence.Quote
import io.ipoli.android.planday.scenes.PlanDayMotivationViewController
import io.ipoli.android.planday.scenes.PlanDayTodayViewController
import io.ipoli.android.player.data.Player
import io.ipoli.android.quest.Quest
import kotlinx.android.synthetic.main.controller_plan_day.view.*
import org.threeten.bp.LocalDate
import org.threeten.bp.format.TextStyle
import java.util.*

sealed class PlanDayAction : Action {

    data class RemoveQuest(val questId: String) : PlanDayAction() {
        override fun toMap() = mapOf("questId" to questId)
    }

    data class UndoRemoveQuest(val questId: String) : PlanDayAction() {
        override fun toMap() = mapOf("questId" to questId)
    }

    data class RescheduleQuest(
        val questId: String,
        val date: LocalDate?,
        val time: Time?,
        val duration: Duration<Minute>?
    ) : PlanDayAction() {
        override fun toMap() = mapOf(
            "questId" to questId,
            "date" to date,
            "time" to time,
            "duration" to duration?.intValue
        )
    }

    data class AcceptSuggestion(val questId: String) : PlanDayAction() {
        override fun toMap() = mapOf("questId" to questId)
    }

    data class AddDailyChallengeQuest(val questId: String) : PlanDayAction() {
        override fun toMap() = mapOf("questId" to questId)
    }

    data class RemoveDailyChallengeQuest(val questId: String) : PlanDayAction() {
        override fun toMap() = mapOf("questId" to questId)
    }

    data class DailyChallengeLoaded(val dailyChallenge: DailyChallenge) : PlanDayAction() {
        override fun toMap() = mapOf("dailyChallenge" to dailyChallenge)
    }

    data class MoveBucketListQuestsToToday(val quests: List<Quest>) : PlanDayAction()

    object Load : PlanDayAction()
    object ShowNext : PlanDayAction()
    object GetWeather : PlanDayAction()
    object ImageLoaded : PlanDayAction()
    object RisingSunAnimationDone : PlanDayAction()
    object Done : PlanDayAction()
    object Back : PlanDayAction()
    object LoadToday : PlanDayAction()
    object LoadMotivation : PlanDayAction()
    object StartDay : PlanDayAction()

}

object PlanDayReducer : BaseViewStateReducer<PlanDayViewState>() {

    override fun reduce(
        state: AppState,
        subState: PlanDayViewState,
        action: Action
    ) =
        when (action) {

            is PlanDayAction.Load -> {
                val today = LocalDate.now()
                val weekDayText =
                    today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

                val player = state.dataState.player

                val playerName = player?.let {
                    it.displayName?.let { n -> firstNameOf(n) }
                }

                subState.copy(
                    type = FIRST_PAGE,
                    playerName = playerName,
                    dateText = "$weekDayText, ${DateFormatter.formatDayWithWeek(today)}",
                    petAvatar = player?.pet?.avatar ?: subState.petAvatar,
                    timeFormat = player?.preferences?.timeFormat ?: subState.timeFormat,
                    temperatureUnit = player?.preferences?.temperatureUnit
                        ?: subState.temperatureUnit,
                    todayQuests = state.dataState.todayQuests
                )
            }

            is PlanDayAction.ShowNext -> {
                if (subState.adapterPosition + 1 > PLAN_TODAY_INDEX)
                    subState
                else
                    subState.copy(
                        type = PlanDayViewState.StateType.NEXT_PAGE,
                        adapterPosition = subState.adapterPosition + 1
                    )
            }

            is DataLoadedAction.PlayerChanged -> {
                val player = action.player
                subState.copy(
                    type = DATA_CHANGED,
                    playerName = player.displayName?.let { firstNameOf(it) },
                    petAvatar = player.pet.avatar,
                    timeFormat = player.preferences.timeFormat,
                    temperatureUnit = player.preferences.temperatureUnit
                )
            }

            is PlanDayAction.LoadMotivation ->
                createNewStateIfMotivationalDataIsLoaded(subState)

            is DataLoadedAction.TodayQuestsChanged -> {
                val newState = subState.copy(
                    type = DATA_CHANGED,
                    todayQuests = action.quests
                )
                createNewStateIfTodayDataIsLoaded(newState)
            }

            is DataLoadedAction.SuggestionsChanged -> {
                val newState = subState.copy(
                    type = DATA_CHANGED,
                    suggestedQuests = action.quests
                )
                createNewStateIfTodayDataIsLoaded(newState)
            }

            is PlanDayAction.DailyChallengeLoaded ->
                createNewStateIfTodayDataIsLoaded(
                    subState.copy(
                        dailyChallengeQuestIds = action.dailyChallenge.questIds,
                        isDailyChallengeCompleted = action.dailyChallenge.isCompleted
                    )
                )

            is PlanDayAction.AcceptSuggestion -> {
                val suggestion = subState.suggestedQuests!!.first { it.id == action.questId }
                val newState = subState.copy(
                    type = DATA_CHANGED,
                    suggestedQuests = subState.suggestedQuests - suggestion
                )
                createNewStateIfTodayDataIsLoaded(newState)
            }

            is DataLoadedAction.QuoteChanged -> {
                val newState = subState.copy(
                    type = DATA_CHANGED,
                    quote = action.quote,
                    quoteLoaded = true
                )
                createNewStateIfMotivationalDataIsLoaded(newState)
            }

            is DataLoadedAction.WeatherChanged -> {
                val newState = action.weather?.let {

                    val conditions = it.conditions

                    if (conditions.contains(Weather.Condition.UNKNOWN))
                        subState.copy(
                            type = DATA_CHANGED,
                            weatherLoaded = true
                        )
                    else
                        subState.copy(
                            type = DATA_CHANGED,
                            weather = action.weather,
                            weatherLoaded = true
                        )
                } ?: subState.copy(
                    type = DATA_CHANGED,
                    weatherLoaded = true
                )
                createNewStateIfMotivationalDataIsLoaded(newState)
            }

            is DataLoadedAction.MotivationalImageChanged -> {
                val newState = action.motivationalImage?.let {
                    subState.copy(
                        type = IMAGE_LOADED,
                        imageUrl = it.url + "&fm=jpg&w=1080&crop=entropy&fit=max&q=40",
                        imageAuthor = it.author,
                        imageAuthorUrl = it.authorUrl,
                        imageLoaded = true
                    )
                } ?: subState.copy(
                    type = IMAGE_LOADED,
                    imageAuthor = "Daniel Roe",
                    imageAuthorUrl = "https://unsplash.com/@danielroe",
                    imageLoaded = true
                )

                createNewStateIfMotivationalDataIsLoaded(newState)
            }

            is PlanDayAction.ImageLoaded -> {
                val newState = subState.copy(
                    type = DATA_CHANGED,
                    imageViewLoaded = true
                )

                createNewStateIfMotivationalDataIsLoaded(newState)
            }

            is PlanDayAction.RisingSunAnimationDone -> {
                val newState = subState.copy(
                    type = DATA_CHANGED,
                    risingSunAnimationDone = true
                )

                createNewStateIfMotivationalDataIsLoaded(newState)
            }

            is PlanDayAction.LoadToday -> {
                createNewStateIfTodayDataIsLoaded(
                    subState.copy(
                        type = DATA_CHANGED
                    )
                )
            }

            is PlanDayAction.Back ->
                if (subState.adapterPosition == 1)
                    subState.copy(
                        adapterPosition = 0
                    )
                else
                    subState.copy(
                        type = CLOSE
                    )

            is PlanDayAction.AddDailyChallengeQuest -> {
                if (subState.dailyChallengeQuestIds!!.size == Constants.DAILY_CHALLENGE_QUEST_COUNT) {
                    subState.copy(
                        type = MAX_DAILY_CHALLENGE_QUESTS_REACHED
                    )
                } else {
                    subState.copy(
                        type = DAILY_CHALLENGE_QUESTS_CHANGED,
                        dailyChallengeQuestIds = subState.dailyChallengeQuestIds + action.questId
                    )
                }
            }

            is PlanDayAction.RemoveDailyChallengeQuest ->
                subState.copy(
                    type = DAILY_CHALLENGE_QUESTS_CHANGED,
                    dailyChallengeQuestIds = subState.dailyChallengeQuestIds!! - action.questId
                )

            PlanDayAction.StartDay -> {
                val count = subState.dailyChallengeQuestIds!!.size
                subState.copy(
                    type = if (count == 0 || count == Constants.DAILY_CHALLENGE_QUEST_COUNT) {
                        DAY_STARTED
                    } else {
                        NOT_ENOUGH_DAILY_CHALLENGE_QUESTS
                    }
                )
            }

            else -> subState

        }

    private fun firstNameOf(displayName: String) =
        if (displayName.isBlank())
            null
        else
            displayName.split(" ")[0].capitalize()

    private fun createNewStateIfMotivationalDataIsLoaded(newState: PlanDayViewState) =
        if (hasMotivationalDataLoaded(newState))
            if (newState.hasShownMotivationalData)
                newState.copy(
                    type = SHOW_MOTIVATION_DATA
                )
            else
                newState.copy(
                    type = MOTIVATION_DATA_LOADED,
                    timeOfDay = Time.now().timeOfDay,
                    hasShownMotivationalData = true
                )
        else
            newState

    private fun hasMotivationalDataLoaded(state: PlanDayViewState) =
        state.weatherLoaded &&
            state.quoteLoaded &&
            state.imageLoaded &&
            state.imageViewLoaded &&
            state.risingSunAnimationDone

    private fun createNewStateIfTodayDataIsLoaded(newState: PlanDayViewState) =
        if (hasTodayDataLoaded(newState))
            newState.copy(
                type = TODAY_DATA_LOADED
            )
        else
            newState

    private fun hasTodayDataLoaded(state: PlanDayViewState) =
        state.todayQuests != null && state.suggestedQuests != null

    override fun defaultState() =
        PlanDayViewState(
            type = INITIAL,
            dateText = null,
            imageUrl = null,
            imageAuthor = "",
            imageAuthorUrl = "",
            quote = null,
            todayQuests = null,
            dailyChallengeQuestIds = emptyList(),
            isDailyChallengeCompleted = false,
            suggestedQuests = null,
            adapterPosition = 0,
            playerName = null,
            weather = null,
            timeOfDay = null,
            weatherLoaded = false,
            quoteLoaded = false,
            imageLoaded = false,
            imageViewLoaded = false,
            hasShownMotivationalData = false,
            risingSunAnimationDone = false,
            petAvatar = null,
            temperatureUnit = Constants.DEFAULT_TEMPERATURE_UNIT,
            timeFormat = Constants.DEFAULT_TIME_FORMAT
        )

    override val stateKey = key<PlanDayViewState>()

}

data class PlanDayViewState(
    val type: StateType,
    val dateText: String?,
    val imageUrl: String?,
    val imageAuthor: String,
    val imageAuthorUrl: String,
    val weather: Weather?,
    val quote: Quote?,
    val todayQuests: List<Quest>?,
    val suggestedQuests: List<Quest>?,
    val dailyChallengeQuestIds: List<String>?,
    val isDailyChallengeCompleted: Boolean,
    val adapterPosition: Int,
    val timeOfDay: TimeOfDay?,
    val playerName: String?,
    val weatherLoaded: Boolean,
    val quoteLoaded: Boolean,
    val imageLoaded: Boolean,
    val imageViewLoaded: Boolean,
    val hasShownMotivationalData: Boolean,
    val risingSunAnimationDone: Boolean,
    val petAvatar: PetAvatar?,
    val temperatureUnit: Player.Preferences.TemperatureUnit,
    val timeFormat: Player.Preferences.TimeFormat
) : BaseViewState() {
    enum class StateType {
        INITIAL,
        FIRST_PAGE,
        NEXT_PAGE,
        CLOSE,
        DATA_CHANGED,
        IMAGE_LOADED,
        MOTIVATION_DATA_LOADED,
        SHOW_MOTIVATION_DATA,
        TODAY_DATA_LOADED,
        MAX_DAILY_CHALLENGE_QUESTS_REACHED,
        DAILY_CHALLENGE_QUESTS_CHANGED,
        DAY_STARTED,
        NOT_ENOUGH_DAILY_CHALLENGE_QUESTS
    }
}

class PlanDayViewController(args: Bundle? = null) :
    ReduxViewController<PlanDayAction, PlanDayViewState, PlanDayReducer>(args) {
    override val reducer = PlanDayReducer

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        requestPermissions(
            mapOf(Manifest.permission.ACCESS_FINE_LOCATION to "If you want to see the weather, please give myPoli permission for your location"),
            Constants.RC_LOCATION_PERM
        )
        return container.inflate(R.layout.controller_plan_day)
    }

    override fun onCreateLoadAction() = PlanDayAction.Load

    override fun onPermissionsGranted(requestCode: Int, permissions: List<String>) {
        enterFullScreen()
        dispatch(PlanDayAction.GetWeather)
    }

    override fun onPermissionsDenied(requestCode: Int, permissions: List<String>) {
        enterFullScreen()
        dispatch(PlanDayAction.GetWeather)
    }

    override fun handleBack(): Boolean {
        dispatch(PlanDayAction.Back)
        return true
    }

    override fun render(state: PlanDayViewState, view: View) {
        when (state.type) {
            FIRST_PAGE ->
                changeChildController(
                    view = view,
                    adapterPosition = state.adapterPosition,
                    animate = false
                )

            NEXT_PAGE ->
                changeChildController(
                    view = view,
                    adapterPosition = state.adapterPosition,
                    animate = true
                )

            CLOSE -> {
                exitFullScreen()
                navigateFromRoot().setHome()
            }

            else -> {
            }
        }
    }

    private fun changeChildController(
        view: View,
        adapterPosition: Int,
        animate: Boolean = true
    ) {
        val childRouter = getChildRouter(view.planDayPager)

        val changeHandler = if (animate) HorizontalChangeHandler() else null

        val transaction = RouterTransaction.with(
            createControllerForPosition(adapterPosition)
        )
            .popChangeHandler(changeHandler)
            .pushChangeHandler(changeHandler)
        childRouter.pushController(transaction)
    }

    private fun createControllerForPosition(position: Int): Controller =
        when (position) {
            MOTIVATION_INDEX -> PlanDayMotivationViewController()
            PLAN_TODAY_INDEX -> PlanDayTodayViewController()
            else -> throw IllegalArgumentException("Unknown controller position $position")
        }

    companion object {
        const val MOTIVATION_INDEX = 0
        const val PLAN_TODAY_INDEX = 1
    }

}