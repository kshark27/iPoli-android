package io.ipoli.android.store.powerup

import org.threeten.bp.LocalDate

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 03/15/2018.
 */

data class PowerUp(
    val type: Type,
    val coinPrice: Int,
    val expirationDate: LocalDate
) {

    companion object {
        fun fromType(type: PowerUp.Type, expirationDate: LocalDate) =
            PowerUp(type, type.coinPrice, expirationDate)
    }

    enum class Type(val coinPrice: Int) {
        TAGS(300),
        GROWTH(300),
        CALENDAR_SYNC(450),
        CUSTOM_DURATION(130),
        TRACK_CHALLENGE_VALUES(400),
        HABIT_WIDGET(500),
        HABITS(350)
    }
}
