package io.ipoli.android.quest.schedule.summary.view.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.support.annotation.ColorInt
import android.support.v4.content.ContextCompat
import android.text.TextPaint
import android.text.TextUtils
import com.haibin.calendarview.Calendar
import com.haibin.calendarview.MonthView
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.view.AndroidColor
import io.ipoli.android.common.view.attrData
import org.json.JSONArray

data class ScheduleItem(val name: String, val isCompleted: Boolean, @ColorInt val color: Int) {
    companion object {

        fun createItemsFromJson(data: JSONArray, context: Context): List<ScheduleItem> {
            if (data.length() == 0) {
                return emptyList()
            }
            return (0 until data.length()).map {
                val o = data.getJSONObject(it)
                val t = o.getString("type")
                when (t) {
                    "quest" -> {
                        val isCompleted = o.getBoolean("isCompleted")
                        val bc = if (isCompleted) {
                            R.color.md_grey_500
                        } else {
                            val c = AndroidColor.valueOf(o.getString("color"))
                            c.color500
                        }

                        val androidColor = ContextCompat.getColor(context, bc)
                        ScheduleItem(
                            name = o.getString("name"),
                            isCompleted = isCompleted,
                            color = androidColor
                        )
                    }
                    "event" -> {
                        val c = o.getInt("color")
                        ScheduleItem(
                            name = o.getString("name"),
                            isCompleted = false,
                            color = c
                        )
                    }
                    else -> throw IllegalStateException("Unknown schedule summary item type $t")
                }

            }
        }
    }
}

@Suppress("unused")
class ScheduleSummaryMonthView(context: Context) : MonthView(context) {

    private val selectedBackgroundPaint = Paint()
    private val currentBackgroundPaint = Paint()

    private val itemTextPaint = TextPaint()

    private val dividerPaint = Paint()

    private val itemPaint = Paint()

    private val selectedDayTextPaint = Paint()

    private var backgroundRadius = 0f

    init {

        selectedBackgroundPaint.isAntiAlias = true
        selectedBackgroundPaint.color = context.attrData(R.attr.colorAccent)

        currentBackgroundPaint.isAntiAlias = true
        currentBackgroundPaint.color = context.attrData(R.attr.colorPrimary)

        itemTextPaint.isFakeBoldText = true
        itemTextPaint.isAntiAlias = true
        itemTextPaint.color = ContextCompat.getColor(context, R.color.md_white)
        itemTextPaint.textSize = ViewUtils.spToPx(12, context).toFloat()

        selectedDayTextPaint.isAntiAlias = true
        selectedDayTextPaint.color = ContextCompat.getColor(context, R.color.md_white)
        selectedDayTextPaint.textSize = ViewUtils.spToPx(12, context).toFloat()
        selectedDayTextPaint.textAlign = Paint.Align.CENTER

        dividerPaint.style = Paint.Style.STROKE
        dividerPaint.strokeWidth = ViewUtils.dpToPx(1f, context)
        dividerPaint.color = context.attrData(android.R.attr.listDivider)

        itemPaint.isAntiAlias = true
        itemPaint.style = Paint.Style.FILL
    }

    override fun onPreviewHook() {
        backgroundRadius = Math.min(mItemWidth, mItemHeight) / 11 * 1.8f
    }

    override fun onDrawSelected(
        canvas: Canvas,
        calendar: Calendar,
        x: Int,
        y: Int,
        hasScheme: Boolean
    ): Boolean {

        val cx = x + mItemWidth / 2
        val cy = mTextBaseLine + y - (mItemHeight / 2.65f) - backgroundRadius / 2
        canvas.drawCircle(cx.toFloat(), cy, backgroundRadius, selectedBackgroundPaint)
        return true
    }

    override fun onDrawScheme(canvas: Canvas, calendar: Calendar, x: Int, y: Int) {

        val data = JSONArray(calendar.scheme)

        val items = ScheduleItem.createItemsFromJson(data, context)

        val cellStart = x + ViewUtils.dpToPx(1f, context)
        val cellEnd = cellStart + mItemWidth - ViewUtils.dpToPx(2f, context)

        canvas.drawLine(
            x.toFloat(),
            y.toFloat(),
            (x + mItemWidth).toFloat(),
            y.toFloat(),
            dividerPaint
        )

        val topOffset = y.toFloat() + mItemHeight / 4.2f

        val qHeight = mItemHeight / 8.3f

        items.forEachIndexed { index, scheduleItem ->

            drawQuestBackground(
                index,
                scheduleItem.color,
                cellStart,
                cellEnd,
                topOffset,
                qHeight,
                canvas,
                y.toFloat()
            )

            drawQuestName(
                index,
                scheduleItem,
                cellStart,
                cellEnd,
                topOffset,
                qHeight,
                canvas,
                y.toFloat()
            )
        }
    }

    private fun drawQuestBackground(
        index: Int,
        @ColorInt color: Int,
        cellStart: Float,
        cellEnd: Float,
        topOffset: Float,
        questHeight: Float,
        canvas: Canvas,
        y: Float,
        padding: Float = 1f
    ) {
        itemPaint.color = color
        val top = topOffset + questHeight * index + index * padding
        val bottom = topOffset + questHeight * (index + 1) + index * padding
        val cellBottom = y + mItemHeight - padding
        if(top > cellBottom) {
            return
        }
        canvas.drawRect(
            cellStart,
            top,
            cellEnd,
            Math.min(bottom, cellBottom),
            itemPaint
        )
    }

    private fun drawQuestName(
        index: Int,
        scheduleItem: ScheduleItem,
        cellStart: Float,
        cellEnd: Float,
        topOffset: Float,
        questHeight: Float,
        canvas: Canvas,
        y: Float,
        padding: Float = 1f
    ) {

        val textStart = cellStart + ViewUtils.dpToPx(2f, context)
        val textEnd = cellEnd - ViewUtils.dpToPx(2f, context)

        val drawnText =
            TextUtils.ellipsize(
                scheduleItem.name,
                itemTextPaint,
                textEnd - textStart,
                TextUtils.TruncateAt.END
            )

        val b = Rect()
        itemTextPaint.getTextBounds(drawnText.toString(), 0, drawnText.length, b)

        itemTextPaint.isStrikeThruText = scheduleItem.isCompleted

        val textY =
            topOffset + questHeight / 2 + b.height() / 2.5f + (questHeight * index) + (padding * index)

        if (textY < y + mItemHeight) {

            canvas.drawText(
                drawnText.toString(),
                textStart,
                textY,
                itemTextPaint
            )
        }
    }

    override fun onDrawText(
        canvas: Canvas,
        calendar: Calendar,
        x: Int,
        y: Int,
        hasScheme: Boolean,
        isSelected: Boolean
    ) {
        val baselineY = mTextBaseLine + y - (mItemHeight / 2.65f)
        val cx = x + mItemWidth / 2

        if (calendar.isCurrentDay) {
            val cy = mTextBaseLine + y - (mItemHeight / 2.65f) - backgroundRadius / 2
            canvas.drawCircle(
                cx.toFloat(),
                cy,
                backgroundRadius,
                currentBackgroundPaint
            )
        }

        canvas.drawText(
            calendar.day.toString(),
            cx.toFloat(),
            baselineY,
            when {
                isSelected -> selectedDayTextPaint
                calendar.isCurrentDay -> selectedDayTextPaint
                calendar.isCurrentMonth -> mSchemeTextPaint
                else -> mOtherMonthTextPaint
            }
        )
    }
}