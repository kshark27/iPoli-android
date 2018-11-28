package io.ipoli.android.common.job

import com.evernote.android.job.Job
import com.evernote.android.job.JobCreator
import io.ipoli.android.achievement.job.UpdateAchievementProgressJob
import io.ipoli.android.common.rate.RatePopupJob
import io.ipoli.android.dailychallenge.job.DailyChallengeCompleteJob
import io.ipoli.android.planday.job.PlanDayJob
import io.ipoli.android.planday.job.SnoozedPlanDayJob
import io.ipoli.android.quest.show.job.TimerCompleteNotificationJob
import io.ipoli.android.store.membership.job.CheckMembershipStatusJob

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 10/28/17.
 */
class myPoliJobCreator : JobCreator {
    override fun create(tag: String): Job? =
        when (tag) {
            RatePopupJob.TAG -> RatePopupJob()
            TimerCompleteNotificationJob.TAG -> TimerCompleteNotificationJob()
            CheckMembershipStatusJob.TAG -> CheckMembershipStatusJob()
            PlanDayJob.TAG -> PlanDayJob()
            SnoozedPlanDayJob.TAG -> SnoozedPlanDayJob()
            DailyChallengeCompleteJob.TAG -> DailyChallengeCompleteJob()
            UpdateAchievementProgressJob.TAG -> UpdateAchievementProgressJob()
            ResetDayJob.TAG -> ResetDayJob()
            ResetDateJob.TAG -> ResetDateJob()
            else -> null
        }
}