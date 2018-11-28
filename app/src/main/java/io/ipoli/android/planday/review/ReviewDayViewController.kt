package io.ipoli.android.planday.review

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.v4.widget.TextViewCompat
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.ionicons_typeface_library.Ionicons
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.text.QuestStartTimeFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleSwipeCallback
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.quest.schedule.addquest.AddQuestAnimationHelper
import kotlinx.android.synthetic.main.controller_review_day.view.*
import kotlinx.android.synthetic.main.item_agenda_quest.view.*
import kotlinx.android.synthetic.main.item_today_habit.view.*
import kotlinx.android.synthetic.main.view_default_toolbar.view.*
import kotlinx.android.synthetic.main.view_loader.view.*
import org.threeten.bp.LocalDate

class ReviewDayViewController(args: Bundle? = null) :
    ReduxViewController<ReviewDayAction, ReviewDayViewState, ReviewDayReducer>(args) {

    override val reducer = ReviewDayReducer

    private lateinit var addQuestAnimationHelper: AddQuestAnimationHelper

    private var canBack = false
    private var hasLoaded = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = container.inflate(R.layout.controller_review_day)
        setToolbar(view.toolbar)

        view.questList.layoutManager = LinearLayoutManager(view.context)
        view.questList.adapter = QuestAdapter()

        view.completedQuestList.layoutManager = LinearLayoutManager(view.context)
        view.completedQuestList.adapter = CompletedQuestAdapter()

        view.habitList.layoutManager = GridLayoutManager(view.context, 3)
        view.habitList.adapter = HabitAdapter()

        view.toolbar.title = stringRes(R.string.review_yesterday)

        initAddQuest(view)
        initIncompleteSwipeHandler(view)
        initCompletedSwipeHandler(view)

        return view
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
                    dispatch(ReviewDayAction.CompleteQuest(questId(viewHolder)))
                    view.questList.adapter.notifyItemChanged(viewHolder.adapterPosition)
                } else {
                    view.questList.adapter.notifyItemChanged(viewHolder.adapterPosition)
                    navigate()
                        .toReschedule(
                            includeToday = false,
                            listener = { date, time, duration ->
                                dispatch(
                                    ReviewDayAction.RescheduleQuest(
                                        questId,
                                        date,
                                        time,
                                        duration
                                    )
                                )
                            },
                            cancelListener = {
                            }
                        )
                }
            }

            private fun questId(holder: RecyclerView.ViewHolder): String {
                val a = view.questList.adapter as QuestAdapter
                return a.getItemAt(holder.adapterPosition).id
            }

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) = (ItemTouchHelper.END or ItemTouchHelper.START)
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(view.questList)
    }

    private fun initCompletedSwipeHandler(view: View) {
        val swipeHandler = object : SimpleSwipeCallback(
            R.drawable.ic_undo_white_24dp,
            R.color.md_amber_500,
            R.drawable.ic_delete_white_24dp,
            R.color.md_red_500
        ) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if (direction == ItemTouchHelper.END) {
                    dispatch(ReviewDayAction.UndoCompleteQuest(questId(viewHolder)))
                    view.completedQuestList.adapter.notifyItemChanged(viewHolder.adapterPosition)
                }
            }

            private fun questId(holder: RecyclerView.ViewHolder): String {
                val a = view.completedQuestList.adapter as CompletedQuestAdapter
                return a.getItemAt(holder.adapterPosition).id
            }

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) = ItemTouchHelper.END
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(view.completedQuestList)
    }

    private fun initAddQuest(view: View) {
        addQuestAnimationHelper = AddQuestAnimationHelper(
            controller = this,
            addContainer = view.addContainer,
            fab = view.addQuest,
            background = view.addContainerBackground
        )

        view.addContainerBackground.setOnClickListener {
            addContainerRouter(view).popCurrentController()
            ViewUtils.hideKeyboard(view)
            addQuestAnimationHelper.closeAddContainer()
        }

        view.addQuest.setOnClickListener {
            addQuestAnimationHelper.openAddContainer(LocalDate.now().minusDays(1))
        }
    }

    private fun addContainerRouter(view: View) =
        getChildRouter(view.addContainer, "add-quest")

    override fun onCreateLoadAction() = ReviewDayAction.Load

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.review_day_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.actionDone) {
            view?.let {
                renderLoader(it)
            }
            dispatch(ReviewDayAction.Done)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        exitFullScreen()
    }

    override fun handleBack(): Boolean {
        return if (!hasLoaded) {
            false
        } else if (!canBack) {
            dispatch(ReviewDayAction.Done)
            false
        } else {
            true
        }
    }

    override fun render(state: ReviewDayViewState, view: View) {
        when (state.type) {

            ReviewDayViewState.StateType.LOADING -> {
                renderLoader(view)
            }

            ReviewDayViewState.StateType.QUESTS_CHANGED -> {
                renderContent(view)
                val questVms = state.questViewModels
                val completedQuestVms = state.completedQuestViewModels

                if (questVms.isEmpty()) {
                    view.questsEmpty.visible()
                    view.questList.gone()
                    view.questsEmpty.text =
                        stringRes(if (completedQuestVms.isEmpty()) R.string.yesterday_empty_quests else R.string.yesterday_all_quests_done)
                } else {
                    view.questsEmpty.gone()
                    view.questList.visible()
                    (view.questList.adapter as QuestAdapter).updateAll(questVms)
                }

                if (completedQuestVms.isEmpty()) {
                    view.completedQuestsLabel.gone()
                    view.completedQuestList.gone()
                } else {
                    view.completedQuestsLabel.visible()
                    view.completedQuestList.visible()
                    (view.completedQuestList.adapter as CompletedQuestAdapter).updateAll(
                        completedQuestVms
                    )
                }

            }

            ReviewDayViewState.StateType.HABITS_CHANGED -> {
                renderContent(view)
                val habitVms = state.habitViewModels
                if (habitVms.isEmpty()) {
                    view.habitsEmpty.visible()
                    view.habitList.gone()
                } else {
                    view.habitsEmpty.gone()
                    view.habitList.visible()
                    (view.habitList.adapter as HabitAdapter).updateAll(habitVms)
                }
            }

            ReviewDayViewState.StateType.DONE -> {
                canBack = true
                dispatch(
                    ReviewDayAction.ApplyDamage(
                        allQuestIds = state.allQuestIds,
                        habits = state.habits.filter { !it.isCompletedForDate(state.date!!) },
                        date = state.date!!
                    )
                )
                router.handleBack()
            }
        }
    }

    private fun renderLoader(view: View) {
        view.loader.visible()
        view.contentContainer.gone()
        view.addQuest.gone()
    }

    private fun renderContent(view: View) {
        hasLoaded = true
        view.loader.gone()
        view.contentContainer.visible()
        view.addQuest.visible()
    }

    data class TagViewModel(val name: String, @ColorRes val color: Int)

    data class QuestItem(
        override val id: String,
        val name: String,
        val startTime: String,
        val tags: List<TagViewModel>,
        @ColorRes val color: Int,
        val icon: IIcon,
        val isRepeating: Boolean,
        val isFromChallenge: Boolean
    ) : RecyclerViewViewModel

    data class CompletedQuestItem(
        override val id: String,
        val name: String,
        val startTime: String,
        val tags: List<TagViewModel>,
        @ColorRes val color: Int,
        val icon: IIcon,
        val isRepeating: Boolean,
        val isFromChallenge: Boolean
    ) : RecyclerViewViewModel

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

    inner class QuestAdapter : BaseRecyclerViewAdapter<QuestItem>(R.layout.item_agenda_quest) {

        override fun onBindViewModel(vm: QuestItem, view: View, holder: SimpleViewHolder) {

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                view.foreground = null
            }

            view.questName.text = vm.name

            if (vm.tags.isNotEmpty()) {
                view.questTagName.visible()
                renderTag(view, vm.tags.first())
            } else {
                view.questTagName.gone()
            }

            view.questIcon.backgroundTintList =
                ColorStateList.valueOf(colorRes(vm.color))
            view.questIcon.setImageDrawable(smallListItemIcon(vm.icon))

            view.questStartTime.text = vm.startTime

            view.questRepeatIndicator.visibility =
                if (vm.isRepeating) View.VISIBLE else View.GONE
            view.questChallengeIndicator.visibility =
                if (vm.isFromChallenge) View.VISIBLE else View.GONE
        }
    }

    inner class CompletedQuestAdapter :
        BaseRecyclerViewAdapter<CompletedQuestItem>(R.layout.item_agenda_quest) {

        override fun onBindViewModel(vm: CompletedQuestItem, view: View, holder: SimpleViewHolder) {

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                view.foreground = null
            }

            val span = SpannableString(vm.name)
            span.setSpan(StrikethroughSpan(), 0, vm.name.length, 0)

            view.questName.text = span

            if (vm.tags.isNotEmpty()) {
                view.questTagName.visible()
                renderTag(view, vm.tags.first())
            } else {
                view.questTagName.gone()
            }

            view.questIcon.backgroundTintList =
                ColorStateList.valueOf(colorRes(vm.color))
            view.questIcon.setImageDrawable(smallListItemIcon(vm.icon))

            view.questStartTime.text = vm.startTime

            view.questRepeatIndicator.visibility =
                if (vm.isRepeating) View.VISIBLE else View.GONE
            view.questChallengeIndicator.visibility =
                if (vm.isFromChallenge) View.VISIBLE else View.GONE
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
        val canBeCompletedMoreTimes: Boolean,
        val isGood: Boolean,
        val completedCount: Int
    ) : RecyclerViewViewModel

    inner class HabitAdapter : BaseRecyclerViewAdapter<HabitViewModel>(R.layout.item_today_habit) {

        override fun onBindViewModel(vm: HabitViewModel, view: View, holder: SimpleViewHolder) {
            renderName(view, vm.name, vm.isGood)

            view.habitCompletedToday.gone()

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
            } else {
                view.habitProgress.visible()
                habitCompleteBackground.invisible()
            }

            view.habitProgress.onDebounceClick {
                val isLastProgress = vm.maxProgress - vm.progress == 1
                if (isLastProgress) {
                    startCompleteAnimation(view, vm)
                } else {
                    if (vm.canBeCompletedMoreTimes) {
                        dispatch(ReviewDayAction.CompleteHabit(vm.id))
                    }
                }
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
                    dispatch(ReviewDayAction.CompleteHabit(vm.id))
                }
            })
            completeAnim.start()
        }
    }

    private val ReviewDayViewState.questViewModels: List<QuestItem>
        get() =
            quests
                .filter { !it.isCompleted }
                .map {
                    QuestItem(
                        id = it.id,
                        name = it.name,
                        tags = it.tags.map { t ->
                            TagViewModel(
                                t.name,
                                AndroidColor.valueOf(it.color.name).color500
                            )
                        },
                        startTime = QuestStartTimeFormatter.formatWithDuration(
                            it,
                            activity!!,
                            shouldUse24HourFormat
                        ),
                        color = it.color.androidColor.color500,
                        icon = it.icon?.androidIcon?.icon
                            ?: Ionicons.Icon.ion_checkmark,
                        isRepeating = it.isFromRepeatingQuest,
                        isFromChallenge = it.isFromChallenge
                    )
                }

    private val ReviewDayViewState.habitViewModels: List<HabitViewModel>
        get() =
            habits.map { habit ->
                HabitViewModel(
                    id = habit.id,
                    name = habit.name,
                    color = habit.color.androidColor.color500,
                    secondaryColor = habit.color.androidColor.color100,
                    icon = habit.icon.androidIcon.icon,
                    timesADay = habit.timesADay,
                    isCompleted = habit.isCompletedForDate(date!!),
                    canBeCompletedMoreTimes = habit.canCompleteMoreForDate(date),
                    isGood = habit.isGood,
                    streak = habit.streak.current,
                    isBestStreak = habit.streak.best != 0 && habit.streak.best == habit.streak.current,
                    progress = habit.completedCountForDate(date),
                    maxProgress = habit.timesADay,
                    completedCount = habit.completedCountForDate(date)
                )
            }

    private val ReviewDayViewState.completedQuestViewModels: List<CompletedQuestItem>
        get() =
            quests
                .filter { it.isCompleted }
                .map {
                    CompletedQuestItem(
                        id = it.id,
                        name = it.name,
                        tags = it.tags.map { t ->
                            TagViewModel(
                                t.name,
                                AndroidColor.valueOf(it.color.name).color500
                            )
                        },
                        startTime = QuestStartTimeFormatter.formatWithDuration(
                            it,
                            activity!!,
                            shouldUse24HourFormat
                        ),
                        color = R.color.md_grey_500,
                        icon = it.icon?.androidIcon?.icon
                            ?: Ionicons.Icon.ion_checkmark,
                        isRepeating = it.isFromRepeatingQuest,
                        isFromChallenge = it.isFromChallenge
                    )
                }
}