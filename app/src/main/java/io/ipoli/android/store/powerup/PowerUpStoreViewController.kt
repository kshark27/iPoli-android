package io.ipoli.android.store.powerup

import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.annotation.ColorRes
import android.support.annotation.StringRes
import android.support.transition.TransitionManager
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.ionicons_typeface_library.Ionicons
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.pager.BasePagerAdapter
import io.ipoli.android.player.inventory.InventoryViewController
import kotlinx.android.synthetic.main.controller_power_up_store.view.*
import kotlinx.android.synthetic.main.item_disabled_power_up.view.*
import kotlinx.android.synthetic.main.item_enabled_power_up.view.*
import kotlinx.android.synthetic.main.view_inventory_toolbar.view.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 03/15/2018.
 */
enum class AndroidPowerUp(
    @StringRes val title: Int,
    @StringRes val subTitle: Int,
    @StringRes val longDescription: Int,
    val icon: IIcon,
    @ColorRes val backgroundColor: Int,
    @ColorRes val darkBackgroundColor: Int

) {
    TAGS(
        R.string.power_up_tags_title,
        R.string.power_up_tags_sub_title,
        R.string.power_up_tags_long_desc,
        MaterialDesignIconic.Icon.gmi_labels,
        R.color.md_indigo_500,
        R.color.md_indigo_700
    ),
    CALENDAR_SYNC(
        R.string.power_up_calendars_title,
        R.string.power_up_calendars_sub_title,
        R.string.power_up_calendars_long_desc,
        GoogleMaterial.Icon.gmd_event_available,
        R.color.md_teal_400,
        R.color.md_teal_600
    ),
    CUSTOM_DURATION(
        R.string.custom_duration,
        R.string.power_up_custom_duration_sub_title,
        R.string.power_up_custom_duration_long_desc,
        GoogleMaterial.Icon.gmd_timelapse,
        R.color.md_deep_orange_500,
        R.color.md_deep_orange_700
    ),
    GROWTH(
        R.string.growth,
        R.string.power_up_growth_sub_title,
        R.string.power_up_growth_long_desc,
        CommunityMaterial.Icon.cmd_chart_areaspline,
        R.color.md_light_green_700,
        R.color.md_light_green_800
    ),
    TRACK_CHALLENGE_VALUES(
        R.string.track_challenge_values,
        R.string.power_up_tracked_value_sub_title,
        R.string.power_up_tracked_value_long_desc,
        Ionicons.Icon.ion_ios_analytics,
        R.color.md_blue_600,
        R.color.md_blue_800
    ),
    HABIT_WIDGET(
        R.string.habit_widget,
        R.string.power_up_habits_widget_sub_title,
        R.string.power_up_habits_widget_long_desc,
        Ionicons.Icon.ion_heart,
        R.color.md_pink_400,
        R.color.md_pink_600
    ),
    HABITS(
        R.string.power_up_habits_title,
        R.string.power_up_habits_sub_title,
        R.string.power_up_habits_long_desc,
        Ionicons.Icon.ion_ios_nutrition,
        R.color.md_orange_500,
        R.color.md_orange_700
    )
}

class PowerUpStoreViewController(args: Bundle? = null) :
    ReduxViewController<PowerUpStoreAction, PowerUpStoreViewState, PowerUpStoreReducer>(args) {

    override val reducer = PowerUpStoreReducer

    private val onPageChangeListener = object : ViewPager.OnPageChangeListener {
        override fun onPageScrollStateChanged(state: Int) {}

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int
        ) {
        }

        override fun onPageSelected(position: Int) {
            val vm = (view!!.powerUpPager.adapter as PowerUpAdapter).itemAt(position)
            colorLayout(vm)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = inflater.inflate(
            R.layout.controller_power_up_store, container, false
        )

        view.icon.setImageDrawable(
            IconicsDrawable(view.context)
                .icon(GoogleMaterial.Icon.gmd_card_membership)
                .color(attrData(R.attr.colorAccent))
                .sizeDp(32)
        )

        view.powerUpPager.addOnPageChangeListener(onPageChangeListener)

        view.powerUpPager.adapter = PowerUpAdapter()
        view.powerUpPager.clipToPadding = false
        view.powerUpPager.pageMargin = ViewUtils.dpToPx(8f, view.context).toInt()

        view.hide.onDebounceClick {
            TransitionManager.beginDelayedTransition(view.rootCoordinator as ViewGroup)
            view.membershipHint.gone()
        }

        view.join.onDebounceClick {
            navigate().toMembership()
        }

        setChildController(
            view.playerGems,
            InventoryViewController(
                showCurrencyConverter = true,
                showCoins = true,
                showGems = false
            )
        )

        return view
    }

    override fun onCreateLoadAction() = PowerUpStoreAction.Load

    override fun onAttach(view: View) {
        super.onAttach(view)
        setToolbar(view.toolbar)
        showBackButton()
        view.toolbarTitle.text = stringRes(R.string.controller_power_up_store_title)
    }

    override fun onDestroyView(view: View) {
        view.powerUpPager.removeOnPageChangeListener(onPageChangeListener)
        super.onDestroyView(view)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            return router.handleBack()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun colorLayout(vm: PowerUpViewModel) {
        view!!.toolbar.setBackgroundColor(vm.backgroundColor)
        activity?.window?.navigationBarColor = vm.backgroundColor
        activity?.window?.statusBarColor = vm.darkBackgroundColor
    }

    override fun render(state: PowerUpStoreViewState, view: View) {
        when (state.type) {

            PowerUpStoreViewState.StateType.DATA_CHANGED -> {
                val adapter = view.powerUpPager.adapter as PowerUpAdapter
                adapter.updateAll(state.powerUpViewModels)
                val vm = adapter.itemAt(view.powerUpPager.currentItem)
                colorLayout(vm)
            }

            else -> {
            }
        }
    }

    sealed class PowerUpViewModel(
        open val icon: IIcon,
        @ColorInt open val backgroundColor: Int,
        @ColorInt open val darkBackgroundColor: Int,
        open val name: String,
        open val slogan: String,
        open val description: String
    ) {
        data class Enabled(
            override val icon: IIcon,
            @ColorInt override val backgroundColor: Int,
            @ColorInt override val darkBackgroundColor: Int,
            override val name: String,
            override val slogan: String,
            override val description: String,
            val expirationMessage: String
        ) : PowerUpViewModel(icon, backgroundColor, darkBackgroundColor, name, slogan, description)

        data class Disabled(
            val type: PowerUp.Type,
            override val icon: IIcon,
            @ColorInt override val backgroundColor: Int,
            @ColorInt override val darkBackgroundColor: Int,
            override val name: String,
            override val slogan: String,
            override val description: String
        ) : PowerUpViewModel(icon, backgroundColor, darkBackgroundColor, name, slogan, description)
    }

    inner class PowerUpAdapter :
        BasePagerAdapter<PowerUpViewModel>() {

        override fun layoutResourceFor(
            item: PowerUpViewModel
        ): Int =
            when (item) {
                is PowerUpViewModel.Enabled ->
                    R.layout.item_enabled_power_up
                is PowerUpViewModel.Disabled ->
                    R.layout.item_disabled_power_up
            }

        override fun bindItem(item: PowerUpViewModel, view: View) {
            when (item) {
                is PowerUpViewModel.Enabled ->
                    bindEnabled(item, view)
                is PowerUpViewModel.Disabled ->
                    bindDisabled(item, view)
            }
        }

        private fun bindDisabled(
            item: PowerUpViewModel.Disabled,
            view: View
        ) {

            view.pImage.setImageDrawable(
                IconicsDrawable(view.context)
                    .icon(item.icon)
                    .colorRes(R.color.md_white)
                    .sizeDp(52)
            )
            view.pImageContainer.setCardBackgroundColor(item.backgroundColor)

            view.pName.text = item.name
            view.pSlogan.text = item.slogan
            view.pDescription.text = item.description

            view.pBuy.onDebounceClick {
                navigate().toMembership()
            }
        }

        private fun bindEnabled(
            item: PowerUpViewModel.Enabled,
            view: View
        ) {
            view.eImage.setImageDrawable(
                IconicsDrawable(view.context)
                    .icon(item.icon)
                    .colorRes(R.color.md_white)
                    .sizeDp(52)
            )
            view.eImageContainer.setCardBackgroundColor(item.backgroundColor)

            view.eName.text = item.name
            view.eSlogan.text = item.slogan
            view.eDescription.text = item.description
            view.eExpiration.text = item.expirationMessage
        }
    }

    private val PowerUpStoreViewState.powerUpViewModels
        get() = powerUps.map {
            when (it) {
                is PowerUpItem.Enabled -> {
                    val expirationMessage = stringRes(
                        R.string.power_up_all_unlocked
                    )
                    val ap = AndroidPowerUp.valueOf(it.type.name)
                    PowerUpViewModel.Enabled(
                        ap.icon,
                        colorRes(ap.backgroundColor),
                        colorRes(ap.darkBackgroundColor),
                        stringRes(ap.title),
                        stringRes(ap.subTitle),
                        stringRes(ap.longDescription),
                        expirationMessage
                    )
                }

                is PowerUpItem.Disabled -> {
                    val ap = AndroidPowerUp.valueOf(it.type.name)
                    PowerUpViewModel.Disabled(
                        it.type,
                        ap.icon,
                        colorRes(ap.backgroundColor),
                        colorRes(ap.darkBackgroundColor),
                        stringRes(ap.title),
                        stringRes(ap.subTitle),
                        stringRes(ap.longDescription)
                    )
                }

            }

        }
}