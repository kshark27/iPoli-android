package io.ipoli.android.quest.schedule.today

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RotateDrawable
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.annotation.ColorRes
import android.support.annotation.DrawableRes
import android.support.constraint.ConstraintSet
import android.support.design.widget.FloatingActionButton
import android.support.v4.widget.TextViewCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.ionicons_typeface_library.Ionicons
import io.ipoli.android.Constants
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.text.CalendarFormatter
import io.ipoli.android.common.text.DurationFormatter
import io.ipoli.android.common.text.LongFormatter
import io.ipoli.android.common.text.QuestStartTimeFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.*
import io.ipoli.android.dailychallenge.usecase.CheckDailyChallengeProgressUseCase
import io.ipoli.android.event.Event
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.AndroidPetMood
import io.ipoli.android.pet.PetItem
import io.ipoli.android.player.data.AndroidAttribute
import io.ipoli.android.player.data.AndroidAvatar
import io.ipoli.android.player.data.AndroidRank
import io.ipoli.android.player.data.Player
import io.ipoli.android.quest.schedule.addquest.AddQuestAnimationHelper
import io.ipoli.android.quest.schedule.today.TodayViewState.StateType.*
import io.ipoli.android.quest.schedule.today.usecase.CreateTodayItemsUseCase
import kotlinx.android.synthetic.main.controller_home.view.*
import kotlinx.android.synthetic.main.controller_today.view.*
import kotlinx.android.synthetic.main.item_agenda_event.view.*
import kotlinx.android.synthetic.main.item_agenda_quest.view.*
import kotlinx.android.synthetic.main.item_habit_list.view.*
import kotlinx.android.synthetic.main.item_today_profile_attribute.view.*
import kotlinx.android.synthetic.main.view_fab.view.*
import kotlinx.android.synthetic.main.view_profile_pet.view.*
import kotlinx.android.synthetic.main.view_today_stats.view.*
import org.threeten.bp.LocalDate
import space.traversal.kapsule.required

class TodayViewController(args: Bundle? = null) :
    ReduxViewController<TodayAction, TodayViewState, TodayReducer>(args = args) {

    override val reducer = TodayReducer

    private lateinit var addQuestAnimationHelper: AddQuestAnimationHelper

    private val imageLoader by required { imageLoader }

    private var showDataAfterStats = false

    constructor(showDataAfterStats: Boolean) : this() {
        this.showDataAfterStats = showDataAfterStats
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = container.inflate(R.layout.controller_today)

        val today = LocalDate.now()
        toolbarTitle = CalendarFormatter(view.context).dateWithoutYear(today)

        view.questItems.layoutManager = LinearLayoutManager(view.context)
        view.questItems.isNestedScrollingEnabled = false
        view.questItems.adapter = TodayItemAdapter()

        val gridLayoutManager = GridLayoutManager(view.context, 3)
        view.habitItems.layoutManager = gridLayoutManager

        val adapter = HabitListAdapter()
        view.habitItems.adapter = adapter

        view.completedQuests.layoutManager = LinearLayoutManager(view.context)
        view.completedQuests.isNestedScrollingEnabled = false
        view.completedQuests.adapter = CompletedQuestAdapter()

        initIncompleteSwipeHandler(view)
        initCompletedSwipeHandler(view)

        if (showDataAfterStats) {
            view.dataContainer.gone()
        } else {
            view.dataContainer.visible()
        }

        return view
    }

    private fun initCompletedSwipeHandler(view: View) {
        val swipeHandler = object : SimpleSwipeCallback(
            R.drawable.ic_undo_white_24dp,
            R.color.md_amber_500,
            R.drawable.ic_delete_white_24dp,
            R.color.md_red_500
        ) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val questId = questId(viewHolder)
                if (direction == ItemTouchHelper.END) {
                    dispatch(TodayAction.UndoCompleteQuest(questId(viewHolder)))
                    view.completedQuests.adapter.notifyItemChanged(viewHolder.adapterPosition)
                } else {
                    dispatch(TodayAction.RemoveQuest(questId))
                    PetMessagePopup(
                        stringRes(R.string.remove_quest_undo_message),
                        {
                            dispatch(TodayAction.UndoRemoveQuest(questId))
                            view.completedQuests.adapter.notifyItemChanged(viewHolder.adapterPosition)
                        },
                        stringRes(R.string.undo)
                    ).show(view.context)
                }
            }

            private fun questId(holder: RecyclerView.ViewHolder): String {
                val a = view.completedQuests.adapter as CompletedQuestAdapter
                return a.getItemAt(holder.adapterPosition).id
            }

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) = ItemTouchHelper.END or ItemTouchHelper.START
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(view.completedQuests)
    }

    private fun initIncompleteSwipeHandler(view: View) {
        val swipeHandler = object : SimpleSwipeCallback(
            R.drawable.ic_done_white_24dp,
            R.color.md_green_500,
            R.drawable.ic_event_white_24dp,
            R.color.md_blue_500
        ) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val questId = questId(viewHolder)
                if (direction == ItemTouchHelper.END) {
                    dispatch(TodayAction.CompleteQuest(questId(viewHolder)))
                    view.questItems.adapter.notifyItemChanged(viewHolder.adapterPosition)
                } else {
                    navigate()
                        .toReschedule(
                            includeToday = false,
                            listener = { date ->
                                dispatch(TodayAction.RescheduleQuest(questId, date))
                            },
                            cancelListener = {
                                view.questItems.adapter.notifyItemChanged(viewHolder.adapterPosition)
                            }
                        )
                }
            }

            private fun questId(holder: RecyclerView.ViewHolder): String {
                val a = view.questItems.adapter as TodayItemAdapter
                val item = a.getItemAt<TodayItemViewModel.QuestViewModel>(holder.adapterPosition)
                return item.id
            }

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) = when {
                viewHolder.itemViewType == QuestViewType.QUEST.ordinal -> (ItemTouchHelper.END or ItemTouchHelper.START)
                else -> 0
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(view.questItems)
    }

    private fun initAddQuest(view: View, addQuest: FloatingActionButton, currentDate: LocalDate) {
        addQuestAnimationHelper = AddQuestAnimationHelper(
            controller = this,
            addContainer = view.addContainer,
            fab = addQuest,
            background = view.addContainerBackground
        )

        view.addContainerBackground.setOnClickListener {
            closeAddIfShown()
        }

        addQuest.setOnClickListener {
            addQuestAnimationHelper.openAddContainer(currentDate)
        }
    }

    private fun closeAddIfShown(endListener: (() -> Unit)? = null) {
        if (view == null) return
        val containerRouter = addContainerRouter(view!!)
        if (containerRouter.hasRootController()) {
            containerRouter.popCurrentController()
            ViewUtils.hideKeyboard(view!!)
            addQuestAnimationHelper.closeAddContainer(endListener)
        } else {
            endListener?.invoke()
        }
    }

    private var statsContainer: View? = null

    private fun addContainerRouter(view: View) =
        getChildRouter(view.addContainer, "add-quest")

    override fun onCreateLoadAction() = TodayAction.Load(LocalDate.now(), showDataAfterStats)

    override fun onAttach(view: View) {
        super.onAttach(view)

        parentController?.view?.let {
            it.levelProgress.gone()
            val fab = (view as ViewGroup).inflate(R.layout.view_fab)
            (it.rootCoordinator as ViewGroup).addView(fab)

            initAddQuest(view, fab as FloatingActionButton, LocalDate.now())

            statsContainer = view.inflate(R.layout.view_today_stats)
            (it.todayCollapsingToolbarContainer as ViewGroup).addView(
                statsContainer,
                0
            )

            val petBgr = statsContainer!!.todayPetAvatar.background as GradientDrawable
            petBgr.mutate()
            petBgr.setColor(colorRes(R.color.md_grey_50))

        }
    }

    override fun onDetach(view: View) {
        parentController?.view?.let {
            it.levelProgress.visible()
            val fabView = it.rootCoordinator.addQuest
            it.rootCoordinator.removeView(fabView)
            it.todayCollapsingToolbarContainer.removeView(statsContainer)
        }

        statsContainer = null

        super.onDetach(view)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.today_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {

            R.id.actionDailyChallenge -> {
                closeAddIfShown {
                    navigateFromRoot().toDailyChallenge()
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }


    private fun loadImage(view: View, state: TodayViewState) {
        state.todayImageUrl?.let {
            imageLoader.loadTodayImage(
                imageUrl = it,
                view = view.todayBackdrop,
                onReady = {
                    activity?.let { _ ->
                        view.todayBackdrop.fadeIn(mediumAnimTime, onComplete = {
                            dispatch(TodayAction.ImageLoaded)
                        })
                    }

                },
                onError = { _ ->
                    activity?.let { _ ->
                        view.todayBackdrop.fadeIn(mediumAnimTime, onComplete = {
                            dispatch(TodayAction.ImageLoaded)
                        })
                    }
                }
            )
        }

    }

    private fun animateStats(state: TodayViewState, view: View) {
        view.backdropTransparentColor.visible()
        view.backdropTransparentColor.fadeIn(
            shortAnimTime,
            to = 0.9f,
            delay = longAnimTime,
            onComplete = {
                activity?.let { _ ->
                    val animTime = shortAnimTime
                    view.todayInfoGroup.visible()
                    val allViews = view.todayInfoGroup.views()
                    val lastViews = allViews.subList(1, allViews.size)
                    lastViews.forEach {
                        it.visible()
                        it.fadeIn(animTime)
                    }

                    allViews.first().let {
                        it.visible()
                        renderPlayerStats(state, view)
                        it.fadeIn(animTime, onComplete = {
                            dispatch(TodayAction.StatsShown)
                        })
                    }
                }
            })
    }

    override fun render(state: TodayViewState, view: View) {

        when (state.type) {

            SHOW_IMAGE ->
                statsContainer?.let {
                    loadImage(it, state)
                }


            SHOW_SUMMARY_STATS ->
                statsContainer?.let {
                    renderSummaryStats(state, it)
                    animateStats(state, it)
                }

            SUMMARY_STATS_CHANGED ->
                statsContainer?.let {
                    renderSummaryStats(state, it)
                }

            PLAYER_STATS_CHANGED ->
                statsContainer?.let {
                    renderPlayerStats(state, it)
                }

            SHOW_DATA ->
                view.dataContainer.visible()

            DATA_CHANGED -> {
                renderHabits(view, state)
                renderQuests(state, view)
            }

            HABITS_CHANGED ->
                renderHabits(view, state)

            QUESTS_CHANGED ->
                renderQuests(state, view)

            else -> {
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun renderSummaryStats(state: TodayViewState, view: View) {

        val focusDuration = DurationFormatter.format(view.context, state.focusDuration!!.intValue)

        view.todayFocusDuration.text = "$focusDuration/${Constants.DAILY_FOCUS_HOURS_GOAL}h"

        val dcProgress = state.dailyChallengeProgress!!
        val dcText = when (dcProgress) {
            is CheckDailyChallengeProgressUseCase.Result.NotScheduledForToday ->
                "Inactive"
            is CheckDailyChallengeProgressUseCase.Result.Inactive ->
                "Inactive"
            is CheckDailyChallengeProgressUseCase.Result.Complete ->
                "Complete"
            is CheckDailyChallengeProgressUseCase.Result.Incomplete ->
                "${dcProgress.completeQuestCount}/${Constants.DAILY_CHALLENGE_QUEST_COUNT}"
        }

        view.todayDailyChallengeProgress.text = dcText
    }

    @SuppressLint("SetTextI18n")
    private fun renderHabitStats(
        state: TodayViewState
    ) {
        statsContainer?.let {
            it.todayHabitsDone.text = "${state.habitCompleteCount}/${state.habitCount}"
        }
    }

    @SuppressLint("SetTextI18n")
    private fun renderQuestStats(
        state: TodayViewState
    ) {
        statsContainer?.let {
            it.todayQuestsDone.text = "${state.questCompleteCount}/${state.questCount}"
        }
    }

    private fun renderPlayerStats(
        state: TodayViewState,
        view: View
    ) {
        val androidAvatar = AndroidAvatar.valueOf(state.avatar.name)

        Glide.with(view.context).load(androidAvatar.image)
            .apply(RequestOptions.circleCropTransform())
            .into(view.todayPlayerAvatar)
        val background = view.todayPlayerAvatar.background as GradientDrawable
        background.mutate()

        background.setColor(colorRes(androidAvatar.backgroundColor))

        view.todayPlayerAvatar.onDebounceClick {
            navigateFromRoot().toProfile()
        }

        view.todayPlayerRank.setText(AndroidRank.valueOf(state.rank.name).title)

        view.todayLevelText.text = state.levelText

        view.todayHealthProgress.max = state.maxHealth
        view.todayHealthProgress.animateProgressFromCurrentValue(state.health)
        view.todayHealthProgressText.text = state.healthProgressText

        view.todayLevelProgress.max = state.levelXpMaxProgress
        view.todayLevelProgress.animateProgressFromCurrentValue(state.levelXpProgress)
        view.todayLevelProgressText.text = state.levelProgressText

        view.todayCoins.text = state.lifeCoinsText

        view.todayCoins.onDebounceClick {
            navigateFromRoot().toCurrencyConverter()
        }

        view.todayGems.text = state.gemsText

        view.todayGems.onDebounceClick {
            navigateFromRoot().toCurrencyConverter()
        }

        state.attributeViewModels.forEach { vm ->

            val v = when (vm.type) {
                Player.AttributeType.STRENGTH -> view.todayAttrStrength
                Player.AttributeType.INTELLIGENCE -> view.todayAttrIntelligence
                Player.AttributeType.CHARISMA -> view.todayAttrCharisma
                Player.AttributeType.EXPERTISE -> view.todayAttrExpertise
                Player.AttributeType.WELL_BEING -> view.todayAttrWellBeing
                Player.AttributeType.WILLPOWER -> view.todayAttrWillpower
            }

            v.attrLevel.text = vm.level

            v.attrLevelProgress.max = vm.progressMax
            v.attrLevelProgress.animateProgressFromCurrentValue(vm.progress)
            val pd = v.attrLevelProgress.progressDrawable as RotateDrawable
            pd.mutate()
            pd.setColorFilter(vm.progressColor, PorterDuff.Mode.SRC_ATOP)

            v.attrIcon.setImageResource(vm.icon)

            v.onDebounceClick {
                navigateFromRoot().toAttributes(vm.type)
            }
        }

        renderPet(state, view)
    }

    private fun renderPet(
        state: TodayViewState,
        view: View
    ) {

        view.todayPetAvatar.onDebounceClick {
            navigateFromRoot().toPet(VerticalChangeHandler())
        }

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

        val drawable = view.todayPetMood.background as GradientDrawable
        drawable.mutate()
        drawable.setColor(colorRes(state.petMoodColor))

        view.todayPetName.text = pet.name
    }

    data class EquipmentItemViewModel(
        @DrawableRes val image: Int,
        val item: PetItem
    )

    private fun TodayViewState.toItemViewModel(petItem: PetItem?): EquipmentItemViewModel? {
        val petItems = AndroidPetAvatar.valueOf(pet!!.avatar.name).items
        return petItem?.let {
            EquipmentItemViewModel(petItems[it]!!, it)
        }
    }

    private fun renderQuests(
        state: TodayViewState,
        view: View
    ) {
        renderQuestStats(state)

        val incompleteQuestViewModels = state.incompleteQuestViewModels
        val completedQuestVMs = state.completedQuestViewModels
        if (incompleteQuestViewModels.isEmpty() && completedQuestVMs.isEmpty()) {
            view.questsLabel.visible()
            view.questItemsEmpty.visible()
            view.questItems.gone()
            view.questItemsEmpty.setText(R.string.today_empty_quests)
        } else if (incompleteQuestViewModels.isEmpty()) {
            view.questsLabel.visible()
            view.questItemsEmpty.visible()
            view.questItems.gone()
            view.questItemsEmpty.setText(R.string.today_all_quests_done)
        } else {
            view.questsLabel.gone()
            view.questItemsEmpty.gone()
            view.questItems.visible()
            (view.questItems.adapter as TodayItemAdapter).updateAll(
                incompleteQuestViewModels
            )
        }

        if (completedQuestVMs.isEmpty()) {
            view.completedQuestsLabel.gone()
            view.completedQuests.gone()
        } else {
            view.completedQuestsLabel.visible()
            view.completedQuests.visible()
            (view.completedQuests.adapter as CompletedQuestAdapter).updateAll(
                completedQuestVMs
            )
        }
    }

    private fun renderHabits(
        view: View,
        state: TodayViewState
    ) {
        renderHabitStats(state)

        view.habitsLabel.visible()
        val habitVMs = state.habitItemViewModels
        if (habitVMs.isEmpty()) {
            view.habitItemsEmpty.visible()
            view.habitItems.gone()
        } else {
            view.habitItemsEmpty.gone()
            view.habitItems.visible()
            (view.habitItems.adapter as HabitListAdapter).updateAll(habitVMs)
        }
    }

    data class AttributeViewModel(
        val type: Player.AttributeType,
        val level: String,
        @DrawableRes val icon: Int,
        val progress: Int,
        val progressMax: Int,
        @ColorInt val progressColor: Int
    )

    private val TodayViewState.attributeViewModels: List<AttributeViewModel>
        get() = attributes.map {
            val attr = AndroidAttribute.valueOf(it.type.name)

            AttributeViewModel(
                type = it.type,
                level = it.level.toString(),
                progress = ((it.progressForLevel * 100f) / it.progressForNextLevel).toInt(),
                progressMax = 100,
                progressColor = colorRes(attr.colorPrimaryDark),
                icon = attr.colorIcon
            )
        }

    data class TagViewModel(val name: String, @ColorRes val color: Int)

    sealed class TodayItemViewModel(override val id: String) : RecyclerViewViewModel {
        data class Section(val text: String) : TodayItemViewModel(text)

        data class QuestViewModel(
            override val id: String,
            val name: String,
            val tags: List<TagViewModel>,
            val startTime: String,
            @ColorRes val color: Int,
            val icon: IIcon,
            val isRepeating: Boolean,
            val isFromChallenge: Boolean
        ) : TodayItemViewModel(id)

        data class EventViewModel(
            override val id: String,
            val name: String,
            val startTime: String,
            @ColorInt val color: Int
        ) : TodayItemViewModel(id)
    }

    enum class QuestViewType {
        SECTION,
        QUEST,
        EVENT
    }

    inner class TodayItemAdapter : MultiViewRecyclerViewAdapter<TodayItemViewModel>() {

        override fun onRegisterItemBinders() {

            registerBinder<TodayItemViewModel.Section>(
                QuestViewType.SECTION.ordinal,
                R.layout.item_agenda_list_section
            ) { vm, view, _ ->
                (view as TextView).text = vm.text
                view.setOnClickListener(null)
            }

            registerBinder<TodayItemViewModel.QuestViewModel>(
                QuestViewType.QUEST.ordinal,
                R.layout.item_agenda_quest
            ) { vm, view, _ ->
                view.questName.text = vm.name

                view.questIcon.backgroundTintList =
                    ColorStateList.valueOf(colorRes(vm.color))
                view.questIcon.setImageDrawable(smallListItemIcon(vm.icon))

                if (vm.tags.isNotEmpty()) {
                    view.questTagName.visible()
                    renderTag(view, vm.tags.first())
                } else {
                    view.questTagName.gone()
                }

                view.questStartTime.text = vm.startTime

                view.questRepeatIndicator.visibility =
                    if (vm.isRepeating) View.VISIBLE else View.GONE
                view.questChallengeIndicator.visibility =
                    if (vm.isFromChallenge) View.VISIBLE else View.GONE

                view.onDebounceClick {
                    navigateFromRoot().toQuest(vm.id, VerticalChangeHandler())
                }
            }

            registerBinder<TodayItemViewModel.EventViewModel>(
                QuestViewType.EVENT.ordinal,
                R.layout.item_agenda_event
            ) { vm, view, _ ->
                view.eventName.text = vm.name
                view.eventStartTime.text = vm.startTime

                view.eventIcon.backgroundTintList =
                    ColorStateList.valueOf(vm.color)
                view.setOnClickListener(null)
            }
        }

        private fun renderTag(view: View, tag: TagViewModel) {
            view.questTagName.text = tag.name
            TextViewCompat.setTextAppearance(
                view.questTagName,
                R.style.TextAppearance_AppCompat_Caption
            )

            val indicator = view.questTagName.compoundDrawablesRelative[0] as GradientDrawable
            indicator.mutate()
            val size = ViewUtils.dpToPx(8f, view.context).toInt()
            indicator.setSize(size, size)
            indicator.setColor(colorRes(tag.color))
            view.questTagName.setCompoundDrawablesRelativeWithIntrinsicBounds(
                indicator,
                null,
                null,
                null
            )
        }
    }

    data class HabitViewModel(
        override val id: String,
        val name: String,
        val icon: IIcon,
        @ColorRes val color: Int,
        @ColorRes val secondaryColor: Int,
        val streak: Int,
        val isBestStreak: Boolean,
        val timesADay: Int,
        val progress: Int,
        val maxProgress: Int,
        val isCompleted: Boolean,
        val isGood: Boolean
    ) : RecyclerViewViewModel

    inner class HabitListAdapter :
        BaseRecyclerViewAdapter<HabitViewModel>(R.layout.item_today_habit) {
        override fun onBindViewModel(vm: HabitViewModel, view: View, holder: SimpleViewHolder) {
            renderName(view, vm.name, vm.isGood)
            renderIcon(view, vm.icon, if (vm.isCompleted) R.color.md_white else vm.color)
            renderStreak(
                view = view,
                streak = vm.streak,
                isBestStreak = vm.isBestStreak,
                color = if (vm.isCompleted) R.color.md_white else vm.color,
                textColor = if (vm.isCompleted) R.color.md_white else colorTextPrimaryResource
            )
            renderCompletedBackground(view, vm.color)

            view.habitProgress.setProgressStartColor(colorRes(vm.color))
            view.habitProgress.setProgressEndColor(colorRes(vm.color))
            view.habitProgress.setProgressBackgroundColor(colorRes(vm.secondaryColor))
            view.habitProgress.setProgressFormatter(null)
            renderProgress(view, vm.progress, vm.maxProgress)

            if (vm.timesADay > 1) {
                view.habitTimesADayProgress.visible()
                view.habitTimesADayProgress.setProgressStartColor(attrData(android.R.attr.colorBackground))
                view.habitTimesADayProgress.setProgressEndColor(attrData(android.R.attr.colorBackground))
                view.habitTimesADayProgress.setProgressFormatter(null)
                renderTimesADayProgress(view, vm.progress, vm.maxProgress)
            } else {
                view.habitTimesADayProgress.gone()
            }

            val habitCompleteBackground = view.habitCompletedBackground
            if (vm.isCompleted) {
                view.habitProgress.invisible()
                habitCompleteBackground.visible()
                view.habitCompletedBackground.setOnLongClickListener {
                    navigateFromRoot().toHabit(vm.id)
                    return@setOnLongClickListener true
                }
                view.habitProgress.setOnLongClickListener(null)
            } else {
                view.habitProgress.visible()
                habitCompleteBackground.invisible()
                view.habitProgress.setOnLongClickListener {
                    navigateFromRoot().toHabit(vm.id)
                    return@setOnLongClickListener true
                }
                view.habitCompletedBackground.setOnLongClickListener(null)
            }

            view.habitProgress.onDebounceClick {
                val isLastProgress = vm.maxProgress - vm.progress == 1
                if (isLastProgress) {
                    startCompleteAnimation(view, vm)
                } else {
                    dispatch(
                        if (vm.isGood) TodayAction.CompleteHabit(vm.id)
                        else TodayAction.UndoCompleteHabit(vm.id)
                    )
                }
            }

            view.habitCompletedBackground.onDebounceClick {
                startUndoCompleteAnimation(view, vm)
            }
        }

        private fun renderCompletedBackground(
            view: View,
            color: Int
        ): View? {
            val habitCompleteBackground = view.habitCompletedBackground
            val b = habitCompleteBackground.background as GradientDrawable
            b.setColor(colorRes(color))
            return habitCompleteBackground
        }

        private fun renderStreak(
            view: View,
            streak: Int,
            isBestStreak: Boolean,
            textColor: Int,
            color: Int
        ) {
            view.habitStreak.text = streak.toString()
            view.habitStreak.setTextColor(colorRes(textColor))
            if (isBestStreak) {
                view.habitBestProgressIndicator.visible()
                view.habitBestProgressIndicator.setImageDrawable(
                    IconicsDrawable(view.context).normalIcon(
                        GoogleMaterial.Icon.gmd_star,
                        color
                    )
                )
            } else {
                view.habitBestProgressIndicator.gone()
            }
        }

        private fun renderIcon(
            view: View,
            icon: IIcon,
            color: Int
        ) {
            view.habitIcon.setImageDrawable(
                IconicsDrawable(view.context).listItemIcon(icon, color)
            )
        }

        private fun renderName(
            view: View,
            name: String,
            isGood: Boolean
        ) {

            view.habitName.text = if (isGood) name else "\u2205 $name"
        }

        private fun renderProgress(
            view: View,
            progress: Int,
            maxProgress: Int
        ) {
            view.habitProgress.max = maxProgress
            view.habitProgress.progress = progress
        }

        private fun renderTimesADayProgress(
            view: View,
            progress: Int,
            maxProgress: Int
        ) {
            view.habitTimesADayProgress.max = maxProgress
            view.habitTimesADayProgress.setLineCount(maxProgress)
            view.habitTimesADayProgress.progress = progress
        }

        private fun startUndoCompleteAnimation(
            view: View,
            vm: HabitViewModel
        ) {
            val hcb = view.habitCompletedBackground
            val half = hcb.width / 2
            val completeAnim = ViewAnimationUtils.createCircularReveal(
                hcb,
                half, half,
                half.toFloat(), 0f
            )
            completeAnim.duration = shortAnimTime
            completeAnim.interpolator = AccelerateDecelerateInterpolator()
            completeAnim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    view.habitProgress.visible()
                }

                override fun onAnimationEnd(animation: Animator?) {
                    hcb.invisible()
                    view.habitIcon.setImageDrawable(
                        IconicsDrawable(view.context).normalIcon(vm.icon, vm.color)
                    )
                    view.habitStreak.setTextColor(colorRes(colorTextPrimaryResource))
                    renderProgress(view, vm.progress - 1, vm.maxProgress)
                    renderTimesADayProgress(view, vm.progress - 1, vm.maxProgress)

                    dispatch(
                        if (vm.isGood) TodayAction.UndoCompleteHabit(vm.id)
                        else TodayAction.CompleteHabit(vm.id)
                    )
                }
            })
            completeAnim.start()
        }

        private fun startCompleteAnimation(
            view: View,
            vm: HabitViewModel
        ) {
            val hcb = view.habitCompletedBackground
            val half = hcb.width / 2
            val completeAnim = ViewAnimationUtils.createCircularReveal(
                hcb,
                half, half,
                0f, half.toFloat()
            )
            completeAnim.duration = shortAnimTime
            completeAnim.interpolator = AccelerateDecelerateInterpolator()
            completeAnim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    hcb.visible()
                }

                override fun onAnimationEnd(animation: Animator?) {
                    view.habitIcon.setImageDrawable(
                        IconicsDrawable(view.context).normalIcon(vm.icon, R.color.md_white)
                    )
                    view.habitStreak.setTextColor(colorRes(R.color.md_white))
                    dispatch(
                        if (vm.isGood) TodayAction.CompleteHabit(vm.id)
                        else TodayAction.UndoCompleteHabit(vm.id)
                    )
                }
            })
            completeAnim.start()
        }
    }

    data class CompletedQuestViewModel(
        override val id: String,
        val name: String,
        val tags: List<TagViewModel>,
        val startTime: String,
        @ColorRes val color: Int,
        val icon: IIcon,
        val isRepeating: Boolean,
        val isFromChallenge: Boolean
    ) : RecyclerViewViewModel

    inner class CompletedQuestAdapter :
        BaseRecyclerViewAdapter<CompletedQuestViewModel>(R.layout.item_agenda_quest) {

        override fun onBindViewModel(
            vm: CompletedQuestViewModel,
            view: View,
            holder: SimpleViewHolder
        ) {
            view.questName.text = vm.name

            view.questIcon.backgroundTintList =
                ColorStateList.valueOf(colorRes(vm.color))
            view.questIcon.setImageDrawable(smallListItemIcon(vm.icon))

            if (vm.tags.isNotEmpty()) {
                view.questTagName.visible()
                renderTag(view, vm.tags.first())
            } else {
                view.questTagName.gone()
            }

            view.questStartTime.text = vm.startTime

            view.questRepeatIndicator.visibility =
                if (vm.isRepeating) View.VISIBLE else View.GONE
            view.questChallengeIndicator.visibility =
                if (vm.isFromChallenge) View.VISIBLE else View.GONE

            view.onDebounceClick {
                navigateFromRoot().toCompletedQuest(vm.id, VerticalChangeHandler())
            }
        }

        private fun renderTag(view: View, tag: TagViewModel) {
            view.questTagName.text = tag.name
            TextViewCompat.setTextAppearance(
                view.questTagName,
                R.style.TextAppearance_AppCompat_Caption
            )

            val indicator = view.questTagName.compoundDrawablesRelative[0] as GradientDrawable
            indicator.mutate()
            val size = ViewUtils.dpToPx(8f, view.context).toInt()
            indicator.setSize(size, size)
            indicator.setColor(colorRes(tag.color))
            view.questTagName.setCompoundDrawablesRelativeWithIntrinsicBounds(
                indicator,
                null,
                null,
                null
            )
        }
    }

    private val TodayViewState.levelText
        get() = "$level"

    private val TodayViewState.levelProgressText
        get() = "$levelXpProgress / $levelXpMaxProgress"

    private val TodayViewState.healthProgressText
        get() = "$health / $maxHealth"

    private val TodayViewState.gemsText
        get() = LongFormatter.format(activity!!, gems.toLong())

    private val TodayViewState.lifeCoinsText
        get() = LongFormatter.format(activity!!, coins.toLong())

    private val TodayViewState.petMoodColor
        get() = AndroidPetMood.valueOf(pet!!.state.name).color

    private val TodayViewState.incompleteQuestViewModels: List<TodayItemViewModel>
        get() =
            quests!!.incomplete.map {
                when (it) {
                    is CreateTodayItemsUseCase.TodayItem.UnscheduledSection ->
                        TodayItemViewModel.Section(stringRes(R.string.unscheduled))

                    is CreateTodayItemsUseCase.TodayItem.MorningSection ->
                        TodayItemViewModel.Section(stringRes(R.string.morning))

                    is CreateTodayItemsUseCase.TodayItem.AfternoonSection ->
                        TodayItemViewModel.Section(stringRes(R.string.afternoon))

                    is CreateTodayItemsUseCase.TodayItem.EveningSection ->
                        TodayItemViewModel.Section(stringRes(R.string.evening))

                    is CreateTodayItemsUseCase.TodayItem.QuestItem -> {
                        val quest = it.quest
                        TodayItemViewModel.QuestViewModel(
                            id = quest.id,
                            name = quest.name,
                            tags = quest.tags.map { t ->
                                TagViewModel(
                                    t.name,
                                    AndroidColor.valueOf(t.color.name).color500
                                )
                            },
                            startTime = QuestStartTimeFormatter.formatWithDuration(
                                quest,
                                activity!!,
                                shouldUse24HourFormat
                            ),
                            color = quest.color.androidColor.color500,
                            icon = quest.icon?.let { ic -> AndroidIcon.valueOf(ic.name).icon }
                                ?: Ionicons.Icon.ion_checkmark,
                            isRepeating = quest.isFromRepeatingQuest,
                            isFromChallenge = quest.isFromChallenge
                        )
                    }

                    is CreateTodayItemsUseCase.TodayItem.EventItem -> {
                        val event = it.event
                        TodayItemViewModel.EventViewModel(
                            id = event.name,
                            name = event.name,
                            startTime = formatStartTime(event),
                            color = event.color
                        )
                    }
                }
            }

    private fun formatStartTime(event: Event): String {
        val start = event.startTime
        val end = start.plus(event.duration.intValue)
        return "${start.toString(shouldUse24HourFormat)} - ${end.toString(shouldUse24HourFormat)}"
    }

    private val TodayViewState.habitItemViewModels: List<HabitViewModel>
        get() =
            todayHabitItems!!.map {
                val habit = it.habit
                HabitViewModel(
                    id = habit.id,
                    name = habit.name,
                    color = habit.color.androidColor.color500,
                    secondaryColor = habit.color.androidColor.color100,
                    icon = habit.icon.androidIcon.icon,
                    timesADay = habit.timesADay,
                    isCompleted = it.isCompleted,
                    isGood = habit.isGood,
                    streak = habit.streak.current,
                    isBestStreak = it.isBestStreak,
                    progress = it.completedCount,
                    maxProgress = habit.timesADay
                )
            }

    private val TodayViewState.completedQuestViewModels: List<CompletedQuestViewModel>
        get() =
            quests!!.complete.map {
                CompletedQuestViewModel(
                    id = it.id,
                    name = it.name,
                    tags = it.tags.map { t ->
                        TagViewModel(
                            t.name,
                            AndroidColor.valueOf(t.color.name).color500
                        )
                    },
                    startTime = QuestStartTimeFormatter.formatWithDuration(
                        it,
                        activity!!,
                        shouldUse24HourFormat
                    ),
                    color = R.color.md_grey_500,
                    icon = it.icon?.let { ic -> AndroidIcon.valueOf(ic.name).icon }
                        ?: Ionicons.Icon.ion_checkmark,
                    isRepeating = it.isFromRepeatingQuest,
                    isFromChallenge = it.isFromChallenge
                )
            }
}