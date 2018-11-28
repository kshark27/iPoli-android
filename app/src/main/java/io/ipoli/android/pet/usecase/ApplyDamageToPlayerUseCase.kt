package io.ipoli.android.pet.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.dailychallenge.data.persistence.DailyChallengeRepository
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.habit.usecase.CalculateHabitStreakUseCase
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.persistence.PlayerRepository
import io.ipoli.android.quest.Quest
import org.threeten.bp.LocalDate
import java.util.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 11/30/17.
 */
class ApplyDamageToPlayerUseCase(
    private val playerRepository: PlayerRepository,
    private val dailyChallengeRepository: DailyChallengeRepository,
    private val calculateHabitStreakUseCase: CalculateHabitStreakUseCase,
    private val randomSeed: Long = System.currentTimeMillis()
) : UseCase<ApplyDamageToPlayerUseCase.Params, ApplyDamageToPlayerUseCase.Result> {

    override fun execute(parameters: Params): ApplyDamageToPlayerUseCase.Result {
        val p = playerRepository.find()
        requireNotNull(p)
        val player = p!!

        if (player.isDead) {
            return Result(player, 0, 0, 0)
        }

        val dcQuestIds =
            dailyChallengeRepository.findForDate(parameters.date)?.questIds?.toSet() ?: emptySet()
        val isPlanDay = player.preferences.planDays.contains(parameters.date.dayOfWeek)

        val questDamage = parameters.quests.sumBy {

            var dmg = QUEST_BASE_DAMAGE

            if (isPlanDay)
                dmg += PRODUCTIVE_DAY_DAMAGE

            if (it.isFromRepeatingQuest)
                dmg += REPEATING_QUEST_DAMAGE

            if (it.isFromChallenge)
                dmg += CHALLENGE_DAMAGE

            if (dcQuestIds.contains(it.id))
                dmg += DAILY_CHALLENGE_DAMAGE
            dmg
        }

        val habitDamage = parameters.habits
            .filter { !it.isCompletedForDate(parameters.date) }
            .sumBy {
                var dmg = HABIT_BASE_DAMAGE

                if (isPlanDay)
                    dmg += PRODUCTIVE_DAY_DAMAGE

                if (it.isFromChallenge)
                    dmg += CHALLENGE_DAMAGE

                val streak = calculateHabitStreakUseCase.execute(
                    CalculateHabitStreakUseCase.Params(
                        it,
                        parameters.date
                    )
                )

                dmg += if (streak.current > 66)
                    HABIT_HIGH_STREAK_DAMAGE
                else
                    HABIT_LOW_STREAK_DAMAGE
                dmg
            }

        val totalDamage = questDamage + habitDamage

        val r = createRandom()

        val newPlayer = savePlayer(
            player = player,
            healthPenalty = totalDamage,
            petHealthPenalty = totalDamage +
                RANDOM_PET_DAMAGE[r.nextInt(RANDOM_PET_DAMAGE.size)],
            petMoodPenalty = totalDamage +
                RANDOM_PET_DAMAGE[r.nextInt(RANDOM_PET_DAMAGE.size)]
        )

        return ApplyDamageToPlayerUseCase.Result(newPlayer, questDamage, habitDamage, totalDamage)
    }

    private fun savePlayer(
        player: Player,
        healthPenalty: Int,
        petHealthPenalty: Int,
        petMoodPenalty: Int
    ) =
        playerRepository.save(
            player
                .removeHealthPoints(healthPenalty)
                .copy(
                    pet = if (player.pet.isDead) player.pet
                    else player.pet.removeHealthAndMoodPoints(petHealthPenalty, petMoodPenalty)
                )
        )

    companion object {

        const val QUEST_BASE_DAMAGE = 3
        const val HABIT_BASE_DAMAGE = 1

        const val PRODUCTIVE_DAY_DAMAGE = 2

        const val CHALLENGE_DAMAGE = 2
        const val REPEATING_QUEST_DAMAGE = 2
        const val DAILY_CHALLENGE_DAMAGE = 2
        const val HABIT_HIGH_STREAK_DAMAGE = 1
        const val HABIT_LOW_STREAK_DAMAGE = 3

        val RANDOM_PET_DAMAGE = intArrayOf(1, 2, 3, 4, 5)
    }

    private fun createRandom() = Random(randomSeed)

    data class Params(val quests: List<Quest>, val habits: List<Habit>, val date: LocalDate)

    data class Result(
        val player: Player,
        val questDamage: Int,
        val habitDamage: Int,
        val totalDamage: Int
    )
}