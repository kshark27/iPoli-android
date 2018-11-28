package io.ipoli.android.quest

import io.ipoli.android.common.Reward
import io.ipoli.android.common.datetime.*
import io.ipoli.android.common.persistence.EntityWithTags
import io.ipoli.android.common.sumByLong
import io.ipoli.android.quest.subquest.SubQuest
import io.ipoli.android.repeatingquest.entity.PeriodProgress
import io.ipoli.android.repeatingquest.entity.RepeatPattern
import io.ipoli.android.tag.Tag
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.LocalTime

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 10/15/17.
 */

interface Entity {
    val id: String
    val updatedAt: Instant
    val createdAt: Instant
}

sealed class Reminder(open val message: String) {
    data class Fixed(override val message: String, val date: LocalDate, val time: Time) :
        Reminder(message)

    data class Relative(override val message: String, val minutesFromStart: Long) :
        Reminder(message)
}

enum class ColorPack(val gemPrice: Int) {
    FREE(0),
    BASIC(4)
}

enum class Color(val pack: ColorPack) {
    GREEN(ColorPack.FREE),
    BLUE_GREY(ColorPack.FREE),
    BLUE(ColorPack.BASIC),
    RED(ColorPack.BASIC),
    INDIGO(ColorPack.FREE),
    ORANGE(ColorPack.FREE),
    PINK(ColorPack.BASIC),
    TEAL(ColorPack.BASIC),
    DEEP_ORANGE(ColorPack.BASIC),
    PURPLE(ColorPack.BASIC),
    BROWN(ColorPack.BASIC),
    LIME(ColorPack.BASIC)
}

enum class Icon(val pack: IconPack) {
    HOME(IconPack.FREE),
    FRIENDS(IconPack.BASIC),
    RESTAURANT(IconPack.BASIC),
    PAW(IconPack.BASIC),
    BRIEFCASE(IconPack.FREE),
    BOOK(IconPack.FREE),
    HEART(IconPack.BASIC),
    RUN(IconPack.FREE),
    MED_KIT(IconPack.BASIC),
    TREE(IconPack.BASIC),
    BEER(IconPack.BASIC),
    PLANE(IconPack.FREE),
    COMPASS(IconPack.BASIC),
    LIGHT_BULB(IconPack.BASIC),
    CAR(IconPack.BASIC),
    WRENCH(IconPack.BASIC),
    STAR(IconPack.FREE),
    FITNESS(IconPack.BASIC),
    COFFEE(IconPack.BASIC),
    BUS(IconPack.BASIC),
    ACADEMIC(IconPack.BASIC),
    CAKE(IconPack.BASIC),
    GAME_CONTROLLER(IconPack.BASIC),
    FLASK(IconPack.BASIC),
    SHOPPING_CART(IconPack.BASIC),
    BIKE(IconPack.BASIC),
    TRAIN(IconPack.FREE),
    PIZZA(IconPack.FREE),
    PHONE(IconPack.BASIC),
    CLOUD(IconPack.FREE),
    SUN(IconPack.BASIC),
    AMERICAN_FOOTBALL(IconPack.BASIC),
    WALLET(IconPack.BASIC),
    FOOTBALL(IconPack.BASIC),
    MONEY(IconPack.BASIC),
    CAMERA(IconPack.BASIC),
    FLOWER(IconPack.BASIC),
    LAPTOP(IconPack.BASIC),
    DROP(IconPack.BASIC),
    DRINK(IconPack.BASIC),
    BUCKET(IconPack.BASIC),
    CALCULATOR(IconPack.BASIC),
    HAIR_CROSS(IconPack.BASIC),
    CREDIT_CARD(IconPack.BASIC),
    TROPHY(IconPack.BASIC),
    TOOLS(IconPack.BASIC),
    MIC(IconPack.BASIC),
    FEATHER(IconPack.BASIC),
    MAIL(IconPack.BASIC),
    SHOP(IconPack.BASIC),
    MEDAL(IconPack.BASIC),
    MAP(IconPack.BASIC),
    BROOM(IconPack.FREE),
    COUCH(IconPack.BASIC),
    HOTEL(IconPack.BASIC),
    MOUNTAIN(IconPack.BASIC),
    BEACH(IconPack.BASIC),
    CHILD_CARE(IconPack.BASIC),
    SWIM(IconPack.BASIC),
    PEOPLE(IconPack.BASIC),
    HEADPHONES(IconPack.BASIC),
    MASK(IconPack.BASIC),
    BRUSH(IconPack.BASIC),
    ICE_CREAM(IconPack.BASIC),
    NATURE(IconPack.BASIC),
    BIOHAZARD(IconPack.BASIC),
    WASHING_MACHINE(IconPack.BASIC),
    GAS_STATION(IconPack.BASIC),
    DUCK(IconPack.FREE),
    CROISSANT(IconPack.BASIC),
    APPLE(IconPack.BASIC),
    FISH(IconPack.BASIC),
    SMILE(IconPack.BASIC),
    TOOTH(IconPack.BASIC),
    SMOKE(IconPack.BASIC),
    HAND(IconPack.BASIC),
    STRETCH(IconPack.BASIC),
    PAUSE(IconPack.BASIC),
    BASKETBALL(IconPack.BASIC),
    ROSE(IconPack.BASIC),
    SCALE(IconPack.BASIC),
    EVENT_NOTE(IconPack.BASIC),
    GLASS_WATER(IconPack.BASIC),
    PHOTO_CAMERA(IconPack.BASIC),
}

enum class IconPack(val gemPrice: Int) {
    FREE(0),
    BASIC(6)
}

sealed class BaseQuest(open val id: String)

data class Quest(
    override val id: String = "",
    val name: String,
    val color: Color,
    val icon: Icon? = null,
    override val tags: List<Tag> = listOf(),
    val startTime: Time? = null,
    val duration: Int,
    val priority: Priority = Priority.NOT_IMPORTANT_NOT_URGENT,
    val preferredStartTime: TimePreference = TimePreference.ANY,
    val reminders: List<Reminder> = listOf(),
    val subQuests: List<SubQuest> = listOf(),
    val startDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val scheduledDate: LocalDate?,
    val originalScheduledDate: LocalDate? = scheduledDate,
    val timeRanges: List<TimeRange> = listOf(),
    val timeRangesToComplete: List<TimeRange> = listOf(),
    val totalPomodoros: Int? = null,
    val completedAtDate: LocalDate? = null,
    val completedAtTime: Time? = null,
    val reward: Reward? = null,
    val isRemoved: Boolean = false,
    val repeatingQuestId: String? = null,
    val challengeId: String? = null,
    val note: String = "",
    override val createdAt: Instant = Instant.now(),
    override val updatedAt: Instant = Instant.now(),
    val removedAt: Instant? = null
) : BaseQuest(id), EntityWithTags {

    sealed class Bounty {
        object None : Bounty()
        data class Food(val food: io.ipoli.android.pet.Food) : Bounty()
    }

    val isCompleted get() = completedAtDate != null
    val endTime get() = startTime?.plus(duration)

    val endDateTime
        get() = endTime?.let {
            scheduledDate?.toLocalDateTime(it)
        }

    val isScheduled get() = startTime != null && scheduledDate != null

    val startMillisecond
        get() = if (!isScheduled) null else LocalDateTime.of(
            scheduledDate!!,
            LocalTime.of(startTime!!.hours, startTime.getMinutes())
        ).toMillis()

    val actualStart get() = if (hasTimer) timeRanges.first().start else null

    val actualStartTime
        get() =
            if (isCompleted) {
                val localCompletedAt = LocalDateTime.of(
                    completedAtDate,
                    LocalTime.of(completedAtTime!!.hours, completedAtTime.getMinutes())
                )
                val result = localCompletedAt.minusSeconds(actualDuration.longValue)
                Time.at(result.hour, result.minute)
            } else {
                startTime
            }

    val actualDuration
        get() =
            when {
                hasCountDownTimer -> {
                    val timeRange = timeRanges.first()
                    if (isCompleted) {
                        timeRange.actualDuration()
                    } else {
                        (Instant.now() - timeRange.start!!).milliseconds.asSeconds
                    }
                }
                hasPomodoroTimer -> timeRanges.sumByLong { it.actualDuration().longValue }.seconds
                else -> duration.minutes.asSeconds
            }

    val hasTimer
        get() = hasCountDownTimer || hasPomodoroTimer

    val hasCountDownTimer get() = timeRanges.firstOrNull()?.type == TimeRange.Type.COUNTDOWN

    val hasPomodoroTimer get() = timeRanges.firstOrNull()?.type == TimeRange.Type.POMODORO_WORK

    val isStarted
        get() =
            !isCompleted &&
                (hasCountDownTimer ||
                    (hasPomodoroTimer && timeRanges.last().end == null))

    fun hasExceededEstimatedDuration() = timeRanges.sumBy { it.duration } >= duration

    val isFromRepeatingQuest get() = repeatingQuestId != null

    val isFromChallenge get() = challengeId != null

    companion object {
        fun createFromRepeatingQuest(
            repeatingQuest: RepeatingQuest,
            scheduleDate: LocalDate? = null,
            startTime: Time? = null
        ) =
            Quest(
                name = repeatingQuest.name,
                subQuests = repeatingQuest.subQuests,
                color = repeatingQuest.color,
                icon = repeatingQuest.icon,
                startTime = startTime ?: repeatingQuest.startTime,
                duration = repeatingQuest.duration,
                priority = repeatingQuest.priority,
                preferredStartTime = repeatingQuest.preferredStartTime,
                scheduledDate = scheduleDate,
                originalScheduledDate = scheduleDate,
                reminders = repeatingQuest.reminders,
                repeatingQuestId = repeatingQuest.id,
                challengeId = repeatingQuest.challengeId,
                tags = repeatingQuest.tags
            )
    }
}

data class TimeRange(
    val type: Type,
    val duration: Int,
    val start: Instant? = null,
    val end: Instant? = null
) {
    enum class Type {
        COUNTDOWN,
        POMODORO_WORK,
        POMODORO_SHORT_BREAK,
        POMODORO_LONG_BREAK
    }

    fun actualDuration(): Duration<Second> {
        if (start != null && end != null) {
            return (end - start).toEpochMilli().milliseconds.asSeconds
        }
        return duration.minutes.asSeconds
    }
}

enum class Priority {
    IMPORTANT_URGENT,
    IMPORTANT_NOT_URGENT,
    NOT_IMPORTANT_URGENT,
    NOT_IMPORTANT_NOT_URGENT
}

data class RepeatingQuest(
    override val id: String = "",
    val name: String,
    val color: Color,
    val icon: Icon? = null,
    override val tags: List<Tag> = listOf(),
    val startTime: Time? = null,
    val duration: Int,
    val priority: Priority = Priority.NOT_IMPORTANT_NOT_URGENT,
    val preferredStartTime: TimePreference = TimePreference.ANY,
    val reminders: List<Reminder> = listOf(),
    val repeatPattern: RepeatPattern,
    val subQuests: List<SubQuest> = listOf(),
    val nextDate: LocalDate? = null,
    val lastCompletedDate: LocalDate? = null,
    val periodProgress: PeriodProgress? = null,
    val challengeId: String? = null,
    val note: String = "",
    override val createdAt: Instant = Instant.now(),
    override val updatedAt: Instant = Instant.now(),
    val removedAt: Instant? = null
) : BaseQuest(id), EntityWithTags {
    val start
        get() = repeatPattern.startDate

    val end
        get() = repeatPattern.endDate

    val isCompleted
        get() = if (end == null) false else LocalDate.now().isAfter(end)

    val endTime: Time?
        get() = startTime?.plus(duration)

    val isFlexible: Boolean
        get() = repeatPattern is RepeatPattern.Flexible

    val isFixed: Boolean
        get() = !isFlexible
}