package io.ipoli.android.quest.schedule

import android.os.Bundle
import android.view.*
import android.widget.ImageView
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.navigation.Navigator
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.quest.schedule.ScheduleViewState.StateType.*
import io.ipoli.android.quest.schedule.addquest.AddQuestAnimationHelper
import io.ipoli.android.quest.schedule.agenda.view.AgendaViewController
import kotlinx.android.synthetic.main.controller_schedule.view.*
import kotlinx.android.synthetic.main.view_calendar_toolbar.view.*

class ScheduleViewController(args: Bundle? = null) :
    ReduxViewController<ScheduleAction, ScheduleViewState, ScheduleReducer>(args) {

    override val reducer = ScheduleReducer

    private var calendarToolbar: ViewGroup? = null

    private lateinit var addQuestAnimationHelper: AddQuestAnimationHelper

    private var viewModeIcon: IIcon = CommunityMaterial.Icon.cmd_calendar_blank

    private var viewModeTitle = "Calendar"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {

        setHasOptionsMenu(true)
        val view = container.inflate(R.layout.controller_schedule)

        addQuestAnimationHelper = AddQuestAnimationHelper(
            controller = this,
            addContainer = view.addContainer,
            fab = view.addQuest,
            background = view.addContainerBackground
        )
        initAddQuest(view)

        parentController!!.view!!.post {
            addToolbarView(R.layout.view_calendar_toolbar)?.let { toolbarView ->
                calendarToolbar = toolbarView as ViewGroup
            }
        }

        setChildController(
            view.contentContainer,
            AgendaViewController()
        )
        return view
    }

    override fun onCreateLoadAction() = ScheduleAction.Load

    override fun onDestroyView(view: View) {
        calendarToolbar?.let { removeToolbarView(it) }
        super.onDestroyView(view)
    }

    override fun onDestroy() {
        dispatch(ScheduleAction.ResetAgendaDate)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.schedule_menu, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.actionViewMode).setIcon(
            IconicsDrawable(view!!.context)
                .icon(viewModeIcon)
                .respectFontBounds(true)
                .colorRes(R.color.md_white)
                .sizeDp(24)
        ).title = viewModeTitle
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {

            R.id.actionGoToToday -> {
                dispatch(ScheduleAction.GoToToday)
                true
            }

            R.id.actionViewMode -> {
                closeAddIfShown {
                    dispatch(ScheduleAction.ToggleViewMode)
                }

                true
            }

            R.id.actionScheduleSummary -> {
                closeAddIfShown {
                    navigateFromRoot().toScheduleSummary()
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }


    private fun initAddQuest(view: View) {
        view.addContainerBackground.setOnClickListener {
            closeAddIfShown()
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

    private fun addContainerRouter(view: View) =
        getChildRouter(view.addContainer, "add-quest")

    override fun render(state: ScheduleViewState, view: View) {
        view.addQuest.setOnClickListener {
            addQuestAnimationHelper.openAddContainer(state.currentDate)
        }

        when (state.type) {

            INITIAL -> {
                renderNewDate(state)
                if (state.viewMode == ScheduleViewState.ViewMode.CALENDAR) {
                    disableToolbarCalendar()
                } else {
                    enableToolbarCalendar()
                }
                activity?.invalidateOptionsMenu()
            }

            DATE_AUTO_CHANGED -> {
                renderNewDate(state)
            }

            CALENDAR_DATE_CHANGED -> {
                renderNewDate(state)
            }

            SWIPE_DATE_CHANGED -> {
                renderNewDate(state)
            }

            VIEW_MODE_CHANGED -> {

                val childRouter = getChildRouter(view.contentContainer, null)
                val n = Navigator(childRouter)
                if (state.viewMode == ScheduleViewState.ViewMode.CALENDAR) {
                    n.replaceWithCalendar()
                    disableToolbarCalendar()
                } else {
                    n.replaceWithAgenda()
                    enableToolbarCalendar()
                }

                viewModeIcon = state.viewModeIcon
                viewModeTitle = state.viewModeTitle
                activity?.invalidateOptionsMenu()
            }

            else -> {
            }
        }
    }

    private fun disableToolbarCalendar() {
        calendarToolbar?.calendarIndicator?.gone()
        calendarToolbar?.background = null
        calendarToolbar?.setOnClickListener(null)
    }

    private fun enableToolbarCalendar() {
        calendarToolbar?.calendarIndicator?.visible()
        calendarToolbar?.setBackgroundResource(attrResourceId(android.R.attr.selectableItemBackgroundBorderless))
        calendarToolbar?.onDebounceClick {
            closeAddIfShown {
                calendarToolbar?.calendarIndicator?.let { indicator ->
                    animateIndicator(indicator)
                }
                dispatch(ScheduleAction.ToggleAgendaPreviewMode)
            }
        }
    }

    private fun animateIndicator(indicator: ImageView) {
        if (indicator.tag == null) {
            indicator.tag = 0
        }
        val rotation = indicator.tag.toString().toInt()
        if (rotation == 180) {
            indicator.animate().rotation(0f).start()
            indicator.tag = 0
        } else {
            indicator.animate().rotation(180f).start()
            indicator.tag = 180
        }
    }

    private fun renderNewDate(state: ScheduleViewState) {
        calendarToolbar?.day?.text = state.dayText(activity!!)
        calendarToolbar?.date?.text = state.dateText(activity!!)
    }

    fun onStartEdit() {
        view!!.addQuest.visible = false
    }

    fun onStopEdit() {
        view!!.addQuest.visible = true
    }

    private val ScheduleViewState.viewModeIcon: IIcon
        get() = if (viewMode == ScheduleViewState.ViewMode.CALENDAR)
            GoogleMaterial.Icon.gmd_format_list_bulleted
        else
            CommunityMaterial.Icon.cmd_calendar_blank
}