package io.ipoli.android.habit.sideeffect

import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.datetime.DateUtils
import io.ipoli.android.common.datetime.daysBetween
import io.ipoli.android.common.redux.Action
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.habit.edit.EditHabitAction
import io.ipoli.android.habit.edit.EditHabitViewState
import io.ipoli.android.habit.list.HabitListAction
import io.ipoli.android.habit.show.HabitAction
import io.ipoli.android.habit.show.HabitViewState
import io.ipoli.android.habit.usecase.*
import io.ipoli.android.planday.review.ReviewDayAction
import io.ipoli.android.quest.schedule.today.TodayAction
import org.threeten.bp.LocalDate
import org.threeten.bp.temporal.TemporalAdjusters
import space.traversal.kapsule.required

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 6/16/18.
 */
object HabitSideEffectHandler : AppSideEffectHandler() {

    private val saveHabitUseCase by required { saveHabitUseCase }
    private val completeHabitUseCase by required { completeHabitUseCase }
    private val undoCompleteHabitUseCase by required { undoCompleteHabitUseCase }
    private val removeHabitUseCase by required { removeHabitUseCase }
    private val createHabitItemsUseCase by required { createHabitItemsUseCase }
    private val createHabitHistoryItemUseCase by required { createHabitHistoryItemsUseCase }
    private val habitRepository by required { habitRepository }

    override suspend fun doExecute(action: Action, state: AppState) {

        when (action) {

            EditHabitAction.Save -> {
                val s = state.stateFor(EditHabitViewState::class.java)
                saveHabitUseCase.execute(
                    SaveHabitUseCase.Params(
                        id = s.id,
                        name = s.name,
                        color = s.color,
                        icon = s.icon,
                        tags = s.habitTags,
                        days = s.days,
                        timesADay = s.timesADay,
                        isGood = s.isGood,
                        challengeId = s.challenge?.id,
                        reminders = s.reminders,
                        note = s.note
                    )
                )
            }

            is HabitAction.Remove ->
                removeHabitUseCase.execute(RemoveHabitUseCase.Params(action.habitId))

            is TodayAction.Load ->
                loadHabits()

            is HabitListAction.Load ->
                loadHabits()

            is DataLoadedAction.HabitsChanged -> {
                dispatchNewHabitItems(action.habits)
                if (state.hasState(HabitViewState::class.java)) {
                    val habitState = state.stateFor(HabitViewState::class.java)
                    val habit = action.habits.firstOrNull { it.id == habitState.id }
                    if (habit != null) {
                        dispatchHabitHistoryItems(habitState.currentDate, habit)
                    }
                }
            }

            is HabitListAction.CompleteHabit ->
                completeHabitUseCase.execute(CompleteHabitUseCase.Params(habitId = action.habitId))

            is TodayAction.CompleteHabit ->
                completeHabitUseCase.execute(CompleteHabitUseCase.Params(habitId = action.habitId))

            is ReviewDayAction.CompleteHabit ->
                completeHabitUseCase.execute(
                    CompleteHabitUseCase.Params(
                        habitId = action.habitId,
                        date = LocalDate.now().minusDays(1)
                    )
                )

            is TodayAction.UndoCompleteHabit ->
                undoCompleteHabitUseCase.execute(UndoCompleteHabitUseCase.Params(habitId = action.habitId))

            is HabitListAction.UndoCompleteHabit ->
                undoCompleteHabitUseCase.execute(UndoCompleteHabitUseCase.Params(habitId = action.habitId))

            is HabitAction.Load -> {
                val habitState = state.stateFor(HabitViewState::class.java)
                dispatchHabitHistoryItems(
                    habitState.currentDate,
                    habitRepository.findById(action.habitId)!!
                )
            }

            is HabitAction.ToggleHistory -> {
                val date = action.date
                if (date > LocalDate.now()) return
                val habit = state.dataState.habits!!.first { it.id == action.habitId }
                if (habit.canCompleteMoreForDate(date))
                    completeHabitUseCase.execute(
                        CompleteHabitUseCase.Params(
                            habitId = action.habitId,
                            date = date
                        )
                    )
                else
                    undoCompleteHabitUseCase.execute(
                        UndoCompleteHabitUseCase.Params(habitId = action.habitId, date = date)
                    )

            }

            is HabitAction.ChangeMonth -> {
                val yearMonth = action.yearMonth
                val date = LocalDate.of(yearMonth.year, yearMonth.month, 1)
                dispatchHabitHistoryItems(
                    date,
                    state.dataState.habits!!.first { it.id == action.habitId })
            }

            else -> {
            }
        }
    }

    private fun dispatchHabitHistoryItems(
        currentDate: LocalDate,
        habit: Habit
    ) {
        val startDate = currentDate.with(TemporalAdjusters.firstDayOfMonth())
            .with(TemporalAdjusters.previousOrSame(DateUtils.firstDayOfWeek))

        val lastWeekEndDate = currentDate.with(TemporalAdjusters.lastDayOfMonth())
            .with(TemporalAdjusters.nextOrSame(DateUtils.lastDayOfWeek))

        val daysBetween = startDate.daysBetween(lastWeekEndDate).toInt()

        val endDate =
            if (daysBetween / 7 == 6) lastWeekEndDate else lastWeekEndDate.plusWeeks(1)

        dispatch(
            DataLoadedAction.HabitChanged(
                habit,
                currentDate,
                createHabitHistoryItemUseCase.execute(
                    CreateHabitHistoryItemsUseCase.Params(
                        habit = habit,
                        today = LocalDate.now(),
                        startDate = startDate,
                        endDate = endDate

                    )
                )
            )
        )
    }

    private fun loadHabits() {
        dispatchNewHabitItems(habitRepository.findAllNotRemoved())
    }

    private fun dispatchNewHabitItems(habits: List<Habit>) {
        val habitItems = createHabitItemsUseCase.execute(
            CreateHabitItemsUseCase.Params(
                habits
            )
        )
        dispatch(DataLoadedAction.HabitItemsChanged(habitItems))
    }

    override fun canHandle(action: Action) =
        action is EditHabitAction
            || action is HabitListAction || action is DataLoadedAction.HabitsChanged
            || action is TodayAction
            || action is HabitAction
            || action is ReviewDayAction.CompleteHabit
}