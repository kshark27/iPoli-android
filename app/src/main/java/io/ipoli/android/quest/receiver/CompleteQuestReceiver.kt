package io.ipoli.android.quest.receiver

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import io.ipoli.android.Constants
import io.ipoli.android.common.AsyncBroadcastReceiver
import io.ipoli.android.common.notification.QuickDoNotificationUtil
import io.ipoli.android.common.view.AppWidgetUtil
import io.ipoli.android.quest.show.usecase.CompleteTimeRangeUseCase
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.launch
import org.threeten.bp.LocalDate
import space.traversal.kapsule.required

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 02/10/2018.
 */
class CompleteQuestReceiver : AsyncBroadcastReceiver() {

    private val completeTimeRangeUseCase by required { completeTimeRangeUseCase }
    private val questRepository by required { questRepository }
    private val sharedPreferences by required { sharedPreferences }

    override suspend fun onReceiveAsync(context: Context, intent: Intent) {
        val questId = intent.getStringExtra(Constants.QUEST_ID_EXTRA_KEY)
        completeTimeRangeUseCase.execute(CompleteTimeRangeUseCase.Params(questId))

        if (sharedPreferences.getBoolean(
                Constants.KEY_QUICK_DO_NOTIFICATION_ENABLED,
                Constants.DEFAULT_QUICK_DO_NOTIFICATION_ENABLED
            )
        ) {
            val todayQuests = questRepository.findScheduledAt(LocalDate.now())
            QuickDoNotificationUtil.update(context, todayQuests)
        }
        GlobalScope.launch(Dispatchers.Main) {
            updateUIElements(context)
        }
    }

    private fun updateUIElements(context: Context) {
        removeTimerNotification(context)
        AppWidgetUtil.updateAgendaWidget(context)
    }

    private fun removeTimerNotification(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(Constants.QUEST_TIMER_NOTIFICATION_ID)
    }
}