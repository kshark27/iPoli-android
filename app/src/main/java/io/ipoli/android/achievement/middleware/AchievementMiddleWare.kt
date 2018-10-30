package io.ipoli.android.achievement.middleware

import io.ipoli.android.MyPoliApp
import io.ipoli.android.achievement.usecase.UnlockAchievementsUseCase
import io.ipoli.android.achievement.usecase.UpdatePlayerStatsUseCase.Params.EventType.*
import io.ipoli.android.challenge.add.EditChallengeAction
import io.ipoli.android.challenge.show.ChallengeAction
import io.ipoli.android.common.AppState
import io.ipoli.android.common.NamespaceAction
import io.ipoli.android.common.di.BackgroundModule
import io.ipoli.android.common.home.HomeAction
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.Dispatcher
import io.ipoli.android.common.redux.MiddleWare
import io.ipoli.android.common.view.CurrencyConverterAction
import io.ipoli.android.friends.invite.AcceptFriendshipAction
import io.ipoli.android.pet.PetAction
import io.ipoli.android.pet.store.PetStoreAction
import io.ipoli.android.planday.PlanDayAction
import io.ipoli.android.player.attribute.AttributeRank
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.usecase.RewardPlayerUseCase
import io.ipoli.android.quest.bucketlist.BucketListAction
import io.ipoli.android.quest.schedule.agenda.view.AgendaAction
import io.ipoli.android.quest.schedule.calendar.dayview.view.DayViewAction
import io.ipoli.android.repeatingquest.add.EditRepeatingQuestAction
import io.ipoli.android.store.avatar.AvatarStoreAction
import io.ipoli.android.store.membership.MembershipAction
import io.ipoli.android.store.powerup.PowerUpStoreAction
import io.ipoli.android.tag.show.TagAction
import org.threeten.bp.LocalDate
import space.traversal.kapsule.Injects
import space.traversal.kapsule.inject
import space.traversal.kapsule.required

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 6/7/18.
 */
object AchievementProgressMiddleWare : MiddleWare<AppState>, Injects<BackgroundModule> {

    private val unlockAchievementsUseCase by required { unlockAchievementsUseCase }
    private val playerRepository by required { playerRepository }
    private val rewardPlayerUseCase by required { rewardPlayerUseCase }

    override fun onCreate() {
        inject(MyPoliApp.backgroundModule(MyPoliApp.instance))
    }

    override fun execute(
        state: AppState,
        dispatcher: Dispatcher,
        action: Action
    ): MiddleWare.Result {

        if (state.dataState.player == null) {
            return MiddleWare.Result.Continue
        }

        val a = (action as? NamespaceAction)?.source ?: action

        val eventType = when (a) {

            is PlanDayAction.CompleteYesterdayQuest,
            is BucketListAction.CompleteQuest,
            is TagAction.CompleteQuest,
            is AgendaAction.CompleteQuest,
            is DayViewAction.CompleteQuest ->
                QuestCompleted

            is PlanDayAction.UndoCompleteQuest,
            is BucketListAction.UndoCompleteQuest,
            is TagAction.UndoCompleteQuest,
            is AgendaAction.UndoCompleteQuest,
            is DayViewAction.UndoCompleteQuest ->
                QuestUncompleted

            is PlanDayAction.StartDay -> {
                val p = playerRepository.find()!!
                val dayPlanStreak = p.statistics.planDayStreak
                if (LocalDate.now() != dayPlanStreak.lastDate) {
                    val willPowerStatus =
                        AttributeRank.of(p.attributeLevel(Player.AttributeType.WILLPOWER), p.rank)
                    if (willPowerStatus != Player.Rank.NOVICE && willPowerStatus != Player.Rank.APPRENTICE) {
                        rewardPlayerUseCase.execute(RewardPlayerUseCase.Params.ForPlanDay())
                    }
                }
                DayPlanned
            }

            is EditRepeatingQuestAction.SaveNew ->
                RepeatingQuestCreated

            is ChallengeAction.Complete ->
                ChallengeCompleted

            is EditChallengeAction.SaveNew ->
                ChallengeCreated

            is CurrencyConverterAction.Convert ->
                GemsConverted(a.gems)

            is PetAction.EquipItem ->
                PetItemEquipped

            is AvatarStoreAction.Change ->
                AvatarChanged

            is PetStoreAction.ChangePet ->
                PetChanged

            is PetAction.Feed ->
                PetFed(a.food)

            is PetAction.PetDied ->
                PetDied

            is HomeAction.FeedbackSent ->
                FeedbackSent

            is MembershipAction.Subscribed ->
                BecomeMember

            is PowerUpStoreAction.Enable ->
                PowerUpActivated

            is PetAction.PetRevived ->
                PetRevived

            is AcceptFriendshipAction.Accept ->
                FriendshipAccepted

            else -> null
        }

        eventType?.let {
            unlockAchievementsUseCase.execute(
                UnlockAchievementsUseCase.Params(
                    player = playerRepository.find()!!,
                    eventType = eventType
                )
            )
        }

        return MiddleWare.Result.Continue
    }
}