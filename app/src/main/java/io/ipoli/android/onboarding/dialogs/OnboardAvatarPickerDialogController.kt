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
import android.widget.ImageView
import io.ipoli.android.R
import io.ipoli.android.common.view.BaseDialogController
import io.ipoli.android.common.view.colorRes
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.player.data.AndroidAvatar
import io.ipoli.android.player.data.Avatar
import kotlinx.android.synthetic.main.dialog_onboard_pick_avatar.view.*
import kotlinx.android.synthetic.main.view_dialog_header.view.*

class OnboardAvatarPickerDialogController(args: Bundle? = null) : BaseDialogController(args) {

    private var listener: (Avatar) -> Unit = {}

    constructor(listener: (Avatar) -> Unit) : this() {
        this.listener = listener
    }

    override fun onHeaderViewCreated(headerView: View?) {
        headerView!!.dialogHeaderTitle.setText(R.string.choose_avatar)
        headerView.dialogHeaderIcon.setImageResource(R.drawable.logo)
        val background = headerView.dialogHeaderIcon.background as GradientDrawable
        background.setColor(colorRes(R.color.md_light_text_100))
    }

    @SuppressLint("InflateParams")
    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_onboard_pick_avatar, null)
        view.avatarList.layoutManager = GridLayoutManager(view.context, 2)
        view.avatarList.setHasFixedSize(true)
        val adapter = AvatarAdapter()
        view.avatarList.adapter = adapter

        val avatars = listOf(
            Avatar.AVATAR_03,
            Avatar.AVATAR_11,
            Avatar.AVATAR_04,
            Avatar.AVATAR_07,
            Avatar.AVATAR_05,
            Avatar.AVATAR_06,
            Avatar.AVATAR_02,
            Avatar.AVATAR_01
        )

        adapter.updateAll(
            avatars.map {
                val androidAvatar = AndroidAvatar.valueOf(it.name)
                AvatarViewModel(androidAvatar.image, androidAvatar.backgroundColor, it)
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
            .setNeutralButton(R.string.no_one) { _, _ ->
                listener(Avatar.AVATAR_00)
            }
            .create()

    data class AvatarViewModel(
        @DrawableRes val image: Int,
        @ColorRes val backgroundColor: Int,
        val avatar: Avatar
    ) : RecyclerViewViewModel {
        override val id: String
            get() = avatar.name
    }

    inner class AvatarAdapter :
        BaseRecyclerViewAdapter<AvatarViewModel>(R.layout.item_onboard_avatar) {

        override fun onBindViewModel(vm: AvatarViewModel, view: View, holder: SimpleViewHolder) {
            val image = view as ImageView
            image.setBackgroundResource(vm.backgroundColor)
            image.setImageResource(vm.image)
            image.setOnClickListener {
                listener(vm.avatar)
                dismiss()
            }
        }

    }
}