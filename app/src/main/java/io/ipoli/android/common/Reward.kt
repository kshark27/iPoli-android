package io.ipoli.android.common

import io.ipoli.android.pet.usecase.LowerPlayerStatsUseCase
import io.ipoli.android.player.data.Player
import io.ipoli.android.quest.Quest

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 12/13/17.
 */
data class Reward(
    val attributePoints: Map<Player.AttributeType, Int>,
    val healthPoints: Int,
    val experience: Int,
    val coins: Int,
    val bounty: Quest.Bounty
) {

    companion object {
        val Empty
            get() = Reward(emptyMap(), 0, 0, 0, Quest.Bounty.None)

        val Low
            get() = Reward(emptyMap(), 0, 1, 1, Quest.Bounty.None)

    }

}