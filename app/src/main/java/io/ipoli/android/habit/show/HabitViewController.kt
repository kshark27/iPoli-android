package io.ipoli.android.habit.show

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.design.widget.AppBarLayout
import android.view.*
import com.haibin.calendarview.Calendar
import com.haibin.calendarview.CalendarView
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import io.ipoli.android.MainActivity
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.habit.show.HabitViewState.StateType.DATA_CHANGED
import io.ipoli.android.habit.show.HabitViewState.StateType.TOGGLE_FUTURE_DATE_ERROR
import io.ipoli.android.quest.schedule.summary.ScheduleSummaryViewController
import io.ipoli.android.tag.Tag
import kotlinx.android.synthetic.main.controller_habit.view.*
import kotlinx.android.synthetic.main.item_quest_tag_list.view.*
import kotlinx.android.synthetic.main.repeating_quest_progress_indicator_empty.view.*
import org.threeten.bp.LocalDate
import org.threeten.bp.YearMonth
import org.threeten.bp.format.TextStyle
import java.util.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 10/11/18.
 */
class HabitViewController(args: Bundle? = null) :
    ReduxViewController<HabitAction, HabitViewState, HabitReducer>(args) {
    override val reducer = HabitReducer

    private var habitId: String = ""
    private var showBackButton = true

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

    constructor(habitId: String, showBackButton: Boolean) : this() {
        this.habitId = habitId
        this.showBackButton = showBackButton
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        applyStatusBarColors = false
        val view = container.inflate(R.layout.controller_habit)

        setToolbar(view.toolbar)
        view.collapsingToolbarContainer.isTitleEnabled = false

        view.appbar.addOnOffsetChangedListener(appBarOffsetListener)

        view.calendarView.setOnCalendarSelectListener(ScheduleSummaryViewController.SkipFirstChangeDateListener { calendar, _ ->
            val date = LocalDate.of(calendar.year, calendar.month, calendar.day)
            dispatch(HabitAction.ToggleHistory(habitId, date))
        })

        view.calendarView.setOnMonthChangeListener(SkipFirstChangeMonthListener { year, month ->
            dispatch(HabitAction.ChangeMonth(habitId, YearMonth.of(year, month)))
        })

        view.calendarHintIcon.setImageDrawable(
            IconicsDrawable(view.context)
                .icon(GoogleMaterial.Icon.gmd_info_outline)
                .color(attrData(R.attr.colorAccent))
                .sizeDp(24)
        )

        return view
    }

    override fun onCreateLoadAction() = HabitAction.Load(habitId)

    class SkipFirstChangeMonthListener(private inline val onChange: (Int, Int) -> Unit) :
        CalendarView.OnMonthChangeListener {

        override fun onMonthChange(year: Int, month: Int) {
            if (isFirstChange) {
                isFirstChange = false
                return
            }

            onChange(year, month)
        }

        private var isFirstChange = true
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        if (showBackButton) {
            showBackButton()
        }
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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.habit_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {

            android.R.id.home ->
                router.handleBack()

            R.id.actionEdit -> {
                navigateFromRoot().toEditHabit(habitId)
                true
            }
            R.id.actionRemove -> {
                navigate().toConfirmation(
                    stringRes(R.string.dialog_confirmation_title),
                    stringRes(R.string.dialog_remove_habit_message)
                ) {
                    showShortToast(R.string.habit_removed)
                    dispatch(HabitAction.Remove(habitId))
                    router.handleBack()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun render(state: HabitViewState, view: View) {
        when (state.type) {
            DATA_CHANGED -> {
                view.calendarView.setSchemeDate(state.calendars.map { it.toString() to it }.toMap())
                view.calendarMonth.text = state.currentMonth

                colorLayout(state, view)

                renderName(state.name!!, view)
                renderNote(state, view)
                renderTags(state.tags, view)

                view.timesADay.text = state.timesADayText
                view.currentStreak.text = state.currentStreak.toString()
                view.bestStreak.text = state.bestStreak.toString()
                view.successRate.text = "${state.successRate}%"

                val inflater = LayoutInflater.from(view.context)
                view.progressContainer.removeAllViews()

                state.weekProgressColors.forEachIndexed { index, c ->
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

                    indicatorView.setColor(c)

                    if (index == 0) {
                        progressView.indicatorLink.gone()
                    }

                    view.progressContainer.addView(progressView)
                }
            }

            TOGGLE_FUTURE_DATE_ERROR ->
                showShortToast(R.string.habit_toggle_future_error)

            else -> {
            }
        }
    }

    private fun colorLayout(
        state: HabitViewState,
        view: View
    ) {
        view.appbar.setBackgroundColor(colorRes(state.color500))
        view.toolbar.setBackgroundColor(colorRes(state.color500))
        view.collapsingToolbarContainer.setContentScrimColor(colorRes(state.color500))
        activity?.window?.navigationBarColor = colorRes(state.color500)
        activity?.window?.statusBarColor = colorRes(state.color700)
    }

    private fun renderName(
        name: String,
        view: View
    ) {
        toolbarTitle = name
        view.habitName.text = name
    }

    private fun renderNote(
        state: HabitViewState,
        view: View
    ) {
        if (state.note != null && state.note.isNotBlank()) {
            view.note.setMarkdown(state.note)
        } else {
            view.note.setText(R.string.tap_to_add_note)
            view.note.setTextColor(colorRes(colorTextSecondaryResource))
        }
        view.note.onDebounceClick {
            navigateFromRoot().toEditHabit(habitId)
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

    private val HabitViewState.currentMonth
        get() = "${currentDate.month.getDisplayName(
            TextStyle.FULL,
            Locale.getDefault()
        )}, ${currentDate.year}"

    private val HabitViewState.color500
        get() = color!!.androidColor.color500

    private val HabitViewState.color700
        get() = color!!.androidColor.color700

    private val HabitViewState.weekProgressColors
        get() = weekProgress.map {
            when (it) {
                HabitViewState.WeekProgress.INCOMPLETE ->
                    colorRes(color500)

                HabitViewState.WeekProgress.COMPLETE ->
                    colorRes(R.color.md_white)

                HabitViewState.WeekProgress.TODO ->
                    colorRes(color500)
            }
        }

    private val HabitViewState.calendars: List<Calendar>
        get() {
            val today = LocalDate.now()
            return history.map {
                val itemDate = it.date

                Calendar().apply {
                    day = itemDate.dayOfMonth
                    month = itemDate.monthValue
                    year = itemDate.year
                    isCurrentDay = itemDate == today
                    isCurrentMonth = itemDate.month == today.month
                    isLeapYear = itemDate.isLeapYear
                    scheme = "${it.state.name},${it.color.name},${it.shouldBeDone}," +
                        "${it.isPreviousCompleted},${it.isNextCompleted}," +
                        "${it.timesADay},${it.completedCount}"
                }
            }
        }

    private val HabitViewState.timesADayText
        get() =
            when (timesADay) {
                Habit.UNLIMITED_TIMES_A_DAY -> stringRes(R.string.unlimited_times_a_day)
                1 -> stringRes(R.string.time_a_day)
                else -> stringRes(R.string.times_a_day, timesADay!!)
            }


}