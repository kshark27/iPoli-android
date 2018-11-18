package io.ipoli.android.repeatingquest.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.RepeatingQuest
import io.ipoli.android.repeatingquest.persistence.RepeatingQuestRepository
import org.threeten.bp.LocalDate

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 3/3/18.
 */
class CreatePlaceholderQuestsForRepeatingQuestsUseCase(
    private val repeatingQuestRepository: RepeatingQuestRepository
) : UseCase<CreatePlaceholderQuestsForRepeatingQuestsUseCase.Params, List<Quest>> {

    override fun execute(parameters: Params): List<Quest> {
        val start = parameters.startDate
        val end = parameters.endDate
        val currentDate = parameters.currentDate

        if (currentDate.isAfter(end)) {
            return listOf()
        }

        val rqs = repeatingQuestRepository.findAllActive(currentDate)

        return rqs.map {
            val rqStart = it.start
            if (end.isBefore(rqStart)) {
                return@map emptyList<Quest>()
            }

            val rqEnd = it.end
            if (rqEnd != null && start.isAfter(rqEnd)) {
                return@map emptyList<Quest>()
            }

            val currStart = if (start.isBefore(rqStart)) rqStart else start
            val currEnd = if (rqEnd != null && rqEnd.isBefore(end)) rqEnd else end

            val scheduleDates = it.repeatPattern.createPlaceholderDates(
                startDate = currStart,
                endDate = currEnd,
                skipScheduled = true
            )

            if (scheduleDates.isNotEmpty()) {
                scheduleDates.map { date ->
                    createQuest(it, date)
                }
            } else {
                emptyList()
            }
        }.flatten()
    }

    private fun createQuest(
        rq: RepeatingQuest,
        scheduleDate: LocalDate
    ) =
        Quest(
            name = rq.name,
            color = rq.color,
            icon = rq.icon,
            startTime = rq.startTime,
            duration = rq.duration,
            scheduledDate = scheduleDate,
            reminders = rq.reminders,
            repeatingQuestId = rq.id,
            note = rq.note
        )

    data class Params(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val currentDate: LocalDate = LocalDate.now()
    )
}