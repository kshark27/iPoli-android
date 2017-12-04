package io.ipoli.android

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import io.ipoli.android.pet.Pet
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.player.AuthProvider
import io.ipoli.android.player.Player
import io.ipoli.android.player.persistence.PlayerRepository

/**
 * Created by Venelin Valkov <venelin@ipoli.io>
 * on 12/1/17.
 */
object TestUtil {
    fun player() = Player(
        level = 1,
        coins = 10,
        experience = 10,
        authProvider = AuthProvider(),
        pet = Pet(
            "",
            avatar = PetAvatar.ELEPHANT,
            healthPoints = 30,
            moodPoints = Pet.AWESOME_MIN_MOOD_POINTS - 1
        )
    )

    fun playerRepoMock(player: Player) = mock<PlayerRepository> {
        on { find() } doReturn player
        on { save(any()) } doAnswer { invocation ->
            invocation.getArgument(0)
        }
    }
}