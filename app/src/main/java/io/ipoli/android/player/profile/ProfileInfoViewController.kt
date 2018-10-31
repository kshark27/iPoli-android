package io.ipoli.android.player.profile

import android.content.res.ColorStateList
import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.annotation.DrawableRes
import android.support.v7.widget.GridLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.ipoli.android.R
import io.ipoli.android.achievement.androidAchievement
import io.ipoli.android.common.redux.android.BaseViewController
import io.ipoli.android.common.view.colorRes
import io.ipoli.android.common.view.gone
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.common.view.stringRes
import io.ipoli.android.common.view.visible
import io.ipoli.android.player.data.Membership
import io.ipoli.android.player.profile.ProfileViewState.StateType.PROFILE_DATA_LOADED
import io.ipoli.android.player.profile.ProfileViewState.StateType.PROFILE_INFO_LOADED
import kotlinx.android.synthetic.main.controller_profile_info.view.*
import kotlinx.android.synthetic.main.item_achievement.view.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 7/17/18.
 */
class ProfileInfoViewController(args: Bundle? = null) :
    BaseViewController<ProfileAction, ProfileViewState>(args) {

    override var stateKey = ""

    private var friendId: String? = null

    constructor(reducerKey: String, friendId: String? = null) : this() {
        this.stateKey = reducerKey
        this.friendId = friendId
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.controller_profile_info, container, false)
        view.achievementList.layoutManager = object : GridLayoutManager(view.context, 5) {
            override fun canScrollVertically() = false
        }
        view.achievementList.adapter = AchievementAdapter()
        return view
    }

    override fun onCreateLoadAction() = ProfileAction.LoadInfo

    override fun colorStatusBars() {

    }

    override fun render(state: ProfileViewState, view: View) {
        when (state.type) {

            PROFILE_INFO_LOADED,
            PROFILE_DATA_LOADED -> {
                renderPlayerStats(state, view)
                renderPlayerMembershipStats(state, view)
                renderAchievements(state, view)
            }

            else -> {
            }
        }
    }

    private fun renderPlayerMembershipStats(state: ProfileViewState, view: View) {
        view.completedQuests.text = state.todayCompletedQuestsText
        view.completedHabits.text = state.todayCompletedHabitsText
        view.convertedGems.text = state.convertedGemsText
    }

    private fun renderPlayerStats(state: ProfileViewState, view: View) {
        view.playerStat1.animateToValueFromZero(state.dailyChallengeStreak)
        view.playerStat2.animateToValueFromZero(state.last7DaysAverageProductiveDuration!!.asHours.intValue)
    }

    private fun renderAchievements(
        state: ProfileViewState,
        view: View
    ) {
        (view.achievementList.adapter as AchievementAdapter).updateAll(state.achievementViewModels)
        if (state.unlockedAchievements.isEmpty()) {
            view.achievementList.gone()
            view.emptyAchievements.visible()
        } else {
            view.achievementList.visible()
            view.emptyAchievements.gone()
        }
    }

    data class AchievementViewModel(
        override val id: String,
        @DrawableRes val icon: Int,
        @ColorRes val backgroundColor: Int,
        val hasStars: Boolean,
        val starsCount: Int = 0
    ) : RecyclerViewViewModel

    inner class AchievementAdapter :
        BaseRecyclerViewAdapter<AchievementViewModel>(R.layout.item_achievement) {

        override fun onBindViewModel(
            vm: AchievementViewModel,
            view: View,
            holder: SimpleViewHolder
        ) {
            view.achievementIcon.setImageResource(vm.icon)
            view.achievementBackground.backgroundTintList =
                ColorStateList.valueOf(colorRes(vm.backgroundColor))
            if (vm.hasStars) {
                view.stars.visible()
                view.star1.setImageResource(if (vm.starsCount >= 1) R.drawable.achievement_star else R.drawable.achievement_star_empty)
                view.star2.setImageResource(if (vm.starsCount >= 2) R.drawable.achievement_star else R.drawable.achievement_star_empty)
                view.star3.setImageResource(if (vm.starsCount == 3) R.drawable.achievement_star else R.drawable.achievement_star_empty)
            } else {
                view.stars.gone()
            }

            if(friendId == null) {
                view.onDebounceClick {
                    navigateFromRoot().toAchievementList()
                }
            } else {
                view.setOnClickListener(null)
            }
        }

    }

    private val ProfileViewState.achievementViewModels: List<AchievementViewModel>
        get() =
            unlockedAchievements.map {
                val aa = it.androidAchievement
                val starsToShow = if (!it.isMultiLevel) -1 else it.currentLevel
                AchievementViewModel(
                    id = stringRes(aa.title),
                    icon = aa.icon,
                    backgroundColor = aa.color,
                    hasStars = starsToShow > 0,
                    starsCount = starsToShow
                )
            }

    private val ProfileViewState.todayCompletedQuestsText
        get() = "${Math.min(
            todayCompletedQuests!!,
            membership!!.dailyHighRewardQuestCap
        )}/${membership.dailyHighRewardQuestCap}"

    private val ProfileViewState.todayCompletedHabitsText
        get() = "${Math.min(
            todayCompletedHabits!!,
            membership!!.dailyHighRewardHabitCap
        )}/${membership.dailyHighRewardHabitCap}"

    @Suppress("IMPLICIT_CAST_TO_ANY")
    private val ProfileViewState.convertedGemsText
        get() = "$thisMonthConvertedGems/" +
            "${if (membership!!.monthlyConvertedGemCap == Membership.UNLIMITED)
                stringRes(R.string.unlimited)
            else
                membership.monthlyConvertedGemCap
            }"
}