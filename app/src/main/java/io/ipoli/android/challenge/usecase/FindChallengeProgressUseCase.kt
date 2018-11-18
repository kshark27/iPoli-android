package io.ipoli.android.challenge.usecase

import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.common.UseCase
import io.ipoli.android.common.datetime.DateUtils
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.datetime.datesBetween
import io.ipoli.android.common.datetime.isBetween
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.data.persistence.QuestRepository
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 3/13/18.
 */
class FindChallengeProgressUseCase(private val questRepository: QuestRepository) :
    UseCase<FindChallengeProgressUseCase.Params, Challenge> {
    override fun execute(parameters: Params): Challenge {
        val challenge = parameters.challenge

        val repeatingCount = challenge.repeatingQuests.sumBy { rq ->
            val rqEnd = rq.end
            val start = rq.start
            val end =
                if (rqEnd == null) challenge.endDate else DateUtils.min(rqEnd, challenge.endDate)

            val removedCount =
                challenge.quests.filter { it.repeatingQuestId == rq.id && it.isRemoved }.size

            val allCount = questRepository.findCountForRepeatingQuestInPeriod(rq.id, start, end)

            allCount - removedCount
        }

        val completedCount = challenge.quests.filter { it.isCompleted && !it.isRemoved }.size
        val allCount = repeatingCount + challenge.baseQuests.filter { it is Quest }.size

        val newTrackedValues = challenge.trackedValues.map {
            when (it) {
                is Challenge.TrackedValue.Progress -> {

                    val increasePerQuest = (1f / allCount) * 100f

                    val historyData =
                        challenge.startDate.datesBetween(parameters.currentDate).map { d ->
                            d to 0f
                        }.toMap().toMutableMap()

                    challenge.quests
                        .filter { q ->
                            q.isCompleted && q.completedAtDate!!.isBetween(
                                challenge.startDate,
                                parameters.currentDate
                            )
                        }
                        .forEach { q ->
                            historyData[q.completedAtDate!!] = historyData[q.completedAtDate]!! +
                                increasePerQuest
                        }

                    val history = historyData.map { h ->
                        h.key to Challenge.TrackedValue.Log(h.value.toDouble(), Time.now(), h.key)
                    }.toMap().toSortedMap()

                    it.copy(
                        completedCount = completedCount,
                        allCount = allCount,
                        history = history
                    )
                }
                is Challenge.TrackedValue.Target -> {

                    var currentValue =
                        if (it.history.isNotEmpty())
                            it.history[it.history.lastKey()]!!.value
                        else
                            it.startValue

                    val cumulativeHistory = if (it.isCumulative) {
                        currentValue = it.startValue + it.history.values.map { l ->
                            l.value
                        }.sum()

                        var cumVal = it.startValue

                        it.history.values.map { l ->
                            cumVal += l.value
                            l.date to l.copy(value = cumVal)
                        }.toMap().toSortedMap()
                    } else
                        null

                    it.copy(
                        currentValue = currentValue,
                        remainingValue = Math.abs(it.targetValue - currentValue),
                        cumulativeHistory = cumulativeHistory
                    )

                }
                else -> it
            }
        }

        return challenge.copy(
            progress = Challenge.Progress(
                completedCount = completedCount,
                allCount = allCount
            ),
            trackedValues = newTrackedValues
        )
    }

    data class Params(
        val challenge: Challenge,
        val lastDayOfWeek: DayOfWeek = DateUtils.lastDayOfWeek,
        val currentDate: LocalDate = LocalDate.now()
    )
}