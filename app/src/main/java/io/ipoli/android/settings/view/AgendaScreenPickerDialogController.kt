package io.ipoli.android.settings.view

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import io.ipoli.android.R
import io.ipoli.android.common.view.ReduxDialogController
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.LoadPetDialogAction
import io.ipoli.android.pet.PetDialogReducer
import io.ipoli.android.pet.PetDialogViewState
import io.ipoli.android.player.data.Player
import kotlinx.android.synthetic.main.view_dialog_header.view.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 5/17/18.
 */
class AgendaScreenPickerDialogController(args: Bundle? = null) :
    ReduxDialogController<LoadPetDialogAction, PetDialogViewState, PetDialogReducer>(args) {

    override val reducer = PetDialogReducer

    private lateinit var selectedScreen: Player.Preferences.AgendaScreen

    private var listener: (Player.Preferences.AgendaScreen) -> Unit = {}

    constructor(
        selectedScreen: Player.Preferences.AgendaScreen,
        listener: (Player.Preferences.AgendaScreen) -> Unit
    ) : this() {
        this.selectedScreen = selectedScreen
        this.listener = listener
    }

    @SuppressLint("InflateParams")
    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View =
        inflater.inflate(R.layout.dialog_time_format, null)

    override fun onHeaderViewCreated(headerView: View) {
        headerView.dialogHeaderTitle.setText(R.string.select_agenda_first_view)
    }

    override fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog {
        val views = Player.Preferences.AgendaScreen.values()

        val checked = views.indexOfFirst { it == selectedScreen }

        return dialogBuilder
            .setSingleChoiceItems(
                views.map { it.name.toLowerCase().capitalize() }.toTypedArray(),
                checked
            ) { _, which ->
                selectedScreen = views[which]
            }
            .setPositiveButton(R.string.dialog_ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    override fun onDialogCreated(dialog: AlertDialog, contentView: View) {
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { _ ->
                listener(selectedScreen)
                dismiss()
            }
        }
    }

    override fun onCreateLoadAction() = LoadPetDialogAction

    override fun render(state: PetDialogViewState, view: View) {
        if (state.type == PetDialogViewState.Type.PET_LOADED) {
            changeIcon(AndroidPetAvatar.valueOf(state.petAvatar!!.name).headImage)
        }
    }
}