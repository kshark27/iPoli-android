package io.ipoli.android.player.profile

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.auth.FirebaseAuth
import io.ipoli.android.R
import io.ipoli.android.common.redux.android.BaseViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.player.data.AndroidAvatar
import io.ipoli.android.player.profile.ProfilePlayerListViewController.FriendViewModel.FriendshipStatus.*
import io.ipoli.android.player.profile.ProfileViewState.StateType.*
import kotlinx.android.synthetic.main.controller_profile_player_list.view.*
import kotlinx.android.synthetic.main.item_friend_list.view.*
import kotlinx.android.synthetic.main.view_empty_list.view.*
import kotlinx.android.synthetic.main.view_loader.view.*
import kotlinx.android.synthetic.main.view_require_login.view.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 7/21/18.
 */
class ProfilePlayerListViewController(args: Bundle? = null) :
    BaseViewController<ProfileAction, ProfileViewState>(args) {

    override var stateKey = ""

    private var playerId: String? = null
    private var showFollowers = false

    constructor(reducerKey: String, showFollowers: Boolean, playerId: String? = null) : this() {
        this.stateKey = reducerKey
        this.playerId = playerId
        this.showFollowers = showFollowers
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = container.inflate(R.layout.controller_profile_player_list)

        view.friendList.layoutManager = LinearLayoutManager(view.context)
        view.friendList.adapter = FriendAdapter()

        view.emptyAnimation.setAnimation("empty_friends.json")

        return view
    }

    override fun onCreateLoadAction() =
        if (showFollowers)
            ProfileAction.LoadFollowers(playerId)
        else
            ProfileAction.LoadFollowing(playerId)

    override fun colorStatusBars() {

    }

    override fun render(state: ProfileViewState, view: View) {
        when (state.type) {
            SHOW_REQUIRE_LOGIN -> {
                view.loader.gone()
                view.emptyAnimation.pauseAnimation()
                view.emptyContainer.gone()
                view.friendList.gone()
                view.loginMessageContainer.visible()
                view.loginMessage.setText(R.string.friends_sign_in)
                view.loginButton.onDebounceClick {
                    navigateFromRoot().toAuth(isSigningUp = true)
                }
            }

            NO_INTERNET_CONNECTION -> {
                view.loader.gone()
                view.emptyContainer.visible()
                view.emptyAnimation.pauseAnimation()
                view.emptyAnimation.gone()
                view.friendList.gone()
                view.loginMessageContainer.gone()

                view.emptyTitle.text = stringRes(R.string.error_no_internet_title)
                view.emptyText.text = stringRes(R.string.friends_no_internet_text)
            }

            FOLLOWING_LIST_CHANGED -> {
                if (showFollowers) {
                    return
                }
                view.loader.gone()
                view.emptyAnimation.pauseAnimation()
                if (state.following!!.isEmpty()) {
                    view.emptyContainer.visible()
                    view.emptyAnimation.visible()
                    view.emptyAnimation.playAnimation()
                    view.emptyTitle.setText(R.string.empty_friend_list_title)
                    view.emptyText.setText(R.string.empty_friend_list_text)
                    view.friendList.gone()
                } else {
                    view.emptyContainer.gone()
                    view.friendList.visible()
                    view.friendTitle.setText(R.string.following)
                    (view.friendList.adapter as FriendAdapter).updateAll(state.followingViewModels)
                }
            }

            FOLLOWER_LIST_CHANGED -> {
                if (!showFollowers) {
                    return
                }
                view.loader.gone()
                view.emptyAnimation.pauseAnimation()
                if (state.followers!!.isEmpty()) {
                    view.emptyContainer.visible()
                    view.emptyAnimation.visible()
                    view.emptyAnimation.playAnimation()
                    view.emptyTitle.setText(R.string.empty_friend_list_title)
                    view.emptyText.setText(R.string.empty_friend_list_text)
                    view.friendList.gone()
                } else {
                    view.emptyContainer.gone()
                    view.friendList.visible()
                    view.friendTitle.setText(R.string.followers)
                    (view.friendList.adapter as FriendAdapter).updateAll(state.followersViewModels)
                }
            }

            else -> {
            }
        }
    }

    data class FriendViewModel(
        override val id: String,
        val avatar: AndroidAvatar,
        val name: String,
        val username: String,
        val level: String,
        val friendshipStatus: FriendshipStatus
    ) : RecyclerViewViewModel {
        enum class FriendshipStatus {
            FOLLOWING, FOLLOWER, YOU, NONE
        }
    }


    inner class FriendAdapter :
        BaseRecyclerViewAdapter<FriendViewModel>(R.layout.item_friend_list) {

        override fun onBindViewModel(vm: FriendViewModel, view: View, holder: SimpleViewHolder) {
            Glide.with(view.context).load(vm.avatar.image)
                .apply(RequestOptions.circleCropTransform())
                .into(view.playerAvatar)

            val gradientDrawable = view.playerAvatar.background as GradientDrawable
            gradientDrawable.mutate()
            gradientDrawable.setColor(colorRes(vm.avatar.backgroundColor))

            view.playerName.text = vm.name
            view.playerUsername.text = vm.username
            view.playerLevel.text = vm.level

            when (vm.friendshipStatus) {
                NONE, YOU -> {
                    view.playerFollow.gone()
                    view.playerUnfollow.gone()
                }
                FOLLOWING -> {
                    view.playerFollow.gone()
                    view.playerUnfollow.visible()
                }
                else -> {
                    view.playerFollow.visible()
                    view.playerUnfollow.gone()
                }
            }

            view.playerFollow.dispatchOnClick {
                view.playerFollow.gone()
                view.playerUnfollow.visible()
                ProfileAction.Follow(vm.id)
            }

            view.playerUnfollow.dispatchOnClick {
                view.playerFollow.visible()
                view.playerUnfollow.gone()
                ProfileAction.Unfollow(vm.id)
            }

            if (vm.friendshipStatus == YOU) {
                view.setOnClickListener(null)
            } else {
                view.onDebounceClick {
                    navigateFromRoot().toProfile(vm.id)
                }
            }
        }
    }

    private val ProfileViewState.followingViewModels: List<FriendViewModel>
        get() {
            val playerId = FirebaseAuth.getInstance().currentUser?.uid
            return following!!.map {
                FriendViewModel(
                    id = it.id,
                    avatar = AndroidAvatar.valueOf(it.avatar.name),
                    name = it.displayName,
                    username = "@${it.username}",
                    level = "Lvl ${it.level}",
                    friendshipStatus = when {
                        playerId == null -> NONE
                        it.id == playerId -> YOU
                        it.isFollowing -> FOLLOWING
                        else -> FOLLOWER
                    }
                )
            }
        }

    private val ProfileViewState.followersViewModels: List<FriendViewModel>
        get() {
            val playerId = FirebaseAuth.getInstance().currentUser?.uid
            return followers!!.map {
                FriendViewModel(
                    id = it.id,
                    avatar = AndroidAvatar.valueOf(it.avatar.name),
                    name = it.displayName,
                    username = "@${it.username}",
                    level = "Lvl ${it.level}",
                    friendshipStatus = when {
                        playerId == null -> NONE
                        it.id == playerId -> YOU
                        it.isFollowing -> FOLLOWING
                        else -> FOLLOWER
                    }
                )
            }
        }
}