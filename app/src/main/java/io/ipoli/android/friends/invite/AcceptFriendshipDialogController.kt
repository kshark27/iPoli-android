package io.ipoli.android.friends.invite

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.common.view.*
import io.ipoli.android.player.data.AndroidAvatar
import io.ipoli.android.player.data.Avatar
import kotlinx.android.synthetic.main.dialog_accept_friendship.*
import kotlinx.android.synthetic.main.dialog_accept_friendship.view.*
import kotlinx.android.synthetic.main.view_loader.view.*

sealed class AcceptFriendshipAction : Action {
    object AlreadyFriends : AcceptFriendshipAction()

    data class Load(val invitePlayerId: String) : AcceptFriendshipAction() {
        override fun toMap() = mapOf("playerId" to invitePlayerId)
    }

    data class Accept(val invitePlayerId: String) : AcceptFriendshipAction() {
        override fun toMap() = mapOf("playerId" to invitePlayerId)
    }
}

object AcceptFriendshipReducer : BaseViewStateReducer<AcceptFriendshipViewState>() {

    override fun reduce(
        state: AppState,
        subState: AcceptFriendshipViewState,
        action: Action
    ) =
        when (action) {

            is AcceptFriendshipAction.Load ->
                subState.copy(
                    invitePlayerId = action.invitePlayerId
                )

            is AcceptFriendshipAction.AlreadyFriends ->
                subState.copy(
                    type = AcceptFriendshipViewState.StateType.ALREADY_FRIENDS
                )

            is DataLoadedAction.AcceptFriendshipDataChanged ->
                subState.copy(
                    type = AcceptFriendshipViewState.StateType.PLAYER_DATA_LOADED,
                    playerAvatar = action.avatar,
                    playerDisplayName = action.displayName,
                    playerUsername = action.username
                )

            else -> subState
        }

    override fun defaultState() =
        AcceptFriendshipViewState(
            type = AcceptFriendshipViewState.StateType.LOADING,
            invitePlayerId = "",
            playerAvatar = Avatar.AVATAR_01,
            playerDisplayName = "",
            playerUsername = ""
        )

    override val stateKey = key<AcceptFriendshipViewState>()
}

data class AcceptFriendshipViewState(
    val type: StateType,
    val invitePlayerId: String,
    val playerAvatar: Avatar,
    val playerDisplayName: String,
    val playerUsername: String
) : BaseViewState() {
    enum class StateType { LOADING, PLAYER_DATA_LOADED, ALREADY_FRIENDS }
}

class AcceptFriendshipDialogController(args: Bundle? = null) :
    ReduxDialogController<AcceptFriendshipAction, AcceptFriendshipViewState, AcceptFriendshipReducer>(
        args
    ) {

    override val reducer = AcceptFriendshipReducer

    private var invitePlayerId: String = ""

    constructor(
        invitePlayerId: String
    ) : this() {
        this.invitePlayerId = invitePlayerId
    }

    @SuppressLint("InflateParams")
    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_accept_friendship, null)
        view.acceptDataGroup.gone()
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
        dialog.acceptClose.setOnClickListener {
            dismiss()
        }
    }

    override fun onCreateLoadAction() = AcceptFriendshipAction.Load(invitePlayerId)

    override fun render(state: AcceptFriendshipViewState, view: View) {
        when (state.type) {

            AcceptFriendshipViewState.StateType.LOADING -> {
                view.acceptDataGroup.gone()
                view.loader.visible()
            }

            AcceptFriendshipViewState.StateType.ALREADY_FRIENDS -> {
                showShortToast(R.string.error_already_friends)
                dismiss()
            }

            AcceptFriendshipViewState.StateType.PLAYER_DATA_LOADED -> {

                view.acceptDataGroup.visible()
                view.loader.gone()

                val avatar = AndroidAvatar.valueOf(state.playerAvatar.name)

                Glide.with(view.context).load(avatar.image)
                    .apply(RequestOptions.circleCropTransform())
                    .into(view.acceptPlayerAvatar)

                val background = view.acceptPlayerAvatar.background as GradientDrawable
                background.setColor(colorRes(avatar.backgroundColor))

                view.acceptPlayerName.text = state.playerDisplayName
                @SuppressLint("SetTextI18n")
                view.acceptPlayerUsername.text = "@${state.playerUsername}"

                view.acceptFriendshipInfo.text =
                    stringRes(R.string.accept_friendship, state.playerDisplayName)

                view.accentFriendship.onDebounceClick {
                    dispatch(AcceptFriendshipAction.Accept(invitePlayerId))
                    showShortToast(stringRes(R.string.friend_accepted, state.playerDisplayName))
                    dismiss()
                }
            }
        }
    }

}