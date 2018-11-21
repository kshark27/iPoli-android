package io.ipoli.android.player.profile

import android.content.res.ColorStateList
import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.ionicons_typeface_library.Ionicons
import io.ipoli.android.R
import io.ipoli.android.common.datetime.daysUntil
import io.ipoli.android.common.redux.android.BaseViewController
import io.ipoli.android.common.text.DateFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.MultiViewRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.player.profile.ProfileViewState.StateType.*
import kotlinx.android.synthetic.main.controller_profile_challenge_list.view.*
import kotlinx.android.synthetic.main.item_profile_challenge.view.*
import kotlinx.android.synthetic.main.view_empty_list.view.*
import kotlinx.android.synthetic.main.view_loader.view.*
import kotlinx.android.synthetic.main.view_require_login.view.*
import org.threeten.bp.LocalDate

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 7/17/18.
 */
class ProfileChallengeListViewController(args: Bundle? = null) :
    BaseViewController<ProfileAction, ProfileViewState>(args) {

    private var friendId: String? = null

    override var stateKey = ""

    constructor(reducerKey: String, friendId: String?) : this() {
        this.stateKey = reducerKey
        this.friendId = friendId
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.controller_profile_challenge_list, container, false)
        view.challengeList.layoutManager = LinearLayoutManager(view.context)
        view.challengeList.adapter = ChallengeAdapter()

        view.emptyAnimation.setAnimation("empty_challenge_list.json")

        return view
    }

    override fun onCreateLoadAction() =
        if (friendId == null) ProfileAction.LoadPlayerChallenges
        else ProfileAction.LoadFriendChallenges(friendId!!)

    override fun colorStatusBars() {

    }

    override fun render(state: ProfileViewState, view: View) {
        if (state.friendId == null) {
            view.shareItem.onDebounceClick {
                navigateFromRoot().toPostItemPicker()
            }
        } else {
            view.shareItem.gone()
        }

        when (state.type) {
            SHOW_REQUIRE_LOGIN -> {
                view.loader.gone()
                view.emptyAnimation.pauseAnimation()
                view.emptyContainer.gone()
                view.shareItem.gone()
                view.challengeList.gone()
                view.loginMessageContainer.visible()
                view.loginMessage.setText(R.string.challenges_sign_in)
                view.loginButton.onDebounceClick {
                    navigateFromRoot().toAuth(isSigningUp = true)
                }
            }

            NO_INTERNET_CONNECTION -> {
                view.loader.gone()
                view.emptyContainer.visible()
                view.emptyAnimation.pauseAnimation()
                view.emptyAnimation.gone()
                view.challengeList.gone()
                view.shareItem.gone()
                view.loginMessageContainer.gone()

                view.emptyTitle.text = stringRes(R.string.error_no_internet_title)
                view.emptyText.text = stringRes(R.string.challenges_no_internet_text)
            }

            CHALLENGE_LIST_DATA_CHANGED -> {
                showShareItemIfNotFriend(state.friendId, view)
                if (state.challenges!!.isEmpty()) {
                    view.loader.gone()
                    view.emptyContainer.visible()
                    view.challengeList.gone()
                    view.loginMessageContainer.gone()

                    view.emptyAnimation.playAnimation()
                    view.emptyAnimation.visible()
                    view.emptyTitle.setText(R.string.empty_shared_challenges_title)
                    view.emptyText.setText(R.string.empty_shared_challenges_text)
                } else {
                    view.loader.gone()
                    view.emptyContainer.gone()
                    view.emptyAnimation.pauseAnimation()
                    view.loginMessageContainer.gone()
                    view.challengeList.visible()
                    (view.challengeList.adapter as ChallengeAdapter).updateAll(state.challengeViewModels)
                }
            }

            else -> {
            }
        }
    }

    private fun showShareItemIfNotFriend(friendId: String?, view: View) {
        if (friendId == null) {
            view.shareItem.visible()
        }
    }

    enum class ItemType {
        LABEL, CHALLENGE
    }

    sealed class ChallengeViewModel(
        override val id: String
    ) : RecyclerViewViewModel {

        data class Challenge(
            override val id: String,
            val name: String,
            val icon: IIcon,
            @ColorRes val color: Int,
            val end: String
        ) : ChallengeViewModel(id)

        data class CompleteLabel(val label: String) : ChallengeViewModel(label)
    }

    inner class ChallengeAdapter : MultiViewRecyclerViewAdapter<ChallengeViewModel>() {

        override fun onRegisterItemBinders() {
            registerBinder<ChallengeViewModel.Challenge>(
                ItemType.CHALLENGE.ordinal,
                R.layout.item_profile_challenge
            ) { vm, view, _ ->
                view.cIcon.backgroundTintList =
                    ColorStateList.valueOf(view.context.colorRes(vm.color))
                view.cIcon.setImageDrawable(
                    IconicsDrawable(view.context).listItemIcon(vm.icon)
                )
                view.cName.text = vm.name
                view.cEnd.text = vm.end
            }

            registerBinder<ChallengeViewModel.CompleteLabel>(
                ItemType.LABEL.ordinal,
                R.layout.item_list_section
            ) { vm, view, _ ->
                (view as TextView).text = vm.label
            }
        }
    }

    private val ProfileViewState.challengeViewModels: List<ChallengeViewModel>
        get() {
            val (incomplete, complete) = challenges!!.partition { it.completedAtDate == null }
            val result = mutableListOf<ChallengeViewModel>()
            result.addAll(
                incomplete.map {
                    val daysUntilComplete = LocalDate.now().daysUntil(it.endDate)
                    ChallengeViewModel.Challenge(
                        id = it.id,
                        name = it.name,
                        icon = it.icon?.let { AndroidIcon.valueOf(it.name).icon }
                            ?: Ionicons.Icon.ion_checkmark,
                        color = it.color.androidColor.color500,
                        end = when {
                            daysUntilComplete < 0L -> stringRes(
                                R.string.inbox_overdue_by,
                                Math.abs(daysUntilComplete),
                                stringRes(R.string.days).toLowerCase()
                            )
                            daysUntilComplete == 0L -> stringRes(R.string.ends_today)
                            daysUntilComplete <= 7 -> stringRes(
                                R.string.ends_in_days,
                                daysUntilComplete
                            )
                            else -> stringRes(
                                R.string.ends_at_date,
                                DateFormatter.formatWithoutYear(activity!!, it.endDate)
                            )
                        }
                    )
                }
            )

            if (complete.isNotEmpty()) {
                result.add(
                    ChallengeViewModel.CompleteLabel(stringRes(R.string.completed))
                )
            }

            result.addAll(
                complete.map {
                    ChallengeViewModel.Challenge(
                        id = it.id,
                        name = it.name,
                        icon = it.icon?.let { AndroidIcon.valueOf(it.name).icon }
                            ?: Ionicons.Icon.ion_checkmark,
                        color = it.color.androidColor.color500,
                        end = stringRes(
                            R.string.completed_at_date,
                            DateFormatter.format(activity!!, it.completedAtDate)
                        )
                    )
                }
            )

            return result
        }
}