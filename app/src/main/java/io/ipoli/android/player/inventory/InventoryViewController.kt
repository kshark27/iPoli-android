package io.ipoli.android.player.inventory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction

import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.BaseViewState
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.gone
import io.ipoli.android.common.view.inflate
import kotlinx.android.synthetic.main.controller_inventory.view.*
import java.util.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 01/28/2018.
 */

data class InventoryViewState(val type: StateType, val gems: Int, val coins: Int) :
    BaseViewState() {

    enum class StateType { LOADING, DATA_CHANGED }
}

class InventoryReducer(someKey: String) : BaseViewStateReducer<InventoryViewState>() {

    override val stateKey = key<InventoryViewState>() + someKey

    override fun reduce(
        state: AppState,
        subState: InventoryViewState,
        action: Action
    ) =
        when (action) {
            LoadInventory -> {
                val p = state.dataState.player
                p?.let {
                    subState.copy(
                        type = InventoryViewState.StateType.DATA_CHANGED,
                        gems = it.gems,
                        coins = it.coins
                    )
                } ?: subState.copy(
                    type = InventoryViewState.StateType.LOADING
                )
            }

            is DataLoadedAction.PlayerChanged -> {
                val p = action.player
                subState.copy(
                    type = InventoryViewState.StateType.DATA_CHANGED,
                    gems = p.gems,
                    coins = p.coins
                )
            }

            else -> subState
        }

    override fun defaultState() =
        InventoryViewState(
            type = InventoryViewState.StateType.LOADING,
            gems = -1,
            coins = -1
        )
}

object LoadInventory : Action

class InventoryViewController :
    ReduxViewController<LoadInventory, InventoryViewState, InventoryReducer> {

    private var showCurrencyConverter: Boolean = true
    private var showGems: Boolean = true
    private var showCoins: Boolean = false

    override val reducer = InventoryReducer(UUID.randomUUID().toString())

    constructor(args: Bundle? = null) : super(args)

    constructor(
        showCurrencyConverter: Boolean,
        showCoins: Boolean = false,
        showGems: Boolean = true
    ) : super() {
        this.showCurrencyConverter = showCurrencyConverter
        this.showGems = showGems
        this.showCoins = showCoins
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        applyStatusBarColors = false
        val view = container.inflate(R.layout.controller_inventory)

        if (!showGems) {
            view.inventoryGems.gone()
        }

        if (!showCoins) {
            view.inventoryCoins.gone()
        }

        if (!(showCoins && showGems)) {
            view.inventorySpace.gone()
        }

        if (showCurrencyConverter) {
            view.onDebounceClick {
                navigateFromRoot().toCurrencyConverter()
            }
        } else {
            view.inventoryGems.background = null
            view.setOnClickListener(null)
        }
        return view
    }

    override fun onCreateLoadAction() = LoadInventory

    override fun render(state: InventoryViewState, view: View) {
        when (state.type) {
            InventoryViewState.StateType.DATA_CHANGED -> {
                view.inventoryGems.text = state.gems.toString()
                view.inventoryCoins.text = state.coins.toString()
            }

            else -> {
            }
        }
    }
}