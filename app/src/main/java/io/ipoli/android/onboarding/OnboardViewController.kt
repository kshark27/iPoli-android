package io.ipoli.android.onboarding

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.ionicons_typeface_library.Ionicons
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.onboarding.OnboardViewController.TutorialStepViewModel.StepAction.*
import io.ipoli.android.onboarding.OnboardViewState.OnboardStep.*
import io.ipoli.android.onboarding.dialogs.OnboardAdventurePickerDialogController
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.pet.PetState
import io.ipoli.android.player.auth.AuthAction
import io.ipoli.android.player.data.AndroidAvatar
import io.ipoli.android.player.data.Avatar
import kotlinx.android.synthetic.main.controller_onboard.view.*
import kotlinx.android.synthetic.main.item_tutorial_step.view.*
import space.traversal.kapsule.required

sealed class OnboardAction : Action {

    data class ChangeAvatar(val avatar: Avatar) : OnboardAction() {
        override fun toMap() = mapOf("avatar" to avatar.name)
    }

    data class ChangePet(val pet: PetAvatar, val name: String) : OnboardAction() {
        override fun toMap() = mapOf("pet" to pet.name)
    }

    data class ChangeAdventure(val adventures: List<OnboardAdventurePickerDialogController.Adventure>) :
        OnboardAction()

    object GetStarted : OnboardAction()

    data class CreateGuestPlayer(
        val playerAvatar: Avatar,
        val petAvatar: PetAvatar,
        val petName: String
    ) : OnboardAction() {
        override fun toMap() =
            mapOf("playerAvatar" to playerAvatar.name, "petAvatar" to petAvatar.name)
    }
}

object OnboardReducer : BaseViewStateReducer<OnboardViewState>() {

    override val stateKey = key<OnboardViewState>()

    override fun reduce(
        state: AppState,
        subState: OnboardViewState,
        action: Action
    ) = when (action) {

        is OnboardAction.GetStarted ->
            subState.copy(
                type = OnboardViewState.StateType.SHOW_GET_STARTED,
                currentStep = PICK_AVATAR
            )

        is OnboardAction.ChangeAvatar -> {
            val newState = subState.copy(
                type = OnboardViewState.StateType.STEP_CHANGED,
                avatar = action.avatar
            )

            newState.copy(
                currentStep = currentStepOf(newState)
            )
        }

        is OnboardAction.ChangePet -> {
            val newState = subState.copy(
                type = OnboardViewState.StateType.STEP_CHANGED,
                pet = action.pet,
                petName = action.name
            )
            newState.copy(
                currentStep = currentStepOf(newState)
            )
        }

        is OnboardAction.ChangeAdventure -> {
            subState.copy(
                type = OnboardViewState.StateType.STEP_CHANGED,
                adventures = action.adventures,
                currentStep = ALL_DONE
            )
        }

        is AuthAction.GuestCreated -> {
            subState.copy(
                type = OnboardViewState.StateType.SHOW_HOME
            )
        }

        else -> subState
    }

    private fun currentStepOf(state: OnboardViewState) =
        when {
            state.avatar == null -> PICK_AVATAR
            state.pet == null -> PICK_PET
            state.adventures == null -> PICK_HELP
            else -> ALL_DONE
        }

    override fun defaultState() = OnboardViewState(
        type = OnboardViewState.StateType.LOADING,
        avatar = null,
        pet = null,
        petName = null,
        adventures = null,
        currentStep = PICK_AVATAR
    )
}

data class OnboardViewState(
    val type: StateType,
    val avatar: Avatar?,
    val pet: PetAvatar?,
    val petName: String?,
    val adventures: List<OnboardAdventurePickerDialogController.Adventure>?,
    val currentStep: OnboardStep
) : BaseViewState() {
    enum class StateType {
        LOADING,
        SHOW_GET_STARTED,
        STEP_CHANGED,
        SHOW_HOME
    }

    enum class OnboardStep { PICK_AVATAR, PICK_PET, PICK_HELP, ALL_DONE }
}

class OnboardViewController(args: Bundle? = null) :
    ReduxViewController<OnboardAction, OnboardViewState, OnboardReducer>(args) {

    override val reducer = OnboardReducer

    private val eventLogger by required { eventLogger }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = container.inflate(R.layout.controller_onboard)
        view.typewriterText.animateText(stringRes(R.string.onboard_intro))
        view.typewriterText.animationCompleteCallback = {
            view.positiveButton.visible()
        }

        view.positiveButton.dispatchOnClick {
            view.positiveButton.isEnabled = false
            eventLogger.logEvent("onboard_get_started")
            OnboardAction.GetStarted
        }

        view.tutorialSteps.layoutManager = LinearLayoutManager(view.context)
        view.tutorialSteps.adapter = TutorialStepAdapter()

        return view
    }

    override fun render(state: OnboardViewState, view: View) {
        when (state.type) {

            OnboardViewState.StateType.SHOW_GET_STARTED -> {
                view.typewriterText.animationCompleteCallback = {}
                view.positiveButton.invisible()
                view.backgroundOverlay.gone()
                view.getStartedContainer.visible()
                view.getStartedContainer.fadeIn(shortAnimTime)
                view.typewriterText.animateText("Let's begin by choosing your Avatar. Tap 'Choose Avatar' above to do it!")
                (view.tutorialSteps.adapter as TutorialStepAdapter).updateAll(state.tutorialStepViewModels)
            }

            OnboardViewState.StateType.STEP_CHANGED -> {
                renderAvatar(state, view)

                renderPet(state, view)

                (view.tutorialSteps.adapter as TutorialStepAdapter).updateAll(state.tutorialStepViewModels)
                view.typewriterText.stopTextAnimation()
                when (state.currentStep) {

                    PICK_PET -> {
                        view.tutorialProgressLabel.text = stringRes(R.string.steps_done, 2, 4)
                        view.tutorialProgress.animateProgressFromCurrentValue(50)
                        view.typewriterText.animateText("Next, choose your Pet and give it a name")
                    }

                    PICK_HELP -> {
                        view.tutorialProgressLabel.text = stringRes(R.string.steps_done, 3, 4)
                        view.tutorialProgress.animateProgressFromCurrentValue(75)
                        view.typewriterText.animateText("Finally, how can I be of most help for you?")
                    }

                    ALL_DONE -> {
                        view.tutorialProgressLabel.text = stringRes(R.string.all_done)
                        view.tutorialProgress.animateProgressFromCurrentValue(100)
                        view.typewriterText.animationCompleteCallback = {
                            view.positiveButton.isEnabled = true
                            view.positiveButton.text = stringRes(R.string.dialog_lets_go)
                            view.positiveButton.visible()
                            view.positiveButton.onDebounceClick {
                                eventLogger.logEvent(
                                    "onboard_survey_help",
                                    mapOf("answers" to state.adventures?.map { it.name }?.joinToString())
                                )
                                view.positiveButton.isEnabled = false
                                dispatch(
                                    OnboardAction.CreateGuestPlayer(
                                        playerAvatar = state.avatar!!,
                                        petAvatar = state.pet!!,
                                        petName = state.petName!!
                                    )
                                )
                            }
                        }
                        view.typewriterText.animateText("All good, let's go! *opening another box of biscuits*")
                    }

                    else -> {
                    }
                }

            }

            OnboardViewState.StateType.SHOW_HOME -> {
                showShortToast(R.string.welcome_hero)
                navigateFromRoot().setHome()
            }

            else -> {
            }
        }
    }

    private fun renderAvatar(
        state: OnboardViewState,
        view: View
    ) {
        state.avatar?.let {
            view.playerAvatar.visible()
            val androidAvatar = AndroidAvatar.valueOf(state.avatar.name)
            Glide.with(view.context).load(androidAvatar.image)
                .apply(RequestOptions.circleCropTransform())
                .into(view.playerAvatar)
            val background = view.playerAvatar.background as GradientDrawable
            background.setColor(colorRes(androidAvatar.backgroundColor))
        }
    }

    private fun renderPet(
        state: OnboardViewState,
        view: View
    ) {
        state.pet?.let {
            view.petImage.visible()
            view.petStateImage.visible()
            val androidAvatar = AndroidPetAvatar.valueOf(state.pet.name)
            view.petImage.setImageResource(androidAvatar.image)
            view.petStateImage.setImageResource(androidAvatar.stateImage[PetState.HAPPY]!!)
            view.petName.text = state.petName!!
        }
    }

    data class TutorialStepViewModel(
        val text: String,
        val isComplete: Boolean,
        val isCurrentStep: Boolean,
        val canEdit: Boolean,
        val icon: IIcon,
        val stepAction: StepAction
    ) : RecyclerViewViewModel {
        override val id: String
            get() = text

        enum class StepAction { NONE, CHOOSE_AVATAR, CHOOSE_PET, CHOOSE_HELP }
    }

    inner class TutorialStepAdapter :
        BaseRecyclerViewAdapter<TutorialStepViewModel>(R.layout.item_tutorial_step) {
        override fun onBindViewModel(
            vm: TutorialStepViewModel,
            view: View,
            holder: SimpleViewHolder
        ) {
            if (vm.isComplete) {
                val span = SpannableString(vm.text)
                span.setSpan(StrikethroughSpan(), 0, vm.text.length, 0)
                view.tutorialStepName.text = span
            } else {
                view.tutorialStepName.text = vm.text
            }

            view.tutorialStepIcon.setImageDrawable(smallListItemIcon(vm.icon))

            val color = when {
                vm.isComplete -> R.color.md_grey_500
                vm.isCurrentStep -> R.color.md_green_500
                else -> R.color.md_grey_200
            }

            view.tutorialStepIcon.backgroundTintList =
                ColorStateList.valueOf(colorRes(color))

            if (vm.canEdit) {
                view.setBackgroundResource(attrResourceId(android.R.attr.selectableItemBackground))
                view.tutorialStepMoreIndicator.visible()
            } else {
                view.tutorialStepMoreIndicator.gone()
                view.background = null
            }

            when (vm.stepAction) {

                CHOOSE_AVATAR -> {
                    if (vm.canEdit) {
                        view.onDebounceClick {
                            eventLogger.logEvent("onboard_pick_avatar")
                            navigate().toOnboardAvatarPicker { a ->
                                dispatch(OnboardAction.ChangeAvatar(a))
                            }
                        }
                    } else {
                        view.setOnClickListener(null)
                    }
                }

                CHOOSE_PET ->
                    if (vm.canEdit) {
                        view.onDebounceClick {
                            eventLogger.logEvent("onboard_pick_pet")
                            navigate().toOnboardPetPicker { p, n ->
                                dispatch(OnboardAction.ChangePet(p, n))
                            }
                        }
                    } else {
                        view.setOnClickListener(null)
                    }

                CHOOSE_HELP ->
                    if (vm.canEdit) {
                        view.onDebounceClick {
                            eventLogger.logEvent("onboard_pick_adventures")
                            navigate().toOnboardAdventurePicker { adventures ->
                                dispatch(OnboardAction.ChangeAdventure(adventures))
                            }
                        }
                    } else {
                        view.setOnClickListener(null)
                    }

                else -> {
                }

            }
        }
    }

    private val OnboardViewState.tutorialStepViewModels
        get() = listOf(
            TutorialStepViewModel(
                text = "Show up",
                isComplete = true,
                canEdit = false,
                isCurrentStep = false,
                icon = Ionicons.Icon.ion_checkmark,
                stepAction = NONE
            ),
            TutorialStepViewModel(
                text = "Choose Avatar",
                isComplete = avatar != null,
                canEdit = true,
                isCurrentStep = currentStep == PICK_AVATAR,
                icon = Ionicons.Icon.ion_person,
                stepAction = CHOOSE_AVATAR
            ),
            TutorialStepViewModel(
                text = "Choose Pet",
                isComplete = pet != null,
                canEdit = pet != null || currentStep == PICK_PET,
                isCurrentStep = currentStep == PICK_PET,
                icon = CommunityMaterial.Icon.cmd_paw,
                stepAction = CHOOSE_PET
            ),
            TutorialStepViewModel(
                text = "Where do you need help?",
                isComplete = adventures != null,
                canEdit = adventures != null || currentStep == PICK_HELP,
                isCurrentStep = currentStep == PICK_HELP,
                icon = Ionicons.Icon.ion_android_compass,
                stepAction = CHOOSE_HELP
            )
        )
}