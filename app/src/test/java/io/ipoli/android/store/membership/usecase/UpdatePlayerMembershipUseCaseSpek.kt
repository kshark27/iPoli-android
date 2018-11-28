package io.ipoli.android.store.membership.usecase

import com.nhaarman.mockito_kotlin.mock
import io.ipoli.android.TestUtil
import io.ipoli.android.player.data.Membership
import io.ipoli.android.player.data.Player
import io.ipoli.android.store.membership.MembershipPlan
import io.ipoli.android.store.powerup.PowerUp
import org.amshove.kluent.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.threeten.bp.LocalDate
import org.threeten.bp.temporal.TemporalAdjusters

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 3/22/18.
 */
class UpdatePlayerMembershipUseCaseSpek : Spek({

    describe("UpdatePlayerMembershipUseCase") {
        fun createEnableAllPowerUpsUseCase(player: Player) =
            mock<EnableAllPowerUpsUseCase> {
                on { execute(any()) } doReturn player
            }

        fun executeUseCase(
            player: Player,
            plan: MembershipPlan,
            purchaseDate: LocalDate = LocalDate.now(),
            expirationDate: LocalDate? = null,
            enableAllPowerUpsUseCase: EnableAllPowerUpsUseCase = createEnableAllPowerUpsUseCase(
                player
            )
        ): Player {
            return UpdatePlayerMembershipUseCase(
                TestUtil.playerRepoMock(player),
                enableAllPowerUpsUseCase
            ).execute(
                UpdatePlayerMembershipUseCase.Params(
                    plan,
                    purchaseDate,
                    expirationDate
                )
            )
        }

        it("should change membership") {
            val result = executeUseCase(
                TestUtil.player.copy(membership = Membership.NONE),
                MembershipPlan.YEARLY
            )
            result.membership.`should be`(Membership.YEARLY)
        }

        it("should compute expiration date") {
            val purchaseDate = LocalDate.now().with(TemporalAdjusters.firstDayOfYear())
            val player = TestUtil.player.copy(
                membership = Membership.NONE,
                inventory = TestUtil.player.inventory
                    .setPowerUps(
                        listOf(
                            PowerUp.fromType(
                                PowerUp.Type.TIMER,
                                LocalDate.now()
                            )
                        )
                    )
            )
            val enableAllPowerUpsUseCase = createEnableAllPowerUpsUseCase(player)
            executeUseCase(
                player,
                MembershipPlan.YEARLY,
                purchaseDate,
                null,
                enableAllPowerUpsUseCase
            )

            Verify on enableAllPowerUpsUseCase that enableAllPowerUpsUseCase.execute(
                EnableAllPowerUpsUseCase.Params(purchaseDate.with(TemporalAdjusters.lastDayOfYear()))
            ) was called
        }

        it("should use expiration date from parameters") {
            val purchaseDate = LocalDate.now().with(TemporalAdjusters.firstDayOfYear())
            val player = TestUtil.player.copy(
                membership = Membership.NONE,
                inventory = TestUtil.player.inventory
                    .setPowerUps(
                        listOf(
                            PowerUp.fromType(
                                PowerUp.Type.TIMER,
                                LocalDate.now()
                            )
                        )
                    )
            )
            val enableAllPowerUpsUseCase = createEnableAllPowerUpsUseCase(player)
            val expirationDate = purchaseDate.plusYears(1)
            executeUseCase(
                player,
                MembershipPlan.YEARLY,
                purchaseDate,
                expirationDate,
                enableAllPowerUpsUseCase
            )

            Verify on enableAllPowerUpsUseCase that enableAllPowerUpsUseCase.execute(
                EnableAllPowerUpsUseCase.Params(expirationDate)
            ) was called
        }
    }

})