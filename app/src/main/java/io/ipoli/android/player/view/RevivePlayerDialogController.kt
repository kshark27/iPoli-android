package io.ipoli.android.player.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import io.ipoli.android.MyPoliApp
import io.ipoli.android.R
import io.ipoli.android.common.di.UIModule
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.view.BaseDialogController
import io.ipoli.android.common.view.Debounce
import kotlinx.android.synthetic.main.dialog_revive_player.view.*
import space.traversal.kapsule.Injects
import space.traversal.kapsule.inject
import space.traversal.kapsule.required

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 09/12/2018.
 */

object ReviveAction : Action

class RevivePlayerDialogController(args: Bundle? = null) : BaseDialogController(args),
    Injects<UIModule> {

    private val stateStore by required { stateStore }

    override fun onContextAvailable(context: Context) {
        inject(MyPoliApp.uiModule(context))
    }

    @SuppressLint("InflateParams")
    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_revive_player, null)
        view.reviveLogo.background.setColorFilter(
            ContextCompat.getColor(view.context, R.color.evil_snail_background),
            PorterDuff.Mode.SRC_ATOP
        )
        view.revivePlayer.setOnClickListener(Debounce.clickListener {
            stateStore.dispatch(ReviveAction)
            dismiss()
        })
        return view
    }

    override fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog =
        dialogBuilder
            .setCustomTitle(null)
            .create()

    override fun onAttach(view: View) {
        super.onAttach(view)
        dialog.window.setBackgroundDrawableResource(android.R.color.transparent)
    }
}