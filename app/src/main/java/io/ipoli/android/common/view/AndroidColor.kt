package io.ipoli.android.common.view

import android.support.annotation.ColorRes
import io.ipoli.android.R

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 9/26/17.
 */
enum class AndroidColor(
    @ColorRes val color100: Int,
    @ColorRes val color200: Int,
    @ColorRes val color300: Int,
    @ColorRes val color400: Int,
    @ColorRes val color500: Int,
    @ColorRes val color600: Int,
    @ColorRes val color700: Int,
    @ColorRes val color800: Int,
    @ColorRes val color900: Int
) {
    RED(
        R.color.md_red_100,
        R.color.md_red_200,
        R.color.md_red_300,
        R.color.md_red_400,
        R.color.md_red_500,
        R.color.md_red_600,
        R.color.md_red_700,
        R.color.md_red_800,
        R.color.md_red_900
    ),

    GREEN(
        R.color.md_green_100,
        R.color.md_green_200,
        R.color.md_green_300,
        R.color.md_green_400,
        R.color.md_green_500,
        R.color.md_green_600,
        R.color.md_green_700,
        R.color.md_green_800,
        R.color.md_green_900
    ),

    BLUE(
        R.color.md_blue_100,
        R.color.md_blue_200,
        R.color.md_blue_300,
        R.color.md_blue_400,
        R.color.md_blue_500,
        R.color.md_blue_600,
        R.color.md_blue_700,
        R.color.md_blue_800,
        R.color.md_blue_900
    ),

    PURPLE(
        R.color.md_purple_100,
        R.color.md_purple_200,
        R.color.md_purple_300,
        R.color.md_purple_400,
        R.color.md_purple_500,
        R.color.md_purple_600,
        R.color.md_purple_700,
        R.color.md_purple_800,
        R.color.md_purple_900
    ),

    BROWN(
        R.color.md_brown_100,
        R.color.md_brown_200,
        R.color.md_brown_300,
        R.color.md_brown_400,
        R.color.md_brown_500,
        R.color.md_brown_600,
        R.color.md_brown_700,
        R.color.md_brown_800,
        R.color.md_brown_900
    ),

    ORANGE(
        R.color.md_orange_100,
        R.color.md_orange_200,
        R.color.md_orange_300,
        R.color.md_orange_400,
        R.color.md_orange_500,
        R.color.md_orange_600,
        R.color.md_orange_700,
        R.color.md_orange_800,
        R.color.md_orange_900
    ),

    PINK(
        R.color.md_pink_100,
        R.color.md_pink_200,
        R.color.md_pink_300,
        R.color.md_pink_400,
        R.color.md_pink_500,
        R.color.md_pink_600,
        R.color.md_pink_700,
        R.color.md_pink_800,
        R.color.md_pink_900
    ),

    TEAL(
        R.color.md_teal_100,
        R.color.md_teal_200,
        R.color.md_teal_300,
        R.color.md_teal_400,
        R.color.md_teal_500,
        R.color.md_teal_600,
        R.color.md_teal_700,
        R.color.md_teal_800,
        R.color.md_teal_900
    ),

    DEEP_ORANGE(
        R.color.md_deep_orange_100,
        R.color.md_deep_orange_200,
        R.color.md_deep_orange_300,
        R.color.md_deep_orange_400,
        R.color.md_deep_orange_500,
        R.color.md_deep_orange_600,
        R.color.md_deep_orange_700,
        R.color.md_deep_orange_800,
        R.color.md_deep_orange_900
    ),

    INDIGO(
        R.color.md_indigo_100,
        R.color.md_indigo_200,
        R.color.md_indigo_300,
        R.color.md_indigo_400,
        R.color.md_indigo_500,
        R.color.md_indigo_600,
        R.color.md_indigo_700,
        R.color.md_indigo_800,
        R.color.md_indigo_900
    ),

    BLUE_GREY(
        R.color.md_blue_grey_100,
        R.color.md_blue_grey_200,
        R.color.md_blue_grey_300,
        R.color.md_blue_grey_400,
        R.color.md_blue_grey_500,
        R.color.md_blue_grey_600,
        R.color.md_blue_grey_700,
        R.color.md_blue_grey_800,
        R.color.md_blue_grey_900
    ),

    LIME(
        R.color.md_lime_100,
        R.color.md_lime_200,
        R.color.md_lime_300,
        R.color.md_lime_400,
        R.color.md_lime_600,
        R.color.md_lime_700,
        R.color.md_lime_800,
        R.color.md_lime_800,
        R.color.md_lime_900
    );
}