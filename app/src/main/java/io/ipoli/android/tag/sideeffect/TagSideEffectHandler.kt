package io.ipoli.android.tag.sideeffect

import io.ipoli.android.Constants
import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.redux.Action
import io.ipoli.android.quest.Quest
import io.ipoli.android.tag.edit.EditTagAction
import io.ipoli.android.tag.edit.EditTagViewState
import io.ipoli.android.tag.list.TagListAction
import io.ipoli.android.tag.show.TagAction
import io.ipoli.android.tag.usecase.*
import kotlinx.coroutines.experimental.channels.Channel
import org.threeten.bp.LocalDate
import space.traversal.kapsule.required

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 4/5/18.
 */
object TagSideEffectHandler : AppSideEffectHandler() {
    private val saveTagUseCase by required { saveTagUseCase }
    private val questRepository by required { questRepository }
    private val undoCompletedQuestUseCase by required { undoCompletedQuestUseCase }
    private val createTagItemsUseCase by required { createTagItemsUseCase }
    private val favoriteTagUseCase by required { favoriteTagUseCase }
    private val unfavoriteTagUseCase by required { unfavoriteTagUseCase }
    private val removeTagUseCase by required { removeTagUseCase }

    private var tagQuestsChannel: Channel<List<Quest>>? = null

    override suspend fun doExecute(action: Action, state: AppState) {
        when (action) {

            is TagAction.Load ->
                listenForChanges(
                    oldChannel = tagQuestsChannel,
                    channelCreator = {
                        tagQuestsChannel = questRepository.listenByTag(action.tagId)
                        tagQuestsChannel!!
                    },
                    onResult = { qs ->
                        val items =
                            createTagItemsUseCase.execute(
                                CreateTagItemsUseCase.Params(
                                    quests = qs,
                                    currentDate = LocalDate.now()
                                )
                            )
                        dispatch(DataLoadedAction.TagItemsChanged(action.tagId, items))
                    }
                )

            EditTagAction.Save -> {
                val subState = state.stateFor(EditTagViewState::class.java)
                saveTagUseCase.execute(
                    SaveTagUseCase.Params(
                        id = subState.id,
                        name = subState.name,
                        icon = subState.icon,
                        color = subState.color,
                        isFavorite = subState.isFavorite
                    )
                )
            }

            is TagListAction.Favorite ->
                favorite(state, action.tag.id)

            is TagAction.Favorite ->
                favorite(state, action.tagId)

            is TagAction.Unfavorite ->
                unfavorite(action.tagId)

            is TagListAction.Unfavorite ->
                unfavorite(action.tag.id)

            is TagAction.Remove ->
                removeTagUseCase.execute(RemoveTagUseCase.Params(action.tagId))

            is TagAction.UndoCompleteQuest ->
                undoCompletedQuestUseCase.execute(
                    action.questId
                )
        }
    }

    private fun unfavorite(tagId: String) {
        unfavoriteTagUseCase.execute(UnfavoriteTagUseCase.Params.WithTagId(tagId))
    }

    private fun favorite(
        state: AppState,
        tagId: String
    ) {
        val tags = state.dataState.tags
        if (tags.filter { it.isFavorite }.size >= Constants.MAX_FAVORITE_TAGS) {
            dispatch(TagAction.TagCountLimitReached)
        } else {
            favoriteTagUseCase.execute(FavoriteTagUseCase.Params.WithTagId(tagId))
        }
    }

    override fun canHandle(action: Action) = action is TagAction
        || action === EditTagAction.Save
        || action is TagListAction.Favorite
        || action is TagListAction.Unfavorite
}