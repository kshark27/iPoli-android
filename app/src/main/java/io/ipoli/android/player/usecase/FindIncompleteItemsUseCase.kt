package io.ipoli.android.player.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.habit.persistence.HabitRepository
import org.threeten.bp.LocalDate

class FindIncompleteItemsUseCase(
    private val habitRepository: HabitRepository
) : UseCase<FindIncompleteItemsUseCase.Params, FindIncompleteItemsUseCase.Result> {

    override fun execute(parameters: Params): Result {

        val yesterday = parameters.today.minusDays(1)

        val habits = habitRepository
            .findAllNotRemoved()
            .filter {
                it.isGood &&
                    it.shouldBeDoneOn(yesterday)
            }

        return Result(habits = habits, date = yesterday)
    }

    data class Params(val today: LocalDate = LocalDate.now())

    data class Result(
        val habits: List<Habit>,
        val date: LocalDate
    )
}