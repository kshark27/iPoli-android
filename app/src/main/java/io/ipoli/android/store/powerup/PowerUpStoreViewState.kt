package io.ipoli.android.store.powerup

import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.player.data.Player

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 03/15/2018.
 */

sealed class PowerUpStoreAction : Action {
    object Load : PowerUpStoreAction()
}

object PowerUpStoreReducer : BaseViewStateReducer<PowerUpStoreViewState>() {

    override val stateKey = key<PowerUpStoreViewState>()

    override fun reduce(
        state: AppState,
        subState: PowerUpStoreViewState,
        action: Action
    ) =
        when (action) {

            PowerUpStoreAction.Load ->
                state.dataState.player?.let {
                    subState.copy(
                        type = PowerUpStoreViewState.StateType.DATA_CHANGED,
                        powerUps = createPowerUps(it)
                    )
                } ?: subState.copy(type = PowerUpStoreViewState.StateType.LOADING)

            is DataLoadedAction.PlayerChanged ->
                subState.copy(
                    type = PowerUpStoreViewState.StateType.DATA_CHANGED,
                    powerUps = createPowerUps(action.player)
                )

            else -> subState
        }

    private fun createPowerUps(player: Player) =
        PowerUp.Type.values()
            .map {
                when {
                    player.isMember -> {
                        PowerUpItem.Enabled(type = it)
                    }
                    else -> PowerUpItem.Disabled(
                        type = it,
                        coinPrice = it.coinPrice
                    )
                }
            }

    override fun defaultState() = PowerUpStoreViewState(
        type = PowerUpStoreViewState.StateType.LOADING,
        powerUp = PowerUp.Type.GROWTH,
        powerUps = emptyList()
    )
}

sealed class PowerUpItem {
    data class Enabled(
        val type: PowerUp.Type
    ) : PowerUpItem()

    data class Disabled(val type: PowerUp.Type, val coinPrice: Int) : PowerUpItem()
}

data class PowerUpStoreViewState(
    val type: StateType,
    val powerUp: PowerUp.Type,
    val powerUps: List<PowerUpItem>
) : BaseViewState() {

    enum class StateType {
        LOADING, DATA_CHANGED
    }
}