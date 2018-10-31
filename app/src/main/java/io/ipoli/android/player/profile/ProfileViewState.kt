package io.ipoli.android.player.profile

import io.ipoli.android.achievement.usecase.CreateAchievementItemsUseCase
import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.challenge.entity.SharingPreference
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.datetime.Duration
import io.ipoli.android.common.datetime.Minute
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.friends.feed.data.Post
import io.ipoli.android.friends.persistence.Friend
import io.ipoli.android.pet.Pet
import io.ipoli.android.player.data.Avatar
import io.ipoli.android.player.data.Membership
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.profile.ProfileViewState.StateType.*

sealed class ProfileAction : Action {
    data class Save(val displayName: String, val bio: String) : ProfileAction() {
        override fun toMap() = mapOf("displayName" to displayName)
    }

    data class Load(val friendId: String?) : ProfileAction() {
        override fun toMap() = mapOf("playerId" to friendId)
    }

    data class LoadFriendChallenges(val friendId: String) : ProfileAction() {
        override fun toMap() = mapOf("playerId" to friendId)
    }

    object LoadPlayerChallenges : ProfileAction()
    data class LoadFollowers(val playerId: String?) : ProfileAction()
    data class LoadFollowing(val playerId: String?) : ProfileAction()
    data class LoadPosts(val playerId: String?) :
        ProfileAction()

    data class SaveDescription(val postId: String, val description: String?) : ProfileAction() {
        override fun toMap() = mapOf("postId" to postId, "description" to description)
    }

    data class ShowReactionPopupForPost(val postId: String) : ProfileAction()

    data class ReactToPost(
        val reaction: Post.ReactionType,
        val friendId: String?
    ) : ProfileAction() {
        override fun toMap() = mapOf(
            "reaction" to reaction.name,
            "playerId" to friendId
        )
    }

    data class Follow(val friendId: String) : ProfileAction()
    data class Unfollow(val friendId: String) : ProfileAction()

    object ShowRequireLogin : ProfileAction()
    object UnloadPosts : ProfileAction()
    object LoadInfo : ProfileAction()
    object NoInternetConnection : ProfileAction()
    object ErrorLoadingFriends : ProfileAction()
}

class ProfileReducer(reducerKey: String) : BaseViewStateReducer<ProfileViewState>() {

    override fun reduce(
        state: AppState,
        subState: ProfileViewState,
        action: Action
    ) =
        when (action) {

            is ProfileAction.Load -> {
                if (action.friendId != null) {
                    subState.copy(
                        type = LOADING,
                        friendId = action.friendId
                    )
                } else {

                    val p = state.dataState.player
                    if (p != null)
                        createStateFromPlayer(p, subState)
                    else
                        subState
                }
            }

            is DataLoadedAction.PlayerChanged -> {
                val p = action.player
                createStateFromPlayer(p, subState)
            }

            is DataLoadedAction.ProfileDataChanged -> {

                val ns = createStateFromPlayer(action.player, subState)

                val newState = ns.copy(
                    dailyChallengeStreak = action.streak,
                    last7DaysAverageProductiveDuration = action.averageProductiveDuration,
                    unlockedAchievements = action.unlockedAchievements
                )
                if (hasProfileDataLoaded(newState)) {
                    newState.copy(
                        type = ProfileViewState.StateType.PROFILE_DATA_LOADED
                    )
                } else {
                    newState
                }
            }

            is DataLoadedAction.FriendDataChanged -> {
                val ns = createStateFromPlayer(action.player, subState)

                val newState = ns.copy(
                    dailyChallengeStreak = action.streak,
                    last7DaysAverageProductiveDuration = action.averageProductiveDuration,
                    unlockedAchievements = action.unlockedAchievements,
                    isCurrentPlayerGuest = action.isCurrentPlayerGuest,
                    isFollowing = action.isFollowing,
                    isFollower = action.isFollower
                )
                if (hasProfileDataLoaded(newState)) {
                    newState.copy(
                        type = ProfileViewState.StateType.PROFILE_DATA_LOADED
                    )
                } else {
                    newState
                }
            }

            is ProfileAction.LoadInfo ->
                if (subState.username != null && subState.last7DaysAverageProductiveDuration != null)
                    subState.copy(
                        type = PROFILE_INFO_LOADED
                    )
                else
                    subState

            is ProfileAction.LoadPlayerChallenges -> {
                val player = state.dataState.player
                when {
                    player == null -> subState.copy(
                        type = ProfileViewState.StateType.CHALLENGE_LIST_LOADING
                    )
                    player.authProvider == null -> subState.copy(
                        type = ProfileViewState.StateType.SHOW_REQUIRE_LOGIN
                    )
                    state.dataState.challenges == null -> subState.copy(
                        type = ProfileViewState.StateType.CHALLENGE_LIST_LOADING
                    )
                    else -> subState.copy(
                        type = CHALLENGE_LIST_DATA_CHANGED,
                        challenges = state.dataState.challenges.filter { it.sharingPreference == SharingPreference.FRIENDS }
                    )
                }

            }

            is DataLoadedAction.FriendChallengesChanged ->
                subState.copy(
                    type = CHALLENGE_LIST_DATA_CHANGED,
                    challenges = action.challenges
                )

            is DataLoadedAction.FollowersChanged ->
                subState.copy(
                    type = FOLLOWER_LIST_CHANGED,
                    followers = action.friends
                )

            is DataLoadedAction.FollowingChanged ->
                subState.copy(
                    type = FOLLOWING_LIST_CHANGED,
                    following = action.friends
                )

            is ProfileAction.ShowRequireLogin ->
                subState.copy(
                    type = ProfileViewState.StateType.SHOW_REQUIRE_LOGIN
                )

            is DataLoadedAction.PostsChanged ->
                subState.copy(
                    type = POSTS_CHANGED,
                    posts = action.posts
                )

            is ProfileAction.ShowReactionPopupForPost ->
                subState.copy(
                    type = REACTION_POPUP_SHOWN,
                    currentPostId = action.postId
                )

            is ProfileAction.ErrorLoadingFriends,
            is ProfileAction.NoInternetConnection -> {
                subState.copy(
                    type = NO_INTERNET_CONNECTION
                )
            }

            is ProfileAction.Follow ->
                subState.copy(
                    type = FOLLOWING_STATUS_CHANGED,
                    isFollowing = true
                )

            is ProfileAction.Unfollow ->
                subState.copy(
                    type = FOLLOWING_STATUS_CHANGED,
                    isFollowing = false
                )

            else -> subState
        }

    private fun hasProfileDataLoaded(state: ProfileViewState) =
        state.pet != null && state.last7DaysAverageProductiveDuration != null && state.dailyChallengeStreak >= 0

    private fun createStateFromPlayer(
        player: Player,
        subState: ProfileViewState
    ): ProfileViewState {
        val newState = subState.copy(
            avatar = player.avatar,
            displayName = player.displayName,
            username = player.username,
            titleIndex = player.level / 10,
            createdAgo = player.createdAt.toEpochMilli(),
            bio = player.bio,
            level = player.level,
            levelXpProgress = player.experienceProgressForLevel,
            levelXpMaxProgress = player.experienceForNextLevel,
            coins = player.coins,
            gems = player.gems,
            pet = player.pet,
            isMember = player.membership != Membership.NONE,
            attributes = Player.AttributeType.values().map {
                player.attributes[it]!!
            },
            health = player.health.current,
            maxHealth = player.health.max,
            rank = player.rank,
            nextRank = player.nextRank,
            isCurrentPlayerGuest = !player.isLoggedIn(),
            membership = player.membership,
            todayCompletedQuests = player.statistics.questCompletedCountForToday.toInt(),
            todayCompletedHabits = player.statistics.habitCompletedCountForToday.toInt(),
            thisMonthConvertedGems = player.statistics.gemConvertedCountForThisMonth.toInt()
        )
        return if (hasProfileDataLoaded(newState)) {
            newState.copy(
                type = PROFILE_DATA_LOADED
            )
        } else {
            newState.copy(type = LOADING)
        }
    }

    override fun defaultState() =
        ProfileViewState(
            type = LOADING,
            avatar = Avatar.AVATAR_00,
            displayName = null,
            username = null,
            bio = null,
            titleIndex = -1,
            createdAgo = -1,
            pet = null,
            level = -1,
            levelXpProgress = -1,
            levelXpMaxProgress = -1,
            coins = -1,
            gems = -1,
            health = -1,
            maxHealth = -1,
            attributes = null,
            dailyChallengeStreak = -1,
            last7DaysAverageProductiveDuration = null,
            unlockedAchievements = emptyList(),
            challenges = null,
            followers = null,
            following = null,
            posts = null,
            currentPostId = null,
            friendId = null,
            isMember = null,
            rank = null,
            nextRank = null,
            isFollowing = null,
            isFollower = null,
            isCurrentPlayerGuest = null,
            membership = null,
            todayCompletedQuests = null,
            todayCompletedHabits = null,
            thisMonthConvertedGems = null
        )

    override val stateKey = reducerKey

    companion object {
        const val PROFILE_KEY = "ProfileViewState"
        const val FRIEND_KEY = "FriendViewState"
    }
}

data class ProfileViewState(
    val type: StateType,
    val avatar: Avatar,
    val displayName: String?,
    val username: String?,
    val titleIndex: Int,
    val createdAgo: Long,
    val bio: String?,
    val pet: Pet?,
    val level: Int,
    val levelXpProgress: Int,
    val levelXpMaxProgress: Int,
    val rank: Player.Rank?,
    val nextRank: Player.Rank?,
    val coins: Int,
    val gems: Int,
    val health: Int,
    val maxHealth: Int,
    val attributes: List<Player.Attribute>?,
    val dailyChallengeStreak: Int,
    val last7DaysAverageProductiveDuration: Duration<Minute>?,
    val unlockedAchievements: List<CreateAchievementItemsUseCase.AchievementItem>,
    val challenges: List<Challenge>?,
    val followers: List<Friend>?,
    val following: List<Friend>?,
    val posts: List<Post>?,
    val currentPostId: String?,
    val friendId: String?,
    val isMember: Boolean?,
    val isFollowing: Boolean?,
    val isFollower: Boolean?,
    val isCurrentPlayerGuest: Boolean?,
    val membership: Membership?,
    val todayCompletedQuests: Int?,
    val todayCompletedHabits: Int?,
    val thisMonthConvertedGems: Int?
) : BaseViewState() {

    enum class StateType {
        LOADING,
        PROFILE_DATA_LOADED,
        CHALLENGE_LIST_DATA_CHANGED,
        CHALLENGE_LIST_LOADING,
        FOLLOWER_LIST_CHANGED,
        FOLLOWING_LIST_CHANGED,
        SHOW_REQUIRE_LOGIN,
        POSTS_CHANGED,
        REACTION_POPUP_SHOWN,
        PROFILE_INFO_LOADED,
        NO_INTERNET_CONNECTION,
        FOLLOWING_STATUS_CHANGED
    }
}