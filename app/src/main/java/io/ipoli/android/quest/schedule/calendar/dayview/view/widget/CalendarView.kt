package io.ipoli.android.quest.schedule.calendar.dayview.view.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.preference.PreferenceManager
import android.support.annotation.AttrRes
import android.support.v4.content.ContextCompat
import android.text.format.DateFormat
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.WindowManager
import android.widget.FrameLayout
import io.ipoli.android.Constants
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.player.data.Player

class CalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyle, defStyleRes) {

    private val hourLinePaint = Paint().apply {
        isAntiAlias = true
        color = context.attrResourceColor(android.R.attr.listDivider)
    }

    private val hourTextPaint = Paint().apply {
        isAntiAlias = true

        color = context.attrResourceColor(android.R.attr.textColorSecondary)
        textSize = ViewUtils.spToPx(12, context).toFloat()
    }

    private fun Context.attrResourceColor(@AttrRes attributeRes: Int) =
        TypedValue().let {
            theme.resolveAttribute(attributeRes, it, true)
            ContextCompat.getColor(this, it.resourceId)
        }

    private val screenWidth by lazy {
        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getMetrics(metrics)
        metrics.widthPixels.toFloat()
    }

    private val hourTexts by lazy {
        (0..23).map {
            if (it > 0) {
                Time.atHours(it).toString(shouldUse24HourFormat, true)
            } else ""
        }
    }

    var hourHeight: Float = 0f

    private val hourLineHeight = ViewUtils.dpToPx(1f, context)
    private val midHourLineHeight = ViewUtils.dpToPx(0.5f, context)
    private val hourLineStartMargin = ViewUtils.dpToPx(72f, context)
    private val midHourLineStartMargin = hourLineStartMargin + ViewUtils.dpToPx(16f, context)

    init {
        setWillNotDraw(false)
        isFocusable = false
    }

    override fun onMeasure(
        widthMeasureSpec: Int, heightMeasureSpec: Int
    ) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val desiredWidth = suggestedMinimumWidth +
            paddingLeft + paddingRight
        val desiredHeight = hourHeight * 24 +
            paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight.toInt(), heightMeasureSpec)
        )
    }

    private val bounds = Rect()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val halfHourHeight = hourHeight / 2f

        (0..23).forEach {
            val top = it * hourHeight

            val hourText = hourTexts[it]

            hourTextPaint.getTextBounds(hourText, 0, hourText.length, bounds)
            canvas.drawText(
                hourText,
                hourLineStartMargin / 2.5f,
                top + hourLineHeight / 2 + bounds.height() / 2,
                hourTextPaint
            )

            canvas.drawRect(
                hourLineStartMargin,
                top, screenWidth,
                top + hourLineHeight,
                hourLinePaint
            )

            val topHalfHour = top + halfHourHeight

            canvas.drawRect(
                midHourLineStartMargin,
                topHalfHour, screenWidth,
                topHalfHour + midHourLineHeight,
                hourLinePaint
            )
        }

        val top = 24 * hourHeight
        canvas.drawRect(
            hourLineStartMargin,
            top, screenWidth,
            top + hourLineHeight,
            hourLinePaint
        )
    }

    private val shouldUse24HourFormat by lazy {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val timeFormat = Player.Preferences.TimeFormat.valueOf(
            sharedPreferences.getString(
                Constants.KEY_TIME_FORMAT,
                Player.Preferences.TimeFormat.DEVICE_DEFAULT.name
            )
        )
        if (timeFormat == Player.Preferences.TimeFormat.DEVICE_DEFAULT)
            DateFormat.is24HourFormat(context)
        else
            timeFormat != Player.Preferences.TimeFormat.TWELVE_HOURS
    }


}