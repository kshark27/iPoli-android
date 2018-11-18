package io.ipoli.android.repeatingquest.show

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.design.widget.AppBarLayout
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import io.ipoli.android.MainActivity
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.text.DateFormatter
import io.ipoli.android.common.text.DurationFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.SimpleRecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.tag.Tag
import kotlinx.android.synthetic.main.controller_repeating_quest.view.*
import kotlinx.android.synthetic.main.item_quest_tag_list.view.*
import kotlinx.android.synthetic.main.item_repeating_quest_sub_quest.view.*
import kotlinx.android.synthetic.main.repeating_quest_progress_indicator_empty.view.*


/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 02/21/2018.
 */
class RepeatingQuestViewController(args: Bundle? = null) :
    ReduxViewController<RepeatingQuestAction, RepeatingQuestViewState, RepeatingQuestReducer>(args) {

    override val reducer = RepeatingQuestReducer

    private var repeatingQuestId: String = ""

    private val appBarOffsetListener = object :
        AppBarStateChangeListener() {
        override fun onStateChanged(appBarLayout: AppBarLayout, state: State) {

            appBarLayout.post {
                if (state == State.EXPANDED) {
                    val supportActionBar = (activity as MainActivity).supportActionBar
                    supportActionBar?.setDisplayShowTitleEnabled(false)
                } else if (state == State.COLLAPSED) {
                    val supportActionBar = (activity as MainActivity).supportActionBar
                    supportActionBar?.setDisplayShowTitleEnabled(true)
                }
            }
        }
    }

    constructor(
        repeatingQuestId: String
    ) : this() {
        this.repeatingQuestId = repeatingQuestId
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        applyStatusBarColors = false
        val view = container.inflate(R.layout.controller_repeating_quest)
        setToolbar(view.toolbar)
        view.collapsingToolbarContainer.isTitleEnabled = false

        view.subQuestList.layoutManager = LinearLayoutManager(activity!!)
        view.subQuestList.adapter = SubQuestsAdapter()

        view.addQuest.onDebounceClick {
            navigate().toReschedule(
                includeToday = true,
                isNewQuest = true,
                listener = { date, time, _ ->
                    dispatch(RepeatingQuestAction.AddQuest(repeatingQuestId, date, time))
                })
        }

        view.appbar.addOnOffsetChangedListener(appBarOffsetListener)

        return view
    }

    override fun onCreateLoadAction() =
        RepeatingQuestAction.Load(repeatingQuestId)

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.repeating_quest_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {

            android.R.id.home ->
                router.handleBack()

            R.id.actionEdit -> {
                showEdit()
                true
            }
            R.id.actionDelete -> {
                navigate().toConfirmation(
                    stringRes(R.string.dialog_confirmation_title),
                    stringRes(R.string.dialog_remove_repeating_quest_message)
                ) {
                    dispatch(RepeatingQuestAction.Remove(repeatingQuestId))
                    router.handleBack()
                }
                true

            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun showEdit() {
        navigateFromRoot().toEditRepeatingQuest(repeatingQuestId)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        showBackButton()
        val showTitle =
            appBarOffsetListener.currentState != AppBarStateChangeListener.State.EXPANDED
        (activity as MainActivity).supportActionBar?.setDisplayShowTitleEnabled(showTitle)
    }

    override fun onDetach(view: View) {
        (activity as MainActivity).supportActionBar?.setDisplayShowTitleEnabled(true)
        super.onDetach(view)
    }

    override fun onDestroyView(view: View) {
        view.appbar.removeOnOffsetChangedListener(appBarOffsetListener)
        super.onDestroyView(view)
    }

    override fun render(state: RepeatingQuestViewState, view: View) {
        when (state.type) {
            RepeatingQuestViewState.StateType.REPEATING_QUEST_CHANGED -> {
                colorLayout(state, view)
                renderName(state, view)
                renderTags(state.tags, view)
                renderSubQuests(state, view)
                renderProgress(state, view)
                renderSummaryStats(state, view)
                renderNote(state, view)
            }

            RepeatingQuestViewState.StateType.HISTORY_CHANGED ->
                view.historyChart.updateData(state.history!!)

            else -> {
            }
        }
    }

    private fun renderTags(
        tags: List<Tag>,
        view: View
    ) {
        view.tagList.removeAllViews()

        val inflater = LayoutInflater.from(activity!!)
        tags.forEach { tag ->
            val item = inflater.inflate(R.layout.item_quest_tag_list, view.tagList, false)
            renderTag(item, tag)
            view.tagList.addView(item)
        }
    }

    private fun renderTag(view: View, tag: Tag) {
        view.tagName.text = tag.name
        val indicator = view.tagName.compoundDrawablesRelative[0] as GradientDrawable
        indicator.setColor(colorRes(tag.color.androidColor.color500))
    }

    private fun renderNote(
        state: RepeatingQuestViewState,
        view: View
    ) {
        if (state.note != null && state.note.isNotBlank()) {
            view.note.setMarkdown(state.note)
        } else {
            view.note.setText(R.string.tap_to_add_note)
            view.note.setTextColor(colorRes(colorTextSecondaryResource))
        }
        view.note.setOnClickListener { showEdit() }
    }

    private fun renderSubQuests(state: RepeatingQuestViewState, view: View) {
        if (state.subQuestNames.isEmpty()) {
            view.emptySubQuestList.visible()
            view.subQuestList.gone()
        } else {
            view.emptySubQuestList.gone()
            (view.subQuestList.adapter as SubQuestsAdapter).updateAll(state.subQuestNames.map {
                SimpleRecyclerViewViewModel(
                    it
                )
            })
            view.subQuestList.visible()
        }
    }

    private fun renderSummaryStats(
        state: RepeatingQuestViewState,
        view: View
    ) {
        view.rqLastComplete.text = state.lastCompletedDateText
        view.rqNextDate.text = state.nextScheduledDateText
        view.rqScheduledTime.text = state.scheduledTimeText
    }

    private fun renderName(
        state: RepeatingQuestViewState,
        view: View
    ) {
        toolbarTitle = state.name
        view.questName.text = state.name
    }

    private fun renderProgress(
        state: RepeatingQuestViewState,
        view: View
    ) {
        val inflater = LayoutInflater.from(view.context)
        view.progressContainer.removeAllViews()

        state.progressViewModels.forEachIndexed { index, vm ->
            val progressView = inflater.inflate(
                R.layout.repeating_quest_progress_indicator_empty,
                view.progressContainer,
                false
            )
            val indicatorView =
                progressView.indicatorDot.background as GradientDrawable
            indicatorView.setStroke(
                ViewUtils.dpToPx(2f, view.context).toInt(),
                colorRes(R.color.md_white)
            )

            indicatorView.setColor(vm.color)

            if (index == 0) {
                progressView.indicatorLink.gone()
            }

            view.progressContainer.addView(progressView)
        }

        view.frequencyText.text = state.frequencyText
    }

    private fun colorLayout(
        state: RepeatingQuestViewState,
        view: View
    ) {
        view.appbar.setBackgroundColor(colorRes(state.color500))
        view.toolbar.setBackgroundColor(colorRes(state.color500))
        view.collapsingToolbarContainer.setContentScrimColor(colorRes(state.color500))
        activity?.window?.navigationBarColor = colorRes(state.color500)
        activity?.window?.statusBarColor = colorRes(state.color700)
    }

    inner class SubQuestsAdapter :
        BaseRecyclerViewAdapter<SimpleRecyclerViewViewModel<String>>(
            R.layout.item_repeating_quest_sub_quest
        ) {
        override fun onBindViewModel(
            vm: SimpleRecyclerViewViewModel<String>,
            view: View,
            holder: SimpleViewHolder
        ) {
            view.subQuestIndicator.backgroundTintList =
                ColorStateList.valueOf(colorRes(colorTextSecondaryResource))
            view.subQuestName.text = vm.value
        }
    }

    private val RepeatingQuestViewState.color500
        get() = color.androidColor.color500

    private val RepeatingQuestViewState.color700
        get() = color.androidColor.color700

    private val RepeatingQuestViewState.progressViewModels
        get() = progress.map {
            when (it) {
                RepeatingQuestViewState.ProgressModel.COMPLETE -> {
                    ProgressViewModel(colorRes(R.color.md_white))
                }

                RepeatingQuestViewState.ProgressModel.INCOMPLETE -> {
                    ProgressViewModel(colorRes(color500))
                }
            }
        }

    private val RepeatingQuestViewState.lastCompletedDateText
        get() = when {
            lastCompletedDate != null -> {
                DateFormatter.format(view!!.context, lastCompletedDate)
            }
            else -> stringRes(R.string.never)
        }


    private val RepeatingQuestViewState.nextScheduledDateText
        get() = when {
            isCompleted -> stringRes(R.string.completed)
            nextScheduledDate != null -> {
                DateFormatter.format(view!!.context, nextScheduledDate)
            }
            else -> stringRes(R.string.unscheduled)
        }

    private val RepeatingQuestViewState.scheduledTimeText: String
        get() = if (startTime != null) {
            "${startTime.toString(shouldUse24HourFormat)} - ${endTime!!.toString(
                shouldUse24HourFormat
            )}"
        } else stringRes(R.string.for_time, DurationFormatter.formatShort(view!!.context, duration))

    private val RepeatingQuestViewState.frequencyText
        get () = when (repeat) {
            RepeatingQuestViewState.RepeatType.Daily -> {
                "Every day"
            }

            is RepeatingQuestViewState.RepeatType.Weekly -> {
                repeat.frequency.let {
                    if (it == 1) {
                        "Once per week"
                    } else {
                        "$it times per week"
                    }
                }
            }

            is RepeatingQuestViewState.RepeatType.Monthly -> {
                repeat.frequency.let {
                    if (it == 1) {
                        "Once per month"
                    } else {
                        "$it times per month"
                    }
                }
            }

            RepeatingQuestViewState.RepeatType.Yearly -> {
                "Once per year"
            }

            RepeatingQuestViewState.RepeatType.Manual -> {
                stringRes(R.string.manual_schedule_repeat_pattern)
            }
        }

    data class ProgressViewModel(@ColorInt val color: Int)
}