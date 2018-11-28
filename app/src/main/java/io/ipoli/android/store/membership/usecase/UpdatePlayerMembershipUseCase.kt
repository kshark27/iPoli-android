package io.ipoli.android.store.membership.usecase

import io.ipoli.android.common.UseCase
import io.ipoli.android.player.data.Membership
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.persistence.PlayerRepository
import io.ipoli.android.store.membership.MembershipPlan
import org.threeten.bp.LocalDate

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 3/22/18.
 */
class UpdatePlayerMembershipUseCase(
    private val playerRepository: PlayerRepository
) : UseCase<UpdatePlayerMembershipUseCase.Params, Player> {

    override fun execute(parameters: Params): Player {

        val membership = when (parameters.plan) {
            MembershipPlan.MONTHLY ->
                Membership.MONTHLY
            MembershipPlan.YEARLY ->
                Membership.YEARLY
            MembershipPlan.QUARTERLY ->
                Membership.QUARTERLY
        }

        return playerRepository.save(
            playerRepository.find()!!.copy(
                membership = membership
            )
        )
    }

    data class Params(
        val plan: MembershipPlan,
        val purchasedDate: LocalDate = LocalDate.now(),
        val expirationDate: LocalDate? = null
    )
}