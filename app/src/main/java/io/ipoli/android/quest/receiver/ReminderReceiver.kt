package io.ipoli.android.quest.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import io.ipoli.android.Constants
import io.ipoli.android.R
import io.ipoli.android.common.AsyncBroadcastReceiver
import io.ipoli.android.common.IntentUtil
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.datetime.minutes
import io.ipoli.android.common.datetime.toTime
import io.ipoli.android.common.notification.NotificationUtil
import io.ipoli.android.common.notification.ScreenUtil
import io.ipoli.android.common.view.AndroidIcon
import io.ipoli.android.common.view.AppWidgetUtil
import io.ipoli.android.common.view.asThemedWrapper
import io.ipoli.android.common.view.largeIcon
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.habit.usecase.CompleteHabitUseCase
import io.ipoli.android.habit.usecase.SnoozeHabitReminderUseCase
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.data.Player.Preferences.NotificationStyle
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.Reminder
import io.ipoli.android.quest.reminder.PetNotificationPopup
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.launch
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import org.threeten.bp.temporal.ChronoUnit
import space.traversal.kapsule.required
import java.util.*

class ReminderReceiver : AsyncBroadcastReceiver() {

    private val findQuestsToRemindUseCase by required { findQuestsToRemindUseCase }
    private val findHabitsToRemindUseCase by required { findHabitsToRemindUseCase }
    private val snoozeQuestUseCase by required { snoozeQuestUseCase }
    private val snoozeHabitReminderUseCase by required { snoozeHabitReminderUseCase }
    private val completeHabitUseCase by required { completeHabitUseCase }
    private val playerRepository by required { playerRepository }

    private val reminderScheduler by required { reminderScheduler }

    override suspend fun onReceiveAsync(context: Context, intent: Intent) {

        if (intent.action != ReminderReceiver.ACTION_SHOW_REMINDER) {
            return
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val c = context.asThemedWrapper()
        val remindAt = intent.extras.getLong("remindAtUTC", -1)

        require(remindAt >= 0)

        val remindDateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(remindAt),
            ZoneId.systemDefault()
        )

        val remindTime = remindDateTime.toTime()

        val quests = findQuestsToRemindUseCase.execute(remindDateTime)
        val habits = findHabitsToRemindUseCase.execute(remindDateTime)

        val player = playerRepository.find()!!

        GlobalScope.launch(Dispatchers.Main) {
            quests.forEach { q ->
                showQuestNotification(q, notificationManager, player, c)
            }
            habits.forEach { h ->
                val idx = h.reminders.sortedBy { it.time.toMinuteOfDay() }
                    .binarySearchBy(remindTime) { it.time }

                val reminderIndex =
                    if (idx < 0)
                        Math.min(Math.max(0, Math.abs(idx + 1)), h.reminders.size - 1)
                    else
                        idx

                val reminder = h.reminders[reminderIndex]

                showHabitNotification(h, reminder, remindDateTime, notificationManager, player, c)
            }
        }
        reminderScheduler.schedule()
    }

    private fun showHabitNotification(
        habit: Habit,
        reminder: Habit.Reminder,
        remindDateTime: LocalDateTime,
        notificationManager: NotificationManager,
        player: Player,
        context: Context
    ) {

        val notificationStyle = player.preferences.reminderNotificationStyle
        val pet = player.pet

        val message = when {
            reminder.message.isNotBlank() -> reminder.message
            else -> "Time to build your Habit!"
        }

        val icon = habit.icon.let {
            val androidIcon = AndroidIcon.valueOf(it.name)
            IconicsDrawable(context)
                .largeIcon(androidIcon.icon, androidIcon.color)
        }

        var notificationId: Int? = showNotification(
            context = context,
            contentIntent = IntentUtil.getActivityPendingIntent(
                context,
                IntentUtil.showHabit(habit.id, context)
            ),
            title = habit.name,
            message = message,
            icon = icon,
            notificationManager = notificationManager
        )

        if (!(notificationStyle == NotificationStyle.NOTIFICATION || notificationStyle == NotificationStyle.ALL)) {
            notificationManager.cancel(notificationId!!)
            notificationId = null
        }

        if (notificationStyle == NotificationStyle.POPUP || notificationStyle == NotificationStyle.ALL) {
            val viewModel = PetNotificationPopup.ViewModel(
                headline = habit.name,
                title = message,
                body = "Do it now",
                petAvatar = pet.avatar,
                petState = pet.state,
                doTextRes = R.string.mark_done,
                doImageRes = R.drawable.ic_done_white_32dp
            )

            PetNotificationPopup(
                viewModel,
                onDismiss = {
                    notificationId?.let {
                        notificationManager.cancel(it)
                    }
                },
                onSnooze = {
                    notificationId?.let {
                        notificationManager.cancel(it)
                    }
                    GlobalScope.launch(Dispatchers.IO) {
                        snoozeHabitReminderUseCase.execute(
                            SnoozeHabitReminderUseCase.Params(
                                habitId = habit.id,
                                remindTime = remindDateTime,
                                snoozeDuration = 15.minutes
                            )
                        )
                    }
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.remind_in_15),
                            Toast.LENGTH_SHORT
                        )
                        .show()
                },
                onDo = {
                    notificationId?.let {
                        notificationManager.cancel(it)
                    }
                    GlobalScope.launch(Dispatchers.IO) {
                        completeHabitUseCase.execute(CompleteHabitUseCase.Params(habit.id))
                    }
                    AppWidgetUtil.updateHabitWidget(context)
                }
            ).show(context)
        }

    }

    private fun showQuestNotification(
        quest: Quest,
        notificationManager: NotificationManager,
        player: Player,
        context: Context
    ) {

        val notificationStyle = player.preferences.reminderNotificationStyle
        val pet = player.pet

        val reminder = quest.reminders.first()

        val message = when {
            reminder.message.isNotBlank() -> reminder.message
            reminder is Reminder.Relative -> if (reminder.minutesFromStart == 0L) "This is your Quest - time to act!" else "Time to prepare for your Quest"
            else -> "This is your Quest - time to act!"
        }

        val startTimeMessage = startTimeMessage(quest)

        val iconicsDrawable = IconicsDrawable(context)
        val icon = quest.icon?.let {
            val androidIcon = AndroidIcon.valueOf(it.name)
            iconicsDrawable.largeIcon(
                androidIcon.icon,
                androidIcon.color
            )
        } ?: iconicsDrawable.largeIcon(
            GoogleMaterial.Icon.gmd_notifications_active,
            R.color.md_blue_500
        )

        val questName = quest.name

        var notificationId: Int? = showNotification(
            context = context,
            contentIntent = IntentUtil.getActivityPendingIntent(
                context,
                IntentUtil.showTimer(quest.id, context)
            ),
            title = questName,
            message = message,
            icon = icon,
            notificationManager = notificationManager
        )
        if (!(notificationStyle == NotificationStyle.NOTIFICATION || notificationStyle == NotificationStyle.ALL)) {
            notificationManager.cancel(notificationId!!)
            notificationId = null
        }

        if (notificationStyle == NotificationStyle.POPUP || notificationStyle == NotificationStyle.ALL) {
            val viewModel = PetNotificationPopup.ViewModel(
                headline = questName,
                title = message,
                body = startTimeMessage,
                petAvatar = pet.avatar,
                petState = pet.state,
                doTextRes = R.string.start,
                doImageRes = R.drawable.ic_play_arrow_white_32dp
            )
            PetNotificationPopup(
                viewModel,
                onDismiss = {
                    notificationId?.let {
                        notificationManager.cancel(it)
                    }
                },
                onSnooze = {
                    notificationId?.let {
                        notificationManager.cancel(it)
                    }
                    GlobalScope.launch(Dispatchers.IO) {
                        snoozeQuestUseCase.execute(quest.id)
                    }
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.remind_in_15),
                            Toast.LENGTH_SHORT
                        )
                        .show()
                },
                onDo = {
                    notificationId?.let {
                        notificationManager.cancel(it)
                    }
                    context.startActivity(IntentUtil.showTimer(quest.id, context))
                }
            ).show(context)
        }
    }

    private fun showNotification(
        context: Context,
        title: String,
        contentIntent: PendingIntent,
        message: String,
        icon: IconicsDrawable,
        notificationManager: NotificationManager
    ): Int {
        val sound =
            Uri.parse("android.resource://" + context.packageName + "/" + R.raw.notification)
        val notification = NotificationUtil.createDefaultNotification(
            context = context,
            title = title,
            icon = icon.toBitmap(),
            message = message,
            sound = sound,
            channelId = Constants.REMINDERS_NOTIFICATION_CHANNEL_ID,
            contentIntent = contentIntent
        )

        val notificationId = Random().nextInt()

        notificationManager.notify(notificationId, notification)
        ScreenUtil.awakeScreen(context)
        return notificationId
    }

    private fun startTimeMessage(quest: Quest): String {
        val daysDiff = ChronoUnit.DAYS.between(quest.scheduledDate, LocalDate.now())
        return if (daysDiff > 0) {
            "Starts in $daysDiff day(s)"
        } else {
            val minutesDiff = quest.startTime!!.toMinuteOfDay() - Time.now().toMinuteOfDay()

            when {
                minutesDiff > Time.MINUTES_IN_AN_HOUR -> "Starts at ${quest.startTime.toString(
                    false
                )}"
                minutesDiff > 0 -> "Starts in $minutesDiff min"
                else -> "Starts now"
            }
        }
    }

    companion object {
        const val ACTION_SHOW_REMINDER = "io.ipoli.android.intent.action.SHOW_REMINDER"
    }
}