package io.ipoli.android.store.membership

import android.content.res.ColorStateList
import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.annotation.StringRes
import android.support.transition.TransitionManager
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.SkuDetailsParams
import io.ipoli.android.Constants
import io.ipoli.android.R
import io.ipoli.android.common.billing.BillingResponseHandler
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.player.data.Membership
import io.ipoli.android.store.membership.MembershipPlan.*
import io.ipoli.android.store.membership.MembershipViewState.StateType.*
import kotlinx.android.synthetic.main.controller_membership.view.*
import kotlinx.android.synthetic.main.view_loader.view.*
import kotlinx.android.synthetic.main.view_no_elevation_toolbar.view.*
import space.traversal.kapsule.required

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 3/16/18.
 */
class MembershipViewController(args: Bundle? = null) :
    ReduxViewController<MembershipAction, MembershipViewState, MembershipReducer>(args) {

    override val reducer = MembershipReducer

    private lateinit var billingClient: BillingClient
    private val billingResponseHandler by required { billingResponseHandler }
    private val billingRequestExecutor by required { billingRequestExecutor }

    private val failureListener = object : BillingResponseHandler.FailureListener {

        override fun onCanceledByUser() {
        }

        override fun onDisconnected() {
            onBillingDisconnected()
        }

        override fun onUnavailable(responseCode: Int) {
            activity?.let {
                Toast.makeText(it, R.string.billing_unavailable, Toast.LENGTH_LONG).show()
            }
        }

        override fun onError(responseCode: Int) {
            activity?.let {
                Toast.makeText(it, R.string.purchase_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = inflater.inflate(R.layout.controller_membership, container, false)
        setToolbar(view.toolbar)

        billingClient =
            BillingClient.newBuilder(activity!!).setListener { responseCode, purchases ->
                billingResponseHandler.handle(responseCode, {
                    purchases!!.forEach { p ->
                        dispatch(
                            MembershipAction.Subscribed(
                                p.sku,
                                p.purchaseTime,
                                p.purchaseToken
                            )
                        )
                    }
                }, failureListener)
            }
                .build()

        return view
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        toolbarTitle = stringRes(R.string.membership_store_title)
        showBackButton()
        billingClient.execute { bc -> queryForSubscriptions(bc) }
    }

    override fun onDetach(view: View) {
        billingClient.endConnection()
        super.onDetach(view)
    }

    private fun queryForSubscriptions(billingClient: BillingClient) {


        val params = SkuDetailsParams.newBuilder()
            .setSkusList(MembershipPlan.values().map { it.sku })
            .setType(BillingClient.SkuType.SUBS)
            .build()

        billingClient.querySkuDetailsAsync(params) { responseCode, skuDetailsList ->
            billingResponseHandler.handle(
                responseCode, {
                    val monthlySku =
                        skuDetailsList.first { it.sku == MONTHLY.sku }
                    val quarterlySku =
                        skuDetailsList.first { it.sku == QUARTERLY.sku }
                    val yearlySku =
                        skuDetailsList.first { it.sku == YEARLY.sku }

                    val purchasesResponse =
                        billingClient.queryPurchases(BillingClient.SkuType.SUBS)
                    billingResponseHandler.handle(purchasesResponse.responseCode, {
                        dispatch(
                            MembershipAction.Load(
                                monthlyPrice = Price(
                                    monthlySku.priceAmountMicros,
                                    monthlySku.priceCurrencyCode
                                ),
                                quarterlyPrice = Price(
                                    quarterlySku.priceAmountMicros,
                                    quarterlySku.priceCurrencyCode
                                ),
                                yearlyPrice = Price(
                                    yearlySku.priceAmountMicros,
                                    yearlySku.priceCurrencyCode
                                ),
                                activeSku = purchasesResponse.purchasesList
                                    .firstOrNull {
                                        it.isAutoRenewing
                                    }?.sku
                            )
                        )
                    }, failureListener)
                }, failureListener
            )
        }
    }


    private fun onBillingDisconnected() {
        activity?.let {
            Toast.makeText(it, R.string.billing_disconnected, Toast.LENGTH_LONG).show()
        }
    }

    override fun colorStatusBars() {
        activity?.window?.statusBarColor = colorRes(R.color.md_blue_900)
        activity?.window?.navigationBarColor = colorRes(R.color.md_blue_500)
        view!!.toolbar.setBackgroundColor(colorRes(R.color.md_blue_800))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            return router.handleBack()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun render(state: MembershipViewState, view: View) {
        when (state.type) {

            LOADING -> {
                view.membershipDataContainer.gone()
            }

            DATA_CHANGED -> {
                hideLoader(view)
                view.membershipCurrentPlan.visible = state.showCurrentPlan
                view.goPremium.visible = !state.showCurrentPlan

                TransitionManager.beginDelayedTransition(view as ViewGroup)
                colorLayout(view, state)
                view.membershipMostPopular.visibleOrGone(state.showMostPopular)
                view.membershipTitle.setText(state.androidMembershipPlan.title)

                renderReasons(view, state)
                renderPrices(view, state)

                view.monthlyPlanContainer.dispatchOnClick {
                    MembershipAction.SelectPlan(MONTHLY)
                }
                view.yearlyPlanContainer.dispatchOnClick {
                    MembershipAction.SelectPlan(YEARLY)
                }
                view.quarterlyPlanContainer.dispatchOnClick {
                    MembershipAction.SelectPlan(QUARTERLY)
                }

                if (!state.showCurrentPlan) {
                    playGoPremiumAnimation(view)
                    view.goPremium.enableClick()
                    view.goPremium.text = state.premiumButtonText
                    view.goPremium.onDebounceClick {
                        view.goPremium.disableClick()
                        goPremium(state.selectedPlan, state.activeSku)
                    }
                }
            }

            SUBSCRIBED -> {
                hideLoader(view)
                showShortToast(R.string.premium_player)
                view.membershipCurrentPlan.visible = state.showCurrentPlan
                view.goPremium.visible = !state.showCurrentPlan
            }
        }

    }

    private fun hideLoader(view: View) {
        view.loader.gone()
        view.membershipDataContainer.visible()
    }

    private fun goPremium(plan: MembershipPlan, oldSku: String?) {
        val flowParams = BillingFlowParams.newBuilder()
            .setSku(plan.sku)
            .setType(BillingClient.SkuType.SUBS)
            .setOldSku(oldSku)
            .build()
        billingResponseHandler.handle(
            responseCode = billingClient.launchBillingFlow(activity!!, flowParams),
            listener = failureListener
        )
    }

    private fun playGoPremiumAnimation(view: ViewGroup) {
        view.goPremium.startAnimation(
            AnimationUtils.loadAnimation(
                view.context,
                R.anim.shake
            )
        )
    }

    private fun renderPrices(view: View, state: MembershipViewState) {
        view.membershipMonthlyPrice.text = state.monthlyPlanPrice
        view.membershipYearlyPrice.text = state.yearlyPlanPrice
        view.membershipQuarterlyPrice.text = state.quarterlyPlanPrice
    }

    private fun renderReasons(view: View, state: MembershipViewState) {
        val plan = state.androidMembershipPlan
        view.membershipMonthlyReasons.visibleOrGone(plan == AndroidMembershipPlan.MONTHLY)
        view.membershipYearlyReasons.visibleOrGone(plan == AndroidMembershipPlan.YEARLY)
        view.membershipQuarterlyReasons.visibleOrGone(plan == AndroidMembershipPlan.QUARTERLY)

        view.membershipMonthlyFreeTrial.text = stringRes(
            R.string.membership_benefit_free_trial,
            Constants.MEMBERSHIP_TRIAL_PERIOD_DAYS
        )

        view.membershipMonthlyConvertGems.text = stringRes(
            R.string.membership_benefit_convert_gems_count,
            Membership.MONTHLY.monthlyConvertedGemCap
        )

        view.membershipMonthlyQuestsHabitsReward.text = stringRes(
            R.string.membership_benefit_quests_habits_reward,
            Membership.MONTHLY.dailyHighRewardQuestCap
        )

        view.membershipYearlyFreeTrial.text = stringRes(
            R.string.membership_benefit_free_trial,
            Constants.MEMBERSHIP_TRIAL_PERIOD_DAYS
        )

        view.membershipYearlyQuestsHabitsReward.text = stringRes(
            R.string.membership_benefit_quests_habits_reward,
            Membership.YEARLY.dailyHighRewardQuestCap
        )

        view.membershipQuarterlyFreeTrial.text = stringRes(
            R.string.membership_benefit_free_trial,
            Constants.MEMBERSHIP_TRIAL_PERIOD_DAYS
        )

        view.membershipQuarterlyConvertGems.text = stringRes(
            R.string.membership_benefit_convert_gems_count,
            Membership.QUARTERLY.monthlyConvertedGemCap
        )

        view.membershipQuarterlyQuestsHabitsReward.text = stringRes(
            R.string.membership_benefit_quests_habits_reward,
            Membership.QUARTERLY.dailyHighRewardQuestCap
        )
    }

    private fun colorLayout(view: View, state: MembershipViewState) {
        val plan = AndroidMembershipPlan.valueOf(state.selectedPlan.name)
        activity?.window?.statusBarColor = colorRes(plan.darkerColor)
        val lighterColor = colorRes(plan.lighterColor)
        activity?.window?.navigationBarColor = lighterColor
        view.toolbar.setBackgroundColor(colorRes(plan.color))
        view.membershipHeaderBackground.setBackgroundColor(colorRes(plan.color))
        view.membershipHeader.setBackgroundColor(lighterColor)
        view.goPremium.backgroundTintList = ColorStateList.valueOf(lighterColor)

        val (selectedContainer, selectedLayout) = when (plan) {
            AndroidMembershipPlan.MONTHLY -> Pair(view.monthlyPlanContainer, view.monthlyPlanLayout)
            AndroidMembershipPlan.YEARLY -> Pair(view.yearlyPlanContainer, view.yearlyPlanLayout)
            AndroidMembershipPlan.QUARTERLY -> Pair(
                view.quarterlyPlanContainer,
                view.quarterlyPlanLayout
            )
        }
        resetCards(view)

        val white = colorRes(R.color.md_white)
        selectedContainer.setCardBackgroundColor(lighterColor)
        selectedLayout.children.forEach {
            (it as TextView).setTextColor(white)
        }
    }

    private fun resetCards(view: View) {
        val defaultBackground = colorRes(colorSurfaceResource)
        view.monthlyPlanContainer.setCardBackgroundColor(defaultBackground)
        view.yearlyPlanContainer.setCardBackgroundColor(defaultBackground)
        view.quarterlyPlanContainer.setCardBackgroundColor(defaultBackground)

        val black = colorRes(colorTextPrimaryResource)
        view.monthlyPlanLayout.children.forEach {
            (it as TextView).setTextColor(black)
        }
        view.yearlyPlanLayout.children.forEach {
            (it as TextView).setTextColor(black)
        }
        view.quarterlyPlanLayout.children.forEach {
            (it as TextView).setTextColor(black)
        }
    }

    private val MembershipViewState.showMostPopular: Boolean
        get() = selectedPlan == YEARLY

    private val MembershipViewState.androidMembershipPlan: AndroidMembershipPlan
        get() = AndroidMembershipPlan.valueOf(selectedPlan.name)

    private val MembershipViewState.premiumButtonText: String
        get() =
            if (currentPlan != null)
                stringRes(R.string.upgrade_now)
            else
                stringRes(R.string.go_premium)

    enum class AndroidMembershipPlan(
        @StringRes val title: Int,
        @ColorRes val lighterColor: Int,
        @ColorRes val color: Int,
        @ColorRes val darkerColor: Int
    ) {
        MONTHLY(
            R.string.monthly_plan,
            R.color.md_red_500,
            R.color.md_red_800,
            R.color.md_red_900
        ),
        YEARLY(
            R.string.yearly_plan,
            R.color.md_blue_500,
            R.color.md_blue_800,
            R.color.md_blue_900
        ),
        QUARTERLY(
            R.string.quarterly_plan,
            R.color.md_green_500,
            R.color.md_green_800,
            R.color.md_green_900
        )
    }

    private fun BillingClient.execute(request: (BillingClient) -> Unit) {
        billingRequestExecutor.execute(this, request, failureListener)
    }
}