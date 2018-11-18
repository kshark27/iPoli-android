package io.ipoli.android.common.job

import io.ipoli.android.MyPoliApp
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.di.BackgroundModule
import io.ipoli.android.common.view.AppWidgetUtil
import io.ipoli.android.habit.usecase.SaveHabitRemindersUseCase
import io.ipoli.android.repeatingquest.persistence.RepeatingQuestRepository
import io.ipoli.android.repeatingquest.usecase.SaveQuestsForRepeatingQuestUseCase
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.launch
import org.threeten.bp.LocalDate
import space.traversal.kapsule.Kapsule

class ResetDateJob : FixedDailyJob(ResetDateJob.TAG) {

    override fun doRunJob(params: Params): Result {

        val kap = Kapsule<BackgroundModule>()
        val repeatingQuestRepository by kap.required { repeatingQuestRepository }
        val saveQuestsForRepeatingQuestUseCase by kap.required { saveQuestsForRepeatingQuestUseCase }
        val saveHabitRemindersUseCase by kap.required { saveHabitRemindersUseCase }
        val reminderScheduler by kap.required { reminderScheduler }
        kap.inject(MyPoliApp.backgroundModule(context))

        scheduleQuestsForRepeatingQuests(
            repeatingQuestRepository,
            saveQuestsForRepeatingQuestUseCase
        )

        saveHabitRemindersUseCase.execute(SaveHabitRemindersUseCase.Params())

        reminderScheduler.schedule()

        GlobalScope.launch(Dispatchers.Main) {
            AppWidgetUtil.updateAgendaWidget(context)
        }

        return Result.SUCCESS
    }

    private fun scheduleQuestsForRepeatingQuests(
        repeatingQuestRepository: RepeatingQuestRepository,
        saveQuestsForRepeatingQuestUseCase: SaveQuestsForRepeatingQuestUseCase
    ) {
        val rqs = repeatingQuestRepository.findAllActive()
        val newRqs = rqs.map {
            saveQuestsForRepeatingQuestUseCase.execute(
                SaveQuestsForRepeatingQuestUseCase.Params(
                    repeatingQuest = it,
                    start = LocalDate.now()
                )
            ).repeatingQuest
        }
        repeatingQuestRepository.save(newRqs)
    }

    companion object {
        const val TAG = "job_reset_date_tag"
    }
}

interface ResetDateScheduler {
    fun schedule()
}

class AndroidResetDateScheduler : ResetDateScheduler {
    override fun schedule() {
        FixedDailyJobScheduler.schedule(ResetDateJob.TAG, Time.atHours(0))
    }
}