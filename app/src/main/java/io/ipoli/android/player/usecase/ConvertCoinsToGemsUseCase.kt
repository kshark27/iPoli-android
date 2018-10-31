package io.ipoli.android.player.usecase

import io.ipoli.android.Constants
import io.ipoli.android.common.UseCase
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.persistence.PlayerRepository

/**
 * Created by vini on 12/21/17.
 */
class ConvertCoinsToGemsUseCase(private val playerRepository: PlayerRepository) :
    UseCase<ConvertCoinsToGemsUseCase.Params, ConvertCoinsToGemsUseCase.Result> {

    override fun execute(parameters: Params): Result {
        val gems = parameters.gems
        val player = playerRepository.find()
        requireNotNull(player)

        val gemsPrice = gems * Constants.GEM_COINS_PRICE

        if (player!!.coins < gemsPrice) {
            return Result.TooExpensive
        }

        val stats = player.statistics

        val newPlayer = player.copy(
            coins = player.coins - gemsPrice,
            gems = player.gems + gems,
            statistics = stats.copy(
                gemConvertedCountForMonth = stats.gemConvertedCountForMonth.addValue(gems.toLong())
            )
        )

        return Result.GemsConverted(playerRepository.save(newPlayer))
    }

    data class Params(val gems: Int)

    sealed class Result {
        data class GemsConverted(val player: Player) : Result()
        object TooExpensive : Result()
    }

}