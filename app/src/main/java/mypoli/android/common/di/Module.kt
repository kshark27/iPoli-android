package mypoli.android.common.di

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.view.LayoutInflater
import com.couchbase.lite.Database
import com.couchbase.lite.DatabaseConfiguration
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Job
import mypoli.android.AndroidJobReminderScheduler
import mypoli.android.ReminderScheduler
import mypoli.android.challenge.PersonalizeChallengePresenter
import mypoli.android.challenge.category.ChallengeCategoryListPresenter
import mypoli.android.challenge.category.list.ChallengeListForCategoryPresenter
import mypoli.android.challenge.usecase.BuyChallengeUseCase
import mypoli.android.challenge.usecase.ScheduleChallengeUseCase
import mypoli.android.common.AppReducer
import mypoli.android.common.AppState
import mypoli.android.common.middleware.LoadDataMiddleware
import mypoli.android.common.redux.StateStore
import mypoli.android.common.text.CalendarFormatter
import mypoli.android.common.view.ColorPickerPresenter
import mypoli.android.common.view.CurrencyConverterPresenter
import mypoli.android.common.view.IconPickerDialogPresenter
import mypoli.android.common.view.PetMessagePresenter
import mypoli.android.pet.AndroidJobLowerPetStatsScheduler
import mypoli.android.pet.LowerPetStatsScheduler
import mypoli.android.pet.PetDialogPresenter
import mypoli.android.pet.PetPresenter
import mypoli.android.pet.usecase.*
import mypoli.android.player.AndroidLevelDownScheduler
import mypoli.android.player.AndroidLevelUpScheduler
import mypoli.android.player.LevelDownScheduler
import mypoli.android.player.LevelUpScheduler
import mypoli.android.player.persistence.CouchbasePlayerRepository
import mypoli.android.player.persistence.PlayerRepository
import mypoli.android.player.usecase.*
import mypoli.android.player.view.LevelUpPresenter
import mypoli.android.quest.AndroidJobQuestCompleteScheduler
import mypoli.android.quest.QuestCompleteScheduler
import mypoli.android.quest.calendar.CalendarPresenter
import mypoli.android.quest.calendar.addquest.AddQuestPresenter
import mypoli.android.quest.calendar.dayview.DayViewPresenter
import mypoli.android.quest.data.persistence.CouchbaseQuestRepository
import mypoli.android.quest.data.persistence.QuestRepository
import mypoli.android.quest.usecase.*
import mypoli.android.quest.view.QuestCompletePresenter
import mypoli.android.rate.AndroidRatePopupScheduler
import mypoli.android.rate.RatePopupScheduler
import mypoli.android.rate.RatePresenter
import mypoli.android.reminder.view.formatter.ReminderTimeFormatter
import mypoli.android.reminder.view.formatter.TimeUnitFormatter
import mypoli.android.reminder.view.picker.ReminderPickerDialogPresenter
import mypoli.android.store.GemStorePresenter
import mypoli.android.store.theme.ThemeStorePresenter
import mypoli.android.store.theme.usecase.BuyThemeUseCase
import mypoli.android.store.theme.usecase.ChangeThemeUseCase
import mypoli.android.store.usecase.PurchaseGemPackUseCase
import mypoli.android.timer.TimerPresenter
import space.traversal.kapsule.HasModules
import space.traversal.kapsule.Injects
import space.traversal.kapsule.required

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 9/10/17.
 */
interface RepositoryModule {
    val questRepository: QuestRepository
    val playerRepository: PlayerRepository
}

class CouchbaseRepositoryModule : RepositoryModule, Injects<Module> {
    private val database by required { database }
    private val job by required { job }
    override val questRepository get() = CouchbaseQuestRepository(database, job + CommonPool)
    override val playerRepository get() = CouchbasePlayerRepository(database, job + CommonPool)
}

interface AndroidModule {
    val layoutInflater: LayoutInflater

    val sharedPreferences: SharedPreferences

    val reminderTimeFormatter: ReminderTimeFormatter

    val timeUnitFormatter: TimeUnitFormatter

    val calendarFormatter: CalendarFormatter

    val database: Database

    val reminderScheduler: ReminderScheduler

    val questCompleteScheduler: QuestCompleteScheduler

    val levelUpScheduler: LevelUpScheduler

    val levelDownScheduler: LevelDownScheduler

    val lowerPetStatsScheduler: LowerPetStatsScheduler

    val ratePopupScheduler: RatePopupScheduler

    val job: Job
}

interface StateStoreModule {
    val stateStore: StateStore<AppState>
}

class AndroidStateStoreModule(private val context: Context) : StateStoreModule, Injects<Module> {

    override val stateStore by required {
        StateStore<AppState>(
            AppReducer,
            listOf(LoadDataMiddleware(context, job + CommonPool))
        )
    }

}

class MainAndroidModule(private val context: Context) : AndroidModule {
    override val layoutInflater: LayoutInflater get() = LayoutInflater.from(context)

    override val sharedPreferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(context)

    override val reminderTimeFormatter get() = ReminderTimeFormatter(context)

    override val timeUnitFormatter get() = TimeUnitFormatter(context)

    override val calendarFormatter get() = CalendarFormatter(context)

    override val reminderScheduler get() = AndroidJobReminderScheduler()

    override val questCompleteScheduler get() = AndroidJobQuestCompleteScheduler()

    override val levelUpScheduler get() = AndroidLevelUpScheduler()

    override val levelDownScheduler get() = AndroidLevelDownScheduler()

    override val lowerPetStatsScheduler get() = AndroidJobLowerPetStatsScheduler()

    override val ratePopupScheduler get() = AndroidRatePopupScheduler()

    override val database: Database
        get() = Database("myPoli", DatabaseConfiguration(context.applicationContext))

    override val job get() = Job()
}

class MainUseCaseModule : UseCaseModule, Injects<Module> {

    private val questRepository by required { questRepository }
    private val playerRepository by required { playerRepository }
    private val reminderScheduler by required { reminderScheduler }
    private val questCompleteScheduler by required { questCompleteScheduler }
    private val levelUpScheduler by required { levelUpScheduler }
    private val levelDownScheduler by required { levelDownScheduler }
    private val rateDialogScheduler by required { ratePopupScheduler }
    override val loadScheduleForDateUseCase
        get() = LoadScheduleForDateUseCase(questRepository)
    override val saveQuestUseCase get() = SaveQuestUseCase(questRepository, reminderScheduler)
    override val removeQuestUseCase get() = RemoveQuestUseCase(questRepository, reminderScheduler)
    override val undoRemoveQuestUseCase get() = UndoRemovedQuestUseCase(questRepository)
    override val findQuestToRemindUseCase get() = FindQuestsToRemindUseCase(questRepository)
    override val snoozeQuestUseCase get() = SnoozeQuestUseCase(questRepository, reminderScheduler)
    override val completeQuestUseCase
        get() = CompleteQuestUseCase(
            questRepository,
            playerRepository,
            reminderScheduler,
            questCompleteScheduler,
            rateDialogScheduler,
            rewardPlayerUseCase
        )
    override val undoCompletedQuestUseCase
        get() = UndoCompletedQuestUseCase(
            questRepository,
            reminderScheduler,
            removeRewardFromPlayerUseCase
        )
    override val listenForPlayerChangesUseCase
        get() = ListenForPlayerChangesUseCase(
            playerRepository
        )
    override val rewardPlayerUseCase get() = RewardPlayerUseCase(playerRepository, levelUpScheduler)
    override val removeRewardFromPlayerUseCase
        get() = RemoveRewardFromPlayerUseCase(
            playerRepository,
            levelDownScheduler
        )
    override val feedPetUseCase get() = FeedPetUseCase(playerRepository)
    override val revivePetUseCase get() = RevivePetUseCase(playerRepository)
    override val buyPetUseCase get() = BuyPetUseCase(playerRepository)
    override val changePetUseCase get() = ChangePetUseCase(playerRepository)
    override val findPetUseCase get() = FindPetUseCase(playerRepository)
    override val changeThemeUseCase get() = ChangeThemeUseCase(playerRepository)
    override val buyThemeUseCase get() = BuyThemeUseCase(playerRepository)
    override val renamePetUseCase get() = RenamePetUseCase(playerRepository)
    override val buyIconPackUseCase get() = BuyIconPackUseCase(playerRepository)
    override val buyColorPackUseCase get() = BuyColorPackUseCase(playerRepository)
    override val convertCoinsToGemsUseCase get() = ConvertCoinsToGemsUseCase(playerRepository)
    override val comparePetItemsUseCase get() = ComparePetItemsUseCase()
    override val buyPetItemUseCase get() = BuyPetItemUseCase(playerRepository)
    override val equipPetItemUseCase get() = EquipPetItemUseCase(playerRepository)
    override val takeOffPetItemUseCase get() = TakeOffPetItemUseCase(playerRepository)
    override val purchaseGemPackUseCase get() = PurchaseGemPackUseCase(playerRepository)
    override val scheduleChallengeUseCase: ScheduleChallengeUseCase
        get() = ScheduleChallengeUseCase(
            questRepository
        )
    override val buyChallengeUseCase get() = BuyChallengeUseCase(playerRepository)
    override val splitDurationForPomodoroTimerUseCase get() = SplitDurationForPomodoroTimerUseCase()
    override val listenForQuestChangeUseCase get() = ListenForQuestChangeUseCase(questRepository)

    override val findPlayerLevelUseCase get() = FindPlayerLevelUseCase(playerRepository)

    override val lowerPetStatsUseCase
        get() = LowerPetStatsUseCase(
            questRepository,
            playerRepository
        )
}

interface UseCaseModule {
    val loadScheduleForDateUseCase: LoadScheduleForDateUseCase
    val saveQuestUseCase: SaveQuestUseCase
    val removeQuestUseCase: RemoveQuestUseCase
    val undoRemoveQuestUseCase: UndoRemovedQuestUseCase
    val findQuestToRemindUseCase: FindQuestsToRemindUseCase
    val snoozeQuestUseCase: SnoozeQuestUseCase
    val completeQuestUseCase: CompleteQuestUseCase
    val undoCompletedQuestUseCase: UndoCompletedQuestUseCase
    val listenForPlayerChangesUseCase: ListenForPlayerChangesUseCase
    val listenForQuestChangeUseCase: ListenForQuestChangeUseCase
    val rewardPlayerUseCase: RewardPlayerUseCase
    val removeRewardFromPlayerUseCase: RemoveRewardFromPlayerUseCase
    val feedPetUseCase: FeedPetUseCase
    val revivePetUseCase: RevivePetUseCase
    val buyPetUseCase: BuyPetUseCase
    val changePetUseCase: ChangePetUseCase
    val findPetUseCase: FindPetUseCase
    val changeThemeUseCase: ChangeThemeUseCase
    val buyThemeUseCase: BuyThemeUseCase
    val renamePetUseCase: RenamePetUseCase
    val buyIconPackUseCase: BuyIconPackUseCase
    val buyColorPackUseCase: BuyColorPackUseCase
    val convertCoinsToGemsUseCase: ConvertCoinsToGemsUseCase
    val comparePetItemsUseCase: ComparePetItemsUseCase
    val buyPetItemUseCase: BuyPetItemUseCase
    val equipPetItemUseCase: EquipPetItemUseCase
    val takeOffPetItemUseCase: TakeOffPetItemUseCase
    val purchaseGemPackUseCase: PurchaseGemPackUseCase
    val scheduleChallengeUseCase: ScheduleChallengeUseCase
    val buyChallengeUseCase: BuyChallengeUseCase
    val splitDurationForPomodoroTimerUseCase: SplitDurationForPomodoroTimerUseCase
    val findPlayerLevelUseCase: FindPlayerLevelUseCase
    val lowerPetStatsUseCase: LowerPetStatsUseCase
}

interface PresenterModule {
    val calendarPresenter: CalendarPresenter
    val dayViewPresenter: DayViewPresenter
    val reminderPickerPresenter: ReminderPickerDialogPresenter
    val addQuestPresenter: AddQuestPresenter
    val petPresenter: PetPresenter
    val petDialogPresenter: PetDialogPresenter
    val themeStorePresenter: ThemeStorePresenter
    val colorPickerPresenter: ColorPickerPresenter
    val iconPickerPresenter: IconPickerDialogPresenter
    val currencyConverterPresenter: CurrencyConverterPresenter
    val gemStorePresenter: GemStorePresenter
    val challengeCategoryListPresenter: ChallengeCategoryListPresenter
    val challengeListForCategoryPresenter: ChallengeListForCategoryPresenter
    val personalizeChallengePresenter: PersonalizeChallengePresenter
    val timerPresenter: TimerPresenter
    val petMessagePresenter: PetMessagePresenter
    val levelUpPresenter: LevelUpPresenter
    val questCompletePresenter: QuestCompletePresenter
    val ratePresenter: RatePresenter

}

class AndroidPresenterModule : PresenterModule, Injects<Module> {
    private val loadScheduleForDateUseCase by required { loadScheduleForDateUseCase }
    private val saveQuestUseCase by required { saveQuestUseCase }
    private val removeQuestUseCase by required { removeQuestUseCase }
    private val undoRemoveQuestUseCase by required { undoRemoveQuestUseCase }
    private val completeQuestUseCase by required { completeQuestUseCase }
    private val undoCompleteQuestUseCase by required { undoCompletedQuestUseCase }
    private val listenForPlayerChangesUseCase by required { listenForPlayerChangesUseCase }
    private val buyChallengeUseCase by required { buyChallengeUseCase }
    private val revivePetUseCase by required { revivePetUseCase }
    private val feedPetUseCase by required { feedPetUseCase }
    private val buyPetUseCase by required { buyPetUseCase }
    private val changePetUseCase by required { changePetUseCase }
    private val findPetUseCase by required { findPetUseCase }
    private val renamePetUseCase by required { renamePetUseCase }
    private val changeThemeUseCase by required { changeThemeUseCase }
    private val buyThemeUseCase by required { buyThemeUseCase }
    private val reminderTimeFormatter by required { reminderTimeFormatter }
    private val timeUnitFormatter by required { timeUnitFormatter }
    private val calendarFormatter by required { calendarFormatter }
    private val buyIconPackUseCase by required { buyIconPackUseCase }
    private val buyColorPackUseCase by required { buyColorPackUseCase }
    private val convertCoinsToGemsUseCase by required { convertCoinsToGemsUseCase }
    private val comparePetItemsUseCase by required { comparePetItemsUseCase }
    private val buyPetItemUseCase by required { buyPetItemUseCase }
    private val equipPetItemUseCase by required { equipPetItemUseCase }
    private val takeOffPetItemUseCase by required { takeOffPetItemUseCase }
    private val purchaseGemPackUseCase by required { purchaseGemPackUseCase }
    private val scheduleChallengeUseCase by required { scheduleChallengeUseCase }
    private val splitDurationForPomodoroTimerUseCase by required { splitDurationForPomodoroTimerUseCase }
    private val listenForQuestChangeUseCase by required { listenForQuestChangeUseCase }
    private val job by required { job }
    override val dayViewPresenter
        get() = DayViewPresenter(
            loadScheduleForDateUseCase,
            saveQuestUseCase,
            removeQuestUseCase,
            undoRemoveQuestUseCase,
            completeQuestUseCase,
            undoCompleteQuestUseCase,
            job
        )
    override val reminderPickerPresenter
        get() = ReminderPickerDialogPresenter(
            reminderTimeFormatter,
            timeUnitFormatter,
            findPetUseCase,
            job
        )
    override val calendarPresenter
        get() = CalendarPresenter(
            listenForPlayerChangesUseCase,
            calendarFormatter,
            job
        )
    override val addQuestPresenter get() = AddQuestPresenter(saveQuestUseCase, job)
    override val petPresenter
        get() = PetPresenter(
            listenForPlayerChangesUseCase,
            feedPetUseCase,
            renamePetUseCase,
            revivePetUseCase,
            comparePetItemsUseCase,
            buyPetItemUseCase,
            equipPetItemUseCase,
            takeOffPetItemUseCase,
            job
        )
    override val petDialogPresenter get() = PetDialogPresenter(findPetUseCase, job)
    override val themeStorePresenter
        get() = ThemeStorePresenter(
            listenForPlayerChangesUseCase,
            changeThemeUseCase,
            buyThemeUseCase,
            job
        )
    override val colorPickerPresenter
        get() = ColorPickerPresenter(
            listenForPlayerChangesUseCase,
            buyColorPackUseCase,
            job
        )
    override val iconPickerPresenter
        get() = IconPickerDialogPresenter(
            listenForPlayerChangesUseCase,
            buyIconPackUseCase,
            job
        )
    override val currencyConverterPresenter
        get() = CurrencyConverterPresenter(
            listenForPlayerChangesUseCase,
            convertCoinsToGemsUseCase,
            job
        )
    override val gemStorePresenter
        get() = GemStorePresenter(
            purchaseGemPackUseCase,
            listenForPlayerChangesUseCase,
            job
        )
    override val challengeCategoryListPresenter
        get() = ChallengeCategoryListPresenter(
            job
        )
    override val challengeListForCategoryPresenter
        get() = ChallengeListForCategoryPresenter(
            listenForPlayerChangesUseCase,
            buyChallengeUseCase,
            job
        )
    override val personalizeChallengePresenter
        get() = PersonalizeChallengePresenter(
            scheduleChallengeUseCase,
            job
        )
    override val timerPresenter
        get() = TimerPresenter(
            splitDurationForPomodoroTimerUseCase,
            listenForQuestChangeUseCase,
            job
        )

    override val petMessagePresenter get() = PetMessagePresenter(listenForPlayerChangesUseCase, job)
    override val levelUpPresenter get() = LevelUpPresenter(listenForPlayerChangesUseCase, job)
    override val questCompletePresenter
        get() = QuestCompletePresenter(
            listenForPlayerChangesUseCase,
            job
        )
    override val ratePresenter get() = RatePresenter(listenForPlayerChangesUseCase, job)
}

class Module(
    androidModule: AndroidModule,
    repositoryModule: RepositoryModule,
    useCaseModule: UseCaseModule,
    presenterModule: PresenterModule,
    stateStoreModule: StateStoreModule
) :
    AndroidModule by androidModule,
    RepositoryModule by repositoryModule,
    UseCaseModule by useCaseModule,
    PresenterModule by presenterModule,
    StateStoreModule by stateStoreModule,
    HasModules {
    override val modules =
        setOf(
            androidModule,
            repositoryModule,
            useCaseModule,
            presenterModule,
            stateStoreModule
        )
}