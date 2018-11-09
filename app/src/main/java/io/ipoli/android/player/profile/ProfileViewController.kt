package io.ipoli.android.player.profile

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.design.widget.TabLayout
import android.support.v4.graphics.drawable.DrawableCompat
import android.view.*
import android.widget.ProgressBar
import android.widget.TextView
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.text.LongFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.player.data.AndroidAttribute
import io.ipoli.android.player.data.AndroidAvatar
import io.ipoli.android.player.data.AndroidRank
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.profile.ProfileViewState.StateType.LOADING
import io.ipoli.android.player.profile.ProfileViewState.StateType.PROFILE_DATA_LOADED
import kotlinx.android.synthetic.main.controller_profile.view.*
import kotlinx.android.synthetic.main.profile_charisma_attribute.view.*
import kotlinx.android.synthetic.main.profile_expertise_attribute.view.*
import kotlinx.android.synthetic.main.profile_intelligence_attribute.view.*
import kotlinx.android.synthetic.main.profile_strength_attribute.view.*
import kotlinx.android.synthetic.main.profile_wellbeing_attribute.view.*
import kotlinx.android.synthetic.main.profile_willpower_attribute.view.*
import kotlinx.android.synthetic.main.view_loader.view.*

class ProfileViewController(args: Bundle? = null) :
    ReduxViewController<ProfileAction, ProfileViewState, ProfileReducer>(args) {

    override val reducer = ProfileReducer(ProfileReducer.PROFILE_KEY)

    override var helpConfig: HelpConfig? =
        HelpConfig(
            io.ipoli.android.R.string.help_dialog_profile_title,
            io.ipoli.android.R.string.help_dialog_profile_message
        )

    private var isEdit: Boolean = false

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = container.inflate(io.ipoli.android.R.layout.controller_profile)
        setToolbar(view.toolbar)
        val collapsingToolbar = view.collapsingToolbarContainer
        collapsingToolbar.isTitleEnabled = false
        view.toolbar.title = stringRes(io.ipoli.android.R.string.controller_profile_title)
        view.toolbar.setBackgroundColor(attrData(R.attr.colorPrimary))

        var coloredBackground = view.coloredBackground.background.mutate()
        coloredBackground = DrawableCompat.wrap(coloredBackground)
        DrawableCompat.setTint(coloredBackground, attrData(R.attr.colorPrimary))
        DrawableCompat.setTintMode(coloredBackground, PorterDuff.Mode.SRC_IN)

        val avatarBackground = view.playerAvatar.background as GradientDrawable
        avatarBackground.setColor(attrData(android.R.attr.colorBackground))

        view.post {
            view.requestFocus()
        }

        return view
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        showBackButton()
        view.tabLayout.addOnTabSelectedListener(tabListener)
        view.tabLayout.getTabAt(0)!!.select()
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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(io.ipoli.android.R.menu.profile_menu, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        val editAction = menu.findItem(io.ipoli.android.R.id.actionEdit)
        editAction.isVisible = !isEdit
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {

            android.R.id.home ->
                return router.handleBack()

            io.ipoli.android.R.id.actionEdit ->
                navigate().toEditProfile()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateLoadAction() = ProfileAction.Load(null)

    override fun render(state: ProfileViewState, view: View) {
        when (state.type) {

            LOADING -> {
                view.profileContainer.gone()
                view.loader.visible()
            }

            PROFILE_DATA_LOADED -> {
                isEdit = false
                activity!!.invalidateOptionsMenu()
                view.loader.gone()
                view.profileContainer.visible()

                renderInfo(state, view)
                renderAvatar(state, view)
                renderMembershipStatus(state, view)
                renderAttributes(state, view)
                renderLevelProgress(state, view)
                renderHealth(state, view)
                renderCoinsAndGems(state, view)
                renderRank(state, view)
            }

            else -> {
            }
        }
    }

    private fun renderRank(state: ProfileViewState, view: View) {
        view.rank.text = state.rankText
        view.nextRank.text = state.nextRankText
    }

    private fun renderAttributes(state: ProfileViewState, view: View) {
        view.moreAttributes.onDebounceClick {
            navigateFromRoot().toAttributes()
        }

        val vms = state.attributeViewModels

        renderAttribute(
            vms[Player.AttributeType.STRENGTH]!!,
            view.strengthProgress,
            view.strengthLevel,
            view.strengthProgressText,
            view.strengthContainer
        )

        renderAttribute(
            vms[Player.AttributeType.INTELLIGENCE]!!,
            view.intelligenceProgress,
            view.intelligenceLevel,
            view.intelligenceProgressText,
            view.intelligenceContainer
        )

        renderAttribute(
            vms[Player.AttributeType.CHARISMA]!!,
            view.charismaProgress,
            view.charismaLevel,
            view.charismaProgressText,
            view.charismaContainer
        )

        renderAttribute(
            vms[Player.AttributeType.EXPERTISE]!!,
            view.expertiseProgress,
            view.expertiseLevel,
            view.expertiseProgressText,
            view.expertiseContainer
        )

        renderAttribute(
            vms[Player.AttributeType.WELL_BEING]!!,
            view.wellbeingProgress,
            view.wellbeingLevel,
            view.wellbeingProgressText,
            view.wellbeingContainer
        )

        renderAttribute(
            vms[Player.AttributeType.WILLPOWER]!!,
            view.willpowerProgress,
            view.willpowerLevel,
            view.willpowerProgressText,
            view.willpowerContainer
        )
    }

    private fun renderAttribute(
        attribute: AttributeViewModel,
        progressView: ProgressBar,
        levelView: TextView,
        levelProgressView: TextView,
        container: View
    ) {
        progressView.progress = attribute.progress
        progressView.max = attribute.max
        progressView.progressTintList =
            ColorStateList.valueOf(attribute.progressColor)
        progressView.secondaryProgressTintList =
            ColorStateList.valueOf(attribute.secondaryColor)
        container.onDebounceClick {
            navigateFromRoot().toAttributes(attribute.type)
        }
        levelView.text = attribute.level
        levelProgressView.text = attribute.progressText
    }


    private fun renderMembershipStatus(state: ProfileViewState, view: View) {
        if (state.isMember!!) {
            view.membershipStatus.visible()
            view.membershipStatusIcon.visible()
            val background = view.membershipStatus.background as GradientDrawable
            background.setStroke(
                ViewUtils.dpToPx(2f, view.context).toInt(),
                attrData(io.ipoli.android.R.attr.colorPrimary)
            )
        } else {
            view.membershipStatus.gone()
            view.membershipStatusIcon.gone()
        }
    }

    private fun renderCoinsAndGems(state: ProfileViewState, view: View) {
        view.coins.text = state.lifeCoinsText
        view.gems.text = state.gemsText
    }

    private fun renderInfo(state: ProfileViewState, view: View) {
        view.profileDisplayName.text = state.displayNameText
        if (state.username.isNullOrBlank()) {
            view.username.gone()
        } else {
            view.username.visible()
            @SuppressLint("SetTextI18n")
            view.username.text = "@${state.username}"
        }
    }

    private fun renderAvatar(state: ProfileViewState, view: View) {
        Glide.with(view.context).load(state.avatarImage)
            .apply(RequestOptions.circleCropTransform())
            .into(view.playerAvatar)
        val background = view.playerAvatar.background as GradientDrawable
        background.setColor(colorRes(AndroidAvatar.valueOf(state.avatar.name).backgroundColor))

        view.playerAvatar.onDebounceClick {
            navigateFromRoot().toAvatarStore(HorizontalChangeHandler())
        }
    }

    private fun renderHealth(state: ProfileViewState, view: View) {
        view.healthProgress.max = state.maxHealth
        view.healthProgress.animateProgressFromZero(state.health)
        view.healthProgressText.text = state.healthProgressText
    }

    private fun renderLevelProgress(state: ProfileViewState, view: View) {
        view.levelProgress.max = state.levelXpMaxProgress
        view.levelProgress.animateProgressFromZero(state.levelXpProgress)
        view.level.text = state.levelText
        view.levelProgressText.text = state.levelProgressText
    }

    private val ProfileViewState.avatarImage
        get() = AndroidAvatar.valueOf(avatar.name).image

    private val ProfileViewState.displayNameText
        get() = if (displayName.isNullOrBlank())
            stringRes(io.ipoli.android.R.string.unknown_hero)
        else
            displayName

    private val ProfileViewState.levelText
        get() = "$level"

    private val ProfileViewState.levelProgressText
        get() = "$levelXpProgress / $levelXpMaxProgress"

    private val ProfileViewState.healthProgressText
        get() = "$health / $maxHealth"

    private val ProfileViewState.gemsText
        get() = LongFormatter.format(activity!!, gems.toLong())

    private val ProfileViewState.lifeCoinsText
        get() = LongFormatter.format(activity!!, coins.toLong())

    private val ProfileViewState.rankText
        get() = stringRes(AndroidRank.valueOf(rank!!.name).title)

    private val ProfileViewState.nextRankText
        get() = stringRes(
            R.string.next_rank,
            stringRes(AndroidRank.valueOf(nextRank!!.name).title)
        )

    private fun getViewControllerForPage(position: Int): Controller {
        return when (position) {
            0 -> ProfileInfoViewController(reducer.stateKey)
            1 -> ProfilePostListViewController(reducer.stateKey, null)
            2 -> ProfilePlayerListViewController(
                reducerKey = reducer.stateKey,
                showFollowers = false
            )
            3 -> ProfilePlayerListViewController(
                reducerKey = reducer.stateKey,
                showFollowers = true
            )
            4 -> ProfileChallengeListViewController(reducer.stateKey, null)
            else -> throw IllegalArgumentException("Unknown controller position $position")
        }
    }

    data class AttributeViewModel(
        val type: Player.AttributeType,
        val level: String,
        val progress: Int,
        val max: Int,
        val progressText: String,
        @ColorInt val secondaryColor: Int,
        @ColorInt val progressColor: Int
    )

    private val ProfileViewState.attributeViewModels: Map<Player.AttributeType, AttributeViewModel>
        get() = attributes!!.map {
            val attr = AndroidAttribute.valueOf(it.type.name)

            it.type to AttributeViewModel(
                type = it.type,
                level = "Lvl ${it.level}",
                progress = ((it.progressForLevel * 100f) / it.progressForNextLevel).toInt(),
                max = 100,
                progressText = "${it.progressForLevel}/${it.progressForNextLevel}",
                secondaryColor = colorRes(attr.colorPrimary),
                progressColor = colorRes(attr.colorPrimaryDark)
            )
        }.toMap()

}