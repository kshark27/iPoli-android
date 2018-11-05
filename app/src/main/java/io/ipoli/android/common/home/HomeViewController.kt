package io.ipoli.android.common.home

import android.annotation.SuppressLint
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.annotation.IdRes
import android.support.design.internal.NavigationMenuView
import android.support.design.widget.AppBarLayout
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.SimpleSwapChangeHandler
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import io.ipoli.android.Constants
import io.ipoli.android.MainActivity
import io.ipoli.android.R
import io.ipoli.android.challenge.list.ChallengeListViewController
import io.ipoli.android.common.home.HomeViewState.StateType.*
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.text.LongFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.friends.feed.FeedViewController
import io.ipoli.android.growth.GrowthViewController
import io.ipoli.android.habit.list.HabitListViewController
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.AndroidPetMood
import io.ipoli.android.player.data.AndroidAvatar
import io.ipoli.android.player.data.AndroidRank
import io.ipoli.android.quest.bucketlist.BucketListViewController
import io.ipoli.android.quest.schedule.ScheduleViewController
import io.ipoli.android.quest.schedule.today.TodayViewController
import io.ipoli.android.repeatingquest.list.RepeatingQuestListViewController
import io.ipoli.android.settings.SettingsViewController
import io.ipoli.android.store.StoreViewController
import io.ipoli.android.tag.Tag
import io.ipoli.android.tag.list.TagListViewController
import io.ipoli.android.tag.show.TagViewController
import kotlinx.android.synthetic.main.controller_home.view.*
import kotlinx.android.synthetic.main.drawer_header_home.view.*
import kotlinx.android.synthetic.main.menu_item_tag_view.view.*
import space.traversal.kapsule.required


/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 8/19/17.
 */
class HomeViewController(args: Bundle? = null) :
    ReduxViewController<HomeAction, HomeViewState, HomeReducer>(args),
    NavigationView.OnNavigationItemSelectedListener {

    private val eventLogger by required { eventLogger }

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

    override val reducer = HomeReducer

    private val appBarOffsetListener = object :
        AppBarStateChangeListener() {
        override fun onStateChanged(appBarLayout: AppBarLayout, state: State) {

            appBarLayout.post {
                if (state == State.EXPANDED) {
                    (activity as MainActivity).supportActionBar!!.setDisplayShowTitleEnabled(
                        false
                    )
                } else if (state == State.COLLAPSED) {
                    (activity as MainActivity).supportActionBar!!.setDisplayShowTitleEnabled(true)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {

        setHasOptionsMenu(true)

        val contentView = container.inflate(R.layout.controller_home)

        contentView.navigationView.setNavigationItemSelectedListener(this)
        val mv = contentView.navigationView.getChildAt(0) as NavigationMenuView
        mv.isVerticalScrollBarEnabled = false

        actionBarDrawerToggle = object :
            ActionBarDrawerToggle(
                activity,
                contentView.drawerLayout,
                R.string.drawer_open,
                R.string.drawer_close
            ) {}

        contentView.drawerLayout.addDrawerListener(actionBarDrawerToggle)

        setToolbar(contentView.toolbar)
        toolbarTitle = ""
        val actionBar = (activity as MainActivity).supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        contentView.todayCollapsingToolbarContainer.isTitleEnabled = false

        contentView.todayAppBar.addOnOffsetChangedListener(appBarOffsetListener)
        actionBarDrawerToggle.syncState()

        return contentView
    }

    override fun onCreateLoadAction() = HomeAction.Load

    private fun onItemSelectedFromDrawer(item: MenuItem) {

        when (item.itemId) {

            R.id.today -> {
                (activity as MainActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
                view?.todayCollapsingToolbarContainer?.isTitleEnabled = false
                changeChildController(TodayViewController(false))
            }

            R.id.calendar ->
                changeChildController(ScheduleViewController())

            R.id.bucketList -> changeChildController(BucketListViewController())

            R.id.habits -> changeChildController(HabitListViewController())

            R.id.repeatingQuests -> changeChildController(RepeatingQuestListViewController())

            R.id.challenges -> changeChildController(ChallengeListViewController())

            R.id.feed -> changeChildController(FeedViewController())

            R.id.tags -> changeChildController(TagListViewController())

            R.id.growth -> changeChildController(GrowthViewController())

            R.id.store ->
                pushWithRootRouter(
                    StoreViewController.routerTransaction()
                )

            R.id.community ->
                navigateFromRoot().toCommunity()

            R.id.chat ->
                startActivity(
                    Intent(ACTION_VIEW, Uri.parse(Constants.DISCORD_CHAT_LINK))
                )

            R.id.shareApp ->
                showShareApp()

            R.id.help ->
                navigateFromRoot().toHelpPage()

            R.id.settings ->
                pushWithRootRouter(
                    SettingsViewController.routerTransaction()
                )

            R.id.feedback ->
                showFeedback()

            R.id.reportBug ->
                navigateFromRoot().toBugReport()

            else -> {
                if (TAG_IDS.contains(item.itemId)) {
                    pushWithRootRouter(
                        TagViewController.routerTransaction(item.actionView.tag as String)
                    )
                }
            }
        }

        view!!.navigationView.setCheckedItem(item.itemId)
    }

    private fun showFeedback() {
        navigateFromRoot()
            .toFeedback(
                object : FeedbackDialogController.FeedbackListener {
                    override fun onSendFeedback(feedback: String) {
                        if (feedback.isNotEmpty()) {
                            eventLogger.logEvent(
                                "feedback",
                                mapOf("feedback" to feedback)
                            )
                            showShortToast(R.string.feedback_response)
                            dispatch(HomeAction.FeedbackSent)
                        }
                    }

                    override fun onSuggestIdea() {
                        eventLogger.logEvent("feedback_suggest_idea")
                        navigateFromRoot().toSuggestIdea()
                    }
                })
    }

    private fun showShareApp() {
        navigate().toShareApp(
            dialogTitle = stringRes(R.string.share_app),
            message = stringRes(R.string.invite_message) + " " + Constants.SHARE_URL
        )
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {

        if (!view!!.navigationView.menu.findItem(item.itemId).isChecked) {
            onItemSelectedFromDrawer(item)
        }

        view!!.drawerLayout.closeDrawer(GravityCompat.START)
        return false
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        view.navigationView.bringToFront()

        val childRouter = getChildRouter(view.childControllerContainer, null)
        if (!childRouter.hasRootController()) {
            childRouter.setRoot(
                RouterTransaction.with(TodayViewController())
            )
        }

        val showTitle =
            appBarOffsetListener.currentState != AppBarStateChangeListener.State.EXPANDED
        (activity as MainActivity).supportActionBar?.setDisplayShowTitleEnabled(showTitle)
    }

    override fun onDetach(view: View) {
        view.rootCoordinator.bringToFront()
        super.onDetach(view)
    }

    override fun onDestroyView(view: View) {
        view.todayAppBar.removeOnOffsetChangedListener(appBarOffsetListener)
        super.onDestroyView(view)
    }

    private fun changeChildController(controller: Controller) {
        val childRouter = getChildRouter(view!!.childControllerContainer, null)
        childRouter.setRoot(
            RouterTransaction.with(controller)
                .pushChangeHandler(SimpleSwapChangeHandler())
                .popChangeHandler(SimpleSwapChangeHandler())
        )
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {

            android.R.id.home -> {
                view!!.drawerLayout.openDrawer(GravityCompat.START)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }

    private fun showAuth() {
        navigate()
            .toAuth(
                onboardData = null,
                changeHandler = VerticalChangeHandler()
            )
    }

    override fun render(state: HomeViewState, view: View) {

        when (state.type) {
            DATA_LOADED -> {
                val drawerHeaderView = view.navigationView.getHeaderView(0)
                renderSignIn(drawerHeaderView, state.showSignIn)
                renderBucketList(state.bucketListQuestCount, view)
                renderPlayer(drawerHeaderView, state)
                renderLevelProgress(state, view)
                renderTags(view, state)
                renderAchievements(drawerHeaderView)
            }

            PLAYER_CHANGED -> {
                val drawerHeaderView = view.navigationView.getHeaderView(0)
                renderSignIn(drawerHeaderView, state.showSignIn)
                renderPlayer(drawerHeaderView, state)
                renderLevelProgress(state, view)
            }

            TAGS_CHANGED ->
                renderTags(view, state)

            UNSCHEDULED_QUESTS_CHANGED ->
                renderBucketList(state.bucketListQuestCount, view)

            else -> {
            }
        }
    }

    private fun renderAchievements(drawerHeaderView: View) {
        Glide.with(drawerHeaderView.context).load(R.drawable.drawer_achievement_trophy)
            .apply(RequestOptions.circleCropTransform())
            .into(drawerHeaderView.achievements)

        (drawerHeaderView.achievements.background as GradientDrawable).setColor(Color.WHITE)

        drawerHeaderView.achievements.onDebounceClick {
            navigateFromRoot().toAchievementList(VerticalChangeHandler())
        }
    }

    @SuppressLint("InflateParams")
    private fun renderBucketList(bucketListQuestCount: Int, view: View) {
        val item = view.navigationView.menu.findItem(R.id.bucketList)

        item.actionView =
            LayoutInflater.from(view.context).inflate(R.layout.menu_item_tag_view, null)
        item.actionView.questCount.text = bucketListQuestCount.toString()
    }

    private fun renderTags(view: View, state: HomeViewState) {
        TAG_IDS.forEach { view.navigationView.menu.removeItem(it) }

        val dropDown =
            view.navigationView.menu.findItem(R.id.tags)
                .actionView.findViewById<ImageView>(R.id.dropDown)

        dropDown.visible()

        if (state.tags.isEmpty()) {
            dropDown.invisible()
            return
        }

        val tagItems = mutableListOf<MenuItem>()
        state.tags.forEachIndexed { index, tag ->
            tagItems.add(
                createTagForNavigationDrawer(
                    view = view,
                    tagId = tag.id,
                    viewId = TAG_IDS[index],
                    name = tag.name,
                    icon = tagIcon(tag),
                    iconColor = tag.color.androidColor.color500,
                    questCount = tag.questCount,
                    isVisible = state.showTags
                )
            )
        }

        dropDown.rotation = if (!state.showTags) 0f else 180f
        dropDown.setOnClickListener {
            val shouldClose = tagItems.isEmpty() || tagItems.first().isVisible
            val rotationDegree = if (shouldClose) 0f else 180f
            it.rotation = rotationDegree
            tagItems.forEach { m ->
                m.isVisible = !m.isVisible
            }
            if (shouldClose) {
                dispatch(HomeAction.HideTags)
            } else {
                dispatch(HomeAction.ShowTags)
            }
        }

    }

    private fun renderPlayer(drawerHeaderView: View, state: HomeViewState) {
        Glide.with(drawerHeaderView.context).load(state.avatarImage)
            .apply(RequestOptions.circleCropTransform())
            .into(drawerHeaderView.playerAvatar)

        (drawerHeaderView.playerAvatar.background as GradientDrawable)
            .setColor(colorRes(AndroidAvatar.valueOf(state.avatar.name).backgroundColor))

        drawerHeaderView.playerAvatar.setOnClickListener {
            navigate().toProfile()
        }

        Glide.with(drawerHeaderView.context).load(state.petHeadImage)
            .apply(RequestOptions.circleCropTransform())
            .apply(RequestOptions.fitCenterTransform())
            .into(drawerHeaderView.petHeadImage)

        (drawerHeaderView.petHeadImage.background as GradientDrawable)
            .setColor(colorRes(R.color.md_white))

        drawerHeaderView.petHeadImage.setOnClickListener {
            navigateFromRoot().toPet(VerticalChangeHandler())
        }

        drawerHeaderView.drawerPlayerGems.text = state.gemsText
        drawerHeaderView.drawerPlayerCoins.text = state.lifeCoinsText
        drawerHeaderView.drawerCurrentExperience.text = state.experienceText

        drawerHeaderView.drawerPlayerTitle.text = state.title

        val drawable = drawerHeaderView.petMood.background as GradientDrawable
        drawable.setColor(colorRes(state.petMoodColor))
    }

    private fun renderLevelProgress(state: HomeViewState, view: View) {
        view.levelProgress.max = state.maxProgress
        view.levelProgress.progress = state.progress
    }

    @SuppressLint("InflateParams")
    private fun createTagForNavigationDrawer(
        view: View,
        @IdRes viewId: Int,
        name: String,
        icon: IIcon,
        questCount: Int,
        @ColorRes iconColor: Int,
        isVisible: Boolean,
        tagId: String
    ): MenuItem {
        val item = view.navigationView.menu.add(
            R.id.drawerMainGroup,
            viewId,
            0,
            name
        )

        item.actionView =
            LayoutInflater.from(view.context).inflate(R.layout.menu_item_tag_view, null)
        item.actionView.questCount.text = questCount.toString()
        item.actionView.tag = tagId

        item.icon = IconicsDrawable(activity!!)
            .icon(icon)
            .paddingDp(3)
            .sizeDp(24)

        val ic = item.icon
        ic.mutate()
        ic.setColorFilter(colorRes(iconColor), PorterDuff.Mode.SRC_ATOP)
        item.isVisible = isVisible
        item.isCheckable = false
        item.isChecked = false
        return item
    }

    private fun renderSignIn(
        drawerHeaderView: View,
        showSignIn: Boolean
    ) {
        if (showSignIn) {
            drawerHeaderView.signIn.visible()
            drawerHeaderView.signIn.setOnClickListener {
                showAuth()
            }
        } else {
            drawerHeaderView.signIn.gone()
        }
    }

    private val HomeViewState.avatarImage
        get() = AndroidAvatar.valueOf(avatar.name).image

    private val HomeViewState.petHeadImage
        get() = AndroidPetAvatar.valueOf(petAvatar.name).headImage

    private val HomeViewState.petMoodColor
        get() = AndroidPetMood.valueOf(petState.name).color

    private val HomeViewState.title
        get() = stringRes(
            R.string.player_level,
            level,
            stringRes(AndroidRank.valueOf(rank.name).title)
        )

    private val HomeViewState.gemsText
        get() = LongFormatter.format(activity!!, gems.toLong())

    private val HomeViewState.lifeCoinsText
        get() = LongFormatter.format(activity!!, lifeCoins.toLong())

    private val HomeViewState.experienceText
        get() = LongFormatter.format(activity!!, experience)

    private fun tagIcon(tag: Tag): IIcon =
        tag.icon?.androidIcon?.icon ?: MaterialDesignIconic.Icon.gmi_label


    companion object {
        val TAG_IDS = listOf(
            R.id.tag1,
            R.id.tag2,
            R.id.tag3,
            R.id.tag4,
            R.id.tag5,
            R.id.tag6,
            R.id.tag7,
            R.id.tag8,
            R.id.tag9,
            R.id.tag10
        )
    }
}