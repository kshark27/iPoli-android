package io.ipoli.android.player.profile

import android.animation.Animator
import android.animation.AnimatorInflater
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import com.google.firebase.auth.FirebaseAuth
import io.ipoli.android.R
import io.ipoli.android.common.redux.android.BaseViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.friends.feed.*
import io.ipoli.android.friends.feed.data.AndroidReactionType
import io.ipoli.android.friends.feed.data.Post
import io.ipoli.android.player.profile.ProfileViewState.StateType.*
import kotlinx.android.synthetic.main.controller_profile_post_list.view.*
import kotlinx.android.synthetic.main.item_popup_reaction.view.*
import kotlinx.android.synthetic.main.view_empty_list.view.*
import kotlinx.android.synthetic.main.view_loader.view.*
import kotlinx.android.synthetic.main.view_require_login.view.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 7/23/18.
 */
class ProfilePostListViewController(args: Bundle? = null) :
    BaseViewController<ProfileAction, ProfileViewState>(args), ReactionPopupHandler {

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
        val view = inflater.inflate(R.layout.controller_profile_post_list, container, false)

        if (friendId == null) {
            view.shareItem.onDebounceClick {
                navigateFromRoot().toPostItemPicker()
            }
        } else {
            view.shareItem.gone()
        }

        view.reactionPopup.layoutManager = GridLayoutManager(view.context, 3)
        view.reactionPopup.adapter = ReactionPopupAdapter(this)
        (view.reactionPopup.adapter as ReactionPopupAdapter).updateAll(
            AndroidReactionType.values().map { rt ->
                val title = stringRes(rt.title)
                ReactionPopupViewModel(
                    title,
                    rt.animation,
                    title,
                    Post.ReactionType.valueOf(rt.name)
                ) {
                    if (FirebaseAuth.getInstance().currentUser == null) {
                        showShortToast(R.string.error_need_registration)
                    } else {
                        dispatch(ProfileAction.ReactToPost(it, friendId))
                    }
                }
            }
        )

        view.feedList.setOnTouchListener { _, _ ->
            if (isPopupShown()) {
                hideReactionPopup()
                true
            } else {
                false
            }
        }

        view.feedList.layoutManager = LinearLayoutManager(view.context)
        view.feedList.adapter = PostAdapter(this, activity!!)

        view.emptyAnimation.setAnimation("empty_posts.json")

        return view
    }

    override fun onCreateLoadAction() =
        ProfileAction.LoadPosts(playerId = friendId)

    override fun colorStatusBars() {

    }

    override fun onDetach(view: View) {
        dispatch(ProfileAction.UnloadPosts)
        super.onDetach(view)
    }

    override fun handleBack() =
        if (view != null && view!!.reactionPopup.visible) {
            hideReactionPopup()
            true
        } else false

    override fun render(state: ProfileViewState, view: View) {
        when (state.type) {
            SHOW_REQUIRE_LOGIN -> {
                view.loader.gone()
                view.emptyContainer.gone()
                view.feedList.gone()
                view.reactionPopup.gone()
                view.shareItem.gone()
                view.loginMessageContainer.visible()
                view.loginMessage.setText(R.string.posts_sign_in)
                view.loginButton.onDebounceClick {
                    navigateFromRoot().toAuth(isSigningUp = true)
                }
            }

            NO_INTERNET_CONNECTION -> {
                view.loader.gone()
                view.emptyContainer.visible()
                view.emptyAnimation.pauseAnimation()
                view.emptyAnimation.gone()
                view.feedList.gone()
                view.reactionPopup.gone()
                view.shareItem.gone()
                view.loginMessageContainer.gone()

                view.emptyTitle.text = stringRes(R.string.error_no_internet_title)
                view.emptyText.text = stringRes(R.string.posts_no_internet_text)
            }

            POSTS_CHANGED -> {

                if (state.posts!!.isNotEmpty()) {
                    renderPosts(state, view)
                } else {
                    renderEmptyPosts(view)
                }
            }

            else -> {
            }
        }
    }

    private fun renderEmptyPosts(view: View) {
        view.loader.gone()
        view.emptyContainer.visible()
        view.feedList.gone()
        view.reactionPopup.gone()
        showShareItemIfNotFriend(view)

        view.emptyAnimation.playAnimation()
        view.emptyAnimation.visible()
        view.emptyTitle.text = stringRes(R.string.feed_empty_title)
        view.emptyText.text = stringRes(R.string.feed_empty_text)
    }

    private fun renderPosts(
        state: ProfileViewState,
        view: View
    ) {

        view.loader.gone()
        view.emptyContainer.gone()
        view.reactionPopup.gone()
        view.loginMessageContainer.gone()
        view.feedList.visible()
        showShareItemIfNotFriend(view)

        val postVMs = state.posts!!.map { p ->
            toPostViewModel(context = activity!!, post = p,
                reactListener = { postId, _ ->
                    dispatch(ProfileAction.ShowReactionPopupForPost(postId))
                },
                reactListListener = {
                    if (FirebaseAuth.getInstance().currentUser == null) {
                        showShortToast(R.string.error_need_registration)
                    } else {
                        navigate().toReactionHistory(it)
                    }
                },
                commentsListener = {
                    if (FirebaseAuth.getInstance().currentUser == null) {
                        showShortToast(R.string.error_need_registration)
                    } else {
                        navigateFromRoot().toPost(it)
                    }
                },
                shareListener = { m ->
                    navigate().toSharePost(m)
                },
                postClickListener = {
                    if (FirebaseAuth.getInstance().currentUser == null) {
                        showShortToast(R.string.error_need_registration)
                    } else {
                        navigateFromRoot().toPost(it)
                    }
                }
            )
        }

        (view.feedList.adapter as PostAdapter).updateAll(postVMs)
    }

    private fun showShareItemIfNotFriend(view: View) {
        if (friendId == null) {
            view.shareItem.visible()
        }
    }

    override fun isPopupShown() = view?.let { it.reactionPopup.alpha > 0 } ?: false

    override fun containerCoordinates() =
        view?.let {
            val parentLoc = IntArray(2)
            view!!.container.getLocationInWindow(parentLoc)
            parentLoc
        } ?: intArrayOf(0, 0)

    override fun hideReactionPopup() {
        view?.reactionPopup?.children?.forEach {
            it.reactionAnimation.cancelAnimation()
        }
        view?.let {
            playAnimation(
                createPopupAnimator(
                    it.reactionPopup, true
                ),
                onEnd = { it.reactionPopup.gone() }
            )
        }
    }

    override fun showReactionPopup(x: Int, y: Float) {
        view?.let {
            val reactionPopup = it.reactionPopup
            reactionPopup.x = x.toFloat()
            reactionPopup.y = y

            playAnimation(
                createPopupAnimator(reactionPopup),
                onStart = { reactionPopup.visible() },
                onEnd = {
                    it.reactionPopup.children.forEach { v ->
                        v.reactionAnimation.playAnimation()
                    }
                })
        }
    }

    private fun createPopupAnimator(view: View, reverse: Boolean = false): Animator {
        val anim = if (reverse) R.animator.popout else R.animator.popup
        val animator = AnimatorInflater.loadAnimator(view.context, anim)
        animator.setTarget(view)
        animator.interpolator = OvershootInterpolator()
        animator.duration = shortAnimTime
        return animator
    }

}