package io.ipoli.android.player.profile

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.annotation.DrawableRes
import android.support.constraint.ConstraintSet
import android.support.design.widget.TabLayout
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.widget.GridLayoutManager
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.datetime.startOfDayUTC
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.PetItem
import io.ipoli.android.player.data.AndroidAttribute
import io.ipoli.android.player.data.AndroidAvatar
import io.ipoli.android.player.data.AndroidRank
import io.ipoli.android.player.profile.ProfileViewState.StateType.*
import kotlinx.android.synthetic.main.controller_profile_friend.view.*
import kotlinx.android.synthetic.main.item_friend_profile_attribute.view.*
import kotlinx.android.synthetic.main.view_loader.view.*
import kotlinx.android.synthetic.main.view_profile_pet.view.*
import org.threeten.bp.LocalDate
import org.threeten.bp.Period
import java.util.*

class FriendProfileViewController(args: Bundle? = null) :
    ReduxViewController<ProfileAction, ProfileViewState, ProfileReducer>(args) {

    override val reducer = ProfileReducer(ProfileReducer.FRIEND_KEY + UUID.randomUUID().toString())

    private var friendId: String = ""

    private val tabListener = object : TabLayout.OnTabSelectedListener {
        override fun onTabReselected(tab: TabLayout.Tab?) {
        }

        override fun onTabUnselected(tab: TabLayout.Tab?) {
        }

        override fun onTabSelected(tab: TabLayout.Tab) {
            showPageAtPosition(view, tab.position)
        }
    }

    private fun showPageAtPosition(view: View?, position: Int) {
        view?.let {
            val childRouter = getChildRouter(it.profileTabContainer)
            childRouter.setRoot(RouterTransaction.with(getViewControllerForPage(position)))
        }
    }

    constructor(friendId: String) : this() {
        this.friendId = friendId
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = container.inflate(io.ipoli.android.R.layout.controller_profile_friend)
        setToolbar(view.toolbar)
        val collapsingToolbar = view.collapsingToolbarContainer
        collapsingToolbar.isTitleEnabled = false
        view.toolbar.title = stringRes(io.ipoli.android.R.string.controller_profile_title)
        view.toolbar.setBackgroundColor(attrData(R.attr.colorPrimary))

        var coloredBackground = view.coloredBackground.background.mutate()
        coloredBackground = DrawableCompat.wrap(coloredBackground)
        DrawableCompat.setTint(coloredBackground, attrData(R.attr.colorPrimary))
        DrawableCompat.setTintMode(coloredBackground, PorterDuff.Mode.SRC_IN)

        val avatarBackground = view.friendPlayerAvatar.background as GradientDrawable
        avatarBackground.setColor(attrData(android.R.attr.colorBackground))

        view.attributes.adapter = AttributeAdapter()
        view.attributes.layoutManager = object : GridLayoutManager(view.context, 6) {
            override fun canScrollVertically() = false
        }

        view.post {
            view.requestFocus()
        }

        view.follow.dispatchOnClick {
            ProfileAction.Follow(friendId)
        }

        view.unfollow.dispatchOnClick {
            ProfileAction.Unfollow(friendId)
        }

        return view
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        showBackButton()
        view.tabLayout.getTabAt(0)!!.select()
        view.tabLayout.addOnTabSelectedListener(tabListener)
        showPageAtPosition(view, 0)
    }

    override fun onDetach(view: View) {
        view.tabLayout.removeOnTabSelectedListener(tabListener)
        resetDecorView()
        super.onDetach(view)
    }

    private fun resetDecorView() {
        activity?.let {
            it.window.decorView.systemUiVisibility = 0
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {

            android.R.id.home ->
                return router.handleBack()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateLoadAction() = ProfileAction.Load(friendId)

    override fun render(state: ProfileViewState, view: View) {
        when (state.type) {

            LOADING -> {
                view.profileContainer.gone()
                view.loader.visible()
            }

            PROFILE_DATA_LOADED -> {
                activity!!.invalidateOptionsMenu()
                view.loader.gone()
                view.profileContainer.visible()
                renderMembershipStatus(state, view)

                renderAvatar(state, view)
                renderInfo(state, view)

                renderPet(state, view)
                renderAttributes(state, view)

                if (state.isCurrentPlayerGuest!!) {
                    view.follow.visible()
                    view.follow.setOnClickListener {
                        showShortToast(R.string.error_need_registration)
                    }
                    view.unfollow.gone()
                    view.isFollower.gone()
                    return
                }

                if (state.isFollower!!) {
                    view.isFollower.visible()
                    view.isFollower.text =
                        stringRes(R.string.player_is_follower, state.displayNameText!!)
                } else {
                    view.isFollower.gone()
                }

                if (state.isFollowing!!) {
                    view.unfollow.visible()
                    view.follow.gone()
                } else {
                    view.unfollow.gone()
                    view.follow.visible()
                }
            }

            FOLLOWING_STATUS_CHANGED -> {
                if (state.isFollowing!!) {
                    view.unfollow.visible()
                    view.follow.gone()
                } else {
                    view.unfollow.gone()
                    view.follow.visible()
                }
            }

            else -> {
            }
        }
    }

    private fun renderAttributes(state: ProfileViewState, view: View) {
        (view.attributes.adapter as AttributeAdapter).updateAll(state.attributeViewModels)
    }

    private fun renderPet(
        state: ProfileViewState,
        view: View
    ) {
        val pet = state.pet!!
        val avatar = AndroidPetAvatar.valueOf(pet.avatar.name)

        view.pet.setImageResource(avatar.image)
        view.petState.setImageResource(avatar.stateImage[pet.state]!!)
        val setItem: (ImageView, EquipmentItemViewModel?) -> Unit = { iv, vm ->
            if (vm == null) iv.invisible()
            else iv.setImageResource(vm.image)
        }
        setItem(view.hat, state.toItemViewModel(pet.equipment.hat))
        setItem(view.mask, state.toItemViewModel(pet.equipment.mask))
        setItem(view.bodyArmor, state.toItemViewModel(pet.equipment.bodyArmor))

        if (pet.equipment.hat == null) {
            val set = ConstraintSet()
            val layout = view.petContainer
            set.clone(layout)
            set.connect(R.id.pet, ConstraintSet.START, R.id.petContainer, ConstraintSet.START, 0)
            set.connect(R.id.pet, ConstraintSet.END, R.id.petContainer, ConstraintSet.END, 0)
            set.connect(R.id.pet, ConstraintSet.TOP, R.id.petContainer, ConstraintSet.TOP, 0)
            set.connect(R.id.pet, ConstraintSet.BOTTOM, R.id.petContainer, ConstraintSet.BOTTOM, 0)
            set.applyTo(layout)
        }

        val background = view.friendPetAvatar.background as GradientDrawable
        background.setColor(colorRes(R.color.md_grey_50))
    }

    private fun renderMembershipStatus(state: ProfileViewState, view: View) {
        if (state.isMember!!) {
            view.friendMembershipStatus.visible()
            view.friendMembershipStatusIcon.visible()
            val background = view.friendMembershipStatus.background as GradientDrawable
            background.setStroke(
                ViewUtils.dpToPx(2f, view.context).toInt(),
                attrData(io.ipoli.android.R.attr.colorAccent)
            )
        } else {
            view.friendMembershipStatus.gone()
            view.friendMembershipStatusIcon.gone()
        }
    }

    private fun renderInfo(state: ProfileViewState, view: View) {
        view.friendDisplayName.text = state.displayNameText
        if (state.username.isNullOrBlank()) {
            view.friendUsername.gone()
        } else {
            view.friendUsername.visible()
            @SuppressLint("SetTextI18n")
            view.friendUsername.text = "@${state.username}"
        }

        view.description.text = state.bioText
        view.level.text = state.level.toString()
        view.joined.text = state.createdAgoText
        view.rank.text = state.rankText
    }

    private fun renderAvatar(state: ProfileViewState, view: View) {
        Glide.with(view.context).load(state.avatarImage)
            .apply(RequestOptions.circleCropTransform())
            .into(view.friendPlayerAvatar)
        val background = view.friendPlayerAvatar.background as GradientDrawable
        background.setColor(colorRes(AndroidAvatar.valueOf(state.avatar.name).backgroundColor))
    }

    private val ProfileViewState.avatarImage
        get() = AndroidAvatar.valueOf(avatar.name).image

    private val ProfileViewState.displayNameText
        get() = if (displayName.isNullOrBlank())
            stringRes(io.ipoli.android.R.string.unknown_hero)
        else
            displayName

    private val ProfileViewState.createdAgoText: String
        get() {
            val p = Period.between(createdAgo.startOfDayUTC, LocalDate.now())
            return when {
                p.isZero || p.isNegative -> stringRes(io.ipoli.android.R.string.today).toLowerCase()
                p.years > 0 -> "${p.years} years ago"
                p.months > 0 -> "${p.months} months ago"
                else -> "${p.days} days ago"
            }
        }

    private val ProfileViewState.bioText
        get() =
            if (bio.isNullOrBlank())
                stringRes(io.ipoli.android.R.string.blank_bio)
            else
                bio

    private val ProfileViewState.rankText
        get() = stringRes(AndroidRank.valueOf(rank!!.name).title)

    private fun getViewControllerForPage(position: Int): Controller {
        return when (position) {
            0 -> ProfileInfoViewController(reducer.stateKey, friendId)
            1 -> ProfilePostListViewController(reducer.stateKey, friendId)
            2 -> ProfilePlayerListViewController(
                reducerKey = reducer.stateKey,
                showFollowers = false,
                playerId = friendId
            )
            3 -> ProfilePlayerListViewController(
                reducerKey = reducer.stateKey,
                showFollowers = true,
                playerId = friendId
            )
            4 -> ProfileChallengeListViewController(reducer.stateKey, friendId)
            else -> throw IllegalArgumentException("Unknown controller position $position")
        }
    }


    data class AttributeViewModel(
        override val id: String,
        val level: String,
        @DrawableRes val background: Int,
        @DrawableRes val icon: Int,
        @ColorInt val levelColor: Int
    ) : RecyclerViewViewModel

    inner class AttributeAdapter :
        BaseRecyclerViewAdapter<AttributeViewModel>(R.layout.item_friend_profile_attribute) {

        override fun onBindViewModel(vm: AttributeViewModel, view: View, holder: SimpleViewHolder) {
            view.attrBackground.setImageResource(vm.background)
            view.attrIcon.setImageResource(vm.icon)

            view.attrLevel.text = vm.level

            val square = view.attrLevelBackground.drawable as GradientDrawable
            square.mutate()
            square.color = ColorStateList.valueOf(vm.levelColor)
            view.attrLevelBackground.setImageDrawable(square)
        }
    }

    data class EquipmentItemViewModel(
        @DrawableRes val image: Int,
        val item: PetItem
    )

    private fun ProfileViewState.toItemViewModel(petItem: PetItem?): EquipmentItemViewModel? {
        val petItems = AndroidPetAvatar.valueOf(pet!!.avatar.name).items
        return petItem?.let {
            EquipmentItemViewModel(petItems[it]!!, it)
        }
    }

    private val ProfileViewState.attributeViewModels: List<AttributeViewModel>
        get() = attributes!!.map {
            val attr = AndroidAttribute.valueOf(it.type.name)
            AttributeViewModel(
                id = attr.name,
                level = it.level.toString(),
                background = attr.background,
                icon = attr.whiteIcon,
                levelColor = colorRes(attr.colorPrimaryDark)
            )
        }


}