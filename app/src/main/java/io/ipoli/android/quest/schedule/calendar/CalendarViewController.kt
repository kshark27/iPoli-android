package io.ipoli.android.quest.schedule.calendar

import android.os.Bundle
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.support.RouterPagerAdapter
import io.ipoli.android.R
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.inflate
import io.ipoli.android.quest.schedule.ScheduleViewController
import io.ipoli.android.quest.schedule.calendar.dayview.view.DayViewController
import kotlinx.android.synthetic.main.controller_calendar.view.*
import org.threeten.bp.LocalDate

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 01/31/2018.
 */
class CalendarViewController(args: Bundle? = null) :
    ReduxViewController<CalendarAction, CalendarViewState, CalendarReducer>(args) {

    override val reducer = CalendarReducer

    private var dayViewPagerAdapter: DayViewPagerAdapter? = null

    private val pageChangeListener = object : ViewPager.SimpleOnPageChangeListener() {

        override fun onPageSelected(position: Int) {
            dispatch(CalendarAction.SwipePage(position))
        }
    }

    private val dummyAdapter: PagerAdapter = object : PagerAdapter() {
        override fun isViewFromObject(view: View, `object`: Any) = false
        override fun getCount() = 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View = container.inflate(R.layout.controller_calendar)

    override fun render(state: CalendarViewState, view: View) =
        when (state.type) {
            CalendarViewState.StateType.INITIAL -> {
                removeDayViewPagerAdapter(view)
                createDayViewPagerAdapter(state, view)
            }

            CalendarViewState.StateType.CALENDAR_DATE_CHANGED -> {
                removeDayViewPagerAdapter(view)
                createDayViewPagerAdapter(state, view)
            }

            CalendarViewState.StateType.SWIPE_DATE_CHANGED -> {
                updateDayViewPagerAdapter(state)
                dispatch(CalendarAction.ChangeDate(state.currentDate))
            }

            else -> {
            }
        }

    override fun onCreateLoadAction() = CalendarAction.Load

    private fun removeDayViewPagerAdapter(view: View) {
        view.pager.removeOnPageChangeListener(pageChangeListener)
        view.pager.adapter = dummyAdapter
    }

    private fun updateDayViewPagerAdapter(state: CalendarViewState) {
        dayViewPagerAdapter?.date = state.currentDate
        dayViewPagerAdapter?.pagerPosition = state.adapterPosition
    }

    private fun createDayViewPagerAdapter(state: CalendarViewState, view: View) {
        dayViewPagerAdapter =
            CalendarViewController.DayViewPagerAdapter(
                state.currentDate,
                state.adapterPosition,
                this
            )
        view.pager.adapter = dayViewPagerAdapter
        view.pager.currentItem = state.adapterPosition
        view.pager.addOnPageChangeListener(pageChangeListener)
    }

    override fun onDestroyView(view: View) {
        if (!activity!!.isChangingConfigurations) {
            view.pager.adapter = null
        }
        super.onDestroyView(view)
    }

    class DayViewPagerAdapter(var date: LocalDate, var pagerPosition: Int, controller: Controller) :
        RouterPagerAdapter(controller) {
        override fun configureRouter(router: Router, position: Int) {
            if (!router.hasRootController()) {
                val plusDays = position - pagerPosition
                val dayViewDate = date.plusDays(plusDays.toLong())
                val page = DayViewController(dayViewDate)
                router.setRoot(RouterTransaction.with(page))
            }
        }

        override fun getItemPosition(`object`: Any): Int = PagerAdapter.POSITION_NONE

        override fun getCount() =
            MAX_VISIBLE_DAYS

        companion object {
            const val MAX_VISIBLE_DAYS = 100
        }
    }

    fun onStartEdit() {
        view!!.pager.isLocked = true
        (parentController as ScheduleViewController).onStartEdit()
    }

    fun onStopEdit() {
        view!!.pager.isLocked = false
        (parentController as ScheduleViewController).onStopEdit()
    }


}