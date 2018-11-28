package io.ipoli.android.player.data

import org.threeten.bp.LocalDate
import org.threeten.bp.YearMonth

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 6/7/18.
 */
data class Statistics(
    val questCompletedCount: Long = 0,
    val questCompletedCountForDay: DayStatistic = DayStatistic(),
    val habitCompletedCountForDay: DayStatistic = DayStatistic(),
    val challengeCompletedCountForDay: DayStatistic = DayStatistic(),
    val gemConvertedCountForMonth: MonthStatistic = MonthStatistic(),
    val questCompletedStreak: StreakStatistic = StreakStatistic(),
    val dailyChallengeCompleteStreak: StreakStatistic = StreakStatistic(),
    val dailyChallengeBestStreak: Long = 0,
    val petHappyStateStreak: Long = 0,
    val awesomenessScoreStreak: Long = 0,
    val planDayStreak: StreakStatistic = StreakStatistic(),
    val focusHoursStreak: Long = 0,
    val repeatingQuestCreatedCount: Long = 0,
    val challengeCompletedCount: Long = 0,
    val challengeCreatedCount: Long = 0,
    val gemConvertedCount: Long = 0,
    val friendInvitedCount: Long = 0,
    val experienceForToday: Long = 0,
    val petItemEquippedCount: Long = 0,
    val avatarChangeCount: Long = 0,
    val petChangeCount: Long = 0,
    val petFedWithPoopCount: Long = 0,
    val petFedCount: Long = 0,
    val feedbackSentCount: Long = 0,
    val joinMembershipCount: Long = 0,
    val powerUpActivatedCount: Long = 0,
    val petRevivedCount: Long = 0,
    val petDiedCount: Long = 0,
    val inviteForFriendCount: Long = 0,
    val strengthStatusIndex: Long = 0,
    val intelligenceStatusIndex: Long = 0,
    val charismaStatusIndex: Long = 0,
    val expertiseStatusIndex: Long = 0,
    val wellBeingStatusIndex: Long = 0,
    val willpowerStatusIndex: Long = 0
) {

    data class StreakStatistic(val count: Long = 0, val lastDate: LocalDate? = null)

    data class DayStatistic(val count: Long = 0, val day: LocalDate = LocalDate.now()) {

        fun addValue(value: Long): DayStatistic {
            return if (LocalDate.now() == day) {
                copy(
                    count = count + value
                )
            } else {
                DayStatistic(value)
            }
        }

        fun removeValue(value: Long): DayStatistic {
            return if (LocalDate.now() == day) {
                copy(
                    count = Math.max(count - value, 0)
                )
            } else {
                DayStatistic(Math.max(value, 0))
            }
        }
    }

    data class MonthStatistic(val count: Long = 0, val month: YearMonth = YearMonth.now()) {

        fun addValue(value: Long): MonthStatistic {
            return if (YearMonth.now() == month) {
                copy(
                    count = count + value
                )
            } else {
                MonthStatistic(value)
            }
        }
    }

    val questCompletedCountForToday
        get() = if (questCompletedCountForDay.day == LocalDate.now()) {
            questCompletedCountForDay.count
        } else 0

    val habitCompletedCountForToday
        get() = if (habitCompletedCountForDay.day == LocalDate.now()) {
            habitCompletedCountForDay.count
        } else 0

    val challengeCompletedCountForToday
        get() = if (challengeCompletedCountForDay.day == LocalDate.now()) {
            challengeCompletedCountForDay.count
        } else 0

    val gemConvertedCountForThisMonth
        get() = if (gemConvertedCountForMonth.month == YearMonth.now()) {
            gemConvertedCountForMonth.count
        } else 0
}