package io.ipoli.android.habit.usecase

import io.ipoli.android.common.ErrorLogger
import io.ipoli.android.common.UseCase
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.habit.persistence.HabitRepository
import io.ipoli.android.player.usecase.RemoveRewardFromPlayerUseCase
import org.threeten.bp.LocalDate

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 6/17/18.
 */
class UndoCompleteHabitUseCase(
    private val habitRepository: HabitRepository,
    private val removeRewardFromPlayerUseCase: RemoveRewardFromPlayerUseCase
) : UseCase<UndoCompleteHabitUseCase.Params, Habit> {

    override fun execute(parameters: Params): Habit {
        val habit = habitRepository.findById(parameters.habitId)
        requireNotNull(habit)

        val date = parameters.date

        val history = habit!!.history.toMutableMap()

        if (habit.completedCountForDate(date) == 0) {
            return habit
        }

        val wasCompleted = habit.isCompletedForDate(date)

        history[date] = history[date]!!.undoLastComplete()

        if (wasCompleted && habit.isGood) {

            if (history[date]!!.reward == null) {
                ErrorLogger.log(IllegalStateException("Illegal undo habit: ${habit.history}, $date"))
            } else {
                removeRewardFromPlayerUseCase.execute(
                    RemoveRewardFromPlayerUseCase.Params(
                        rewardType = RemoveRewardFromPlayerUseCase.Params.RewardType.GOOD_HABIT,
                        reward = history[date]!!.reward!!
                    )
                )
            }
        }

        return habitRepository.save(habit.copy(history = history))
    }

    data class Params(val habitId: String, val date: LocalDate = LocalDate.now())
}