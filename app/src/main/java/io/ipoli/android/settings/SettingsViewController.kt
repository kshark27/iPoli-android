package io.ipoli.android.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import io.ipoli.android.BuildConfig
import io.ipoli.android.Constants
import io.ipoli.android.R
import io.ipoli.android.common.EmailUtils
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.privacy.PrivacyPolicyViewController
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.data.Player.Preferences.*
import io.ipoli.android.settings.SettingsViewState.StateType.DATA_CHANGED
import io.ipoli.android.settings.SettingsViewState.StateType.ENABLE_SYNC_CALENDARS
import kotlinx.android.synthetic.main.controller_settings.view.*
import kotlinx.android.synthetic.main.view_default_toolbar.view.*
import org.threeten.bp.format.TextStyle
import java.util.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 4/2/18.
 */
class SettingsViewController(args: Bundle? = null) :
    ReduxViewController<SettingsAction, SettingsViewState, SettingsReducer>(args) {

    override val reducer = SettingsReducer

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = container.inflate(R.layout.controller_settings)
        setToolbar(view.toolbar)
        return view
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        toolbarTitle = stringRes(R.string.settings)
        showBackButton()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            return router.handleBack()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateLoadAction() = SettingsAction.Load

    override fun render(state: SettingsViewState, view: View) {
        when (state.type) {
            DATA_CHANGED -> {
                renderAboutSection(state, view)
                renderGeneralSection(state, view)
                renderPlanMyDay(state, view)
                renderSyncCalendarsSection(state, view)
            }

            ENABLE_SYNC_CALENDARS -> {
                renderSyncCalendarsSection(state, view)
                showSyncCalendars()
            }

            else -> {
            }
        }
    }

    private fun renderGeneralSection(
        state: SettingsViewState,
        view: View
    ) {
        renderTimeFormat(state, view)
        renderDefaultAgendaView(state, view)
        renderTemperatureUnit(state, view)
        renderReminderNotificationStyle(state, view)
        renderResetDay(state, view)

        renderQuickDoNotification(view, state)
        renderAutoPosting(view, state)
    }

    private fun renderQuickDoNotification(
        view: View,
        state: SettingsViewState
    ) {
        view.enableOngoingNotification.setOnCheckedChangeListener(null)
        view.enableOngoingNotification.isChecked = state.isQuickDoNotificationEnabled
        view.ongoingNotificationContainer.dispatchOnClick {
            SettingsAction.ToggleQuickDoNotification(!state.isQuickDoNotificationEnabled)
        }
        view.enableOngoingNotification.setOnCheckedChangeListener { _, isChecked ->
            dispatch(SettingsAction.ToggleQuickDoNotification(isChecked))
        }
    }

    private fun renderAutoPosting(
        view: View,
        state: SettingsViewState
    ) {
        view.enableAutoPosting.setOnCheckedChangeListener(null)
        view.enableAutoPosting.isChecked = state.isAutoPostingEnabled
        view.autoPostingContainer.dispatchOnClick {
            SettingsAction.ToggleAutoPosting(!state.isAutoPostingEnabled)
        }
        view.enableAutoPosting.setOnCheckedChangeListener { _, isChecked ->
            dispatch(SettingsAction.ToggleAutoPosting(isChecked))
        }
    }

    private fun renderResetDay(state: SettingsViewState, view: View) {
        view.resetDayTime.text = state.resetDayTime.toString(shouldUse24HourFormat)
        view.resetDayTimeContainer.onDebounceClick { _ ->

            createTimePickerDialog(
                startTime = state.resetDayTime,
                onTimePicked = {
                    dispatch(SettingsAction.ResetDayTimeChanged(it!!))
                },
                showNeutral = false
            ).show(router)
        }

    }

    private fun renderTemperatureUnit(state: SettingsViewState, view: View) {
        view.temperatureUnit.text = state.temperatureUnitText
        view.temperatureUnitContainer.onDebounceClick {
            navigate()
                .toTemperatureUnitPicker(
                    state.temperatureUnit
                ) { unit ->
                    dispatch(SettingsAction.TemperatureUnitChanged(unit))
                }
        }
    }

    private fun renderTimeFormat(state: SettingsViewState, view: View) {
        view.timeFormat.text = state.timeFormatText
        view.timeFormatContainer.onDebounceClick {
            navigate()
                .toTimeFormatPicker(
                    state.timeFormat
                ) { format ->
                    dispatch(SettingsAction.TimeFormatChanged(format))
                }
        }
    }

    private fun renderDefaultAgendaView(state: SettingsViewState, view: View) {
        view.defaultAgendaView.text = state.agendaStartText
        view.defaultAgendaViewContainer.onDebounceClick {
            navigate()
                .toAgendaStartScreenPicker(
                    state.agendaStartScreen
                ) { agendaView ->
                    dispatch(SettingsAction.AgendaStartScreenChanged(agendaView))
                }
        }
    }

    private fun renderReminderNotificationStyle(state: SettingsViewState, view: View) {
        view.reminderNotificationStyle.text = state.reminderNotificationStyleText
        view.reminderNotificationStyleContainer.onDebounceClick {
            navigate()
                .toNotificationStylePicker(
                    state.reminderNotificationStyle
                ) { style ->
                    dispatch(SettingsAction.ReminderNotificationStyleChanged(style))
                }
        }
    }

    private fun renderPlanMyDay(state: SettingsViewState, view: View) {
        val use24HourFormat =
            if (state.timeFormat == TimeFormat.DEVICE_DEFAULT) {
                DateFormat.is24HourFormat(activity)
            } else state.timeFormat != TimeFormat.TWELVE_HOURS


        view.planDayTime.text = state.planTime.toString(use24HourFormat)
        view.planMyDayTimeContainer.onDebounceClick { _ ->

            createTimePickerDialog(
                startTime = state.planTime,
                onTimePicked = {
                    dispatch(SettingsAction.PlanDayTimeChanged(it!!))
                },
                showNeutral = false
            ).show(router)
        }

        val daysText = state.planDays.joinToString(", ") {
            it.getDisplayName(
                TextStyle.FULL,
                Locale.getDefault()
            ).substring(0, 3)
        }
        view.planDays.text =
            if (daysText.isNotEmpty()) daysText else stringRes(R.string.no_challenge_days)

        view.planDaysContainer.onDebounceClick {
            navigate()
                .toDaysPicker(
                    state.planDays
                ) { days ->
                    dispatch(SettingsAction.PlanDaysChanged(days))
                }
        }

        view.planNowContainer.onDebounceClick {
            navigateFromRoot().toPlanDay()
        }

        renderPlanDayNotificationStyle(state, view)
    }

    private fun renderPlanDayNotificationStyle(state: SettingsViewState, view: View) {
        view.planDayNotificationStyle.text = state.planDayNotificationStyleText
        view.planDayNotificationStyleContainer.onDebounceClick {
            navigate()
                .toNotificationStylePicker(
                    state.planDayNotificationStyle
                ) { style ->
                    dispatch(SettingsAction.PlanDayNotificationStyleChanged(style))
                }
        }
    }

    private fun renderSyncCalendarsSection(
        state: SettingsViewState,
        view: View
    ) {
        view.enableSyncCalendars.setOnCheckedChangeListener(null)
        view.enableSyncCalendars.isChecked = state.isCalendarSyncEnabled
        view.syncCalendarsContainer.setOnClickListener {
            dispatch(SettingsAction.ToggleSyncCalendar(!state.isCalendarSyncEnabled))
        }
        view.enableSyncCalendars.setOnCheckedChangeListener { _, isChecked ->
            dispatch(SettingsAction.ToggleSyncCalendar(isChecked))
        }

        view.selectedSyncCalendars.text = state.selectedCalendarsText
        if (state.isCalendarSyncEnabled) {
            view.calendarsToSyncTitle.setTextColor(colorRes(colorTextPrimaryResource))
            view.selectedSyncCalendars.setTextColor(colorRes(colorTextSecondaryResource))
            view.selectSyncCalendarsContainer.isEnabled = true
            view.selectSyncCalendarsContainer.setOnClickListener {
                showSyncCalendars()
            }
        } else {
            view.selectSyncCalendarsContainer.isEnabled = false
            view.calendarsToSyncTitle.setTextColor(colorRes(colorTextHintResource))
            view.selectedSyncCalendars.setTextColor(colorRes(colorTextHintResource))
            view.selectSyncCalendarsContainer.setOnClickListener(null)
        }
    }

    override fun onPermissionsGranted(requestCode: Int, permissions: List<String>) {
        showCalendarPicker()
    }

    private fun showCalendarPicker() {
        navigate().toCalendarPicker { calendarIds ->
            dispatch(SettingsAction.SyncCalendarsSelected(calendarIds))
        }
    }

    private fun showSyncCalendars() {
        requestPermissions(
            mapOf(Manifest.permission.READ_CALENDAR to stringRes(R.string.allow_read_calendars_perm_reason)),
            Constants.RC_CALENDAR_PERM
        )
    }

    private fun renderAboutSection(state: SettingsViewState, view: View) {

        view.contactContainer.onDebounceClick {
            EmailUtils.send(
                activity!!,
                "Hi",
                state.playerId,
                stringRes(R.string.contact_us_email_chooser_title)
            )
        }

        view.rateContainer.onDebounceClick {
            val uri = Uri.parse("market://details?id=" + activity!!.packageName)
            val linkToMarket = Intent(Intent.ACTION_VIEW, uri)
            startActivity(linkToMarket)
        }

        view.appVersion.text = BuildConfig.VERSION_NAME

        view.privacyPolicyContainer.onDebounceClick {
            val changeHandler = VerticalChangeHandler()
            pushWithRootRouter(
                RouterTransaction.with(PrivacyPolicyViewController())
                    .pushChangeHandler(changeHandler)
                    .popChangeHandler(changeHandler)
            )
        }
    }

    private val SettingsViewState.selectedCalendarsText: String
        get() = if (selectedCalendars == 0) {
            stringRes(R.string.no_calendars_selected_to_sync)
        } else {
            stringRes(R.string.sync_calendars_count, selectedCalendars)
        }

    private val SettingsViewState.timeFormatText: String
        get() = when (timeFormat) {
            TimeFormat.TWELVE_HOURS -> stringRes(
                R.string.twelve_hour_format,
                Time.now().toString(false)
            )
            TimeFormat.TWENTY_FOUR_HOURS -> stringRes(
                R.string.twenty_four_hour_format,
                Time.now().toString(true)
            )
            else -> stringRes(
                R.string.device_default_time_format, Time.now().toString(
                    DateFormat.is24HourFormat(view!!.context)
                )
            )
        }

    private val SettingsViewState.agendaStartText: String
        get() = when (agendaStartScreen) {
            Player.Preferences.AgendaScreen.CALENDAR -> stringRes(R.string.calendar)
            Player.Preferences.AgendaScreen.AGENDA -> stringRes(R.string.agenda)
        }


    private val SettingsViewState.reminderNotificationStyleText: String
        get() = when (reminderNotificationStyle) {
            NotificationStyle.NOTIFICATION -> stringRes(R.string.notification_style_notification)
            NotificationStyle.POPUP -> stringRes(R.string.notification_style_popup)
            else -> stringRes(R.string.notification_style_all)
        }

    private val SettingsViewState.temperatureUnitText: String
        get() = if (temperatureUnit == TemperatureUnit.FAHRENHEIT) {
            stringRes(R.string.temperature_unit_fahrenheit)
        } else {
            stringRes(R.string.temperature_unit_celsius)
        }

    private val SettingsViewState.planDayNotificationStyleText: String
        get() = when (planDayNotificationStyle) {
            NotificationStyle.NOTIFICATION -> stringRes(R.string.notification_style_notification)
            NotificationStyle.POPUP -> stringRes(R.string.notification_style_popup)
            else -> stringRes(R.string.notification_style_all)
        }

    companion object {
        fun routerTransaction() =
            RouterTransaction.with(SettingsViewController())
                .pushChangeHandler(VerticalChangeHandler())
                .popChangeHandler(VerticalChangeHandler())
    }
}