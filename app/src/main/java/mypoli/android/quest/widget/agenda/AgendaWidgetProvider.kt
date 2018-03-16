package mypoli.android.quest.widget.agenda

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import mypoli.android.Constants
import mypoli.android.R
import mypoli.android.common.IntentUtil
import mypoli.android.common.text.CalendarFormatter
import mypoli.android.myPoliApp
import mypoli.android.quest.receiver.CompleteQuestReceiver
import org.threeten.bp.LocalDate
import org.threeten.bp.format.TextStyle
import java.util.*


/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 02/10/2018.
 */
class AgendaWidgetProvider : AppWidgetProvider() {

    companion object {
        const val WIDGET_QUEST_LIST_ACTION =
            "mypoli.android.intent.actions.WIDGET_QUEST_LIST_ACTION"

        const val QUEST_ACTION_EXTRA_KEY = "quest_action"

        const val QUEST_ACTION_VIEW = 1
        const val QUEST_ACTION_COMPLETE = 2
    }

    override fun onReceive(context: Context, intent: Intent) {

        if (WIDGET_QUEST_LIST_ACTION == intent.action) {
            val questId = intent.getStringExtra(Constants.QUEST_ID_EXTRA_KEY)

            val questAction = intent.getIntExtra(QUEST_ACTION_EXTRA_KEY, 0)

            if (questAction == QUEST_ACTION_VIEW) {
                context.startActivity(IntentUtil.showTimer(questId, context))

            } else if (questAction == QUEST_ACTION_COMPLETE) {
                onCompleteQuest(context, questId)

            } else throw IllegalArgumentException("Unknown agenda widget quest list action $questAction")
        }

        super.onReceive(context, intent)
    }

    private fun onCompleteQuest(context: Context, questId: String) {
        val i = Intent(context, CompleteQuestReceiver::class.java)
        i.putExtra(Constants.QUEST_ID_EXTRA_KEY, questId)
        context.sendBroadcast(i)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {

        val calendarFormatter = CalendarFormatter(myPoliApp.instance)

        val today = LocalDate.now()
        val dayOfWeek = today.dayOfWeek.getDisplayName(
            TextStyle.FULL, Locale.getDefault()
        )
        val date = calendarFormatter.dateWithoutYear(today)

        appWidgetIds.forEach {

            val rv = RemoteViews(context.packageName, R.layout.widget_agenda)

            rv.setTextViewText(R.id.widgetDayOfWeek, dayOfWeek)
            rv.setTextViewText(R.id.widgetDate, date)

            rv.setOnClickPendingIntent(R.id.widgetAgendaHeader, createStartAppIntent(context))

            rv.setRemoteAdapter(
                R.id.widgetAgendaList,
                createQuestListIntent(context, it)
            )

            rv.setPendingIntentTemplate(
                R.id.widgetAgendaList,
                createQuestClickIntent(context, it)
            )

            rv.setEmptyView(R.id.widgetAgendaList, R.id.widgetAgendaEmpty)

            appWidgetManager.notifyAppWidgetViewDataChanged(it, R.id.widgetDayOfWeek)
            appWidgetManager.notifyAppWidgetViewDataChanged(it, R.id.widgetDate)

            appWidgetManager.notifyAppWidgetViewDataChanged(it, R.id.widgetAgendaList)
            appWidgetManager.updateAppWidget(it, rv)
        }

        super.onUpdate(context, appWidgetManager, appWidgetIds)

    }


    private fun createQuestListIntent(context: Context, widgetId: Int) =
        Intent(context, AgendaWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }

    private fun createQuestClickIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, AgendaWidgetProvider::class.java)
        intent.action = AgendaWidgetProvider.WIDGET_QUEST_LIST_ACTION
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createStartAppIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            0,
            IntentUtil.startApp(context),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}