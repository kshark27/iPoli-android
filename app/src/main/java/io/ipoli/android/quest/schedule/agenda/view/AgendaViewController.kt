package io.ipoli.android.quest.schedule.agenda.view

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.annotation.ColorRes
import android.support.annotation.DrawableRes
import android.support.v4.widget.TextViewCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bluelinelabs.conductor.RouterTransaction
import com.haibin.calendarview.Calendar
import com.haibin.calendarview.CalendarView
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.ionicons_typeface_library.Ionicons
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.datetime.*
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.text.DateFormatter
import io.ipoli.android.common.text.QuestStartTimeFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.MultiViewRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.MultiViewTypeSwipeCallback
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SwipeResource
import io.ipoli.android.event.Event
import io.ipoli.android.habit.show.HabitViewController
import io.ipoli.android.quest.CompletedQuestViewController
import io.ipoli.android.quest.schedule.agenda.usecase.CreateAgendaItemsUseCase
import io.ipoli.android.quest.schedule.agenda.usecase.CreateAgendaPreviewItemsUseCase
import io.ipoli.android.quest.schedule.agenda.view.AgendaViewState.StateType.*
import kotlinx.android.synthetic.main.controller_agenda.view.*
import kotlinx.android.synthetic.main.item_agenda_event.view.*
import kotlinx.android.synthetic.main.item_agenda_month_divider.view.*
import kotlinx.android.synthetic.main.item_agenda_quest.view.*
import org.json.JSONArray
import org.json.JSONObject
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate
import org.threeten.bp.Month
import org.threeten.bp.YearMonth
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.TextStyle
import java.util.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 1/26/18.
 */
class AgendaViewController(args: Bundle? = null) :
    ReduxViewController<AgendaAction, AgendaViewState, AgendaReducer>(args) {

    override val reducer = AgendaReducer

    private var scrollToPositionListener: RecyclerView.OnScrollListener? = null

    private val monthToImage = mapOf(
        Month.JANUARY to R.drawable.agenda_january,
        Month.FEBRUARY to R.drawable.agenda_february,
        Month.MARCH to R.drawable.agenda_march,
        Month.APRIL to R.drawable.agenda_april,
        Month.MAY to R.drawable.agenda_may,
        Month.JUNE to R.drawable.agenda_june,
        Month.JULY to R.drawable.agenda_july,
        Month.AUGUST to R.drawable.agenda_august,
        Month.SEPTEMBER to R.drawable.agenda_september,
        Month.OCTOBER to R.drawable.agenda_october,
        Month.NOVEMBER to R.drawable.agenda_november,
        Month.DECEMBER to R.drawable.agenda_december
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.controller_agenda, container, false)
        val layoutManager =
            LinearLayoutManager(container.context, LinearLayoutManager.VERTICAL, false)
        view.agendaList.layoutManager = layoutManager
        view.agendaList.adapter = AgendaAdapter()

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
                            dispatch(
                                AgendaAction.CompleteQuest(
                                    questId
                                )
                            )
                        } else if (direction == ItemTouchHelper.START) {
                            view.agendaList.adapter.notifyItemChanged(viewHolder.adapterPosition)
                            val a = view.agendaList.adapter as AgendaAdapter
                            val vm =
                                a.getItemAt<AgendaViewModel.QuestViewModel>(viewHolder.adapterPosition)
                            navigate()
                                .toReschedule(
                                    includeToday = !vm.isScheduledForToday,
                                    listener = { date, time, duration ->
                                        dispatch(
                                            AgendaAction.RescheduleQuest(
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

                    ItemType.COMPLETED_QUEST.ordinal -> {
                        if (direction == ItemTouchHelper.END) {
                            dispatch(
                                AgendaAction.UndoCompleteQuest(
                                    questId
                                )
                            )
                        } else if (direction == ItemTouchHelper.START) {
                            dispatch(
                                AgendaAction.RemoveQuest(
                                    questId
                                )
                            )
                            PetMessagePopup(
                                stringRes(R.string.remove_quest_undo_message),
                                {
                                    dispatch(
                                        AgendaAction.UndoRemoveQuest(
                                            questId
                                        )
                                    )
                                    view.agendaList.adapter.notifyItemChanged(viewHolder.adapterPosition)
                                },
                                stringRes(R.string.undo)
                            ).show(view.context)
                        }
                    }

                    else -> throw IllegalStateException("Swiping unknown view type ${viewHolder.itemViewType} in direction $direction")
                }

            }

            private fun questId(holder: RecyclerView.ViewHolder): String {
                val a = view.agendaList.adapter as AgendaAdapter
                return when {
                    holder.itemViewType == ItemType.QUEST.ordinal -> {
                        val item =
                            a.getItemAt<AgendaViewModel.QuestViewModel>(holder.adapterPosition)
                        item.id
                    }
                    holder.itemViewType == ItemType.COMPLETED_QUEST.ordinal -> {
                        val item =
                            a.getItemAt<AgendaViewModel.CompletedQuestViewModel>(holder.adapterPosition)
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
        itemTouchHelper.attachToRecyclerView(view.agendaList)

        when (DateUtils.firstDayOfWeek) {
            DayOfWeek.SATURDAY -> view.calendarView.setWeekStarWithSat()
            DayOfWeek.SUNDAY -> view.calendarView.setWeekStarWithSun()
            else -> view.calendarView.setWeekStarWithMon()
        }

        view.calendarView.setOnMonthChangeListener(HabitViewController.SkipFirstChangeMonthListener { year, month ->
            dispatch(AgendaAction.ChangePreviewMonth(YearMonth.of(year, month)))
        })

        view.calendarView.setOnCalendarSelectListener(object :
            CalendarView.OnCalendarSelectListener {
            override fun onCalendarSelect(calendar: Calendar, isClick: Boolean) {

                if (isClick) {
                    dispatch(
                        AgendaAction.ChangePreviewDate(
                            LocalDate.of(
                                calendar.year,
                                calendar.month,
                                calendar.day
                            )
                        )
                    )
                }
            }

            override fun onCalendarOutOfRange(calendar: Calendar) {
            }

        })

        fixInitialHeight(view)

        return view
    }

    override fun onCreateLoadAction() =
        AgendaAction.Load

    override fun onAttach(view: View) {
        if (!view.calendarContainer.isExpand) {
            fixInitialHeight(view)
        }
        super.onAttach(view)
    }

    private fun fixInitialHeight(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return
        }
        view.postDelayed({
            activity?.let {
                val calendarHeight = ViewUtils.dpToPx(80f, view.context).toInt()
                val lp = view.agendaListContainer.layoutParams as ViewGroup.MarginLayoutParams
                lp.topMargin = calendarHeight
                view.agendaListContainer.layoutParams = lp
            }
        }, 100)
    }

    override fun onDetach(view: View) {
        view.agendaList.clearOnScrollListeners()
        super.onDetach(view)
    }

    override fun render(state: AgendaViewState, view: View) {

        when (state.type) {

            DATA_CHANGED -> {
                ViewUtils.goneViews(view.topLoader, view.bottomLoader)
                val agendaList = view.agendaList
                agendaList.clearOnScrollListeners()
                (agendaList.adapter as AgendaAdapter).updateAll(state.toAgendaItemViewModels())
                addScrollListeners(agendaList, state)
            }

            CALENDAR_DATA_CHANGED -> {
                view.calendarView.setSchemeDate(state.previewCalendars.map { it.toString() to it }.toMap())
            }

            SHOW_TOP_LOADER -> {
                ViewUtils.showViews(view.topLoader)
            }

            SHOW_BOTTOM_LOADER -> {
                ViewUtils.showViews(view.bottomLoader)
            }

            PREVIEW_MODE_CHANGED -> {
                if (state.previewMode == AgendaViewState.PreviewMode.MONTH) {
                    renderMonthPreview(view)
                } else {
                    renderWeekPreview(view)
                }
            }

            VISIBLE_DATE_CHANGED -> {
                val date = state.currentDate!!
                if (view.calendarView.curDay != date.dayOfMonth || view.calendarView.curMonth != date.monthValue || view.calendarView.curYear != date.year) {
                    view.calendarView.scrollToCalendar(
                        date.year,
                        date.monthValue,
                        date.dayOfMonth,
                        true
                    )
                }
            }

            SHOW_TODAY -> {
                val date = state.currentDate!!
                view.calendarView.scrollToCalendar(
                    date.year,
                    date.monthValue,
                    date.dayOfMonth,
                    true
                )
            }

            else -> {
            }
        }
    }

    private fun renderWeekPreview(view: View) {
        val calendarHeight = ViewUtils.dpToPx(88f, view.context).toInt()
        view.calendarView.setCalendarItemHeight(calendarHeight)
        view.calendarContainer.shrink()

        val lp = view.agendaListContainer.layoutParams as ViewGroup.MarginLayoutParams
        lp.topMargin = calendarHeight
        view.agendaListContainer.layoutParams = lp
    }

    private fun renderMonthPreview(view: View) {
        val calendarHeight = ViewUtils.dpToPx(36f, view.context).toInt()
        view.calendarView.setCalendarItemHeight(calendarHeight)
        view.calendarContainer.expand()
        val lp = view.agendaListContainer.layoutParams as ViewGroup.MarginLayoutParams
        lp.topMargin = 0
        view.agendaListContainer.layoutParams = lp
    }

    private fun addScrollListeners(
        agendaList: RecyclerView,
        state: AgendaViewState
    ) {

        val endlessRecyclerViewScrollListener =
            EndlessRecyclerViewScrollListener(
                agendaList.layoutManager as LinearLayoutManager,
                { side, position ->
                    agendaList.clearOnScrollListeners()
                    if (side == EndlessRecyclerViewScrollListener.Side.TOP) {
                        dispatch(
                            AgendaAction.LoadBefore(
                                position
                            )
                        )
                    } else {
                        dispatch(
                            AgendaAction.LoadAfter(
                                position
                            )
                        )
                    }
                },
                15
            )
        val changeItemScrollListener = ChangeItemScrollListener(
            agendaList.layoutManager as LinearLayoutManager
        ) { pos ->
            dispatch(
                AgendaAction.FirstVisibleItemChanged(
                    pos
                )
            )
        }

        scrollToPositionListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                agendaList.addOnScrollListener(endlessRecyclerViewScrollListener)
                agendaList.addOnScrollListener(changeItemScrollListener)
                agendaList.removeOnScrollListener(scrollToPositionListener)
            }
        }

        if (state.scrollToPosition != null) {
            agendaList.addOnScrollListener(scrollToPositionListener)
            agendaList.postDelayed({
                (agendaList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                    state.scrollToPosition, 0
                )
            }, 100)

        } else {
            agendaList.addOnScrollListener(endlessRecyclerViewScrollListener)
            agendaList.addOnScrollListener(changeItemScrollListener)
        }
    }

    private fun showCompletedQuest(questId: String) {
        pushWithRootRouter(RouterTransaction.with(CompletedQuestViewController(questId)))
    }

    private fun showQuest(questId: String) {
        navigateFromRoot().toQuest(questId)
    }

    data class TagViewModel(val name: String, @ColorRes val color: Int)

    sealed class AgendaViewModel(override val id: String) : RecyclerViewViewModel {
        interface QuestItemViewModel {
            val name: String
            val tags: List<TagViewModel>
            val startTime: String
            val color: Int
            val icon: IIcon
            val showDivider: Boolean
            val isRepeating: Boolean
            val isFromChallenge: Boolean
        }

        data class QuestViewModel(
            override val id: String,
            override val name: String,
            override val tags: List<TagViewModel>,
            override val startTime: String,
            override val color: Int,
            override val icon: IIcon,
            override val showDivider: Boolean,
            override val isRepeating: Boolean,
            override val isFromChallenge: Boolean,
            val isScheduledForToday: Boolean
        ) : AgendaViewModel(id),
            QuestItemViewModel

        data class QuestPlaceholderViewModel(
            override val id: String,
            override val name: String,
            override val tags: List<TagViewModel>,
            override val startTime: String,
            override val color: Int,
            override val icon: IIcon,
            override val showDivider: Boolean,
            override val isRepeating: Boolean,
            override val isFromChallenge: Boolean
        ) : AgendaViewModel(id),
            QuestItemViewModel

        data class CompletedQuestViewModel(
            override val id: String,
            override val name: String,
            override val tags: List<TagViewModel>,
            override val startTime: String,
            override val color: Int,
            override val icon: IIcon,
            override val showDivider: Boolean,
            override val isRepeating: Boolean,
            override val isFromChallenge: Boolean
        ) : AgendaViewModel(id),
            QuestItemViewModel

        data class EventViewModel(
            override val id: String,
            val name: String,
            val startTime: String,
            @ColorInt val color: Int,
            val showDivider: Boolean
        ) :
            AgendaViewModel(id)

        data class DateHeaderViewModel(
            override val id: String,
            val text: String,
            @ColorInt val textColor: Int
        ) : AgendaViewModel(id)

        data class MonthDividerViewModel(
            override val id: String,
            @DrawableRes val image: Int, val text: String
        ) : AgendaViewModel(id)

        data class WeekHeaderViewModel(
            override val id: String,
            val text: String
        ) : AgendaViewModel(id)
    }

    enum class ItemType {
        QUEST_PLACEHOLDER, QUEST, COMPLETED_QUEST, EVENT, DATE_HEADER, MONTH_DIVIDER, WEEK_HEADER
    }

    inner class AgendaAdapter : MultiViewRecyclerViewAdapter<AgendaViewModel>() {
        override fun onRegisterItemBinders() {

            registerBinder<AgendaViewModel.QuestPlaceholderViewModel>(
                ItemType.QUEST_PLACEHOLDER.ordinal,
                R.layout.item_agenda_quest
            ) { vm, view, _ ->
                bindPlaceholderViewModel(view, vm)
            }

            registerBinder<AgendaViewModel.QuestViewModel>(
                ItemType.QUEST.ordinal,
                R.layout.item_agenda_quest
            ) { vm, view, _ ->
                bindQuestViewModel(view, vm)
            }

            registerBinder<AgendaViewModel.CompletedQuestViewModel>(
                ItemType.COMPLETED_QUEST.ordinal,
                R.layout.item_agenda_quest
            ) { vm, view, _ ->
                bindCompleteQuestViewModel(view, vm)
            }

            registerBinder<AgendaViewModel.EventViewModel>(
                ItemType.EVENT.ordinal,
                R.layout.item_agenda_event
            ) { vm, view, _ ->
                bindEventViewModel(view, vm)
            }

            registerBinder<AgendaViewModel.DateHeaderViewModel>(
                ItemType.DATE_HEADER.ordinal,
                R.layout.item_agenda_list_section
            ) { vm, view, _ ->
                bindDateHeaderViewModel(view, vm)
            }

            registerBinder<AgendaViewModel.MonthDividerViewModel>(
                ItemType.MONTH_DIVIDER.ordinal,
                R.layout.item_agenda_month_divider
            ) { vm, view, _ ->
                bindMonthDividerViewModel(view, vm)
            }

            registerBinder<AgendaViewModel.WeekHeaderViewModel>(
                ItemType.WEEK_HEADER.ordinal,
                R.layout.item_agenda_week_header
            ) { vm, view, _ ->
                bindWeekHeaderViewModel(view, vm)
            }
        }

        private fun bindPlaceholderViewModel(
            view: View,
            vm: AgendaViewModel.QuestPlaceholderViewModel
        ) {
            view.setOnClickListener(null)
            view.questName.text = vm.name
            bindQuest(view, vm)
        }

        private fun bindEventViewModel(view: View, viewModel: AgendaViewModel.EventViewModel) {

            view.eventName.text = viewModel.name
            view.eventStartTime.text = viewModel.startTime

            view.eventIcon.backgroundTintList =
                ColorStateList.valueOf(viewModel.color)
        }

        private fun bindWeekHeaderViewModel(
            view: View,
            viewModel: AgendaViewModel.WeekHeaderViewModel
        ) {
            view.setOnClickListener(null)
            (view as TextView).text = viewModel.text
        }

        private fun bindMonthDividerViewModel(
            view: View,
            viewModel: AgendaViewModel.MonthDividerViewModel
        ) {
            view.setOnClickListener(null)
            view.dateLabel.text = viewModel.text
            view.monthImage.setImageResource(viewModel.image)
        }

        private fun bindDateHeaderViewModel(
            view: View,
            viewModel: AgendaViewModel.DateHeaderViewModel
        ) {
            view.setOnClickListener(null)
            val tv = view as TextView
            tv.text = viewModel.text
            tv.setTextColor(viewModel.textColor)
        }

        private fun bindCompleteQuestViewModel(
            view: View,
            vm: AgendaViewModel.CompletedQuestViewModel
        ) {

            view.setOnClickListener {
                showCompletedQuest(vm.id)
            }

            val span = SpannableString(vm.name)
            span.setSpan(StrikethroughSpan(), 0, vm.name.length, 0)

            view.questName.text = span
            bindQuest(view, vm)
        }

        private fun bindQuestViewModel(
            view: View,
            vm: AgendaViewModel.QuestViewModel
        ) {
            view.setOnClickListener {
                showQuest(vm.id)
            }
            view.questName.text = vm.name
            bindQuest(view, vm)
        }

        private fun bindQuest(
            view: View,
            vm: AgendaViewModel.QuestItemViewModel
        ) {
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

            view.questRepeatIndicator.visibility = if (vm.isRepeating) View.VISIBLE else View.GONE
            view.questChallengeIndicator.visibility =
                if (vm.isFromChallenge) View.VISIBLE else View.GONE
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

    private fun AgendaViewState.toAgendaItemViewModels() =
        agendaItems.mapIndexed { index, item ->
            toAgendaViewModel(
                item,
                if (agendaItems.lastIndex >= index + 1) agendaItems[index + 1] else null
            )
        }

    private fun toAgendaViewModel(
        agendaItem: CreateAgendaItemsUseCase.AgendaItem,
        nextAgendaItem: CreateAgendaItemsUseCase.AgendaItem? = null
    ): AgendaViewModel =
        when (agendaItem) {
            is CreateAgendaItemsUseCase.AgendaItem.QuestItem -> {
                val quest = agendaItem.quest
                val color = if (quest.isCompleted)
                    R.color.md_grey_500
                else
                    AndroidColor.valueOf(quest.color.name).color500

                when {
                    quest.isCompleted -> AgendaViewModel.CompletedQuestViewModel(
                        id = quest.id,
                        name = quest.name,
                        tags = quest.tags.map {
                            TagViewModel(
                                it.name,
                                AndroidColor.valueOf(it.color.name).color500
                            )
                        },
                        startTime = QuestStartTimeFormatter.formatWithDuration(
                            quest,
                            activity!!,
                            shouldUse24HourFormat
                        ),
                        color = color,
                        icon = quest.icon?.let { AndroidIcon.valueOf(it.name).icon }
                            ?: Ionicons.Icon.ion_checkmark,
                        showDivider = shouldShowDivider(nextAgendaItem),
                        isRepeating = quest.isFromRepeatingQuest,
                        isFromChallenge = quest.isFromChallenge
                    )
                    quest.id.isEmpty() -> AgendaViewModel.QuestPlaceholderViewModel(
                        id = quest.id,
                        name = quest.name,
                        tags = quest.tags.map {
                            TagViewModel(
                                it.name,
                                AndroidColor.valueOf(it.color.name).color500
                            )
                        },
                        startTime = QuestStartTimeFormatter.formatWithDuration(
                            quest,
                            activity!!,
                            shouldUse24HourFormat
                        ),
                        color = color,
                        icon = quest.icon?.let { AndroidIcon.valueOf(it.name).icon }
                            ?: Ionicons.Icon.ion_checkmark,
                        showDivider = shouldShowDivider(nextAgendaItem),
                        isRepeating = quest.isFromRepeatingQuest,
                        isFromChallenge = quest.isFromChallenge
                    )
                    else -> AgendaViewModel.QuestViewModel(
                        id = quest.id,
                        name = quest.name,
                        tags = quest.tags.map {
                            TagViewModel(
                                it.name,
                                AndroidColor.valueOf(it.color.name).color500
                            )
                        },
                        startTime = QuestStartTimeFormatter.formatWithDuration(
                            quest,
                            activity!!,
                            shouldUse24HourFormat
                        ),
                        color = color,
                        icon = quest.icon?.let { AndroidIcon.valueOf(it.name).icon }
                            ?: Ionicons.Icon.ion_checkmark,
                        showDivider = shouldShowDivider(nextAgendaItem),
                        isRepeating = quest.isFromRepeatingQuest,
                        isFromChallenge = quest.isFromChallenge,
                        isScheduledForToday = quest.scheduledDate!!.isToday
                    )
                }
            }

            is CreateAgendaItemsUseCase.AgendaItem.EventItem -> {
                val event = agendaItem.event

                AgendaViewModel.EventViewModel(
                    id = event.name,
                    name = event.name,
                    startTime = formatStartTime(event),
                    color = event.color,
                    showDivider = shouldShowDivider(nextAgendaItem)
                )
            }

            is CreateAgendaItemsUseCase.AgendaItem.Date -> {
                val date = agendaItem.date
                val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    .toUpperCase()
                val month = date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                val dayOfMonth = date.dayOfMonth

                val prefix = if (date.isToday) {
                    "today - "
                } else if (date.isTomorrow) {
                    "tomorrow - "
                } else if (date.isYesterday) {
                    "yesterday - "
                } else {
                    ""
                }

                AgendaViewModel.DateHeaderViewModel(
                    date.toString(),
                    "$prefix$dayOfWeek, $month $dayOfMonth",
                    if (date.isToday)
                        attrData(R.attr.colorAccent)
                    else
                        colorRes(colorTextSecondaryResource)
                )

            }
            is CreateAgendaItemsUseCase.AgendaItem.Week -> {
                val start = agendaItem.start
                val end = agendaItem.end
                val label = if (start.month != end.month) {
                    "${DateFormatter.formatDayWithWeek(start)} - ${DateFormatter.formatDayWithWeek(
                        end
                    )}"
                } else {
                    "${start.dayOfMonth} - ${DateFormatter.formatDayWithWeek(end)}"
                }

                AgendaViewModel.WeekHeaderViewModel(
                    start.weekOfYear.toString() + start.year.toString(),
                    label
                )
            }
            is CreateAgendaItemsUseCase.AgendaItem.Month -> {
                AgendaViewModel.MonthDividerViewModel(
                    agendaItem.startDate().month.toString() + agendaItem.startDate().year.toString(),
                    monthToImage[agendaItem.month.month]!!,
                    agendaItem.month.format(
                        DateTimeFormatter.ofPattern("MMMM yyyy")
                    )
                )
            }
        }

    private fun shouldShowDivider(nextAgendaItem: CreateAgendaItemsUseCase.AgendaItem?) =
        !(nextAgendaItem == null || (nextAgendaItem !is CreateAgendaItemsUseCase.AgendaItem.QuestItem && nextAgendaItem !is CreateAgendaItemsUseCase.AgendaItem.EventItem))

    private fun formatStartTime(event: Event): String {
        val start = event.startTime
        val end = start.plus(event.duration.intValue)
        return "${start.toString(shouldUse24HourFormat)} - ${end.toString(shouldUse24HourFormat)}"
    }

    private val AgendaViewState.previewCalendars: List<com.haibin.calendarview.Calendar>
        get() = previewItems!!.map {
            val itemDate = it.date

            val schemeData = JSONObject()

            val weekIndicators = it.weekIndicators.map { i ->
                val json = JSONObject()
                when (i) {
                    is CreateAgendaPreviewItemsUseCase.PreviewItem.WeekIndicator.Quest -> {
                        json.put("type", "quest")
                        json.put("color", i.color.name)
                    }
                    is CreateAgendaPreviewItemsUseCase.PreviewItem.WeekIndicator.Event -> {
                        json.put("type", "event")
                        json.put("color", i.color.toString())
                    }
                }
                json.put("duration", i.duration)
                json.put("start", i.startMinute)
            }

            schemeData.put("weekIndicators", JSONArray(weekIndicators))

            val monthIndicators = it.monthIndicators.map { i ->
                when (i) {
                    is CreateAgendaPreviewItemsUseCase.PreviewItem.MonthIndicator.Event -> {
                        i.color
                    }
                    is CreateAgendaPreviewItemsUseCase.PreviewItem.MonthIndicator.Quest -> {
                        colorRes(i.color.androidColor.color500)
                    }
                }
            }

            schemeData.put("monthIndicators", JSONArray(monthIndicators))

            com.haibin.calendarview.Calendar().apply {
                day = itemDate.dayOfMonth
                month = itemDate.monthValue
                year = itemDate.year
                isCurrentDay = itemDate == currentDate
                isCurrentMonth = itemDate.month == currentDate!!.month
                isLeapYear = itemDate.isLeapYear
                scheme = schemeData.toString()
            }
        }

}