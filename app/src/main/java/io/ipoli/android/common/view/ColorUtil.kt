package io.ipoli.android.common.view

import android.graphics.Color
import android.support.annotation.ColorInt
import android.support.annotation.FloatRange


/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 03/11/2018.
 */
object ColorUtil {
    private val GOOGLE_CALENDAR_COLOR_MAPPING = mapOf(
        -509406 to -2818048,
        -509406 to -2818048,
        -370884 to -765666,
        -35529 to -1086464,
        -21178 to -1010944,
        -339611 to -606426,
        -267901 to -1784767,
        -4989844 to -4142541,
        -8662712 to -8604862,
        -15292571 to -16023485,
        -12396910 to -16738680,
        -7151168 to -13388167,
        -6299161 to -16540699,
        -6306073 to -12417548,
        -11958553 to -12627531,
        -6644481 to -8812853,
        -4613377 to -5005861,
        -5997854 to -6395473,
        -3312410 to -7461718,
        -3365204 to -5434281,
        -618062 to -2614432,
        -3118236 to -1672077,
        -5475746 to -8825528,
        -4013374 to -10395295,
        -3490369 to -5792882,
        -2350809 to -2818048,
        -18312 to -765666,
        -272549 to -606426,
        -11421879 to -16023485,
        -8722497 to -13388167,
        -12134693 to -16540699,
        -11238163 to -12627531,
        -5980676 to -8812853,
        -2380289 to -7461718,
        -30596 to -1672077,
        -1973791 to -10395295,
        -2883584 to -2818048,
        -831459 to -765666,
        -1152256 to -1086464,
        -1076736 to -1010944,
        -672219 to -606426,
        -1914036 to -1784767,
        -4208334 to -4142541,
        -8670655 to -8604862,
        -16089278 to -16023485,
        -16738937 to -16738680,
        -16606492 to -16540699,
        -12483341 to -12417548,
        -12624727 to -12627531,
        -8878646 to -8812853,
        -5071654 to -5005861,
        -7527511 to -7461718,
        -5500074 to -5434281,
        -2680225 to -2614432,
        -1737870 to -1672077,
        -8891321 to -8825528,
        -10263709 to -10395295
    )


    fun fromGoogleCalendarDisplayColor(color: Int): Int {

        // taken from https://stackoverflow.com/questions/19775686/android-calendar-color-from-calendar-color-differ-from-real-calendar-color

        if (GOOGLE_CALENDAR_COLOR_MAPPING.containsKey(color)) {
            return GOOGLE_CALENDAR_COLOR_MAPPING[color]!!
        }
        if (GOOGLE_CALENDAR_COLOR_MAPPING.containsValue(color)) {
            return color
        }
        val colorData = FloatArray(3)
        Color.colorToHSV(color, colorData)
        if (colorData[2] > 0.79f) {
            colorData[1] = Math.min(colorData[1] * 1.3f, 1.0f)
            colorData[2] = colorData[2] * 0.8f
        }
        return Color.HSVToColor(Color.alpha(color), colorData)
    }

    @ColorInt
    fun darkenColor(@ColorInt color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] *= 0.5f
        return Color.HSVToColor(hsv)
    }

    @ColorInt
    fun lightenColor(@ColorInt color: Int, factor: Float): Int {
        return Color.argb(
            Color.alpha(color),
            ((Color.red(color) * (1 - factor) / 255 + factor) * 255).toInt(),
            ((Color.green(color) * (1 - factor) / 255 + factor) * 255).toInt(),
            ((Color.blue(color) * (1 - factor) / 255 + factor) * 255).toInt()
        )
    }

    fun alphaInt(@FloatRange(from = 0.0, to = 1.0) value: Float): Int {
        return (255 * value).toInt()
    }

    fun getColorWithAlpha(@ColorInt color: Int, ratio: Float): Int {
        return Color.argb(Math.round(Color.alpha(color) * ratio), Color.red(color), Color.green(color), Color.blue(color))
    }
}