package io.ipoli.android.quest.schedule.summary.view.widget

import android.content.Context
import android.graphics.Canvas
import com.haibin.calendarview.Calendar
import com.haibin.calendarview.WeekView

@Suppress("unused")
class ScheduleSummaryWeekView(context: Context) : WeekView(context) {

    override fun onDrawSelected(
        canvas: Canvas,
        calendar: Calendar,
        x: Int,
        hasScheme: Boolean
    ): Boolean {

        return true
    }

    override fun onDrawScheme(canvas: Canvas, calendar: Calendar, x: Int) {

    }

    override fun onDrawText(
        canvas: Canvas,
        calendar: Calendar,
        x: Int,
        hasScheme: Boolean,
        isSelected: Boolean
    ) {
    }

}