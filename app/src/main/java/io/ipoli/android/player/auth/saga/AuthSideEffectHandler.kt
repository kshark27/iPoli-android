package io.ipoli.android.player.auth.saga

import android.annotation.SuppressLint
import android.arch.persistence.room.Transaction
import com.google.firebase.auth.*
import io.ipoli.android.Constants
import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.LoadDataAction
import io.ipoli.android.common.redux.Action
import io.ipoli.android.onboarding.OnboardAction
import io.ipoli.android.pet.Pet
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.player.auth.AuthAction
import io.ipoli.android.player.auth.UsernameValidator
import io.ipoli.android.player.data.AuthProvider
import io.ipoli.android.player.data.Avatar
import io.ipoli.android.player.data.Player
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import io.ipoli.android.quest.Quest
import io.ipoli.android.tag.Tag
import org.threeten.bp.LocalDate
import space.traversal.kapsule.required
import java.util.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 02/07/2018.
 */
object AuthSideEffectHandler : AppSideEffectHandler() {

    private val eventLogger by required { eventLogger }
    private val playerRepository by required { playerRepository }
    private val tagRepository by required { tagRepository }
    private val questRepository by required { questRepository }
    private val sharedPreferences by required { sharedPreferences }
    private val checkMembershipStatusScheduler by required { checkMembershipStatusScheduler }
    private val planDayScheduler by required { planDayScheduler }
    private val updateAchievementProgressScheduler by required { updateAchievementProgressScheduler }
    private val resetDayScheduler by required { resetDayScheduler }
    private val resetDateScheduler by required { resetDateScheduler }
    private val dataImporter by required { dataImporter }
    private val dataExporter by required { dataExporter }

    override fun canHandle(action: Action) =
        action is AuthAction || action is OnboardAction.CreateGuestPlayer

    override suspend fun doExecute(action: Action, state: AppState) {

        when (action) {
            is AuthAction.Load -> {
                val hasPlayer = playerRepository.hasPlayer()
                var isGuest = false
                var hasUsername = false
                if (hasPlayer) {
                    val player = playerRepository.find()
                    isGuest = player!!.authProvider == null
                    hasUsername = !player.username.isNullOrEmpty()
                }
                dispatch(AuthAction.Loaded(hasPlayer, isGuest, hasUsername))
            }

            is AuthAction.UserAuthenticated -> {
                val user = action.user

                val currentPlayerId = sharedPreferences.getString(Constants.KEY_PLAYER_ID, null)
                val isCurrentlyGuest = currentPlayerId != null
                when {
                    !action.isNew && isCurrentlyGuest ->
                        loginExistingPlayerFromGuest()

                    action.isNew && isCurrentlyGuest ->
                        loginNewPlayerFromGuest(user)

                    action.isNew && !isCurrentlyGuest ->
                        createNewPlayer(user)

                    else -> loginExistingPlayer()
                }
            }

            is AuthAction.CompleteSetup -> {

                val username = action.username

                val usernameValidationError =
                    UsernameValidator(playerRepository).validate(username)

                if (usernameValidationError != null) {
                    dispatch(
                        AuthAction.UsernameValidationFailed(
                            usernameValidationError
                        )
                    )
                } else {

                    val player = playerRepository.find()!!
                    playerRepository.save(
                        player.copy(
                            username = action.username
                        )
                    )
                    playerRepository.addUsername(action.username)
                    prepareAppStart()
                    dispatch(AuthAction.PlayerSetupCompleted)
                    dataExporter.export()
                }
            }

            is AuthAction.ValidateUsername -> {
                val username = action.username

                val usernameValidationError =
                    UsernameValidator(playerRepository).validate(username)
                if (usernameValidationError != null) {
                    dispatch(
                        AuthAction.UsernameValidationFailed(
                            usernameValidationError
                        )
                    )
                } else {
                    dispatch(AuthAction.UsernameValid)
                }
            }

            is OnboardAction.CreateGuestPlayer ->
                createGuestPlayer(action.playerAvatar, action.petAvatar, action.petName)
        }
    }

    private fun loginNewPlayerFromGuest(user: FirebaseUser) {
        updatePlayerAuthProvider(user)
        dispatch(AuthAction.NewPlayerLoggedInFromGuest)
        dataExporter.export()
    }

    private fun loginExistingPlayerFromGuest() {
        try {
            dataImporter.import()
        } catch (e: Throwable) {
            dispatch(AuthAction.ShowImportDataError)
            return
        }
        dispatch(AuthAction.ExistingPlayerLoggedInFromGuest)
    }

    private fun updatePlayerAuthProvider(
        user: FirebaseUser
    ) {
        val authProviders =
            user.providerData.filter { it.providerId != FirebaseAuthProvider.PROVIDER_ID }
        require(authProviders.size == 1)
        val authProvider = authProviders.first()

        val auth = when {
            authProvider.providerId == FacebookAuthProvider.PROVIDER_ID ->
                createFacebookAuthProvider(
                    authProvider,
                    user
                )
            authProvider.providerId == GoogleAuthProvider.PROVIDER_ID ->
                createGoogleAuthProvider(
                    authProvider,
                    user
                )
            else -> throw IllegalStateException("Unknown Auth provider")
        }

        val player = playerRepository.find()!!
        replacePlayer(
            player.copy(
                id = user.uid,
                authProvider = auth
            )
        )
        savePlayerId(user.uid)
    }

    @Transaction
    private fun replacePlayer(newPlayer: Player) {
        playerRepository.delete()
        playerRepository.save(newPlayer)
    }

    private fun createGoogleAuthProvider(
        authProvider: UserInfo,
        user: FirebaseUser
    ) =
        AuthProvider.Google(
            userId = authProvider.uid,
            displayName = user.displayName,
            email = user.email,
            imageUrl = user.photoUrl
        )

    private fun createFacebookAuthProvider(
        authProvider: UserInfo,
        user: FirebaseUser
    ) =
        AuthProvider.Facebook(
            userId = authProvider.uid,
            displayName = user.displayName,
            email = user.email,
            imageUrl = user.photoUrl
        )

    private fun createNewPlayer(
        user: FirebaseUser
    ) {
        val displayName = if (user.displayName != null) user.displayName!! else ""

        saveNewPlayerData(
            playerId = user.uid,
            auth = createAuthProvider(user),
            displayName = displayName,
            playerAvatar = Avatar.AVATAR_00,
            petAvatar = Constants.DEFAULT_PET_AVATAR,
            petName = Constants.DEFAULT_PET_NAME
        )
        dispatch(AuthAction.ShowSetUp)
    }

    private fun createAuthProvider(user: FirebaseUser): AuthProvider {
        val authProvider = if (user.providerData.size == 1) {
            user.providerData.first()
        } else {
            val authProviders =
                user.providerData.filter { it.providerId != FirebaseAuthProvider.PROVIDER_ID }
            require(authProviders.size == 1)
            authProviders.first()
        }

        return when (authProvider.providerId) {
            FacebookAuthProvider.PROVIDER_ID ->
                createFacebookAuthProvider(authProvider, user)

            GoogleAuthProvider.PROVIDER_ID ->
                createGoogleAuthProvider(authProvider, user)

            else -> throw IllegalStateException("Unknown Auth provider")
        }
    }

    private fun createGuestPlayer(
        playerAvatar: Avatar,
        petAvatar: PetAvatar,
        petName: String
    ) {
        saveNewPlayerData(
            playerId = UUID.randomUUID().toString(),
            auth = null,
            displayName = "",
            playerAvatar = playerAvatar,
            petAvatar = petAvatar,
            petName = petName
        )
        prepareAppStart()
        dispatch(AuthAction.GuestCreated)
    }

    private fun saveNewPlayerData(
        playerId: String,
        auth: AuthProvider?,
        displayName: String,
        playerAvatar: Avatar,
        petAvatar: PetAvatar,
        petName: String
    ) {
        val player = Player(
            id = playerId,
            authProvider = auth,
            username = null,
            bio = null,
            displayName = displayName,
            schemaVersion = Constants.SCHEMA_VERSION,
            pet = Pet(petName, petAvatar),
            avatar = playerAvatar,
            rank = Player.Rank.NOVICE,
            nextRank = Player.Rank.APPRENTICE
        )
        val newPlayer = playerRepository.save(player)
        savePlayerId(playerId)

        val tags = saveDefaultTags(newPlayer)

        val learningTag = tags.first { it.name == "Learning" }

        questRepository.save(
            listOf(
                Quest(
                    name = "Swipe to -> to complete me",
                    color = Color.GREEN,
                    icon = Icon.BOOK,
                    duration = 10,
                    scheduledDate = LocalDate.now(),
                    tags = listOf(learningTag)
                ),
                Quest(
                    name = "Swipe to <- to reschedule me",
                    color = Color.BLUE,
                    icon = Icon.BOOK,
                    duration = 10,
                    scheduledDate = LocalDate.now(),
                    tags = listOf(learningTag)
                )
            )
        )
    }

    private fun saveDefaultTags(player: Player): List<Tag> {

        val tags = tagRepository.save(
            listOf(
                Tag(
                    name = "Learning",
                    color = Color.BLUE,
                    icon = Icon.BOOK,
                    isFavorite = true
                ),
                Tag(
                    name = "Personal",
                    color = Color.ORANGE,
                    icon = Icon.DUCK,
                    isFavorite = true
                ),
                Tag(
                    name = "Work",
                    color = Color.RED,
                    icon = Icon.BRIEFCASE,
                    isFavorite = true
                ),
                Tag(
                    name = "Wellness",
                    color = Color.GREEN,
                    icon = Icon.FLOWER,
                    isFavorite = true
                ),
                Tag(
                    name = "Fun",
                    color = Color.PURPLE,
                    icon = Icon.GAME_CONTROLLER,
                    isFavorite = true
                ),
                Tag(
                    name = "Chores",
                    color = Color.BLUE_GREY,
                    icon = Icon.BROOM,
                    isFavorite = true
                )
            )
        )

        val wellness = tags.first { it.name == "Wellness" }
        val work = tags.first { it.name == "Work" }
        val funTag = tags.first { it.name == "Fun" }
        val learning = tags.first { it.name == "Learning" }
        val chores = tags.first { it.name == "Chores" }
        val personal = tags.first { it.name == "Personal" }

        playerRepository.save(
            player
                .addTagToAttribute(Player.AttributeType.STRENGTH, wellness)
                .addTagToAttribute(Player.AttributeType.INTELLIGENCE, work)
                .addTagToAttribute(Player.AttributeType.INTELLIGENCE, learning)
                .addTagToAttribute(Player.AttributeType.INTELLIGENCE, funTag)
                .addTagToAttribute(Player.AttributeType.CHARISMA, personal)
                .addTagToAttribute(Player.AttributeType.EXPERTISE, work)
                .addTagToAttribute(Player.AttributeType.EXPERTISE, learning)
                .addTagToAttribute(Player.AttributeType.WELL_BEING, wellness)
                .addTagToAttribute(Player.AttributeType.WELL_BEING, personal)
                .addTagToAttribute(Player.AttributeType.WILLPOWER, learning)
                .addTagToAttribute(Player.AttributeType.WILLPOWER, work)
                .addTagToAttribute(Player.AttributeType.WILLPOWER, chores)
        )

        return tags
    }

    private fun loginExistingPlayer() {
        try {
            dataImporter.import()
        } catch (e: Throwable) {
            dispatch(AuthAction.ShowImportDataError)
            return
        }

        val p = playerRepository.find()

        when {
            p == null -> {
                val user = FirebaseAuth.getInstance().currentUser!!

                val displayName = if (user.displayName != null) user.displayName!! else ""
                saveNewPlayerData(
                    playerId = user.uid,
                    auth = createAuthProvider(user),
                    displayName = displayName,
                    playerAvatar = Avatar.AVATAR_00,
                    petAvatar = Constants.DEFAULT_PET_AVATAR,
                    petName = Constants.DEFAULT_PET_NAME
                )
                dispatch(AuthAction.ShowSetUp)
            }
            p.username.isNullOrBlank() -> dispatch(AuthAction.ShowSetUp)
            else -> {
                dispatch(AuthAction.ExistingPlayerLoggedIn)
                prepareAppStart()
            }
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun savePlayerId(playerId: String) {
        eventLogger.setPlayerId(playerId)
        sharedPreferences.edit().putString(Constants.KEY_PLAYER_ID, playerId).commit()
    }

    private fun prepareAppStart() {
        dispatch(LoadDataAction.All)
        checkMembershipStatusScheduler.schedule()
        planDayScheduler.schedule()
        updateAchievementProgressScheduler.schedule()
        resetDayScheduler.schedule()
        resetDateScheduler.schedule()
    }
}