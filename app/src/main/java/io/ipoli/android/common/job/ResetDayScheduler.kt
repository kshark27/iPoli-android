package io.ipoli.android.common.job

import android.content.Context
import io.ipoli.android.Constants
import io.ipoli.android.MyPoliApp
import io.ipoli.android.common.di.BackgroundModule
import io.ipoli.android.common.notification.QuickDoNotificationUtil
import io.ipoli.android.common.view.AppWidgetUtil
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.launch
import org.threeten.bp.LocalDate
import space.traversal.kapsule.Kapsule

class ResetDayJob : FixedDailyJob(ResetDayJob.TAG) {

    override fun doRunJob(params: Params): Result {

        val kap = Kapsule<BackgroundModule>()
        val playerRepository by kap.required { playerRepository }
        val questRepository by kap.required { questRepository }
        val sharedPreferences by kap.required { sharedPreferences }
        kap.inject(MyPoliApp.backgroundModule(context))

        val player = playerRepository.find()!!

        val todayQuests = questRepository.findScheduledAt(LocalDate.now())

        sharedPreferences.edit().putBoolean(Constants.KEY_SHOULD_REVIEW_DAY, true).apply()

        GlobalScope.launch(Dispatchers.Main) {

            if (player.preferences.isQuickDoNotificationEnabled) {
                QuickDoNotificationUtil.update(context, todayQuests)
            }

            AppWidgetUtil.updateAgendaWidget(context)
            AppWidgetUtil.updateHabitWidget(context)
        }

        return Result.SUCCESS
    }

    companion object {
        const val TAG = "job_reset_day_tag"
    }
}

interface ResetDayScheduler {
    fun schedule()
}

class AndroidResetDayScheduler(private val context: Context) : ResetDayScheduler {

    override fun schedule() {
        GlobalScope.launch(Dispatchers.IO) {

            val kap = Kapsule<BackgroundModule>()
            val playerRepository by kap.required { playerRepository }
            kap.inject(MyPoliApp.backgroundModule(this@AndroidResetDayScheduler.context))

            val p = playerRepository.find()

            requireNotNull(p)

            val t = p!!.preferences.resetDayTime
            FixedDailyJobScheduler.schedule(ResetDayJob.TAG, t)
        }
    }

}