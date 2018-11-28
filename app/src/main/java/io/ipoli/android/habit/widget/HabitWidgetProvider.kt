package io.ipoli.android.habit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import io.ipoli.android.Constants
import io.ipoli.android.MyPoliApp
import io.ipoli.android.R
import io.ipoli.android.common.IntentUtil
import io.ipoli.android.common.di.BackgroundModule
import io.ipoli.android.habit.receiver.CompleteHabitReceiver
import io.ipoli.android.habit.receiver.UndoCompleteHabitReceiver
import io.ipoli.android.store.powerup.PowerUp
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import space.traversal.kapsule.Injects
import space.traversal.kapsule.inject
import space.traversal.kapsule.required

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 7/27/18.
 */
class HabitWidgetProvider : AppWidgetProvider(), Injects<BackgroundModule> {

    private val playerRepository by required { playerRepository }

    companion object {
        const val WIDGET_HABIT_LIST_ACTION =
            "mypoli.android.intent.actions.WIDGET_HABIT_LIST_ACTION"

        const val HABIT_ACTION_EXTRA_KEY = "habit_action"

        const val HABIT_ACTION_COMPLETE = 1
        const val HABIT_ACTION_UNDO_COMPLETE = 2
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (WIDGET_HABIT_LIST_ACTION == intent.action) {
            val habitId = intent.getStringExtra(Constants.HABIT_ID_EXTRA_KEY)

            val habitAction = intent.getIntExtra(HABIT_ACTION_EXTRA_KEY, 0)

            if (habitAction == HABIT_ACTION_COMPLETE) {
                onCompleteHabit(context, habitId)
            } else if (habitAction == HABIT_ACTION_UNDO_COMPLETE) {
                onUndoCompleteHabit(context, habitId)
            } else throw IllegalArgumentException("Unknown habit widget habit list action $habitAction")
        }
        super.onReceive(context, intent)
    }

    private fun onCompleteHabit(context: Context, habitId: String) {
        val i = Intent(context, CompleteHabitReceiver::class.java)
        i.putExtra(Constants.HABIT_ID_EXTRA_KEY, habitId)
        context.sendBroadcast(i)
    }

    private fun onUndoCompleteHabit(context: Context, habitId: String) {
        val i = Intent(context, UndoCompleteHabitReceiver::class.java)
        i.putExtra(Constants.HABIT_ID_EXTRA_KEY, habitId)
        context.sendBroadcast(i)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        inject(MyPoliApp.backgroundModule(context))
        appWidgetIds.forEach {
            GlobalScope.launch(Dispatchers.IO) {
                val player = playerRepository.find()

                withContext(Dispatchers.Main) {
                    val rv = RemoteViews(context.packageName, R.layout.widget_habits)
                    if (player == null) {
                        showEmptyView(rv)
                    } else if (!player.isMember) {
                        showNoPowerUp(rv, context)
                    } else if(player.isDead) {
                        showDeadView(rv, context)
                    } else {
                        showHabitList(rv, context, it)

                        appWidgetManager.notifyAppWidgetViewDataChanged(it, R.id.widgetHabitList)
                    }

                    appWidgetManager.updateAppWidget(it, rv)
                    super.onUpdate(context, appWidgetManager, appWidgetIds)
                }
            }
        }
    }

    private fun showHabitList(
        rv: RemoteViews,
        context: Context,
        widgetId: Int
    ) {
        rv.setViewVisibility(R.id.habitWidgetLockedContainer, View.GONE)
        rv.setRemoteAdapter(
            R.id.widgetHabitList,
            createHabitListIntent(context, widgetId)
        )

        rv.setPendingIntentTemplate(
            R.id.widgetHabitList,
            createHabitClickIntent(context, widgetId)
        )

        rv.setEmptyView(R.id.widgetHabitList, R.id.widgetHabitEmpty)
    }

    private fun showDeadView(rv: RemoteViews, context: Context) {
        rv.setViewVisibility(R.id.habitWidgetPlayerDiedContainer, View.VISIBLE)
        rv.setViewVisibility(R.id.habitWidgetLockedContainer, View.GONE)
        rv.setViewVisibility(R.id.widgetHabitEmpty, View.GONE)
        rv.setViewVisibility(R.id.widgetHabitList, View.GONE)

        rv.setOnClickPendingIntent(
            R.id.widgetHabitRevive,
            createStartAppIntent(context)
        )
    }

    private fun showNoPowerUp(rv: RemoteViews, context: Context) {
        rv.setViewVisibility(R.id.habitWidgetLockedContainer, View.VISIBLE)
        rv.setViewVisibility(R.id.widgetHabitEmpty, View.GONE)
        rv.setViewVisibility(R.id.widgetHabitList, View.GONE)

        rv.setOnClickPendingIntent(
            R.id.widgetHabitUnlock,
            createShowBuyPowerUpIntent(context)
        )
    }

    private fun showEmptyView(rv: RemoteViews) {
        rv.setViewVisibility(R.id.habitWidgetLockedContainer, View.GONE)
        rv.setViewVisibility(R.id.widgetHabitEmpty, View.VISIBLE)
        rv.setViewVisibility(R.id.widgetHabitList, View.GONE)
    }

    private fun createHabitClickIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, HabitWidgetProvider::class.java)
        intent.action = HabitWidgetProvider.WIDGET_HABIT_LIST_ACTION
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createShowBuyPowerUpIntent(context: Context) =
        IntentUtil.getActivityPendingIntent(
            context,
            IntentUtil.showBuyPowerUp(context, PowerUp.Type.HABIT_WIDGET)
        )

    private fun createHabitListIntent(context: Context, widgetId: Int) =
        Intent(context, HabitWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }

    private fun createStartAppIntent(context: Context) =
        IntentUtil.getActivityPendingIntent(
            context,
            IntentUtil.startApp(context)
        )
}