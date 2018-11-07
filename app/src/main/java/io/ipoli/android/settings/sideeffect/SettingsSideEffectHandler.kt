package io.ipoli.android.settings.sideeffect

import io.ipoli.android.Constants
import io.ipoli.android.MyPoliApp
import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.notification.QuickDoNotificationUtil
import io.ipoli.android.common.redux.Action
import io.ipoli.android.settings.SettingsAction
import io.ipoli.android.settings.usecase.*
import space.traversal.kapsule.required

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 5/17/18.
 */
object SettingsSideEffectHandler : AppSideEffectHandler() {

    private val savePlanDayTimeUseCase by required { savePlanDayTimeUseCase }
    private val savePlanDaysUseCase by required { savePlanDaysUseCase }
    private val saveTimeFormatUseCase by required { saveTimeFormatUseCase }
    private val saveAgendaStartScreenUseCase by required { saveAgendaStartScreenUseCase }
    private val saveTemperatureUnitUseCase by required { saveTemperatureUnitUseCase }
    private val saveResetDayTimeUseCase by required { saveResetDayTimeUseCase }
    private val saveReminderNotificationStyleUseCase by required { saveReminderNotificationStyleUseCase }
    private val savePlanDayNotificationStyleUseCase by required { savePlanDayNotificationStyleUseCase }
    private val saveQuickDoNotificationSettingUseCase by required { saveQuickDoNotificationSettingUseCase }
    private val saveAutoPostingSettingUseCase by required { saveAutoPostingSettingUseCase }
    private val sharedPreferences by required { sharedPreferences }

    override suspend fun doExecute(action: Action, state: AppState) {
        when (action) {
            is SettingsAction.PlanDayTimeChanged ->
                savePlanDayTimeUseCase.execute(SavePlanDayTimeUseCase.Params(action.time))

            is SettingsAction.PlanDaysChanged ->
                savePlanDaysUseCase.execute(SavePlanDaysUseCase.Params(action.days))

            is SettingsAction.TimeFormatChanged ->
                saveTimeFormatUseCase.execute(SaveTimeFormatUseCase.Params(action.format))

            is SettingsAction.AgendaStartScreenChanged -> {
                saveAgendaStartScreenUseCase.execute(SaveAgendaStartScreenUseCase.Params(action.startScreen))
                sharedPreferences.edit()
                    .putString(Constants.KEY_AGENDA_START_SCREEN, action.startScreen.name).apply()
            }

            is SettingsAction.TemperatureUnitChanged ->
                saveTemperatureUnitUseCase.execute(SaveTemperatureUnitUseCase.Params(action.unit))

            is SettingsAction.ReminderNotificationStyleChanged ->
                saveReminderNotificationStyleUseCase.execute(
                    SaveReminderNotificationStyleUseCase.Params(
                        action.style
                    )
                )

            is SettingsAction.PlanDayNotificationStyleChanged ->
                savePlanDayNotificationStyleUseCase.execute(
                    SavePlanDayNotificationStyleUseCase.Params(
                        action.style
                    )
                )

            is SettingsAction.ToggleAutoPosting -> {
                saveAutoPostingSettingUseCase.execute(
                    SaveAutoPostSettingUseCase.Params(
                        action.isEnabled
                    )
                )
            }

            is SettingsAction.ToggleQuickDoNotification -> {
                saveQuickDoNotificationSettingUseCase.execute(
                    SaveQuickDoNotificationSettingUseCase.Params(
                        action.isEnabled
                    )
                )

                if (action.isEnabled) {
                    sharedPreferences.edit()
                        .putBoolean(Constants.KEY_QUICK_DO_NOTIFICATION_ENABLED, true).apply()
                    state.dataState.todayQuests?.let {
                        QuickDoNotificationUtil.update(MyPoliApp.instance, it)
                    }

                } else {
                    sharedPreferences.edit()
                        .putBoolean(Constants.KEY_QUICK_DO_NOTIFICATION_ENABLED, false).apply()
                    QuickDoNotificationUtil.disable(MyPoliApp.instance)
                }

            }

            is SettingsAction.ResetDayTimeChanged ->
                saveResetDayTimeUseCase.execute(SaveResetDayTimeUseCase.Params(action.time))
        }
    }

    override fun canHandle(action: Action) = action is SettingsAction

}