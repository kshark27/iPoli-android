package io.ipoli.android.common.migration

import android.annotation.SuppressLint
import android.arch.persistence.room.Transaction
import android.content.Context
import android.support.annotation.WorkerThread
import com.crashlytics.android.Crashlytics
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Source
import io.ipoli.android.BuildConfig
import io.ipoli.android.Constants
import io.ipoli.android.MyPoliApp
import io.ipoli.android.challenge.persistence.FirestoreChallengeRepository
import io.ipoli.android.common.di.BackgroundModule
import io.ipoli.android.dailychallenge.data.persistence.FirestoreDailyChallengeRepository
import io.ipoli.android.habit.persistence.FirestoreHabitRepository
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.persistence.FirestorePlayerRepository
import io.ipoli.android.quest.data.persistence.FirestoreQuestRepository
import io.ipoli.android.repeatingquest.persistence.FirestoreRepeatingQuestRepository
import io.ipoli.android.tag.Tag
import io.ipoli.android.tag.persistence.FirestoreTagRepository
import space.traversal.kapsule.Injects
import space.traversal.kapsule.inject
import space.traversal.kapsule.required
import timber.log.Timber

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 06/28/2018.
 */
class DataImporter(private val appContext: Context) : Injects<BackgroundModule> {

    private val remoteDatabase by required { remoteDatabase }
    private val localDatabase by required { localDatabase }
    private val sharedPreferences by required { sharedPreferences }

    private val playerRepository by required { playerRepository }
    private val tagRepository by required { tagRepository }
    private val habitRepository by required { habitRepository }
    private val dailyChallengeRepository by required { dailyChallengeRepository }
    private val challengeRepository by required { challengeRepository }
    private val repeatingQuestRepository by required { repeatingQuestRepository }
    private val questRepository by required { questRepository }
    private val eventLogger by required { eventLogger }

    @SuppressLint("ApplySharedPref")
    @WorkerThread
    fun import() {
        inject(MyPoliApp.backgroundModule(appContext))

        val authUser = FirebaseAuth.getInstance().currentUser
        requireNotNull(authUser) { "DataImporter called without FirebaseAuth user" }

        sharedPreferences.edit().putBoolean(Constants.KEY_PLAYER_DATA_IMPORTED, false).commit()

        val remotePlayerId = authUser!!.uid

        val remotePlayerDoc = Tasks.await(
            remoteDatabase
                .collection("players")
                .document(remotePlayerId)
                .get(Source.SERVER)
        )

        if (!remotePlayerDoc.exists()) {
            sharedPreferences.edit().putBoolean(Constants.KEY_PLAYER_DATA_IMPORTED, true).commit()
            return
        }

        val remoteSchemaVersion = remotePlayerDoc.data!!["schemaVersion"] as Long

        if (remoteSchemaVersion > Constants.SCHEMA_VERSION) {
            // @TODO require upgrade dialog !!important
        }

        val pref = try {
            importData().preferences
        } catch (e: Throwable) {
            if (BuildConfig.DEBUG) {
                Timber.e(e)
            } else {
                Crashlytics.logException(e)
            }
            return
        }

        sharedPreferences.edit()
            .putString(Constants.KEY_AGENDA_START_SCREEN, pref.agendaStartScreen.name)
            .putString(Constants.KEY_PLAYER_ID, remotePlayerId)
            .putBoolean(Constants.KEY_PLAYER_DATA_IMPORTED, true)
            .putInt(Constants.KEY_SCHEMA_VERSION, remoteSchemaVersion.toInt())
            .putLong(Constants.KEY_LAST_SYNC_MILLIS, System.currentTimeMillis())
            .putString(Constants.KEY_THEME, pref.theme.name)
            .putBoolean(
                Constants.KEY_QUICK_DO_NOTIFICATION_ENABLED,
                pref.isQuickDoNotificationEnabled
            )
            .commit()
        eventLogger.setPlayerId(remotePlayerId)
    }

    @Transaction
    private fun importData(): Player {
        localDatabase.clearAllTables()

        val ft = FirestoreTagRepository(remoteDatabase).findAllNotRemoved()
        val validTagIds = ft.map { it.id }.toSet()
        tagRepository.save(ft)

        val fp = FirestorePlayerRepository(remoteDatabase).find()!!
        playerRepository.save(fp)

        val fh = FirestoreHabitRepository(remoteDatabase)
            .findAllNotRemoved()
            .map {
                it.copy(
                    tags = filterInvalidTags(it.tags, validTagIds)
                )
            }

        habitRepository.save(fh)

        val fdc = FirestoreDailyChallengeRepository(remoteDatabase).findAllNotRemoved()
        dailyChallengeRepository.save(fdc)

        val fc = FirestoreChallengeRepository(remoteDatabase)
            .findAllNotRemoved()
            .map {
                it.copy(
                    tags = filterInvalidTags(it.tags, validTagIds)
                )
            }
        challengeRepository.save(fc)

        val rqs = FirestoreRepeatingQuestRepository(remoteDatabase)
            .findAllNotRemoved()
            .map {
                it.copy(
                    tags = filterInvalidTags(it.tags, validTagIds)
                )
            }
        repeatingQuestRepository.save(rqs)

        val rqIds = rqs.map { it.id }.toSet()
        val qs = FirestoreQuestRepository(remoteDatabase)
            .findAll()
            .filter {
                !it.isRemoved || rqIds.contains(it.repeatingQuestId)
            }
            .map {
                it.copy(
                    tags = filterInvalidTags(it.tags, validTagIds)
                )
            }
        questRepository.save(qs)

        return fp
    }

    private fun filterInvalidTags(
        tags: List<Tag>,
        validTagIds: Set<String>
    ) = tags.filter { validTagIds.contains(it.id) }
}