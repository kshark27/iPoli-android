package io.ipoli.android.player.usecase

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import io.ipoli.android.Constants
import io.ipoli.android.TestUtil
import io.ipoli.android.pet.Food
import io.ipoli.android.player.ExperienceForLevelGenerator
import io.ipoli.android.player.LevelUpScheduler
import io.ipoli.android.player.attribute.usecase.CheckForOneTimeBoostUseCase
import io.ipoli.android.player.data.Inventory
import io.ipoli.android.player.data.Player
import org.amshove.kluent.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import kotlin.text.Typography.less

/**
 * Created by Venelin Valkov <venelin@io.ipoli.io>
 * on 28.11.17.
 */
class RewardPlayerUseCaseSpek : Spek({

    describe("RewardPlayerUseCase") {

        val levelUpScheduler = mock<LevelUpScheduler>()

        val player = TestUtil.player
        val pet = player.pet

        fun executeUseCase(
            params: RewardPlayerUseCase.Params,
            seed: Long = Constants.RANDOM_SEED.toLong()
        ) =
            RewardPlayerUseCase(
                playerRepository = TestUtil.playerRepoMock(player),
                levelUpScheduler = levelUpScheduler,
                unlockAchievementsUseCase = mock(),
                checkForOneTimeBoostUseCase = CheckForOneTimeBoostUseCase(mock()),
                removeRewardFromPlayerUseCase = mock(),
                randomSeed = seed
            ).execute(
                params
            ).player

        beforeEachTest {
            reset(levelUpScheduler)
        }

        it("should give experience and coins") {
            val xp = 3
            val coins = 4
            val newPlayer =
                executeUseCase(RewardPlayerUseCase.Params.ForQuest(TestUtil.quest, player))
            newPlayer.experience.`should be`(player.experience + xp)
            newPlayer.coins.`should be`(player.coins + coins)
        }

        it("should give attribute points") {
            val t = TestUtil.tag
            val p = TestUtil.player.addTagToAttribute(Player.AttributeType.STRENGTH, t)
            val q = TestUtil.quest.copy(tags = listOf(t))

            val newPlayer = executeUseCase(RewardPlayerUseCase.Params.ForQuest(q, p))
            newPlayer.attributes[Player.AttributeType.STRENGTH]!!.points.`should be greater than`(0)
        }

        it("should gain new level") {
            val p = player.copy(
                experience = ExperienceForLevelGenerator.forLevel(player.level + 1) - 1
            )

            val newPlayer = executeUseCase(RewardPlayerUseCase.Params.ForQuest(TestUtil.quest, p))
            newPlayer.level.`should be`(player.level + 1)
            Verify on levelUpScheduler that levelUpScheduler.schedule(newPlayer.level) was called
        }

        it("should give reward to the Pet") {
            val newPet =
                executeUseCase(RewardPlayerUseCase.Params.ForQuest(TestUtil.quest, player)).pet
            newPet.healthPoints.`should not be less than`(pet.healthPoints)
        }

        it("should not add bounty to Inventory") {
            val p = player.copy(
                inventory = Inventory()
            )
            val newPlayer =
                executeUseCase(RewardPlayerUseCase.Params.ForQuest(TestUtil.quest, p))
            newPlayer.inventory.food.`should be`(p.inventory.food)
        }

        it("should add bounty to Inventory") {
            val p = player.copy(
                inventory = Inventory(),
                pet = player.pet.copy(
                    itemDropBonus = 10000f
                )
            )
            val newPlayer =
                executeUseCase(
                    params = RewardPlayerUseCase.Params.ForQuest(TestUtil.quest, p),
                    seed = 42L
                )
            newPlayer.inventory.`should equal`(
                Inventory(
                    food = mapOf(Food.BANANA to 1)
                )
            )
        }
    }
})