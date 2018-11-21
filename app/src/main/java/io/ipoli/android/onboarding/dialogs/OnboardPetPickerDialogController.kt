package io.ipoli.android.onboarding.dialogs

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.annotation.DrawableRes
import android.support.v7.app.AlertDialog
import android.support.v7.widget.GridLayoutManager
import android.view.LayoutInflater
import android.view.View
import io.ipoli.android.R
import io.ipoli.android.challenge.add.TextWatcherAdapter
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.common.view.ReduxDialogController
import io.ipoli.android.common.view.colorRes
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.pet.PetState
import kotlinx.android.synthetic.main.dialog_onboard_pick_pet.view.*
import kotlinx.android.synthetic.main.item_onboard_pet.view.*
import kotlinx.android.synthetic.main.view_dialog_header.view.*

sealed class OnboardPetPickerAction : Action {
    data class ChoosePet(val avatar: PetAvatar) : OnboardPetPickerAction()
    data class ChooseName(val name: String) : OnboardPetPickerAction()
}

object OnboardPetPickerReducer : BaseViewStateReducer<OnboardPetPickerViewState>() {
    override fun reduce(
        state: AppState,
        subState: OnboardPetPickerViewState,
        action: Action
    ) = when (action) {

        is OnboardPetPickerAction.ChoosePet -> {
            subState.copy(
                type = OnboardPetPickerViewState.StateType.SHOW_NAME_PICKER,
                avatar = action.avatar
            )
        }

        is OnboardPetPickerAction.ChooseName -> {
            subState.copy(
                type = OnboardPetPickerViewState.StateType.DONE,
                petName = action.name
            )
        }

        else -> subState
    }

    override fun defaultState() =
        OnboardPetPickerViewState(
            type = OnboardPetPickerViewState.StateType.LOADING,
            avatar = PetAvatar.DUCK,
            petName = ""
        )

    override val stateKey = key<OnboardPetPickerViewState>()

}

data class OnboardPetPickerViewState(
    val type: StateType,
    val avatar: PetAvatar,
    val petName: String
) : BaseViewState() {
    enum class StateType {
        LOADING,
        SHOW_NAME_PICKER,
        DONE
    }
}

class OnboardPetPickerDialogController(args: Bundle? = null) :
    ReduxDialogController<OnboardPetPickerAction, OnboardPetPickerViewState, OnboardPetPickerReducer>(
        args
    ) {

    override val reducer = OnboardPetPickerReducer

    private var listener: (PetAvatar, String) -> Unit = { _, _ -> }

    constructor(listener: (PetAvatar, String) -> Unit) : this() {
        this.listener = listener
    }

    override fun onHeaderViewCreated(headerView: View) {
        headerView.dialogHeaderTitle.setText(R.string.choose_pet)
        headerView.dialogHeaderIcon.setImageResource(R.drawable.logo)
        val background = headerView.dialogHeaderIcon.background as GradientDrawable
        background.setColor(colorRes(R.color.md_light_text_100))
    }

    @SuppressLint("InflateParams")
    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_onboard_pick_pet, null)
        view.avatarList.layoutManager = GridLayoutManager(view.context, 2)
        view.avatarList.setHasFixedSize(true)
        val adapter = AvatarAdapter()
        view.avatarList.adapter = adapter

        val avatars = listOf(
            PetAvatar.PIG,
            PetAvatar.ELEPHANT,
            PetAvatar.BEAVER,
            PetAvatar.DUCK
        )

        val backgroundColors = listOf(
            R.color.md_pink_200,
            R.color.md_orange_200,
            R.color.md_brown_200,
            R.color.md_green_200
        )

        adapter.updateAll(
            avatars.mapIndexed { index, a ->
                val androidAvatar = AndroidPetAvatar.valueOf(a.name)
                AvatarViewModel(
                    image = androidAvatar.image,
                    stateImage = androidAvatar.stateImage[PetState.HAPPY]!!,
                    backgroundColor = backgroundColors[index],
                    avatar = a
                )
            }
        )
        return view
    }

    override fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog =
        dialogBuilder
            .create()

    override fun render(state: OnboardPetPickerViewState, view: View) {
        when (state.type) {
            OnboardPetPickerViewState.StateType.SHOW_NAME_PICKER -> {
                view.rootContainer.showNext()
                view.pickPetName.isEnabled = false
                view.petName.addTextChangedListener(TextWatcherAdapter { e ->
                    view.pickPetName.isEnabled = !e.isBlank()
                })
                view.pickPetName.dispatchOnClick {
                    OnboardPetPickerAction.ChooseName(view.petName.text.toString())
                }
            }

            OnboardPetPickerViewState.StateType.DONE -> {
                listener(state.avatar, state.petName)
                dismiss()
            }

            else -> {
            }
        }
    }

    data class AvatarViewModel(
        @DrawableRes val image: Int,
        @DrawableRes val stateImage: Int,
        @ColorRes val backgroundColor: Int,
        val avatar: PetAvatar
    ) : RecyclerViewViewModel {
        override val id: String
            get() = avatar.name
    }

    inner class AvatarAdapter :
        BaseRecyclerViewAdapter<AvatarViewModel>(R.layout.item_onboard_pet) {

        override fun onBindViewModel(vm: AvatarViewModel, view: View, holder: SimpleViewHolder) {
            view.setBackgroundResource(vm.backgroundColor)
            view.petImage.setImageResource(vm.image)
            view.petStateImage.setImageResource(vm.stateImage)
            view.setOnClickListener {
                dispatch(OnboardPetPickerAction.ChoosePet(vm.avatar))
            }
        }

    }
}