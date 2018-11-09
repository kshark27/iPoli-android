package io.ipoli.android.common.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.support.transition.TransitionManager
import android.support.v7.app.AlertDialog
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.widget.Toast
import com.mikepenz.iconics.IconicsDrawable
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction

import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.common.view.IconPickerViewState.Type.*
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.usecase.BuyIconPackUseCase
import io.ipoli.android.quest.Icon
import io.ipoli.android.quest.IconPack
import kotlinx.android.synthetic.main.dialog_icon_picker.view.*
import kotlinx.android.synthetic.main.item_icon_picker.view.*
import kotlinx.android.synthetic.main.view_dialog_header.view.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 9/2/17.
 */

sealed class IconPickerAction : Action {
    data class Load(val selectedIcon: Icon? = null) : IconPickerAction()
    object ShowUnlock : IconPickerAction()
    data class BuyIconPack(val iconPack: IconPack) : IconPickerAction()
    data class BuyIconPackTransactionComplete(val result: BuyIconPackUseCase.Result) :
        IconPickerAction()
}

object IconPickerReducer : BaseViewStateReducer<IconPickerViewState>() {
    override fun reduce(
        state: AppState,
        subState: IconPickerViewState,
        action: Action
    ) = when (action) {

        is IconPickerAction.Load ->
            state.dataState.player?.let {
                createPlayerState(subState, it).copy(
                    selectedIcon = action.selectedIcon
                )
            } ?: subState.copy(type = LOADING)


        is DataLoadedAction.PlayerChanged ->
            createPlayerState(subState, action.player)

        is IconPickerAction.ShowUnlock ->
            subState.copy(type = SHOW_UNLOCK)

        is IconPickerAction.BuyIconPackTransactionComplete ->
            subState.copy(
                type = when (action.result) {
                    is BuyIconPackUseCase.Result.IconPackBought -> ICON_PACK_UNLOCKED
                    is BuyIconPackUseCase.Result.TooExpensive -> ICON_PACK_TOO_EXPENSIVE
                }
            )

        else -> subState
    }

    private fun createPlayerState(state: IconPickerViewState, player: Player) =
        state.copy(
            type = DATA_CHANGED,
            petAvatar = player.pet.avatar,
            icons = Icon.values().toSet(),
            iconPacks = player.inventory.iconPacks
        )

    override fun defaultState() = IconPickerViewState(LOADING)

    override val stateKey = key<IconPickerViewState>()

}

data class IconPickerViewState(
    val type: Type,
    val petAvatar: PetAvatar? = null,
    val selectedIcon: Icon? = null,
    val icons: Set<Icon> = emptySet(),
    val iconPacks: Set<IconPack> = emptySet()
) : BaseViewState() {
    enum class Type {
        LOADING,
        DATA_CHANGED,
        SHOW_UNLOCK,
        ICON_PACK_UNLOCKED,
        ICON_PACK_TOO_EXPENSIVE
    }
}

class IconPickerDialogController :
    ReduxDialogController<IconPickerAction, IconPickerViewState, IconPickerReducer> {

    override val reducer = IconPickerReducer

    private var listener: (Icon?) -> Unit = {}
    private var selectedIcon: Icon? = null

    constructor(listener: (Icon?) -> Unit = {}, selectedIcon: Icon? = null) : this() {
        this.listener = listener
        this.selectedIcon = selectedIcon
    }

    constructor(args: Bundle? = null) : super(args)


    override fun onHeaderViewCreated(headerView: View) {
        headerView.dialogHeaderTitle.setText(R.string.icon_picker_title)
    }

    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View {
        @SuppressLint("InflateParams")
        val contentView = inflater.inflate(R.layout.dialog_icon_picker, null)
        contentView.iconGrid.layoutManager = GridLayoutManager(activity!!, 4)
        contentView.iconGrid.adapter = IconAdapter()
        return contentView
    }

    override fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog =
        dialogBuilder
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.no_icon) { _, _ ->
                listener(null)
            }
            .create()

    override fun onCreateLoadAction() =
        IconPickerAction.Load(selectedIcon)

    override fun render(state: IconPickerViewState, view: View) {

        when (state.type) {

            DATA_CHANGED -> {
                changeIcon(AndroidPetAvatar.valueOf(state.petAvatar!!.name).headImage)
                (view.iconGrid.adapter as IconAdapter).updateAll(state.iconViewModels)
            }

            SHOW_UNLOCK ->
                showUnlock(view)

            ICON_PACK_UNLOCKED -> {
                showShortToast(R.string.icon_pack_unlocked)
                showIcons(view)
            }

            ICON_PACK_TOO_EXPENSIVE -> {
                navigate().toCurrencyConverter()
                Toast.makeText(
                    view.context,
                    stringRes(R.string.icon_pack_not_enough_coins),
                    Toast.LENGTH_SHORT
                ).show()
            }

            else -> {
            }
        }
    }

    private fun showUnlock(view: View) {
        val fadeOut = ObjectAnimator.ofFloat(dialog.window.decorView, "alpha", 1f, 0.0f)
        val fadeIn = ObjectAnimator.ofFloat(dialog.window.decorView, "alpha", 0.5f, 1f)
        fadeIn.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                renderBuyIconPack(view)
            }
        })

        fadeIn.interpolator = AccelerateInterpolator()
        fadeOut.duration = shortAnimTime
        fadeIn.duration = mediumAnimTime

        val set = AnimatorSet()
        set.playSequentially(fadeOut, fadeIn)
        set.start()
    }

    private fun showIcons(view: View) {
        TransitionManager.beginDelayedTransition(view.container as ViewGroup)
        view.iconGrid.visibility = View.VISIBLE
        view.unlockContainer.visibility = View.GONE
        changeTitle(R.string.icon_picker_title)
        changeNeutralButtonText(R.string.no_icon)
        setNeutralButtonListener(null)
    }

    private fun renderBuyIconPack(view: View) {
        view.iconGrid.visibility = View.GONE
        view.unlockContainer.visibility = View.VISIBLE
        changeTitle(R.string.unlock_icon_pack_title)
        changeNeutralButtonText(R.string.back)
        setNeutralButtonListener {
            showIcons(view)
        }
        view.buyIconPack.dispatchOnClick { IconPickerAction.BuyIconPack(IconPack.BASIC) }
        view.iconPackPrice.text = IconPack.BASIC.gemPrice.toString()
    }

    data class IconViewModel(val icon: Icon, val isSelected: Boolean, val isLocked: Boolean)

    inner class IconAdapter(private var icons: List<IconViewModel> = emptyList()) :
        RecyclerView.Adapter<IconAdapter.ViewHolder>() {
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val vm = icons[holder.adapterPosition]
            val view = holder.itemView
            val iconView = view.icon

            val androidIcon = AndroidIcon.valueOf(vm.icon.name)

            iconView.setImageDrawable(
                IconicsDrawable(iconView.context)
                    .icon(androidIcon.icon)
                    .colorRes(if (vm.isSelected) R.color.md_white else androidIcon.color)
                    .paddingDp(8)
                    .sizeDp(48)
            )

            when {
                vm.isSelected -> {
                    iconView.setBackgroundResource(R.drawable.oval_background)
                    iconView.backgroundTintList = ColorStateList.valueOf(attrData(R.attr.colorAccent))
                    view.lockedIcon.visibility = View.GONE
                }
                vm.isLocked -> {
                    view.lockedIcon.visibility = View.VISIBLE
                    view.lockedIcon.imageTintList = ColorStateList.valueOf(attrData(R.attr.colorAccent))
                }
                else -> {
                    iconView.background = null
                    view.lockedIcon.visibility = View.GONE
                }
            }

            if (!vm.isLocked) {
                view.setOnClickListener {
                    listener(vm.icon)
                    dismiss()
                }
            } else {
                view.dispatchOnClick { IconPickerAction.ShowUnlock }
            }
        }

        override fun getItemCount() = icons.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_icon_picker,
                    parent,
                    false
                )
            )

        fun updateAll(viewModels: List<IconViewModel>) {
            this.icons = viewModels
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    }

    private val IconPickerViewState.iconViewModels: List<IconPickerDialogController.IconViewModel>
        get() =
            icons.map {
                val isSelected = if (selectedIcon == null) false else selectedIcon == it
                IconPickerDialogController.IconViewModel(
                    it,
                    isSelected,
                    !iconPacks.contains(it.pack)
                )
            }
}