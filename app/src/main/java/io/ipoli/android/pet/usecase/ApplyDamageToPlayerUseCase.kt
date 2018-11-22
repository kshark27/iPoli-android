package io.ipoli.android.pet.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.dailychallenge.data.persistence.DailyChallengeRepository
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.habit.persistence.HabitRepository
import io.ipoli.android.habit.usecase.CalculateHabitStreakUseCase
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.persistence.PlayerRepository
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.data.persistence.QuestRepository
import org.threeten.bp.Duration
import org.threeten.bp.LocalDate
import java.util.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 11/30/17.
 */
class ApplyDamageToPlayerUseCase(
    private val questRepository: QuestRepository,
    private val playerRepository: PlayerRepository,
    private val habitRepository: HabitRepository,
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

        val resetDayTime = player.preferences.resetDayTime

        val today = parameters.today
        val yesterday = today.minusDays(1)

        val yesterdayQuests = questRepository.findScheduledAt(yesterday)

        val quests = mutableListOf<Quest>()
        val habits = mutableListOf<Habit>()

        val yesterdayStart = yesterday.atTime(resetDayTime.toLocalTime())
        val todayStart = today.atTime(0, 0)
        val todayEnd = today.atTime(resetDayTime.toLocalTime())

        val primaryDate = when {
            resetDayTime == Time.atHours(0) -> yesterday
            Duration.between(yesterdayStart, todayStart) >
                Duration.between(todayStart, todayEnd) -> yesterday
            else -> today
        }

        if (resetDayTime == Time.atHours(0)) {
            quests.addAll(yesterdayQuests)
        } else {

            val todayQuests = questRepository.findScheduledAt(today)
            val (todayScheduled, todayUnscheduled) = todayQuests.partition { it.isScheduled }
            val (yesterdayScheduled, yesterdayUnscheduled) = yesterdayQuests.partition { it.isScheduled }

            if (primaryDate == yesterday) {
                quests.addAll(yesterdayUnscheduled)
            } else {
                quests.addAll(todayUnscheduled)
            }

            quests.addAll(yesterdayScheduled.filter { it.startTime!! >= resetDayTime })
            quests.addAll(todayScheduled.filter { it.startTime!! < resetDayTime })
        }

        habits.addAll(
            habitRepository
                .findAllNotRemoved()
                .filter {
                    it.isGood && it.shouldBeDoneOn(primaryDate)
                }
        )

        val dcQuestIds =
            dailyChallengeRepository.findForDate(primaryDate)?.questIds?.toSet() ?: emptySet()
        val isPlanDay = player.preferences.planDays.contains(primaryDate.dayOfWeek)

        val questDamage = quests.sumBy {
            if (it.isCompleted) {
                0
            } else {
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
        }

        val habitDamage = habits.sumBy {
            if (it.isCompletedForDate(primaryDate)) {
                0
            } else {
                var dmg = HABIT_BASE_DAMAGE

                if (isPlanDay)
                    dmg += PRODUCTIVE_DAY_DAMAGE

                if (it.isFromChallenge)
                    dmg += CHALLENGE_DAMAGE

                val streak = calculateHabitStreakUseCase.execute(
                    CalculateHabitStreakUseCase.Params(
                        it,
                        primaryDate
                    )
                )

                dmg += if (streak.current > 66)
                    HABIT_HIGH_STREAK_DAMAGE
                else
                    HABIT_LOW_STREAK_DAMAGE
                dmg
            }
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

    data class Params(val today: LocalDate = LocalDate.now())

    data class Result(
        val player: Player,
        val questDamage: Int,
        val habitDamage: Int,
        val totalDamage: Int
    )
}