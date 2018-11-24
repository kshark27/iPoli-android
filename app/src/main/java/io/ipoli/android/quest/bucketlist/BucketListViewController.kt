package io.ipoli.android.quest.bucketlist

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
import io.ipoli.android.Constants
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.datetime.daysUntil
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.text.DateFormatter
import io.ipoli.android.common.text.QuestStartTimeFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.MultiViewRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.MultiViewTypeSwipeCallback
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SwipeResource
import io.ipoli.android.quest.CompletedQuestViewController
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.bucketlist.usecase.CreateBucketListItemsUseCase
import io.ipoli.android.quest.schedule.addquest.AddQuestAnimationHelper
import io.ipoli.android.tag.Tag
import kotlinx.android.synthetic.main.controller_bucket_list.view.*
import kotlinx.android.synthetic.main.item_agenda_quest.view.*
import kotlinx.android.synthetic.main.view_empty_list.view.*
import kotlinx.android.synthetic.main.view_loader.view.*
import org.threeten.bp.LocalDate
import space.traversal.kapsule.required

class BucketListViewController(args: Bundle? = null) :
    ReduxViewController<BucketListAction, BucketListViewState, BucketListReducer>(args) {

    override val reducer = BucketListReducer

    private val sharedPreferences by required { sharedPreferences }

    private lateinit var addQuestAnimationHelper: AddQuestAnimationHelper

    private var showCompleted = true
    private var selectedTags = emptySet<Tag>()

    override var helpConfig: HelpConfig? =
        HelpConfig(R.string.help_dialog_bucket_list_title, R.string.help_dialog_bucket_list_message)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = container.inflate(R.layout.controller_bucket_list)

        view.questList.layoutManager = LinearLayoutManager(view.context)
        view.questList.adapter = QuestAdapter()

        val swipeHandler = object : MultiViewTypeSwipeCallback(
            startResources = mapOf(
                ItemType.QUEST.ordinal to SwipeResource(
                    R.drawable.ic_done_white_24dp,
                    R.color.md_green_500
                ),
                ItemType.COMPLETED_QUEST.ordinal to SwipeResource(
                    R.drawable.ic_undo_white_24dp,
                    R.color.md_amber_500
                )
            ),
            endResources = mapOf(
                ItemType.QUEST.ordinal to SwipeResource(
                    R.drawable.ic_event_white_24dp,
                    R.color.md_blue_500
                ),
                ItemType.COMPLETED_QUEST.ordinal to SwipeResource(
                    R.drawable.ic_delete_white_24dp,
                    R.color.md_red_500
                )
            )
        ) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val questId = questId(viewHolder)
                when (viewHolder.itemViewType) {
                    ItemType.QUEST.ordinal -> {
                        if (direction == ItemTouchHelper.END) {
                            dispatch(BucketListAction.CompleteQuest(questId))
                        } else if (direction == ItemTouchHelper.START) {
                            navigate()
                                .toReschedule(
                                    includeToday = true,
                                    listener = { date, time, duration ->
                                        dispatch(
                                            BucketListAction.RescheduleQuest(
                                                questId,
                                                date,
                                                time,
                                                duration
                                            )
                                        )
                                    },
                                    cancelListener = {
                                        view.questList.adapter.notifyItemChanged(viewHolder.adapterPosition)
                                    }
                                )
                        }
                    }

                    ItemType.COMPLETED_QUEST.ordinal -> {
                        if (direction == ItemTouchHelper.END) {
                            dispatch(BucketListAction.UndoCompleteQuest(questId))
                        } else if (direction == ItemTouchHelper.START) {
                            dispatch(BucketListAction.RemoveQuest(questId))
                            PetMessagePopup(
                                stringRes(R.string.remove_quest_undo_message),
                                {
                                    dispatch(BucketListAction.UndoRemoveQuest(questId))
                                    view.questList.adapter.notifyItemChanged(viewHolder.adapterPosition)
                                },
                                stringRes(R.string.undo)
                            ).show(view.context)
                        }
                    }

                    else -> throw IllegalStateException("Swiping unknown view type ${viewHolder.itemViewType} in direction $direction")
                }

            }

            private fun questId(holder: RecyclerView.ViewHolder): String {
                val a = view.questList.adapter as QuestAdapter
                return when {
                    holder.itemViewType == ItemType.QUEST.ordinal -> {
                        val item =
                            a.getItemAt<ItemViewModel.QuestItem>(holder.adapterPosition)
                        item.id
                    }
                    holder.itemViewType == ItemType.COMPLETED_QUEST.ordinal -> {
                        val item =
                            a.getItemAt<ItemViewModel.CompletedQuestItem>(holder.adapterPosition)
                        item.id
                    }
                    else -> throw IllegalStateException("Unknown questId for viewType ${holder.itemViewType}")
                }

            }

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) = when {
                viewHolder.itemViewType == ItemType.QUEST.ordinal -> (ItemTouchHelper.END or ItemTouchHelper.START)
                viewHolder.itemViewType == ItemType.COMPLETED_QUEST.ordinal -> (ItemTouchHelper.END or ItemTouchHelper.START)
                else -> 0
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(view.questList)

        initEmptyView(view)

        initAddQuest(view)

        showCompleted = sharedPreferences.getBoolean(
            Constants.KEY_BUCKET_LIST_SHOW_COMPLETED,
            Constants.DEFAULT_BUCKET_LIST_SHOW_COMPLETED
        )

        return view
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
            addQuestAnimationHelper.openAddContainer(null)
        }
    }

    private fun addContainerRouter(view: View) =
        getChildRouter(view.addContainer, "add-quest")

    private fun initEmptyView(view: View) {
        view.emptyAnimation.setAnimation("empty_bucket_list.json")
        view.emptyTitle.setText(R.string.empty_bucket_list_title)
        view.emptyText.setText(R.string.empty_bucket_list_text)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        enableToolbarTitle()
        toolbarTitle = stringRes(R.string.title_bucket_list)
    }

    override fun onCreateLoadAction() = BucketListAction.Load(showCompleted)

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.bucket_list_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.actionFilter) {
            navigate().toFilter(showCompleted, selectedTags) { sc, st ->
                sharedPreferences
                    .edit()
                    .putBoolean(Constants.KEY_BUCKET_LIST_SHOW_COMPLETED, sc)
                    .apply()
                showCompleted = sc
                selectedTags = st
                dispatch(BucketListAction.Filter(sc, st))
            }
            return true
        } else if (item.itemId == R.id.actionRemoveCompleted) {
            navigate().toConfirmation(
                stringRes(R.string.dialog_confirmation_title),
                stringRes(R.string.dialog_remove_completed_quests_confirmation_message)
            ) {
                dispatch(BucketListAction.RemoveAllCompleted)
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun render(state: BucketListViewState, view: View) {
        when (state.type) {

            BucketListViewState.StateType.EMPTY ->
                renderEmpty(view)

            BucketListViewState.StateType.DATA_CHANGED ->
                renderQuests(state, view)

            else -> {
            }
        }
    }

    private fun renderEmpty(view: View) {
        view.loader.gone()
        view.questList.gone()
        view.emptyContainer.visible()
        view.emptyAnimation.playAnimation()
    }

    private fun renderQuests(
        state: BucketListViewState,
        view: View
    ) {
        view.loader.gone()
        view.emptyContainer.gone()
        view.emptyAnimation.pauseAnimation()
        view.questList.visible()
        (view.questList.adapter as QuestAdapter).updateAll(state.itemViewModels)
    }

    sealed class ItemViewModel(override val id: String) : RecyclerViewViewModel {

        data class SectionItem(val text: String) : ItemViewModel(text)

        data class TagViewModel(val name: String, @ColorRes val color: Int)

        data class QuestItem(
            override val id: String,
            val name: String,
            val dueDate: String,
            @ColorRes val color: Int,
            val tags: List<TagViewModel>,
            val icon: IIcon,
            val isRepeating: Boolean,
            val isFromChallenge: Boolean
        ) : ItemViewModel(id)

        data class CompletedQuestItem(
            override val id: String,
            val name: String,
            val startTime: String,
            @ColorRes val color: Int,
            val tags: List<TagViewModel>,
            val icon: IIcon,
            val isRepeating: Boolean,
            val isFromChallenge: Boolean
        ) : ItemViewModel(id)
    }

    enum class ItemType {
        SECTION,
        QUEST,
        COMPLETED_QUEST
    }

    inner class QuestAdapter : MultiViewRecyclerViewAdapter<ItemViewModel>() {

        override fun onRegisterItemBinders() {

            registerBinder<ItemViewModel.SectionItem>(
                ItemType.SECTION.ordinal,
                R.layout.item_list_section
            ) { vm, view, _ ->
                (view as TextView).text = vm.text
            }

            registerBinder<ItemViewModel.QuestItem>(
                ItemType.QUEST.ordinal,
                R.layout.item_agenda_quest
            ) { vm, view, _ ->
                view.questName.text = vm.name

                view.questIcon.backgroundTintList =
                    ColorStateList.valueOf(colorRes(vm.color))

                view.questIcon.setImageDrawable(listItemIcon(vm.icon))

                view.questStartTime.text = vm.dueDate

                view.questRepeatIndicator.visibility =
                    if (vm.isRepeating) View.VISIBLE else View.GONE
                view.questChallengeIndicator.visibility =
                    if (vm.isFromChallenge) View.VISIBLE else View.GONE

                if (vm.tags.isNotEmpty()) {
                    view.questTagName.visible()
                    renderTag(view, vm.tags.first())
                } else {
                    view.questTagName.gone()
                }

                view.onDebounceClick {
                    navigateFromRoot().toQuest(vm.id)
                }
            }

            registerBinder<ItemViewModel.CompletedQuestItem>(
                ItemType.COMPLETED_QUEST.ordinal,
                R.layout.item_agenda_quest
            ) { vm, view, _ ->
                val span = SpannableString(vm.name)
                span.setSpan(StrikethroughSpan(), 0, vm.name.length, 0)

                view.questName.text = span

                view.questIcon.backgroundTintList =
                    ColorStateList.valueOf(colorRes(vm.color))
                view.questIcon.setImageDrawable(listItemIcon(vm.icon))

                view.questStartTime.text = vm.startTime

                view.questRepeatIndicator.visibility =
                    if (vm.isRepeating) View.VISIBLE else View.GONE
                view.questChallengeIndicator.visibility =
                    if (vm.isFromChallenge) View.VISIBLE else View.GONE

                if (vm.tags.isNotEmpty()) {
                    view.questTagName.visible()
                    renderTag(view, vm.tags.first())
                } else {
                    view.questTagName.gone()
                }

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

    private val BucketListViewState.itemViewModels: List<ItemViewModel>
        get() = visibleItems.map {
            when (it) {
                is CreateBucketListItemsUseCase.BucketListItem.QuestItem -> {

                    val q = it.quest

                    val color = if (q.isCompleted)
                        R.color.md_grey_500
                    else
                        q.color.androidColor.color500

                    if (q.isCompleted) {
                        ItemViewModel.CompletedQuestItem(
                            id = q.id,
                            name = q.name,
                            startTime = QuestStartTimeFormatter.formatWithDuration(
                                q,
                                activity!!,
                                shouldUse24HourFormat
                            ),
                            color = color,
                            icon = q.icon?.androidIcon?.icon
                                ?: Ionicons.Icon.ion_checkmark,
                            tags = q.tags.map { t ->
                                ItemViewModel.TagViewModel(
                                    t.name,
                                    t.color.androidColor.color500
                                )
                            },
                            isRepeating = q.isFromRepeatingQuest,
                            isFromChallenge = q.isFromChallenge
                        )
                    } else {

                        ItemViewModel.QuestItem(
                            id = q.id,
                            name = q.name,
                            dueDate = formatDueDate(q),
                            color = color,
                            icon = q.icon?.androidIcon?.icon
                                ?: Ionicons.Icon.ion_checkmark,
                            tags = q.tags.map { t ->
                                ItemViewModel.TagViewModel(
                                    t.name,
                                    t.color.androidColor.color500
                                )
                            },
                            isRepeating = q.isFromRepeatingQuest,
                            isFromChallenge = q.isFromChallenge
                        )
                    }
                }

                is CreateBucketListItemsUseCase.BucketListItem.Today ->
                    ItemViewModel.SectionItem(stringRes(R.string.today))

                is CreateBucketListItemsUseCase.BucketListItem.Tomorrow ->
                    ItemViewModel.SectionItem(stringRes(R.string.tomorrow))

                is CreateBucketListItemsUseCase.BucketListItem.Upcoming ->
                    ItemViewModel.SectionItem(stringRes(R.string.upcoming))

                is CreateBucketListItemsUseCase.BucketListItem.Completed ->
                    ItemViewModel.SectionItem(stringRes(R.string.completed))

                is CreateBucketListItemsUseCase.BucketListItem.Overdue ->
                    ItemViewModel.SectionItem(stringRes(R.string.overdue))

                CreateBucketListItemsUseCase.BucketListItem.SomeDay ->
                    ItemViewModel.SectionItem(stringRes(R.string.someday))
            }

        }

    private fun formatDueDate(quest: Quest): String {
        if (quest.dueDate == null) {
            return QuestStartTimeFormatter.formatWithDuration(
                quest,
                activity!!,
                shouldUse24HourFormat
            )
        }
        val dueDate = quest.dueDate
        val today = LocalDate.now()
        return if (dueDate.isBefore(today)) {
            val overdueDays = dueDate.daysUntil(today)
            "Overdue by $overdueDays days"
        } else {
            "Due " + DateFormatter.formatWithoutYear(
                activity!!,
                dueDate
            ) + QuestStartTimeFormatter.formatWithDuration(quest, activity!!, shouldUse24HourFormat)
        }
    }
}