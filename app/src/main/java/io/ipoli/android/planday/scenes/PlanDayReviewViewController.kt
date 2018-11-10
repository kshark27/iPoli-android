package io.ipoli.android.planday.scenes

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.v4.widget.TextViewCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.view.*
import android.widget.TextView
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.ionicons_typeface_library.Ionicons
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.redux.android.BaseViewController
import io.ipoli.android.common.text.QuestStartTimeFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.MultiViewRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SwipeCallback
import io.ipoli.android.common.view.recyclerview.SwipeResource
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.PetState
import io.ipoli.android.planday.PlanDayAction
import io.ipoli.android.planday.PlanDayReducer
import io.ipoli.android.planday.PlanDayViewState
import io.ipoli.android.planday.PlanDayViewState.AwesomenessGrade.*
import io.ipoli.android.planday.PlanDayViewState.StateType.REVIEW_DATA_LOADED
import io.ipoli.android.quest.CompletedQuestViewController
import io.ipoli.android.quest.schedule.addquest.AddQuestAnimationHelper
import kotlinx.android.synthetic.main.controller_plan_day_review.view.*
import kotlinx.android.synthetic.main.item_agenda_quest.view.*
import kotlinx.android.synthetic.main.view_empty_list.view.*
import kotlinx.android.synthetic.main.view_loader.view.*
import org.threeten.bp.LocalDate

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 5/14/18.
 */

class PlanDayReviewViewController(args: Bundle? = null) :
    BaseViewController<PlanDayAction, PlanDayViewState>(args) {

    override val stateKey = PlanDayReducer.stateKey

    private lateinit var addQuestAnimationHelper: AddQuestAnimationHelper

    override var helpConfig: HelpConfig? =
        HelpConfig(
            R.string.help_dialog_plan_day_review_title,
            R.string.help_dialog_plan_day_review_message
        )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = container.inflate(R.layout.controller_plan_day_review)
        setToolbar(view.toolbar)
        val collapsingToolbar = view.collapsingToolbarContainer
        collapsingToolbar.isTitleEnabled = false
        view.toolbar.title = stringRes(R.string.yesterday_review)


        view.reviewQuests.layoutManager =
            LinearLayoutManager(container.context, LinearLayoutManager.VERTICAL, false)
        view.reviewQuests.adapter = QuestAdapter()

        initSwipe(view)
        initAddQuest(view)
        initEmptyView(view)
        return view
    }

    private fun initEmptyView(view: View) {
        view.emptyAnimation.setAnimation("empty_yesterday_review.json")
        view.emptyTitle.setText(R.string.empty_review_quest_list_title)
        view.emptyText.setText(R.string.empty_review_quest_list_text)
    }


    private fun initSwipe(view: View) {
        val swipeHandler = object : SwipeCallback() {
            private val completeQuestSwipeRes = SwipeResource(
                R.drawable.ic_done_white_24dp,
                R.color.md_green_500
            )

            private val scheduleQuestSwipeRes = SwipeResource(
                R.drawable.ic_event_white_24dp,
                R.color.md_blue_500
            )

            private val undoCompleteQuestSwipeRes = SwipeResource(
                R.drawable.ic_close_white_24dp, R.color.md_amber_500
            )

            override fun swipeStartResource(itemViewType: Int) =
                when (itemViewType) {
                    ViewType.QUEST.value -> completeQuestSwipeRes
                    else -> throw IllegalStateException("Can't swipe start type $itemViewType")
                }

            override fun swipeEndResource(itemViewType: Int) =
                when (itemViewType) {
                    ViewType.QUEST.value -> scheduleQuestSwipeRes
                    ViewType.COMPLETED_QUEST.value -> undoCompleteQuestSwipeRes
                    else -> throw IllegalStateException("Can't swipe end type $itemViewType")
                }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                when {
                    viewHolder.itemViewType == ViewType.QUEST.value && direction == ItemTouchHelper.END ->
                        dispatch(PlanDayAction.CompleteYesterdayQuest(questId(viewHolder)))

                    viewHolder.itemViewType == ViewType.COMPLETED_QUEST.value && direction == ItemTouchHelper.START ->
                        dispatch(PlanDayAction.UndoCompleteQuest(questId(viewHolder)))

                    viewHolder.itemViewType == ViewType.QUEST.value && direction == ItemTouchHelper.START -> {
                        val questId = questId(viewHolder)
                        navigate()
                            .toReschedule(
                                includeToday = true,
                                listener = { date, time, duration ->
                                    dispatch(PlanDayAction.RescheduleQuest(questId, date, time, duration))
                                },
                                cancelListener = {
                                    view.reviewQuests.adapter.notifyItemChanged(viewHolder.adapterPosition)
                                }
                            )
                    }

                }
            }

            private fun questId(holder: RecyclerView.ViewHolder): String {
                val adapter = view.reviewQuests.adapter as QuestAdapter
                return if (holder.itemViewType == ViewType.QUEST.value) {
                    val item = adapter.getItemAt<ItemViewModel.QuestItem>(holder.adapterPosition)
                    item.id
                } else {
                    val item =
                        adapter.getItemAt<ItemViewModel.CompletedQuestItem>(holder.adapterPosition)
                    item.id
                }
            }

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) = when {
                viewHolder.itemViewType == ViewType.QUEST.value -> (ItemTouchHelper.END or ItemTouchHelper.START)
                viewHolder.itemViewType == ViewType.COMPLETED_QUEST.value -> (ItemTouchHelper.START)
                else -> 0
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(view.reviewQuests)
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

    override fun onCreateLoadAction() = PlanDayAction.LoadReviewDay

    override fun onAttach(view: View) {
        super.onAttach(view)
        exitFullScreen()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.plan_day_review_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.actionPlanDay) {
            dispatch(PlanDayAction.ShowNext)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun render(state: PlanDayViewState, view: View) {
        when (state.type) {

            REVIEW_DATA_LOADED -> {
                view.reviewAwesomenessScore.text = state.awesomenessScore!!.name
                view.loader.gone()
                (view.reviewQuests.adapter as QuestAdapter).updateAll(state.reviewDayQuestViewModels)
                if (state.reviewDayQuests!!.isEmpty()) {
                    view.emptyContainer.visible()
                    view.emptyAnimation.playAnimation()
                } else {
                    view.emptyContainer.gone()
                    view.emptyAnimation.pauseAnimation()
                }
                renderPet(view, state)
            }

            else -> {
            }
        }
    }

    private fun renderPet(view: View, state: PlanDayViewState) {
        view.reviewPet.setImageResource(state.petAvatarImage)
        view.reviewPetState.setImageResource(state.petAvatarStateImage)
        view.reviewPet.visible()
        view.reviewPetState.visible()
    }

    enum class ViewType(val value: Int) {
        QUEST(1),
        COMPLETE_QUEST_LABEL_TYPE(2),
        COMPLETED_QUEST(3)
    }

    sealed class ItemViewModel(override val id: String) : RecyclerViewViewModel {

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
        ) : ItemViewModel(id)

        data class CompletedLabel(
            override val id: String, val label: String
        ) : ItemViewModel(id)

        data class CompletedQuestItem(
            override val id: String,
            val name: String,
            val startTime: String,
            val tags: List<TagViewModel>,
            @ColorRes val color: Int,
            val icon: IIcon,
            val isRepeating: Boolean,
            val isFromChallenge: Boolean
        ) : ItemViewModel(id)

    }

    inner class QuestAdapter : MultiViewRecyclerViewAdapter<ItemViewModel>() {

        override fun onRegisterItemBinders() {

            registerBinder<ItemViewModel.QuestItem>(
                ViewType.QUEST.value,
                R.layout.item_agenda_quest
            ) { vm, view, _ ->

                view.questName.text = vm.name

                if (vm.tags.isNotEmpty()) {
                    view.questTagName.visible()
                    renderTag(view, vm.tags.first())
                } else {
                    view.questTagName.gone()
                }

                view.questIcon.backgroundTintList =
                    ColorStateList.valueOf(colorRes(vm.color))
                view.questIcon.setImageDrawable(listItemIcon(vm.icon))

                view.questStartTime.text = vm.startTime

                view.questRepeatIndicator.visibility =
                    if (vm.isRepeating) View.VISIBLE else View.GONE
                view.questChallengeIndicator.visibility =
                    if (vm.isFromChallenge) View.VISIBLE else View.GONE
            }

            registerBinder<ItemViewModel.CompletedLabel>(
                ViewType.COMPLETE_QUEST_LABEL_TYPE.value,
                R.layout.item_list_section
            ) { vm, view, _ ->
                (view as TextView).text = vm.label
            }

            registerBinder<ItemViewModel.CompletedQuestItem>(
                ViewType.COMPLETED_QUEST.value,
                R.layout.item_agenda_quest
            ) { vm, view, _ ->
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
                view.questIcon.setImageDrawable(listItemIcon(vm.icon))

                view.questStartTime.text = vm.startTime

                view.questRepeatIndicator.visibility =
                    if (vm.isRepeating) View.VISIBLE else View.GONE
                view.questChallengeIndicator.visibility =
                    if (vm.isFromChallenge) View.VISIBLE else View.GONE

                view.setOnClickListener {
                    val handler = FadeChangeHandler()
                    rootRouter.pushController(
                        RouterTransaction
                            .with(CompletedQuestViewController(vm.id))
                            .pushChangeHandler(handler)
                            .popChangeHandler(handler)
                    )
                }
            }
        }

        private fun renderTag(view: View, tag: ItemViewModel.TagViewModel) {
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

    private val PlanDayViewState.reviewDayQuestViewModels: List<ItemViewModel>
        get() {
            if (reviewDayQuests!!.isEmpty()) {
                return emptyList()
            }
            val (completed, notCompleted) = reviewDayQuests.partition { it.isCompleted }
            val vms = mutableListOf<ItemViewModel>()
            vms.addAll(notCompleted.map {
                ItemViewModel.QuestItem(
                    id = it.id,
                    name = it.name,
                    tags = it.tags.map { t ->
                        ItemViewModel.TagViewModel(
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
            })

            vms.add(ItemViewModel.CompletedLabel("Label", stringRes(R.string.completed)))
            vms.addAll(completed.map {
                ItemViewModel.CompletedQuestItem(
                    id = it.id,
                    name = it.name,
                    tags = it.tags.map { t ->
                        ItemViewModel.TagViewModel(
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
            })
            return vms
        }

    private val PlanDayViewState.petAvatarImage: Int
        get() = AndroidPetAvatar.valueOf(petAvatar!!.name).image

    private val PlanDayViewState.petAvatarStateImage: Int
        get() {
            val stateImage = AndroidPetAvatar.valueOf(petAvatar!!.name).stateImage
            return when (awesomenessScore!!) {
                A -> stateImage[PetState.AWESOME]!!
                B -> stateImage[PetState.HAPPY]!!
                C -> stateImage[PetState.GOOD]!!
                D -> stateImage[PetState.SAD]!!
                F -> stateImage[PetState.DEAD]!!
            }
        }

}