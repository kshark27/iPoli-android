package io.ipoli.android.store.powerup.buy

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import com.mikepenz.iconics.IconicsDrawable
import io.ipoli.android.R
import io.ipoli.android.common.view.BaseDialogController
import io.ipoli.android.common.view.colorRes
import io.ipoli.android.common.view.navigate
import io.ipoli.android.common.view.stringRes
import io.ipoli.android.store.powerup.AndroidPowerUp
import io.ipoli.android.store.powerup.PowerUp
import kotlinx.android.synthetic.main.dialog_buy_power_up.view.*
import kotlinx.android.synthetic.main.view_dialog_header.view.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 03/20/2018.
 */
class BuyPowerUpDialogController(args: Bundle? = null) : BaseDialogController(args) {

    private var powerUp: PowerUp.Type = PowerUp.Type.CALENDAR_SYNC

    constructor(
        powerUp: PowerUp.Type
    ) : this() {
        this.powerUp = powerUp
    }

    @SuppressLint("InflateParams")
    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_buy_power_up, null)
        val ap = AndroidPowerUp.valueOf(powerUp.name)
        view.title.text = stringRes(R.string.power_up_dialog_title, stringRes(ap.title))
        view.description.setText(ap.longDescription)
        return view
    }

    override fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog =
        dialogBuilder
            .setPositiveButton(R.string.go_premium) { _, _ ->
                dismiss()
                navigate().toMembership()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

    override fun onHeaderViewCreated(headerView: View?) {
        val ap = AndroidPowerUp.valueOf(powerUp.name)

        headerView!!.dialogHeaderTitle.setText(R.string.ready_for_power_up)

        val progressViewEmptyBackground =
            headerView.dialogHeaderIcon.background as GradientDrawable

        progressViewEmptyBackground.setColor(colorRes(ap.backgroundColor))

        headerView.dialogHeaderIcon.setImageDrawable(
            IconicsDrawable(headerView.context)
                .icon(ap.icon)
                .paddingDp(8)
                .colorRes(R.color.md_white)
                .sizeDp(24)
        )
    }
}