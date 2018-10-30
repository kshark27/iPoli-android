package io.ipoli.android.friends.feed.sideeffect

import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.challenge.entity.SharingPreference
import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.redux.Action
import io.ipoli.android.friends.ReactionHistoryDialogAction
import io.ipoli.android.friends.feed.FeedAction
import io.ipoli.android.friends.feed.FeedViewState
import io.ipoli.android.friends.feed.data.Post
import io.ipoli.android.friends.feed.picker.PostItemPickerAction
import io.ipoli.android.friends.usecase.CreateReactionHistoryItemsUseCase
import io.ipoli.android.friends.usecase.SavePostReactionUseCase
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.quest.Quest
import kotlinx.coroutines.experimental.channels.Channel
import org.threeten.bp.LocalDate
import space.traversal.kapsule.required


/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 7/16/18.
 */
object FeedSideEffectHandler : AppSideEffectHandler() {

    private val postRepository by required { postRepository }
    private val createReactionHistoryItemsUseCase by required { createReactionHistoryItemsUseCase }
    private val savePostReactionUseCase by required { savePostReactionUseCase }
    private val internetConnectionChecker by required { internetConnectionChecker }

    private var postsChangedChannel: Channel<List<Post>>? = null

    override suspend fun doExecute(action: Action, state: AppState) {
        when (action) {

            is FeedAction.Load -> {

                if (!internetConnectionChecker.isConnected()) {
                    dispatch(FeedAction.NoInternetConnection)
                    return
                }

                listenForChanges(
                    oldChannel = postsChangedChannel,
                    channelCreator = {
                        postsChangedChannel = postRepository.listenForAll(30)
                        postsChangedChannel!!
                    },
                    onResult = { posts ->
                        dispatch(DataLoadedAction.PostsChanged(posts))
                    }
                )
            }

            is FeedAction.Unload -> {
                postsChangedChannel?.close()
            }

            is FeedAction.React -> {
                val s = state.stateFor(FeedViewState::class.java)
                savePostReactionUseCase.execute(
                    SavePostReactionUseCase.Params(
                        postId = s.currentPostId!!,
                        reactionType = action.reactionType
                    )
                )
            }

            is ReactionHistoryDialogAction.Load -> {
                dispatch(
                    DataLoadedAction.ReactionHistoryItemsChanged(
                        createReactionHistoryItemsUseCase.execute(
                            CreateReactionHistoryItemsUseCase.Params(
                                action.reactions
                            )
                        )
                    )
                )
            }

            is PostItemPickerAction.Load -> {
                var resultQuests: List<Quest>? = null
                var resultHabits: List<Habit>? = null
                var resultChallenges: List<Challenge>? = null

                val quests = state.dataState.todayQuests
                if (quests != null) {
                    val qs = quests.filter { it.isCompleted }
                    val res = postRepository.hasPostedQuests(qs.map { it.id })
                    resultQuests = qs.filter { res[it.id] == false }
                }

                val habits = state.dataState.habits
                if (habits != null) {
                    val today = LocalDate.now()
                    val hs = habits.filter {
                        if (it.isGood) it.isCompletedForDate(today)
                        else !it.isCompletedForDate(today)
                    }
                    val res = postRepository.hasPostedHabits(hs.map { it.id })
                    resultHabits = hs.filter { res[it.id] == false }
                }


                val challenges = state.dataState.challenges
                if (challenges != null) {
                    resultChallenges =
                        challenges.filter {
                            !it.isCompleted &&
                                it.sharingPreference == SharingPreference.PRIVATE
                        }
                }

                dispatch(
                    DataLoadedAction.PostItemPickerItemsChanged(
                        resultQuests,
                        resultHabits,
                        resultChallenges
                    )
                )
            }

            is DataLoadedAction.TodayQuestsChanged -> {
                val quests = state.dataState.todayQuests!!.filter { it.isCompleted }
                val res = postRepository.hasPostedQuests(quests.map { it.id })
                dispatch(
                    DataLoadedAction.PostItemPickerItemsChanged(
                        quests.filter { res[it.id] == false },
                        null, null
                    )
                )
            }

            is DataLoadedAction.ChallengesChanged -> {
                val challenges =
                    state.dataState.challenges!!.filter {
                        !it.isCompleted &&
                            it.sharingPreference == SharingPreference.PRIVATE
                    }
                dispatch(
                    DataLoadedAction.PostItemPickerItemsChanged(
                        null,
                        null,
                        challenges
                    )
                )
            }

            is DataLoadedAction.HabitsChanged -> {
                val today = LocalDate.now()
                val habits = state.dataState.habits!!.filter {
                    if (it.isGood) it.isCompletedForDate(today)
                    else !it.isCompletedForDate(today)
                }
                val res = postRepository.hasPostedHabits(habits.map { it.id })
                dispatch(
                    DataLoadedAction.PostItemPickerItemsChanged(
                        null,
                        habits.filter { res[it.id] == false },
                        null
                    )
                )
            }
        }
    }

    override fun canHandle(action: Action) =
        action is PostItemPickerAction ||
            action is FeedAction ||
            action is ReactionHistoryDialogAction
}