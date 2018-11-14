package io.ipoli.android.player.auth.saga

import android.annotation.SuppressLint
import android.arch.persistence.room.Transaction
import com.google.firebase.auth.*
import io.ipoli.android.Constants
import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.LoadDataAction
import io.ipoli.android.common.redux.Action
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.habit.predefined.PredefinedHabit
import io.ipoli.android.onboarding.OnboardData
import io.ipoli.android.pet.Pet
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.player.auth.AuthAction
import io.ipoli.android.player.auth.AuthViewState
import io.ipoli.android.player.auth.UsernameValidator
import io.ipoli.android.player.data.AuthProvider
import io.ipoli.android.player.data.Avatar
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.data.Player.AttributeType.*
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import io.ipoli.android.quest.RepeatingQuest
import io.ipoli.android.repeatingquest.usecase.SaveRepeatingQuestUseCase
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
    private val habitRepository by required { habitRepository }
    private val sharedPreferences by required { sharedPreferences }
    private val removeExpiredPowerUpsScheduler by required { removeExpiredPowerUpsScheduler }
    private val checkMembershipStatusScheduler by required { checkMembershipStatusScheduler }
    private val planDayScheduler by required { planDayScheduler }
    private val updateAchievementProgressScheduler by required { updateAchievementProgressScheduler }
    private val resetDayScheduler by required { resetDayScheduler }
    private val resetDateScheduler by required { resetDateScheduler }
    private val saveRepeatingQuestUseCase by required { saveRepeatingQuestUseCase }
    private val dataImporter by required { dataImporter }
    private val dataExporter by required { dataExporter }

    override fun canHandle(action: Action) = action is AuthAction

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
                dispatch(AuthAction.Loaded(hasPlayer, isGuest, hasUsername, action.onboardData))
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
                        createNewPlayer(user, state.stateFor(AuthViewState::class.java))

                    else -> loginExistingPlayer()
                }
            }

            is AuthAction.ContinueAsGuest -> {
                createGuestPlayer(state.stateFor(AuthViewState::class.java))
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
                            username = action.username,
                            avatar = action.avatar
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
        user: FirebaseUser,
        state: AuthViewState
    ) {

        val displayName = if (user.displayName != null) user.displayName!! else ""

        saveNewPlayerData(state, user.uid, createAuthProvider(user), displayName)
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

        val auth = when {

            authProvider.providerId == FacebookAuthProvider.PROVIDER_ID ->
                createFacebookAuthProvider(authProvider, user)

            authProvider.providerId == GoogleAuthProvider.PROVIDER_ID ->
                createGoogleAuthProvider(authProvider, user)

            else -> throw IllegalStateException("Unknown Auth provider")
        }
        return auth
    }

    private fun createGuestPlayer(
        state: AuthViewState
    ) {
        saveNewPlayerData(state, UUID.randomUUID().toString(), null, "")
        prepareAppStart()
        dispatch(AuthAction.GuestCreated)
    }

    private fun saveNewPlayerData(
        state: AuthViewState,
        playerId: String,
        auth: AuthProvider?,
        displayName: String
    ) {
        val petAvatar = state.petAvatar ?: Constants.DEFAULT_PET_AVATAR
        val petName =
            if (state.petName.isNullOrBlank()) Constants.DEFAULT_PET_NAME else state.petName!!

        val tags =
            savePlayerWithTags(playerId, auth, displayName, petName, petAvatar, state.playerAvatar)
        saveRepeatingQuests(state.repeatingQuests, tags)
        saveHabits(state.habits, tags)
    }

    private fun savePlayerWithTags(
        playerId: String,
        auth: AuthProvider?,
        displayName: String,
        petName: String,
        petAvatar: PetAvatar,
        playerAvatar: Avatar
    ): List<Tag> {
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

        return saveDefaultTags(newPlayer)
    }

    private fun saveRepeatingQuests(
        repeatingQuests: Set<Pair<RepeatingQuest, OnboardData.Tag?>>,
        tags: List<Tag>
    ) {
        repeatingQuests.forEach {
            val rq = it.first
            val ts = it.second?.let { onboardTag ->
                listOf(tags.first { t -> t.name.toUpperCase() == onboardTag.name })
            } ?: listOf()
            saveRepeatingQuestUseCase.execute(
                SaveRepeatingQuestUseCase.Params(
                    name = rq.name,
                    subQuestNames = rq.subQuests.map { sq -> sq.name },
                    color = rq.color,
                    icon = rq.icon,
                    tags = ts,
                    startTime = rq.startTime,
                    duration = rq.duration,
                    reminders = rq.reminders,
                    repeatPattern = rq.repeatPattern
                )
            )
        }
    }

    private fun saveHabits(
        habits: Set<Pair<PredefinedHabit, OnboardData.Tag?>>,
        tags: List<Tag>
    ) {
        habits.forEach {
            val h = it.first
            val ts = it.second?.let { onboardTag ->
                listOf(tags.first { t -> t.name.toUpperCase() == onboardTag.name })
            } ?: listOf()

            habitRepository.save(
                Habit(
                    name = h.name,
                    color = h.color,
                    icon = h.icon,
                    tags = ts,
                    isGood = h.isGood,
                    timesADay = h.timesADay,
                    days = h.days,
                    streak = Habit.Streak(0, 0),
                    preferenceHistory = Habit.PreferenceHistory(
                        days = sortedMapOf(LocalDate.now() to h.days),
                        timesADay = sortedMapOf(LocalDate.now() to h.timesADay)
                    )
                )
            )
        }
    }

    private fun saveDefaultTags(player: Player): List<Tag> {
        val tags = tagRepository.save(
            listOf(
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
                )
            )
        )

        val personalTag = tags.first { it.name == "Personal" }
        val workTag = tags.first { it.name == "Work" }
        val wellnessTag = tags.first { it.name == "Wellness" }

        playerRepository.save(
            player
                .addTagToAttribute(STRENGTH, wellnessTag)
                .addTagToAttribute(INTELLIGENCE, workTag)
                .addTagToAttribute(CHARISMA, personalTag)
                .addTagToAttribute(EXPERTISE, workTag)
                .addTagToAttribute(WELL_BEING, wellnessTag)
                .addTagToAttribute(WELL_BEING, personalTag)
                .addTagToAttribute(WILLPOWER, workTag)
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
                savePlayerWithTags(
                    user.uid,
                    createAuthProvider(user),
                    displayName,
                    Constants.DEFAULT_PET_NAME,
                    Constants.DEFAULT_PET_AVATAR,
                    Avatar.AVATAR_00
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
        removeExpiredPowerUpsScheduler.schedule()
        checkMembershipStatusScheduler.schedule()
        planDayScheduler.schedule()
        updateAchievementProgressScheduler.schedule()
        resetDayScheduler.schedule()
        resetDateScheduler.schedule()
    }
}