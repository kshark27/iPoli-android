package io.ipoli.android.challenge.preset.sideeffect

import com.google.firebase.auth.FirebaseAuth
import io.ipoli.android.challenge.preset.PresetChallengeAction
import io.ipoli.android.challenge.preset.add.AddPresetChallengeAction
import io.ipoli.android.challenge.preset.category.list.ChallengeListForCategoryAction
import io.ipoli.android.challenge.preset.usecase.UnlockPresetChallengeUseCase
import io.ipoli.android.challenge.usecase.CreateChallengeFromPresetUseCase
import io.ipoli.android.challenge.usecase.CreatePresetChallengeUseCase
import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.ErrorLogger
import io.ipoli.android.common.redux.Action
import org.threeten.bp.LocalDate
import space.traversal.kapsule.required
import timber.log.Timber

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 9/28/18.
 */
object PresetChallengeSideEffectHandler : AppSideEffectHandler() {

    private val challengeRepository by required { challengeRepository }
    private val presetChallengeRepository by required { presetChallengeRepository }
    private val createChallengeFromPresetUseCase by required { createChallengeFromPresetUseCase }
    private val unlockPresetChallengeUseCase by required { unlockPresetChallengeUseCase }
    private val createPresetChallengeUseCase by required { createPresetChallengeUseCase }

    override suspend fun doExecute(action: Action, state: AppState) {
        when (action) {
            is ChallengeListForCategoryAction.Load -> {
                try {
                    dispatch(
                        DataLoadedAction.PresetChallengeListForCategoryChanged(
                            category = action.category,
                            challenges = presetChallengeRepository.findForCategory(action.category)
                        )
                    )
                } catch (e: Throwable) {
                    Timber.e(e)
                    dispatch(ChallengeListForCategoryAction.ErrorLoadingChallenges)
                }
            }

            is PresetChallengeAction.Accept -> {

                if (challengeRepository.hasActivePresetCHallenge(action.challenge.id)) {
                    dispatch(PresetChallengeAction.ChallengeAlreadyAccepted)
                    return
                }

                val c = createChallengeFromPresetUseCase.execute(
                    CreateChallengeFromPresetUseCase.Params(
                        preset = action.challenge,
                        schedule = action.schedule,
                        tags = action.tags,
                        startDate = LocalDate.now(),
                        questsStartTime = action.startTime,
                        playerPhysicalCharacteristics = action.physicalCharacteristics
                    )
                )
                presetChallengeRepository.join(action.challenge.id)
                dispatch(PresetChallengeAction.Accepted(c.id))
            }

            is PresetChallengeAction.Unlock -> {
                val result = unlockPresetChallengeUseCase.execute(
                    UnlockPresetChallengeUseCase.Params(action.challenge)
                )
                when (result) {
                    is UnlockPresetChallengeUseCase.Result.Unlocked -> {
                        dispatch(PresetChallengeAction.Unlocked)
                    }

                    UnlockPresetChallengeUseCase.Result.TooExpensive -> {
                        dispatch(
                            PresetChallengeAction.ChallengeTooExpensive
                        )
                    }
                }
            }

            is AddPresetChallengeAction.Save -> {
                try {
                    savePresetChallenge(action)
                    dispatch(AddPresetChallengeAction.Saved)
                } catch (e: Throwable) {
                    ErrorLogger.log(e)
                    dispatch(AddPresetChallengeAction.SaveError)
                }
            }

        }
    }

    private fun savePresetChallenge(action: AddPresetChallengeAction.Save) {
        createPresetChallengeUseCase.execute(
            CreatePresetChallengeUseCase.Params(
                playerId = FirebaseAuth.getInstance().currentUser!!.uid,
                name = action.name,
                shortDescription = action.shortDescription,
                description = action.description,
                icon = action.icon,
                color = action.color,
                duration = action.duration,
                category = action.category,
                expectedResults = action.expectedResults,
                requirements = action.requirements,
                difficulty = action.difficulty,
                quests = action.quests,
                habits = action.habits
            )
        )
    }

    override fun canHandle(action: Action) =
        action is ChallengeListForCategoryAction
            || action is PresetChallengeAction
            || action is AddPresetChallengeAction

}