package io.ipoli.android.pet.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.player.attribute.AttributeRank
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.persistence.PlayerRepository
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.data.persistence.QuestRepository
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import java.util.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 11/30/17.
 */
class LowerPlayerStatsUseCase(
    private val questRepository: QuestRepository,
    private val playerRepository: PlayerRepository,
    private val randomSeed: Long = System.currentTimeMillis()
) : UseCase<LowerPlayerStatsUseCase.Params, Player> {

    private lateinit var player: Player

    override fun execute(parameters: Params): Player {
        val p = playerRepository.find()
        requireNotNull(p)
        player = p!!

        if (player.isDead) {
            return player
        }

        val dateTime = parameters.dateTime.minusDays(1)

        val isPlanDay = player.preferences.planDays.contains(dateTime.toLocalDate().dayOfWeek)

        val highProductiveHours =
            if (isPlanDay) PLAN_DAY_HIGH_PRODUCTIVE_HOURS else NON_PLAN_DAY_HIGH_PRODUCTIVE_HOURS
        val mediumProductiveHours =
            if (isPlanDay) PLAN_DAY_MEDIUM_PRODUCTIVE_HOURS else NON_PLAN_DAY_MEDIUM_PRODUCTIVE_HOURS
        val lowProductiveHours =
            if (isPlanDay) PLAN_DAY_LOW_PRODUCTIVE_HOURS else NON_PLAN_DAY_LOW_PRODUCTIVE_HOURS

        val petRHP = PET_HEALTH_POINTS_PENALTIES[createRandom().nextInt(
            PET_HEALTH_POINTS_PENALTIES.size
        )]

        val petRMP = PET_MOOD_POINTS_PENALTIES[createRandom().nextInt(
            PET_MOOD_POINTS_PENALTIES.size
        )]

        val playerRHP = PLAYER_HEALTH_PENALTIES[createRandom().nextInt(
            PLAYER_HEALTH_PENALTIES.size
        )]

        val (start, end) = player.datesSpan(dateTime)

        val qs = end?.let {
            questRepository.findCompletedForDate(start) + questRepository.findCompletedForDate(it)
        } ?: questRepository.findCompletedForDate(start)

        val totalDuration = findQuestsDurationInInterval(
            start = start,
            end = end,
            resetDayTime = player.preferences.resetDayTime,
            quests = qs
        )

        if (totalDuration >= highProductiveHours * Time.MINUTES_IN_AN_HOUR) {
            return savePlayer(
                healthPenalty = addAdditionalDamage(playerRHP),
                petHealthPenalty = petRHP,
                petMoodPenalty = petRMP
            )
        }

        if (totalDuration >= mediumProductiveHours * Time.MINUTES_IN_AN_HOUR) {
            return savePlayer(
                healthPenalty = addAdditionalDamage(playerRHP + PLAYER_LOW_PENALTY),
                petHealthPenalty = PET_LOW_PENALTY + petRHP,
                petMoodPenalty = PET_LOW_PENALTY + petRMP
            )
        }

        if (totalDuration >= lowProductiveHours * Time.MINUTES_IN_AN_HOUR) {
            return savePlayer(
                healthPenalty = addAdditionalDamage(playerRHP + PLAYER_MEDIUM_PENALTY),
                petHealthPenalty = PET_MEDIUM_PENALTY + petRHP,
                petMoodPenalty = PET_MEDIUM_PENALTY + petRMP
            )
        }

        if (totalDuration > 0) {
            return savePlayer(
                healthPenalty = addAdditionalDamage(playerRHP + PLAYER_HIGH_PENALTY),
                petHealthPenalty = PET_HIGH_PENALTY + petRHP,
                petMoodPenalty = PET_HIGH_PENALTY + petRMP
            )
        }

        return savePlayer(
            healthPenalty = addAdditionalDamage(playerRHP + PLAYER_MAX_PENALTY),
            petHealthPenalty = PET_MAX_PENALTY + petRHP,
            petMoodPenalty = PET_MAX_PENALTY + petRMP
        )
    }

    private fun playerDamage(): Int {
        return when (AttributeRank.of(
            player.attributeLevel(Player.AttributeType.STRENGTH),
            player.rank
        )) {
            Player.Rank.NOVICE -> 0
            Player.Rank.APPRENTICE -> 10
            else -> 30
        }
    }

    private fun savePlayer(healthPenalty: Int, petHealthPenalty: Int, petMoodPenalty: Int) =
        playerRepository.save(
            player
                .removeHealthPoints(healthPenalty)
                .copy(
                    pet = if (player.pet.isDead) player.pet
                    else player.pet.removeHealthAndMoodPoints(petHealthPenalty, petMoodPenalty)
                )
        )

    companion object {
        val PET_HEALTH_POINTS_PENALTIES = intArrayOf(4, 5, 6, 7, 8, 9)
        val PET_MOOD_POINTS_PENALTIES = intArrayOf(4, 5, 6, 7, 8, 9)

        val PLAYER_HEALTH_PENALTIES = intArrayOf(3, 4, 5)

        const val PET_MAX_PENALTY = 30
        const val PET_HIGH_PENALTY = 25
        const val PET_MEDIUM_PENALTY = 15
        const val PET_LOW_PENALTY = 4

        const val PLAYER_MAX_PENALTY = 22
        const val PLAYER_HIGH_PENALTY = 16
        const val PLAYER_MEDIUM_PENALTY = 5
        const val PLAYER_LOW_PENALTY = 2

        const val PLAN_DAY_HIGH_PRODUCTIVE_HOURS = 10
        const val PLAN_DAY_MEDIUM_PRODUCTIVE_HOURS = 6
        const val PLAN_DAY_LOW_PRODUCTIVE_HOURS = 3

        const val NON_PLAN_DAY_HIGH_PRODUCTIVE_HOURS = 5
        const val NON_PLAN_DAY_MEDIUM_PRODUCTIVE_HOURS = 3
        const val NON_PLAN_DAY_LOW_PRODUCTIVE_HOURS = 1


        fun findQuestsDurationInInterval(
            start: LocalDate,
            end: LocalDate?,
            resetDayTime: Time,
            quests: List<Quest>
        ) =
            quests.filter {
                when {
                    it.completedAtDate!! == start -> it.startTime != null && it.endTime!! >= resetDayTime
                    end != null && it.completedAtDate == end -> it.startTime != null && it.startTime < resetDayTime
                    else -> false
                }
            }.map {

                if (it.completedAtDate!! == start) {
                    val startMinute =
                        Math.max(resetDayTime.toMinuteOfDay(), it.startTime!!.toMinuteOfDay())
                    val endMinute = it.endTime!!.toMinuteOfDay()

                    if (endMinute < startMinute)
                        Time.MINUTES_IN_A_DAY - startMinute + endMinute
                    else
                        endMinute - startMinute
                } else {
                    val startMinute = it.startTime!!.toMinuteOfDay()
                    val endMinute =
                        Math.min(resetDayTime.toMinuteOfDay(), it.endTime!!.toMinuteOfDay())
                    endMinute - startMinute
                }

            }.fold(0) { acc, dur -> acc + dur }
    }

    private fun addAdditionalDamage(penalty: Int): Int {
        val damageCoef = (100 + playerDamage().toFloat()) / 100
        return (penalty * damageCoef).toInt()
    }

    private fun createRandom() = Random(randomSeed)

    data class Params(val dateTime: LocalDateTime = LocalDateTime.now())
}