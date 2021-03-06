package io.ipoli.android.player

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 11/14/17.
 */
object ExperienceForLevelGenerator {

    private val REQUIREMENTS = longArrayOf(
        0,
        50,
        150,
        300,
        500,
        860,
        1340,
        1900,
        2540,
        3260,
        4060,
        4940,
        5900,
        6940,
        8060,
        9260,
        10540,
        11900,
        13340,
        14860,
        16460,
        18140,
        19900,
        21740,
        23660,
        25660,
        27740,
        29900,
        32140,
        34460,
        36860,
        39340,
        41900,
        44540,
        47260,
        50060,
        52940,
        55900,
        58940,
        62060,
        65260,
        68540,
        71900,
        75340,
        78860,
        82460,
        86140,
        89900,
        93740,
        97660,
        101660,
        105740,
        109900,
        114140,
        118460,
        122860,
        127340,
        131900,
        136540,
        142840,
        150400,
        159472,
        170358,
        183421,
        199096,
        217906,
        240478,
        267564,
        300067,
        339070,
        385873,
        442036,
        509431,
        590305,
        687353,
        803810,
        943558,
        1111255,
        1312491,
        1553974,
        1843753,
        2191487,
        2608767,
        3109503,
        3710386,
        4431445,
        5296715,
        6335039,
        7581027,
        9076212,
        10870434,
        13023500,
        15607179,
        18707593,
        22428089,
        26892684,
        32250198,
        38679214,
        46394033,
        55651815
    )

    fun forLevel(level: Int) = REQUIREMENTS[level - 1]
}