package io.ipoli.android.common.home

import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.home.HomeViewState.StateType.*
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.pet.PetState
import io.ipoli.android.player.data.Avatar
import io.ipoli.android.player.data.Player
import io.ipoli.android.tag.Tag

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 2/12/18.
 */
sealed class HomeAction : Action {
    object Load : HomeAction()
    object HideTags : HomeAction()
    object ShowTags : HomeAction()
    object ShowPlayerSetup : HomeAction()
    object FeedbackSent : HomeAction()
}

object HomeReducer : BaseViewStateReducer<HomeViewState>() {

    override val stateKey = key<HomeViewState>()

    override fun reduce(state: AppState, subState: HomeViewState, action: Action) =
        when (action) {
            is HomeAction.Load -> {
                val dataState = state.dataState
                val player = dataState.player
                val tags = dataState.tags
                val bq = dataState.unscheduledQuests

                val s = player?.let {
                    createStateFromPlayer(subState, it)
                } ?: subState

                s.copy(
                    type = DATA_LOADED,
                    tags = tags.filter { it.isFavorite },
                    bucketListQuestCount = bq.count { !it.isCompleted }
                )
            }


            is DataLoadedAction.PlayerChanged ->
                createStateFromPlayer(subState, action.player).copy(
                    type = PLAYER_CHANGED
                )

            is DataLoadedAction.TagsChanged ->
                subState.copy(
                    type = TAGS_CHANGED,
                    tags = action.tags.filter { it.isFavorite }
                )

            is DataLoadedAction.UnscheduledQuestsChanged ->
                subState.copy(
                    type = UNSCHEDULED_QUESTS_CHANGED,
                    bucketListQuestCount = action.quests.count { !it.isCompleted }
                )

            is HomeAction.HideTags ->
                subState.copy(
                    type = TAGS_HIDDEN,
                    showTags = false
                )

            is HomeAction.ShowTags ->
                subState.copy(
                    type = TAGS_SHOWN,
                    showTags = true
                )

            else -> subState
        }

    private fun createStateFromPlayer(state: HomeViewState, player: Player) =
        state.copy(
            showSignIn = !player.isLoggedIn(),
            avatar = player.avatar,
            petAvatar = player.pet.avatar,
            petState = player.pet.state,
            gems = player.gems,
            lifeCoins = player.coins,
            experience = player.experience,
            titleIndex = player.level / 10,
            level = player.level,
            progress = player.experienceProgressForLevel,
            maxProgress = player.experienceForNextLevel,
            rank = player.rank
        )

    override fun defaultState() = HomeViewState(
        type = LOADING,
        showSignIn = true,
        titleIndex = 0,
        level = 0,
        avatar = Avatar.AVATAR_00,
        petAvatar = PetAvatar.ELEPHANT,
        petState = PetState.AWESOME,
        gems = 0,
        lifeCoins = 0,
        rank = Player.Rank.NOVICE,
        experience = 0,
        progress = 0,
        maxProgress = 0,
        tags = listOf(),
        showTags = false,
        selectedTagIndex = null,
        bucketListQuestCount = 0
    )
}

data class HomeViewState(
    val type: StateType,
    val showSignIn: Boolean,
    val titleIndex: Int,
    val level: Int,
    val avatar: Avatar,
    val petAvatar: PetAvatar,
    val petState: PetState,
    val gems: Int,
    val lifeCoins: Int,
    val rank: Player.Rank,
    val experience: Long,
    val progress: Int,
    val maxProgress: Int,
    val tags: List<Tag>,
    val showTags: Boolean,
    val selectedTagIndex: Int?,
    val bucketListQuestCount: Int
) : BaseViewState() {

    enum class StateType {
        LOADING,
        DATA_LOADED,
        PLAYER_CHANGED,
        UNSCHEDULED_QUESTS_CHANGED,
        TAGS_CHANGED,
        TAGS_SHOWN,
        TAGS_HIDDEN
    }
}