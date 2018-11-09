package io.ipoli.android.common.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.transition.TransitionManager
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.widget.ImageView
import android.widget.Toast
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction

import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.common.view.ColorPickerViewState.Type.*
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.usecase.BuyColorPackUseCase
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.ColorPack
import kotlinx.android.synthetic.main.dialog_color_picker.view.*
import kotlinx.android.synthetic.main.view_dialog_header.view.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 9/2/17.
 */

sealed class ColorPickerAction : Action {
    data class Load(val selectedColor: Color? = null) : ColorPickerAction()
    object ShowUnlock : ColorPickerAction()
    data class BuyColorPack(val colorPack: ColorPack) : ColorPickerAction()

    data class BuyColorPackTransactionComplete(val result: BuyColorPackUseCase.Result) :
        ColorPickerAction()
}

object ColorPickerReducer : BaseViewStateReducer<ColorPickerViewState>() {

    override fun reduce(
        state: AppState,
        subState: ColorPickerViewState,
        action: Action
    ) = when (action) {

        is ColorPickerAction.Load ->
            state.dataState.player?.let {
                createPlayerState(subState, it).copy(
                    selectedColor = action.selectedColor
                )
            } ?: subState.copy(type = LOADING)


        is DataLoadedAction.PlayerChanged ->
            createPlayerState(subState, action.player)

        is ColorPickerAction.BuyColorPackTransactionComplete ->
            subState.copy(
                type = when (action.result) {
                    is BuyColorPackUseCase.Result.ColorPackBought -> COLOR_PACK_UNLOCKED
                    is BuyColorPackUseCase.Result.TooExpensive -> COLOR_PACK_TOO_EXPENSIVE
                }
            )

        is ColorPickerAction.ShowUnlock ->
            subState.copy(type = SHOW_UNLOCK)

        else -> subState
    }

    private fun createPlayerState(
        subState: ColorPickerViewState,
        player: Player
    ) = subState.copy(
        type = DATA_CHANGED,
        petAvatar = player.pet.avatar,
        colors = Color.values().toSet(),
        colorPacks = player.inventory.colorPacks
    )

    override fun defaultState() = ColorPickerViewState(ColorPickerViewState.Type.LOADING)

    override val stateKey = key<ColorPickerViewState>()

}

data class ColorPickerViewState(
    val type: Type,
    val petAvatar: PetAvatar? = null,
    val selectedColor: Color? = null,
    val colors: Set<Color> = emptySet(),
    val colorPacks: Set<ColorPack> = emptySet()
) : BaseViewState() {
    enum class Type {
        LOADING,
        DATA_CHANGED,
        COLOR_PACK_UNLOCKED,
        SHOW_UNLOCK,
        COLOR_PACK_TOO_EXPENSIVE
    }
}

class ColorPickerDialogController :
    ReduxDialogController<ColorPickerAction, ColorPickerViewState, ColorPickerReducer> {

    private var listener: ((Color) -> Unit) = {}
    private var selectedColor: Color? = null

    override val reducer = ColorPickerReducer

    constructor(listener: (Color) -> Unit = {}, selectedColor: Color? = null) : this() {
        this.listener = listener
        this.selectedColor = selectedColor
    }

    constructor(args: Bundle? = null) : super(args)

    override fun onHeaderViewCreated(headerView: View) {
        headerView.dialogHeaderTitle.setText(R.string.color_picker_title)
    }

    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View {
        @SuppressLint("InflateParams")
        val contentView = inflater.inflate(R.layout.dialog_color_picker, null)
        contentView.colorGrid.layoutManager = GridLayoutManager(activity!!, 4)
        contentView.colorGrid.adapter = ColorAdapter()
        return contentView
    }

    override fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog =
        dialogBuilder
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.back, null)
            .create()

    override fun onDialogCreated(dialog: AlertDialog, contentView: View) {
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).visibility = View.GONE
        }
    }

    override fun onCreateLoadAction() =
        ColorPickerAction.Load(selectedColor)

    override fun render(state: ColorPickerViewState, view: View) {
        when (state.type) {

            DATA_CHANGED -> {
                changeIcon(AndroidPetAvatar.valueOf(state.petAvatar!!.name).headImage)
                (view.colorGrid.adapter as ColorAdapter).updateAll(state.colorViewModels)
            }

            COLOR_PACK_UNLOCKED -> {
                showShortToast(R.string.color_pack_unlocked)
                showColors(view)
            }

            SHOW_UNLOCK -> showUnlock(view)

            COLOR_PACK_TOO_EXPENSIVE -> {
                navigate().toCurrencyConverter()
                Toast.makeText(
                    view.context,
                    stringRes(R.string.color_pack_not_enough_coins),
                    Toast.LENGTH_SHORT
                ).show()
            }

            else -> {
            }
        }
    }

    private fun showUnlock(view: View) {
        TransitionManager.beginDelayedTransition(dialog.window.decorView as ViewGroup)
        view.colorGrid.visibility = View.GONE
        view.unlockContainer.visibility = View.VISIBLE
        changeTitle(R.string.unlock_color_pack_title)
        changeNeutralButtonText(R.string.back)

        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).visibility = View.VISIBLE

        setNeutralButtonListener {
            showColors(view)
        }
        view.buyColorPack.dispatchOnClick { ColorPickerAction.BuyColorPack(ColorPack.BASIC) }
        view.colorPackPrice.text = ColorPack.BASIC.gemPrice.toString()
    }

    private fun renderColorGrid(view: View) {
        view.unlockContainer.visibility = View.GONE
        view.colorGrid.visibility = View.VISIBLE
        changeTitle(R.string.color_picker_title)
        setNeutralButtonListener(null)
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).visibility = View.GONE
    }

    private fun showColors(view: View) {
        val fadeOut = ObjectAnimator.ofFloat(dialog.window.decorView, "alpha", 1f, 0.0f)
        val fadeIn = ObjectAnimator.ofFloat(dialog.window.decorView, "alpha", 0.5f, 1f)
        fadeIn.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                renderColorGrid(view)
            }
        })

        fadeIn.interpolator = AccelerateInterpolator()
        fadeOut.duration = shortAnimTime
        fadeIn.duration = mediumAnimTime

        val set = AnimatorSet()
        set.playSequentially(fadeOut, fadeIn)
        set.start()
    }

    data class ColorViewModel(val color: Color, val isSelected: Boolean, val isLocked: Boolean)

    inner class ColorAdapter(private var colors: List<ColorViewModel> = emptyList()) :
        RecyclerView.Adapter<ColorAdapter.ViewHolder>() {
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val vm = colors[position]
            val iv = holder.itemView as ImageView

            val androidColor = AndroidColor.valueOf(vm.color.name)

            val drawable = iv.background as GradientDrawable
            drawable.setColor(ContextCompat.getColor(iv.context, androidColor.color500))

            when {
                vm.isSelected -> iv.setImageResource(R.drawable.ic_done_white_24dp)
                vm.isLocked -> iv.setImageResource(R.drawable.ic_lock_white_24dp)
                else -> iv.setImageDrawable(null)
            }
            if (!vm.isLocked) {
                iv.onDebounceClick {
                    listener(vm.color)
                    dismiss()
                }
            } else {
                iv.dispatchOnClick { ColorPickerAction.ShowUnlock }
            }
        }

        override fun getItemCount() = colors.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_color_picker,
                    parent,
                    false
                )
            )

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

        fun updateAll(viewModels: List<ColorViewModel>) {
            this.colors = viewModels
            notifyDataSetChanged()
        }

    }

    private val ColorPickerViewState.colorViewModels: List<ColorPickerDialogController.ColorViewModel>
        get() =
            colors.map {
                val isSelected = if (selectedColor == null) false else selectedColor == it
                ColorPickerDialogController.ColorViewModel(
                    color = it,
                    isSelected = isSelected,
                    isLocked = !colorPacks.contains(it.pack)
                )
            }
}