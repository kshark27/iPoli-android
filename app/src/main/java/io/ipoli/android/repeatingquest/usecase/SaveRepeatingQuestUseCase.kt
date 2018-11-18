package io.ipoli.android.repeatingquest.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import io.ipoli.android.quest.Reminder
import io.ipoli.android.quest.RepeatingQuest
import io.ipoli.android.quest.data.persistence.QuestRepository
import io.ipoli.android.quest.job.ReminderScheduler
import io.ipoli.android.quest.subquest.SubQuest
import io.ipoli.android.repeatingquest.entity.RepeatPattern
import io.ipoli.android.repeatingquest.persistence.RepeatingQuestRepository
import io.ipoli.android.tag.Tag
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 2/23/18.
 */
class SaveRepeatingQuestUseCase(
    private val questRepository: QuestRepository,
    private val repeatingQuestRepository: RepeatingQuestRepository,
    private val saveQuestsForRepeatingQuestUseCase: SaveQuestsForRepeatingQuestUseCase,
    private val reminderScheduler: ReminderScheduler
) : UseCase<SaveRepeatingQuestUseCase.Params, RepeatingQuest> {

    override fun execute(parameters: Params): RepeatingQuest {

        require(parameters.name.isNotBlank())

        val repeatingQuest = if (parameters.id.isEmpty()) {

            RepeatingQuest(
                name = parameters.name.trim(),
                icon = parameters.icon,
                color = parameters.color,
                startTime = parameters.startTime,
                duration = parameters.duration,
                reminders = parameters.reminders ?: emptyList(),
                repeatPattern = parameters.repeatPattern,
                subQuests = createSubQuests(parameters.subQuestNames),
                challengeId = parameters.challengeId,
                note = parameters.note,
                tags = parameters.tags
            )
        } else {

            val repeatingQuest = repeatingQuestRepository.findById(parameters.id)!!

            repeatingQuest.copy(
                name = parameters.name.trim(),
                icon = parameters.icon,
                color = parameters.color,
                startTime = parameters.startTime,
                duration = parameters.duration,
                reminders = parameters.reminders ?: repeatingQuest.reminders,
                repeatPattern = parameters.repeatPattern,
                subQuests = createSubQuests(parameters.subQuestNames),
                challengeId = parameters.challengeId,
                note = parameters.note,
                tags = parameters.tags
            )
        }

        val rq = if (parameters.id.isEmpty()) {
            // we should wait for updating the RepeatPattern and then save the Repeating Quest
            saveQuestsFor(
                repeatingQuest.copy(
                    id = repeatingQuestRepository.generateId(),
                    createdAt = Instant.now()
                )
            )
        } else {
            questRepository.removeAllNotCompletedForRepeating(repeatingQuest.id, LocalDate.now())

            val rp = repeatingQuest.repeatPattern
            val newRepeatPattern = when (rp) {
                is RepeatPattern.Daily -> rp.copy(lastScheduledPeriodStart = null)
                is RepeatPattern.Weekly -> rp.copy(
                    lastScheduledPeriodStart = null
                )
                is RepeatPattern.Monthly -> rp.copy(
                    lastScheduledPeriodStart = null
                )
                is RepeatPattern.Flexible.Weekly -> rp.copy(
                    lastScheduledPeriodStart = null
                )
                is RepeatPattern.Flexible.Monthly -> rp.copy(
                    lastScheduledPeriodStart = null
                )
                is RepeatPattern.Yearly -> rp.copy(
                    lastScheduledPeriodStart = null
                )
                is RepeatPattern.Manual -> rp
            }

            saveQuestsFor(repeatingQuest.copy(repeatPattern = newRepeatPattern))
        }
        reminderScheduler.schedule()
        return repeatingQuestRepository.save(rq)
    }

    private fun createSubQuests(subQuestNames: List<String>) =
        subQuestNames
            .filter { it.isNotBlank() }
            .map {
                SubQuest(
                    name = it.trim(),
                    completedAtDate = null,
                    completedAtTime = null
                )
            }

    private fun saveQuestsFor(repeatingQuest: RepeatingQuest) =
        saveQuestsForRepeatingQuestUseCase.execute(
            SaveQuestsForRepeatingQuestUseCase.Params(
                repeatingQuest = repeatingQuest,
                start = LocalDate.now()
            )
        ).repeatingQuest

    data class Params(
        val id: String = "",
        val name: String,
        val subQuestNames: List<String>,
        val color: Color,
        val icon: Icon? = null,
        val startTime: Time? = null,
        val duration: Int,
        val reminders: List<Reminder>?,
        val repeatPattern: RepeatPattern,
        val challengeId: String? = null,
        val note: String = "",
        val tags: List<Tag> = listOf()
    )
}