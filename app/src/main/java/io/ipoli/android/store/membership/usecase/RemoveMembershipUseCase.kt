package io.ipoli.android.store.membership.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.player.data.Membership
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.persistence.PlayerRepository

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 03/24/2018.
 */
class RemoveMembershipUseCase(private val playerRepository: PlayerRepository) :
    UseCase<Unit, Player> {

    override fun execute(parameters: Unit): Player {
        val p = playerRepository.find()
        requireNotNull(p)

        if (p!!.membership == Membership.NONE) {
            return p
        }

        return playerRepository.save(
            p.copy(
                membership = Membership.NONE
            )
        )
    }

}