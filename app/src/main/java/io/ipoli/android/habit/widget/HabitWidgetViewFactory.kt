package io.ipoli.android.habit.widget

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.mikepenz.iconics.IconicsDrawable
import io.ipoli.android.Constants
import io.ipoli.android.MyPoliApp
import io.ipoli.android.R
import io.ipoli.android.common.di.BackgroundModule
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.widget.CircleProgressBar
import io.ipoli.android.habit.data.Habit
import org.threeten.bp.LocalDate
import space.traversal.kapsule.Injects
import space.traversal.kapsule.inject
import space.traversal.kapsule.required
import java.util.concurrent.CopyOnWriteArrayList


/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 7/27/18.
 */
class HabitWidgetViewsFactory(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory, Injects<BackgroundModule> {

    private val habitRepository by required { habitRepository }

    private var items = CopyOnWriteArrayList<Habit>()

    override fun onCreate() {
        inject(MyPoliApp.backgroundModule(context))
    }

    override fun getLoadingView() =
        RemoteViews(context.packageName, R.layout.item_widget_habit_loading)

    override fun getItemId(position: Int): Long = position.toLong()

    override fun onDataSetChanged() {
        items.clear()
        val today = LocalDate.now()
        items.addAll(habitRepository.findAllNotRemoved()
            .filter { it.shouldBeDoneOn(today) }
            .sortedWith(
                compareBy<Habit> { it.isCompletedForDate(today) }
                    .thenByDescending { it.timesADay }
                    .thenByDescending { it.isGood }
            ))
    }

    override fun hasStableIds() = true

    override fun getViewAt(position: Int): RemoteViews {
        val today = LocalDate.now()
        return items[position].let {
            RemoteViews(context.packageName, R.layout.item_widget_habit).apply {
                val progress = it.completedCountForDate(today)
                val maxProgress = it.timesADay
                val isCompletedFor = it.isCompletedForDate(today)
                val isCompleted = if (it.isGood) isCompletedFor else !isCompletedFor

                val px = context.resources.getDimensionPixelSize(R.dimen.habit_widget_item_size)

                val icon = it.icon.let { i -> AndroidIcon.valueOf(i.name).icon }
                val iconColor =
                    if (isCompleted) R.color.md_white else AndroidColor.valueOf(it.color.name).color500
                val iconDrawable =
                    IconicsDrawable(context).normalIcon(icon, iconColor)

                val habitColor = ContextCompat.getColor(
                    context,
                    AndroidColor.valueOf(it.color.name).color500
                )

                val whiteColor = ContextCompat.getColor(
                    context,
                    R.color.md_white
                )

                val myView =
                    LayoutInflater.from(context).inflate(R.layout.item_widget_habit_progress, null)
                myView.measure(px, px)
                myView.layout(0, 0, px, px)

                val habitProgress = myView.findViewById<CircleProgressBar>(R.id.habitProgress)
                val habitTimesADayProgress =
                    myView.findViewById<CircleProgressBar>(R.id.habitTimesADayProgress)

                habitProgress.setProgressStartColor(habitColor)
                habitProgress.setProgressEndColor(habitColor)
                habitProgress.setProgressBackgroundColor(
                    ContextCompat.getColor(
                        context,
                        AndroidColor.valueOf(it.color.name).color100
                    )
                )
                habitProgress.setProgressFormatter(null)
                habitProgress.max = maxProgress
                habitProgress.progress = progress

                if (it.timesADay > 1) {
                    habitTimesADayProgress.visible()
                    habitTimesADayProgress.setProgressStartColor(whiteColor)
                    habitTimesADayProgress.setProgressEndColor(whiteColor)
                    habitTimesADayProgress.setProgressFormatter(null)
                    habitTimesADayProgress.max = maxProgress
                    habitTimesADayProgress.setLineCount(maxProgress)
                    habitTimesADayProgress.progress = progress
                } else {
                    habitTimesADayProgress.gone()
                }

                val habitCompleteBackground =
                    myView.findViewById<View>(R.id.habitCompletedBackground)

                if (isCompleted) {
                    habitCompleteBackground.visible()
                    val b = habitCompleteBackground.background as GradientDrawable
                    b.mutate()
                    b.setColor(habitColor)
                    habitCompleteBackground.background = b
                } else {
                    habitCompleteBackground.gone()
                }

                myView.findViewById<ImageView>(R.id.habitIcon).setImageDrawable(iconDrawable)
                myView.isDrawingCacheEnabled = true
                val bitmap = myView.drawingCache
                setImageViewBitmap(R.id.habitProgressContainer, bitmap)

                setOnClickFillInIntent(
                    R.id.habitProgressContainer,
                    if (it.canCompleteMoreForDate(today)) createCompleteHabitIntent(it.id)
                    else createUndoCompleteHabitIntent(it.id)
                )
            }
        }
    }

    override fun getCount() = items.size

    override fun getViewTypeCount() = 1

    override fun onDestroy() {
    }

    private fun createCompleteHabitIntent(habitId: String): Intent {
        val b = Bundle().apply {
            putInt(
                HabitWidgetProvider.HABIT_ACTION_EXTRA_KEY,
                HabitWidgetProvider.HABIT_ACTION_COMPLETE
            )
            putString(Constants.HABIT_ID_EXTRA_KEY, habitId)
        }

        return Intent().putExtras(b)
    }

    private fun createUndoCompleteHabitIntent(habitId: String): Intent {
        val b = Bundle().apply {
            putInt(
                HabitWidgetProvider.HABIT_ACTION_EXTRA_KEY,
                HabitWidgetProvider.HABIT_ACTION_UNDO_COMPLETE
            )
            putString(Constants.HABIT_ID_EXTRA_KEY, habitId)
        }

        return Intent().putExtras(b)
    }
}