package io.ipoli.android.challenge.usecase

import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.challenge.preset.PresetChallenge
import io.ipoli.android.common.UseCase
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.Reminder
import io.ipoli.android.quest.job.ReminderScheduler
import io.ipoli.android.quest.subquest.SubQuest
import io.ipoli.android.tag.Tag
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate
import java.util.*

class CreateChallengeFromPresetUseCase(
    private val saveChallengeUseCase: SaveChallengeUseCase,
    private val reminderScheduler: ReminderScheduler
) :
    UseCase<CreateChallengeFromPresetUseCase.Params, Challenge> {

    override fun execute(parameters: Params): Challenge {
        val pc = parameters.preset
        val start = parameters.startDate
        val schedule = parameters.schedule

        if (parameters.playerPhysicalCharacteristics != null || pc.config.nutritionMacros != null) {
            require(parameters.playerPhysicalCharacteristics != null)
            require(pc.config.nutritionMacros != null)
        }

        val quests = schedule.quests.map {
            Quest(
                name = it.name,
                icon = it.icon,
                color = it.color,
                tags = parameters.tags,
                scheduledDate = start.plusDays(it.day - 1L),
                startTime = parameters.questsStartTime,
                subQuests = it.subQuests.map { sq ->
                    SubQuest(sq, null, null)
                },
                duration = it.duration.intValue,
                note = it.note,
                reminders = listOf(Reminder.Relative("", 0))
            )
        }

        val habits = schedule.habits
            .filter { it.isSelected }
            .map {
                Habit(
                    name = it.name,
                    icon = it.icon,
                    color = it.color,
                    tags = parameters.tags,
                    isGood = it.isGood,
                    timesADay = it.timesADay,
                    streak = Habit.Streak(0, 0),
                    days = DayOfWeek.values().toSet(),
                    preferenceHistory = Habit.PreferenceHistory(
                        days = sortedMapOf(LocalDate.now() to DayOfWeek.values().toSet()),
                        timesADay = sortedMapOf(LocalDate.now() to it.timesADay)
                    )
                )
            }

        val trackedValues = pc.trackedValues.toMutableList()

        pc.config.nutritionMacros?.let {
            val c = parameters.playerPhysicalCharacteristics!!
            val weightInKg =
                if (c.units == PhysicalCharacteristics.Units.METRIC) c.weight.toDouble() else c.weight * 0.453592

            val nutritionDetails =
                if (c.gender == PhysicalCharacteristics.Gender.FEMALE) it.female else it.male

            trackedValues.add(
                Challenge.TrackedValue.Target(
                    id = UUID.randomUUID().toString(),
                    name = "weight",
                    units = if (c.units == PhysicalCharacteristics.Units.METRIC) "kgs" else "lbs",
                    startValue = c.weight.toDouble(),
                    targetValue = c.targetWeight.toDouble(),
                    currentValue = 0.0,
                    remainingValue = 0.0,
                    history = emptyMap<LocalDate, Challenge.TrackedValue.Log>().toSortedMap()
                )
            )

            trackedValues.add(
                createAverageTrackedValue(
                    name = "calories",
                    units = "kCal",
                    targetValue = weightInKg * nutritionDetails.caloriesPerKg
                )
            )

            trackedValues.add(
                createAverageTrackedValue(
                    name = "protein",
                    units = "grams",
                    targetValue = weightInKg * nutritionDetails.proteinPerKg
                )
            )

            trackedValues.add(
                createAverageTrackedValue(
                    name = "carbohydrates",
                    units = "grams",
                    targetValue = weightInKg * nutritionDetails.carbohydratesPerKg
                )
            )

            trackedValues.add(
                createAverageTrackedValue(
                    name = "fat",
                    units = "grams",
                    targetValue = weightInKg * nutritionDetails.fatPerKg
                )
            )
        }

        val challenge = saveChallengeUseCase.execute(
            SaveChallengeUseCase.Params.WithNewQuestsAndHabits(
                name = pc.name,
                tags = parameters.tags,
                color = pc.color,
                icon = pc.icon,
                difficulty = pc.difficulty,
                trackedValues = trackedValues,
                motivations = pc.expectedResults,
                end = start.plusDays(pc.duration.longValue - 1),
                note = pc.note,
                presetChallengeId = pc.id,
                quests = quests,
                habits = habits
            )
        )
        reminderScheduler.schedule()
        return challenge
    }

    private fun createAverageTrackedValue(
        name: String,
        units: String,
        targetValue: Double
    ) =
        Challenge.TrackedValue.Average(
            id = UUID.randomUUID().toString(),
            name = name,
            units = units,
            targetValue = Math.round(targetValue).toDouble(),
            lowerBound = Math.round(targetValue - (targetValue * 0.05)).toDouble(),
            upperBound = Math.round(targetValue + (targetValue * 0.05)).toDouble(),
            history = emptyMap<LocalDate, Challenge.TrackedValue.Log>().toSortedMap()
        )

    data class Params(
        val preset: PresetChallenge,
        val schedule: PresetChallenge.Schedule,
        val tags: List<Tag> = emptyList(),
        val startDate: LocalDate = LocalDate.now(),
        val questsStartTime: Time? = null,
        val playerPhysicalCharacteristics: PhysicalCharacteristics? = null
    )

    data class PhysicalCharacteristics(
        val units: Units,
        val gender: Gender,
        val weight: Int,
        val targetWeight: Int
    ) {
        enum class Units { METRIC, IMPERIAL }
        enum class Gender { FEMALE, MALE }
    }
}