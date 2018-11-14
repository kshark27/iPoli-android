package io.ipoli.android.planday.job

import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import com.evernote.android.job.Job
import com.evernote.android.job.JobRequest
import io.ipoli.android.Constants
import io.ipoli.android.MyPoliApp
import io.ipoli.android.R
import io.ipoli.android.common.IntentUtil
import io.ipoli.android.common.datetime.Duration
import io.ipoli.android.common.datetime.Minute
import io.ipoli.android.common.datetime.minutes
import io.ipoli.android.common.di.BackgroundModule
import io.ipoli.android.common.job.FixedDailyJob
import io.ipoli.android.common.job.FixedDailyJobScheduler
import io.ipoli.android.common.notification.NotificationUtil
import io.ipoli.android.common.notification.ScreenUtil
import io.ipoli.android.common.view.asThemedWrapper
import io.ipoli.android.dailychallenge.data.DailyChallenge
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.Pet
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.data.Player.Preferences.NotificationStyle
import io.ipoli.android.quest.reminder.PetNotificationPopup
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.launch
import org.threeten.bp.LocalDate
import space.traversal.kapsule.Kapsule
import java.util.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 05/18/2018.
 */

object PlanDayNotification {

    fun show(
        context: Context,
        player: Player,
        planDayScheduler: PlanDayScheduler
    ) {

        val pet = player.pet

        val c = context.asThemedWrapper()

        ScreenUtil.awakeScreen(c)

        val notificationManager =
            c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val style = player.preferences.planDayNotificationStyle

        var notificationId: Int? = showNotification(c, pet, notificationManager)

        if (!(style == NotificationStyle.NOTIFICATION || style == NotificationStyle.ALL)) {
            notificationManager.cancel(notificationId!!)
            notificationId = null
        }
        if (style == NotificationStyle.POPUP || style == NotificationStyle.ALL) {
            val vm = PetNotificationPopup.ViewModel(
                headline = "Time to plan your day",
                title = null,
                body = null,
                petAvatar = pet.avatar,
                petState = pet.state,
                doTextRes = R.string.start,
                doImageRes = R.drawable.ic_play_arrow_white_32dp
            )
            showPetPopup(vm, notificationId, notificationManager, planDayScheduler, c).show(c)
        }
    }

    private fun showPetPopup(
        vm: PetNotificationPopup.ViewModel,
        notificationId: Int?,
        notificationManager: NotificationManager,
        planDayScheduler: PlanDayScheduler,
        context: Context
    ) =
        PetNotificationPopup(
            vm,
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
                    planDayScheduler.scheduleAfter(15.minutes)
                }
                Toast
                    .makeText(context, context.getString(R.string.remind_in_15), Toast.LENGTH_SHORT)
                    .show()
            },
            onDo = {
                notificationId?.let {
                    notificationManager.cancel(it)
                }
                context.startActivity(IntentUtil.startPlanDay(context))
            })

    private fun showNotification(
        context: Context,
        pet: Pet,
        notificationManager: NotificationManager
    ): Int {
        val icon = BitmapFactory.decodeResource(
            context.resources,
            AndroidPetAvatar.valueOf(pet.avatar.name).headImage
        )

        val sound =
            Uri.parse("android.resource://" + context.packageName + "/" + R.raw.notification)

        val notification = NotificationUtil.createDefaultNotification(
            context = context,
            icon = icon,
            title = "Time to plan your day",
            message = "Amazing new day ahead!",
            sound = sound,
            channelId = Constants.PLAN_DAY_NOTIFICATION_CHANNEL_ID,
            contentIntent = IntentUtil.getActivityPendingIntent(
                context,
                IntentUtil.startPlanDay(context)
            )
        )

        val notificationId = Random().nextInt()

        notificationManager.notify(notificationId, notification)
        return notificationId
    }

}

class SnoozedPlanDayJob : Job() {

    override fun onRunJob(params: Params): Result {

        val kap = Kapsule<BackgroundModule>()
        val playerRepository by kap.required { playerRepository }
        val planDayScheduler by kap.required { planDayScheduler }
        kap.inject(MyPoliApp.backgroundModule(context))

        val p = playerRepository.find()
        requireNotNull(p)

        GlobalScope.launch(Dispatchers.Main) {
            PlanDayNotification.show(context, p!!, planDayScheduler)
        }

        return Result.SUCCESS
    }

    companion object {
        const val TAG = "job_snoozed_plan_day_tag"
    }
}

class PlanDayJob : FixedDailyJob(PlanDayJob.TAG) {

    override fun doRunJob(params: Params): Result {
        val kap = Kapsule<BackgroundModule>()
        val playerRepository by kap.required { playerRepository }
        val planDayScheduler by kap.required { planDayScheduler }
        val dailyChallengeRepository by kap.required { dailyChallengeRepository }
        kap.inject(MyPoliApp.backgroundModule(context))

        val p = playerRepository.find()
        requireNotNull(p)

        if (!p!!.preferences.planDays.contains(LocalDate.now().dayOfWeek)) {
            return Job.Result.SUCCESS
        }

        dailyChallengeRepository.findForDate(LocalDate.now())
            ?: dailyChallengeRepository.save(DailyChallenge(date = LocalDate.now()))

        GlobalScope.launch(Dispatchers.Main) {
            PlanDayNotification.show(context, p, planDayScheduler)
        }

        return Job.Result.SUCCESS
    }

    companion object {
        const val TAG = "job_plan_day_tag"
    }
}

interface PlanDayScheduler {
    fun scheduleAfter(minutes: Duration<Minute>)
    fun schedule()
}

class AndroidPlanDayScheduler(private val context: Context) : PlanDayScheduler {

    override fun scheduleAfter(minutes: Duration<Minute>) {
        JobRequest.Builder(SnoozedPlanDayJob.TAG)
            .setUpdateCurrent(true)
            .setExact(minutes.millisValue)
            .build()
            .schedule()
    }

    override fun schedule() {
        GlobalScope.launch(Dispatchers.IO) {

            val kap = Kapsule<BackgroundModule>()
            val playerRepository by kap.required { playerRepository }
            kap.inject(MyPoliApp.backgroundModule(this@AndroidPlanDayScheduler.context))

            val p = playerRepository.find()

            requireNotNull(p)

            val pTime = p!!.preferences.planDayTime

            FixedDailyJobScheduler.schedule(PlanDayJob.TAG, pTime)
        }
    }
}