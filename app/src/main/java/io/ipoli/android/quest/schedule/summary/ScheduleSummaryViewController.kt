package io.ipoli.android.quest.schedule.summary

import android.os.Build
import android.os.Bundle
import android.view.*
import com.haibin.calendarview.Calendar
import com.haibin.calendarview.CalendarView
import io.ipoli.android.MainActivity
import io.ipoli.android.R
import io.ipoli.android.common.datetime.DateUtils
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.quest.schedule.summary.ScheduleSummaryViewState.StateType.*
import io.ipoli.android.quest.schedule.summary.usecase.CreateScheduleSummaryItemsUseCase
import kotlinx.android.synthetic.main.controller_schedule_summary.view.*
import org.json.JSONArray
import org.json.JSONObject
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate
import org.threeten.bp.Month
import org.threeten.bp.format.TextStyle
import java.util.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 07/03/2018.
 */
class ScheduleSummaryViewController(args: Bundle? = null) :
    ReduxViewController<ScheduleSummaryAction, ScheduleSummaryViewState, ScheduleSummaryReducer>(
        args
    ) {

    override val reducer = ScheduleSummaryReducer

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = container.inflate(R.layout.controller_schedule_summary)
        setToolbar(view.toolbar)
        activity?.let {
            (it as MainActivity).supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_text_secondary_24dp)
        }
        setHasOptionsMenu(true)


        when (DateUtils.firstDayOfWeek) {
            DayOfWeek.SATURDAY -> view.calendarView.setWeekStarWithSat()
            DayOfWeek.SUNDAY -> view.calendarView.setWeekStarWithSun()
            else -> view.calendarView.setWeekStarWithMon()
        }

        view.calendarView.setOnCalendarSelectListener(SkipFirstChangeDateListener { calendar, isClicked ->

            val newDate = LocalDate.of(calendar.year, calendar.month, calendar.day)
            if (isClicked) {
                dispatch(ScheduleSummaryAction.ChangeDate(newDate))
            }
        })

        return view
    }

    private fun renderToolbarDate(view: View, month: Int, year: Int) {
        view.currentMonth.text =
            Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())
        view.currentYear.text = "$year"
    }

    override fun onCreateLoadAction() = ScheduleSummaryAction.Load

    override fun colorStatusBars() {
        activity?.let {
            it.window.statusBarColor = colorRes(attrResourceId(android.R.attr.colorBackground))
            it.window.navigationBarColor = colorRes(attrResourceId(android.R.attr.colorBackground))
            if (it.isDarkTheme) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                it.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        showBackButton()

        view.toolbar.onDebounceMenuClick({ item ->
            if (item.itemId == R.id.actionGoToToday) {
                view.calendarView.scrollToCurrent(true)
                dispatch(ScheduleSummaryAction.GoToToday)
            }
        }, { _ ->
            router.handleBack()
        })
    }

    override fun onDetach(view: View) {
        resetDecorView()
        view.toolbar.clearDebounceListeners()
        super.onDetach(view)
    }

    private fun resetDecorView() {
        activity?.let {
            it.window.decorView.systemUiVisibility = 0
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.month_preview_menu, menu)
    }

    override fun render(state: ScheduleSummaryViewState, view: View) {
        when (state.type) {

            DATE_DATA_CHANGED -> {
                val currentDate = state.currentDate
                view.calendarView.scrollToCalendar(
                    currentDate.year,
                    currentDate.monthValue,
                    currentDate.dayOfMonth
                )
                renderToolbarDate(view, currentDate.monthValue, currentDate.year)
            }

            SCHEDULE_SUMMARY_DATA_CHANGED -> {
                view.calendarView.setSchemeDate(state.calendars.map { it.toString() to it }.toMap())
            }

            DATE_SELECTED -> {
                router.handleBack()
            }

            else -> {
            }
        }
    }

    private val ScheduleSummaryViewState.calendars: List<Calendar>
        get() = items.map {
            val itemDate = it.date

            val items = it.items.map { sc ->
                val json = JSONObject()
                when (sc) {
                    is CreateScheduleSummaryItemsUseCase.Schedule.Item.Quest -> {
                        json.put("type", "quest")
                        json.put("name", sc.name)
                        json.put("isCompleted", sc.isCompleted)
                        json.put("color", sc.color.name)
                    }
                    is CreateScheduleSummaryItemsUseCase.Schedule.Item.Event -> {
                        json.put("type", "event")
                        json.put("name", sc.name)
                        json.put("color", sc.color)
                    }
                }
                json
            }

            Calendar().apply {
                day = itemDate.dayOfMonth
                month = itemDate.monthValue
                year = itemDate.year
                isCurrentDay = itemDate == currentDate
                isCurrentMonth = itemDate.month == currentDate.month
                isLeapYear = itemDate.isLeapYear
                scheme = JSONArray(items).toString()
            }
        }

    class SkipFirstChangeDateListener(private inline val onChange: (Calendar, Boolean) -> Unit) :
        CalendarView.OnCalendarSelectListener {
        override fun onCalendarSelect(calendar: Calendar, isClick: Boolean) {
            if (isFirstChange) {
                isFirstChange = false
                return
            }

            onChange(calendar, isClick)
        }

        override fun onCalendarOutOfRange(calendar: Calendar) {

        }

        private var isFirstChange = true
    }
}