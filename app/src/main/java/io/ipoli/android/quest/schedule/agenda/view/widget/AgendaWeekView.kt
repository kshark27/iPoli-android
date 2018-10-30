package io.ipoli.android.quest.schedule.agenda.view.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.support.annotation.ColorInt
import android.support.annotation.ColorRes
import android.support.v4.content.ContextCompat
import com.haibin.calendarview.Calendar
import com.haibin.calendarview.WeekView
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.view.AndroidColor
import io.ipoli.android.common.view.attrData
import org.json.JSONArray
import org.json.JSONObject

@Suppress("unused")
class AgendaWeekView(context: Context) : WeekView(context) {

    companion object {
        const val MINUTES = 16 * 60
        const val EVENT_HEIGHT_DP = 5
        const val GAP_DP = 2
        const val EVENTS_OFFSET_DP = 1
    }

    private val selectedDayPaint = Paint()
    private val currentDayPaint = Paint()
    private val dividerPaint = Paint()
    private val itemPaint = Paint()

    private var backgroundRadius = 0f

    init {

        selectedDayPaint.style = Paint.Style.FILL
        selectedDayPaint.isAntiAlias = true
        selectedDayPaint.color = context.attrData(R.attr.colorAccent)

        currentDayPaint.style = Paint.Style.FILL
        currentDayPaint.isAntiAlias = true
        currentDayPaint.color = context.attrData(R.attr.colorPrimary)

        dividerPaint.style = Paint.Style.STROKE
        dividerPaint.strokeWidth = ViewUtils.dpToPx(1f, context)
        dividerPaint.color = context.attrData(android.R.attr.listDivider)

        itemPaint.isAntiAlias = true
        itemPaint.style = Paint.Style.STROKE
        itemPaint.strokeWidth = ViewUtils.dpToPx(EVENT_HEIGHT_DP.toFloat(), context)
    }

    private fun Paint.initWithColor(@ColorRes color: Int) {
        isAntiAlias = true
        style = Paint.Style.FILL
        this.color = ContextCompat.getColor(context, color)
    }

    private fun createTextPaint(context: Context, @ColorRes color: Int, textSize: Int): Paint {
        val paint = Paint()
        paint.color = ContextCompat.getColor(context, color)
        paint.isAntiAlias = true
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = ViewUtils.spToPx(textSize, context).toFloat()
        paint.isFakeBoldText = true

        return paint
    }

    override fun onDrawSelected(
        canvas: Canvas,
        calendar: Calendar,
        x: Int,
        hasScheme: Boolean
    ): Boolean {
        return true
    }

    override fun onPreviewHook() {
        backgroundRadius = (Math.min(mItemWidth, mItemHeight) / 40f * 9)
    }

    override fun onDrawScheme(canvas: Canvas, calendar: Calendar, x: Int) {

        val data = JSONObject(calendar.scheme)
        val weekIndicators = data.getJSONArray("weekIndicators")
        if(weekIndicators.length() == 0) {
            return
        }
        val items = WeekViewItem.createItemsFromJson(weekIndicators, context)

        val offset = ViewUtils.dpToPx(EVENTS_OFFSET_DP.toFloat(), context)
        val gap = ViewUtils.dpToPx(GAP_DP.toFloat(), context)
        val topY =
            2 * backgroundRadius + ViewUtils.dpToPx(8f, context)

        val minuteWidth = (mItemWidth - 2 * offset) / MINUTES.toFloat()
        val eventHeight = ViewUtils.dpToPx(EVENT_HEIGHT_DP.toFloat(), context)

        items.forEachIndexed { index, item ->
            itemPaint.color = item.color
            val iy = topY + index * (eventHeight + gap)
            val startX = x.toFloat() + offset + item.startMinute * minuteWidth

            canvas.drawLine(
                startX,
                iy,
                startX + item.duration * minuteWidth,
                iy,
                itemPaint
            )

        }
    }

    override fun onDrawText(
        canvas: Canvas,
        calendar: Calendar,
        x: Int,
        hasScheme: Boolean,
        isSelected: Boolean
    ) {

        val textPaint = textPaint(calendar, isSelected)
        canvas.drawLine(
            (x + mItemWidth).toFloat(),
            0f,
            (x + mItemWidth).toFloat(),
            mItemHeight.toFloat(),
            dividerPaint
        )

        val baselineY = mTextBaseLine - mItemHeight / 2 + backgroundRadius
        val cx = x + mItemWidth / 2

        if (calendar.isCurrentDay) {
            canvas.drawCircle(
                cx.toFloat(),
                backgroundRadius,
                backgroundRadius,
                currentDayPaint
            )
        } else if(isSelected) {
            canvas.drawCircle(
                cx.toFloat(),
                backgroundRadius,
                backgroundRadius,
                selectedDayPaint
            )
        }

        canvas.drawText(
            calendar.day.toString(),
            cx.toFloat(),
            baselineY,
            textPaint
        )
    }

    private fun textPaint(
        calendar: Calendar,
        isSelected: Boolean = false
    ): Paint {
        return when {
            isSelected -> mCurDayTextPaint
            calendar.isCurrentDay -> mCurDayTextPaint
            calendar.isCurrentMonth -> mSchemeTextPaint
            else -> mOtherMonthTextPaint
        }
    }

    data class WeekViewItem(
        @ColorInt val color: Int,
        val duration: Int,
        val startMinute: Int
    ) {
        companion object {

            fun createItemsFromJson(data: JSONArray, context: Context): List<WeekViewItem> {
                if (data.length() == 0) {
                    return emptyList()
                }
                return (0 until data.length()).map {
                    val o = data.getJSONObject(it)
                    val type = o.getString("type")
                    WeekViewItem(
                        color = if (type == "quest") {
                            val c = AndroidColor.valueOf(o.getString("color"))
                            ContextCompat.getColor(context, c.color500)
                        } else o.getString("color").toInt(),
                        duration = o.getInt("duration"),
                        startMinute = o.getInt("start")
                    )
                }
            }
        }
    }

}