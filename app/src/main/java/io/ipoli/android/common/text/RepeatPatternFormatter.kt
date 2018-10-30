package io.ipoli.android.common.text

import android.content.Context
import io.ipoli.android.R
import io.ipoli.android.repeatingquest.entity.RepeatPattern

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 04/11/2018.
 */
object RepeatPatternFormatter {
    fun format(context: Context, repeatPattern: RepeatPattern): String =
        when (repeatPattern) {
            is RepeatPattern.Daily -> context.getString(R.string.every_day)
            is RepeatPattern.Weekly, is RepeatPattern.Flexible.Weekly ->
                if (repeatPattern.periodCount == 1)
                    context.getString(R.string.once_a_week)
                else
                    context.getString(R.string.times_a_week, repeatPattern.periodCount)
            is RepeatPattern.Monthly, is RepeatPattern.Flexible.Monthly ->
                if (repeatPattern.periodCount == 1)
                    context.getString(R.string.once_a_month)
                else
                    context.getString(R.string.times_a_month, repeatPattern.periodCount)
            is RepeatPattern.Yearly -> context.getString(R.string.once_a_year)
            is RepeatPattern.EveryXDays -> context.getString(R.string.x_days, repeatPattern.xDays)
        }
}