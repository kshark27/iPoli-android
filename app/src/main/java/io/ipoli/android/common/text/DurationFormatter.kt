package io.ipoli.android.common.text

import android.content.Context
import io.ipoli.android.Constants
import io.ipoli.android.R
import java.util.concurrent.TimeUnit


/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 8/21/17.
 */
object DurationFormatter {
    fun formatHours(context: Context, hours: Int): String {
        if (hours < 0) {
            return formatEmptyDuration(context)
        }
        return context.getString(R.string.quest_item_hours_duration, hours)

    }

    fun format(context: Context, duration: Int): String {
        if (duration < 0) {
            return formatEmptyDuration(context)
        }
        var hours = 0
        var minutes = 0
        if (duration > 0) {
            hours = TimeUnit.MINUTES.toHours(duration.toLong()).toInt()
            minutes = duration - hours * 60
        }
        return if (hours > 0 && minutes == 0) {
            context.getString(R.string.quest_item_hours_duration, hours)
        } else if (hours > 0) {
            context.getString(R.string.quest_item_full_duration, hours, minutes)
        } else {
            context.getString(R.string.quest_item_minutes_duration, minutes)
        }
    }

    fun formatReadable(context: Context, duration: Int): String {
        if (duration < 0) {
            return formatEmptyDuration(context)
        }
        if (duration <= Constants.QUEST_MIN_DURATION) {
            return String.format(
                context.getString(R.string.duration_minutes_or_less),
                Constants.QUEST_MIN_DURATION
            )
        }
        val hours = TimeUnit.MINUTES.toHours(duration.toLong()).toInt()
        val mins = duration - hours * 60
        if (hours <= 0 && mins <= 0) {
            return ""
        }
        if (hours > 0 && mins > 0) {
            return String.format(context.getString(R.string.duration_format_full), hours, mins)
        }

        return if (hours > 0 && mins == 0) {
            context.resources.getQuantityString(R.plurals.duration_hours, hours, hours)
        } else context.resources.getQuantityString(R.plurals.duration_minutes, mins.toInt(), mins)

    }

    fun formatReadableShort(duration: Int): String {
        if (duration < 0) {
            return ""
        }
        if (duration <= Constants.QUEST_MIN_DURATION) {
            return Constants.QUEST_MIN_DURATION.toString() + " min or less"
        }

        val separator = "and"
        val hours = TimeUnit.MINUTES.toHours(duration.toLong()).toInt()
        val mins = duration - hours * 60
        if (hours <= 0 && mins <= 0) {
            return ""
        }
        if (hours > 0 && mins > 0) {
            return hours.toString() + "h " + separator + " " + mins + "m"
        }

        return if (hours > 0 && mins == 0) {
            if (hours == 1) "1 hour" else hours.toString() + " hours"
        } else mins.toString() + " min"

    }

    fun formatShort(duration: Int): String {
        if (duration < 0) {
            return ""
        }
        if (duration == 0) {
            return "0 min"
        }
        val hours = TimeUnit.MINUTES.toHours(duration.toLong()).toInt()
        val mins = duration - hours * 60
        if (hours <= 0 && mins <= 0) {
            return ""
        }
        if (hours > 0 && mins > 0) {
            return hours.toString() + "h " + mins + "m"
        }

        return if (hours > 0 && mins == 0) {
            if (hours == 1) "1 hour" else hours.toString() + " hours"
        } else mins.toString() + " min"

    }

    fun formatShort(context: Context, duration: Int): String {
        if (duration < 0) {
            return ""
        }
        if (duration == 0) {
            return context.resources.getQuantityString(R.plurals.duration_minutes, 0, 0)
        }
        val hours = TimeUnit.MINUTES.toHours(duration.toLong()).toInt()
        val mins = duration - hours * 60
        if (hours <= 0 && mins <= 0) {
            return ""
        }
        if (hours > 0 && mins > 0) {
            return String.format(context.getString(R.string.duration_format_short), hours, mins)
        }

        return if (hours > 0 && mins == 0) {
            context.resources.getQuantityString(R.plurals.duration_hours, hours, hours)
        } else context.resources.getQuantityString(R.plurals.duration_minutes, mins, mins)
    }

    fun formatNarrow(duration: Int): String {
        if (duration < 0) {
            return ""
        }
        if (duration == 0) {
            return "0m"
        }
        val hours = TimeUnit.MINUTES.toHours(duration.toLong()).toInt()
        val mins = duration - hours * 60
        if (hours <= 0 && mins <= 0) {
            return ""
        }

        if (hours == 0 && mins > 0) {
            return "${mins}m"
        }

        if (hours > 0 && mins > 0) {
            return hours.toString() + "h " + mins + "m"
        }

        return "${hours}h"
    }

    private fun formatEmptyDuration(context: Context): String {
        return context.getString(R.string.do_not_know)
    }
}