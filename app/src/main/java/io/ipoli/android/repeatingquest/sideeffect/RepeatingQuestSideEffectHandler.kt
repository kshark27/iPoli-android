package io.ipoli.android.repeatingquest.sideeffect

import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.datetime.DateUtils
import io.ipoli.android.common.redux.Action
import io.ipoli.android.quest.Reminder
import io.ipoli.android.repeatingquest.add.EditRepeatingQuestAction
import io.ipoli.android.repeatingquest.add.EditRepeatingQuestViewState
import io.ipoli.android.repeatingquest.show.RepeatingQuestAction
import io.ipoli.android.repeatingquest.usecase.AddQuestToRepeatingQuestUseCase
import io.ipoli.android.repeatingquest.usecase.CreateRepeatingQuestHistoryUseCase
import io.ipoli.android.repeatingquest.usecase.RemoveRepeatingQuestUseCase
import io.ipoli.android.repeatingquest.usecase.SaveRepeatingQuestUseCase
import org.threeten.bp.LocalDate
import org.threeten.bp.temporal.TemporalAdjusters
import space.traversal.kapsule.required

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 03/03/2018.
 */
object RepeatingQuestSideEffectHandler : AppSideEffectHandler() {

    private val saveRepeatingQuestUseCase by required { saveRepeatingQuestUseCase }
    private val removeRepeatingQuestUseCase by required { removeRepeatingQuestUseCase }
    private val createRepeatingQuestHistoryUseCase by required { createRepeatingQuestHistoryUseCase }
    private val addQuestToRepeatingQuestUseCase by required { addQuestToRepeatingQuestUseCase }

    override suspend fun doExecute(action: Action, state: AppState) {
        when (action) {
            is RepeatingQuestAction.Load -> {
                val today = LocalDate.now()
                val history = createRepeatingQuestHistoryUseCase.execute(
                    CreateRepeatingQuestHistoryUseCase.Params(
                        action.repeatingQuestId,
                        today.minusWeeks(3).with(TemporalAdjusters.previousOrSame(DateUtils.firstDayOfWeek)),
                        today.plusWeeks(1).with(TemporalAdjusters.nextOrSame(DateUtils.lastDayOfWeek))
                    )
                )

                dispatch(
                    DataLoadedAction.RepeatingQuestHistoryChanged(
                        action.repeatingQuestId,
                        history
                    )
                )
            }

            is EditRepeatingQuestAction.SaveNew ->
                saveRepeatingQuest(state, action.newSubQuestNames)

            is EditRepeatingQuestAction.Save ->
                saveRepeatingQuest(state, action.newSubQuestNames)

            is RepeatingQuestAction.Remove ->
                removeRepeatingQuestUseCase.execute(RemoveRepeatingQuestUseCase.Params(action.repeatingQuestId))

            is RepeatingQuestAction.AddQuest ->
                addQuestToRepeatingQuestUseCase.execute(
                    AddQuestToRepeatingQuestUseCase.Params(
                        action.repeatingQuestId,
                        action.date,
                        action.time
                    )
                )
        }
    }

    private fun saveRepeatingQuest(
        state: AppState,
        subQuestNames: List<String>
    ) {
        val rqState = state.stateFor(EditRepeatingQuestViewState::class.java)

        val r = rqState.reminder
        val reminder = r?.let {
            Reminder.Relative(it.message, it.minutesFromStart)
        }
        val rqParams = SaveRepeatingQuestUseCase.Params(
            id = rqState.id,
            name = rqState.name,
            tags = rqState.questTags,
            subQuestNames = subQuestNames,
            color = rqState.color,
            icon = rqState.icon,
            startTime = rqState.startTime,
            duration = rqState.duration.intValue,
            reminders = reminder?.let { listOf(it) },
            repeatPattern = rqState.repeatPattern!!,
            challengeId = rqState.challenge?.id,
            note = rqState.note
        )
        saveRepeatingQuestUseCase.execute(rqParams)
    }

    enum class ValidationError {
        EMPTY_NAME
    }


    override fun canHandle(action: Action) =
        action is EditRepeatingQuestAction.Save
            || action is EditRepeatingQuestAction.SaveNew
            || action is RepeatingQuestAction.Remove
            || action is RepeatingQuestAction.Load
            || action is RepeatingQuestAction.AddQuest
}