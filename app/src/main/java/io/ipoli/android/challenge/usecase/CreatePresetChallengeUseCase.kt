package io.ipoli.android.challenge.usecase

import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.challenge.preset.PresetChallenge
import io.ipoli.android.challenge.preset.persistence.PresetChallengeRepository
import io.ipoli.android.common.UseCase
import io.ipoli.android.common.datetime.Day
import io.ipoli.android.common.datetime.Duration
import io.ipoli.android.common.datetime.minutes
import io.ipoli.android.friends.feed.data.Post
import io.ipoli.android.player.data.Avatar
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import org.threeten.bp.LocalDate
import java.util.*

class CreatePresetChallengeUseCase(
    private val presetChallengeRepository: PresetChallengeRepository
) : UseCase<CreatePresetChallengeUseCase.Params, Unit> {

    override fun execute(parameters: Params) {

        val (rep, nonRep) = parameters.quests.partition { it.second }

        val rqs = rep.map { it.first }
        val qs = nonRep.map { it.first }
            .filter { it.day <= parameters.duration.intValue }
            .toMutableList()

        rqs.forEach { rq ->
            (1..parameters.duration.intValue).forEach { day ->
                qs.add(
                    rq.copy(
                        name = rq.name + " (Day $day)",
                        day = day
                    )
                )
            }
        }

        val totalBusyness = Math.max(qs.sumBy { it.duration.intValue }, 10)

        val pc = PresetChallenge(
            id = UUID.randomUUID().toString(),
            name = parameters.name,
            shortDescription = parameters.shortDescription,
            description = parameters.description,
            color = parameters.color,
            icon = parameters.icon,
            category = parameters.category,
            duration = parameters.duration,
            difficulty = parameters.difficulty,
            expectedResults = parameters.expectedResults,
            requirements = parameters.requirements,
            gemPrice = 0,
            level = null,
            note = parameters.description,
            config = PresetChallenge.Config(),
            status = Post.Status.PENDING,
            imageUrl = "",
            author = PresetChallenge.Author(
                id = parameters.playerId,
                displayName = "",
                username = "",
                avatar = Avatar.AVATAR_00,
                level = 0
            ),
            participantCount = 0,
            schedule = PresetChallenge.Schedule(
                quests = qs,
                habits = parameters.habits
            ),
            busynessPerWeek = (totalBusyness / 7).minutes,
            trackedValues = listOf(
                Challenge.TrackedValue.Progress(
                    id = UUID.randomUUID().toString(),
                    history = emptyMap<LocalDate, Challenge.TrackedValue.Log>().toSortedMap()
                )
            )
        )

        presetChallengeRepository.save(pc)
    }

    data class Params(
        val name: String,
        val playerId: String,
        val shortDescription: String,
        val description: String,
        val color: Color,
        val icon: Icon,
        val category: PresetChallenge.Category,
        val duration: Duration<Day>,
        val difficulty: Challenge.Difficulty,
        val expectedResults: List<String>,
        val requirements: List<String>,
        val quests: List<Pair<PresetChallenge.Quest, Boolean>>,
        val habits: List<PresetChallenge.Habit>
    )
}