package io.ipoli.android.habit.data

import io.ipoli.android.common.Reward
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.persistence.EntityWithTags
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import io.ipoli.android.tag.Tag
import org.threeten.bp.DayOfWeek
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import java.util.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 6/16/18.
 */
data class Habit(
    override val id: String = "",
    val name: String,
    val color: Color,
    val icon: Icon,
    override val tags: List<Tag> = listOf(),
    val days: Set<DayOfWeek>,
    val isGood: Boolean,
    val timesADay: Int = 1,
    val challengeId: String? = null,
    val reminders: List<Reminder> = emptyList(),
    val note: String = "",
    val streak: Streak,
    val successRate: Int = 0,
    val history: Map<LocalDate, CompletedEntry> = emptyMap(),
    val preferenceHistory: PreferenceHistory,
    val isRemoved: Boolean = false,
    override val createdAt: Instant = Instant.now(),
    override val updatedAt: Instant = Instant.now(),
    val removedAt: Instant? = null
) : EntityWithTags {

    data class Reminder(val message: String, val time: Time)

    data class PreferenceHistory(
        val days: SortedMap<LocalDate, Set<DayOfWeek>>,
        val timesADay: SortedMap<LocalDate, Int>
    )

    fun completedCountForDate(date: LocalDate) =
        if (history.containsKey(date)) history[date]!!.completedCount else 0

    fun isCompletedForDate(date: LocalDate) =
        completedCountForDate(date) >= requiredTimesToComplete(date)

    private fun requiredTimesToComplete(date: LocalDate) =
        if (preferenceHistory.timesADay.size == 1) {
            timesADay
        } else if (preferenceHistory.timesADay.contains(date)) {
            preferenceHistory.timesADay[date]!!
        } else {
            val hm = preferenceHistory.timesADay.headMap(date)
            if (hm.isEmpty()) {
                val tm = preferenceHistory.timesADay.tailMap(date)
                preferenceHistory.timesADay[tm.firstKey()]!!
            } else {
                preferenceHistory.timesADay[hm.lastKey()]!!
            }
        }

    fun shouldBeDoneOn(date: LocalDate) =
        if (preferenceHistory.days.size == 1) {
            days.contains(date.dayOfWeek)
        } else if (preferenceHistory.days.contains(date)) {
            preferenceHistory.days[date]!!.contains(date.dayOfWeek)
        } else {
            val hm = preferenceHistory.days.headMap(date)
            if (hm.isEmpty()) {
                val tm = preferenceHistory.days.tailMap(date)
                preferenceHistory.days[tm.firstKey()]!!.contains(date.dayOfWeek)
            } else {
                preferenceHistory.days[hm.lastKey()]!!.contains(date.dayOfWeek)
            }
        }

    val isFromChallenge get() = challengeId != null

    data class Streak(val current: Int, val best: Int)
}

data class CompletedEntry(
    val completedAtTimes: List<Time> = emptyList(),
    val reward: Reward? = null
) {
    val completedCount = completedAtTimes.size

    fun complete(time: Time = Time.now()) =
        copy(
            completedAtTimes = completedAtTimes + time
        )

    fun undoLastComplete() =
        copy(
            completedAtTimes = completedAtTimes.dropLast(1)
        )
}