package io.ipoli.android.challenge.preset.persistence

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.challenge.preset.PresetChallenge
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.datetime.days
import io.ipoli.android.common.datetime.minutes
import io.ipoli.android.common.persistence.documents
import io.ipoli.android.common.persistence.getAsync
import io.ipoli.android.friends.feed.data.Post
import io.ipoli.android.player.data.Avatar
import io.ipoli.android.player.persistence.model.DbPlayer
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.threeten.bp.LocalDate
import java.util.*

interface PresetChallengeRepository {
    fun findForCategory(category: PresetChallenge.Category): List<PresetChallenge>
    fun findById(id: String): PresetChallenge
    fun join(presetChallengeId: String)
    fun save(presetChallenge: PresetChallenge)
}

class FirestorePresetChallengeRepository(private val database: FirebaseFirestore) :
    PresetChallengeRepository {

    private val collectionReference: CollectionReference
        get() = database.collection("presetChallenges")

    override fun findForCategory(category: PresetChallenge.Category): List<PresetChallenge> {
        val cDocs = collectionReference
            .whereEqualTo("category", category.name)
            .whereEqualTo("status", Post.Status.APPROVED.name)
            .documents.map { it.data!! }

        val playerIds = cDocs.mapNotNull {
            playerIdFromDocumentData(it)
        }.toSet()

        return runBlocking(Dispatchers.IO) {
            val dbPlayers = getStringToPlayerMap(playerIds)
            cDocs.map {
                toEntityObject(it, dbPlayers)
            }
        }
    }

    private fun playerIdFromDocumentData(presetChallengeDoc: Map<String, Any>): String? {
        val playerId = presetChallengeDoc["playerId"]
        return if (playerId != null) {
            playerId as String
        } else null
    }

    override fun findById(id: String) =
        runBlocking(Dispatchers.IO) {
            val doc = collectionReference.document(id).getAsync().data!!
            val dbPlayers = playerIdFromDocumentData(doc)?.let {
                getStringToPlayerMap(setOf(it))
            } ?: emptyMap()
            toEntityObject(doc, dbPlayers)
        }

    private suspend fun getStringToPlayerMap(playerIds: Set<String>): Map<String, DbPlayer> {
        val dbJobs = playerIds.map {
            GlobalScope.async(Dispatchers.IO) {
                it to DbPlayer(playerRef(it).getAsync().data!!)
            }
        }

        return dbJobs.map { it.await() }.toMap()
    }

    override fun join(presetChallengeId: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val playerId = currentUser.uid
        Tasks.await(
            collectionReference.document(presetChallengeId).update(
                mapOf(
                    "participants.$playerId" to true
                )
            )
        )
    }

    override fun save(presetChallenge: PresetChallenge) {
        val docRef = collectionReference.document(presetChallenge.id)
        Tasks.await(docRef.set(toDatabaseObject(presetChallenge).map))
    }

    private fun toDatabaseObject(presetChallenge: PresetChallenge): DbPresetChallenge {

        val dbConfig = DbPresetChallenge.Config().apply {
            defaultStartMinute = presetChallenge.config.defaultStartTime?.toMinuteOfDay()?.toLong()
            nutritionMacros = null
        }

        val dbTrackedValues = presetChallenge.trackedValues.map {
            when (it) {
                is Challenge.TrackedValue.Progress -> {
                    DbPresetChallenge.TrackedValue().apply {
                        id = it.id
                        type = DbPresetChallenge.TrackedValue.Type.PROGRESS.name
                    }.map
                }

                is Challenge.TrackedValue.Target -> {
                    DbPresetChallenge.TrackedValue().apply {
                        id = it.id
                        type = DbPresetChallenge.TrackedValue.Type.TARGET.name
                        name = it.name
                        units = it.units
                        startValue = it.startValue.toFloat()
                        targetValue = it.targetValue.toFloat()
                        isCumulative = it.isCumulative
                    }.map
                }

                is Challenge.TrackedValue.Average -> {
                    DbPresetChallenge.TrackedValue().apply {
                        id = it.id
                        type = DbPresetChallenge.TrackedValue.Type.AVERAGE.name
                        name = it.name
                        units = it.units
                        targetValue = it.targetValue.toFloat()
                        lowerBound = it.lowerBound.toFloat()
                        upperBound = it.upperBound.toFloat()
                    }.map
                }
            }
        }

        val dbQuests = presetChallenge.schedule.quests.map {
            DbPresetChallenge.Schedule.Quest().apply {
                name = it.name
                color = it.color.name
                icon = it.icon.name
                day = it.day.toLong()
                duration = it.duration.longValue
                subQuests = it.subQuests
                note = it.note
            }.map
        }

        val dbHabits = presetChallenge.schedule.habits.map {
            DbPresetChallenge.Schedule.Habit().apply {
                name = it.name
                color = it.color.name
                icon = it.icon.name
                isGood = it.isGood
                timesADay = it.timesADay.toLong()
            }.map
        }


        val dbSchedule = DbPresetChallenge.Schedule().apply {
            quests = dbQuests
            habits = dbHabits
        }

        return DbPresetChallenge().apply {
            id = presetChallenge.id
            playerId = presetChallenge.author!!.id
            name = presetChallenge.name
            color = presetChallenge.color.name
            icon = presetChallenge.icon.name
            category = presetChallenge.category.name
            imageUrl = presetChallenge.imageUrl
            shortDescription = presetChallenge.shortDescription
            description = presetChallenge.description
            difficulty = presetChallenge.difficulty.name
            requirements = presetChallenge.requirements
            expectedResults = presetChallenge.expectedResults
            duration = presetChallenge.duration.longValue
            busynessPerWeek = presetChallenge.busynessPerWeek.longValue
            level = presetChallenge.level?.toLong()
            gemPrice = presetChallenge.gemPrice.toLong()
            status = presetChallenge.status.name
            participants = emptyMap()
            config = dbConfig.map
            note = presetChallenge.note
            trackedValues = dbTrackedValues
            schedule = dbSchedule.map
        }
    }

    private fun playerRef(playerId: String) =
        database
            .collection("players")
            .document(playerId)

    fun toEntityObject(
        dataMap: MutableMap<String, Any?>,
        dbPlayers: Map<String, DbPlayer>
    ): PresetChallenge {

        val c = DbPresetChallenge(dataMap.withDefault {
            null
        })

        val dbSchedule = DbPresetChallenge.Schedule(c.schedule)

        val qs = dbSchedule.quests.map {
            val q = DbPresetChallenge.Schedule.Quest(it)
            PresetChallenge.Quest(
                name = q.name,
                color = Color.valueOf(q.color),
                icon = Icon.valueOf(q.icon),
                day = q.day.toInt(),
                duration = q.duration.minutes,
                subQuests = q.subQuests,
                note = q.note
            )
        }

        val hs = dbSchedule.habits.map {
            val h = DbPresetChallenge.Schedule.Habit(it)
            PresetChallenge.Habit(
                name = h.name,
                color = Color.valueOf(h.color),
                icon = Icon.valueOf(h.icon),
                isGood = h.isGood,
                timesADay = h.timesADay.toInt(),
                isSelected = true
            )
        }

        val schedule = PresetChallenge.Schedule(qs, hs)

        val dbConfig = DbPresetChallenge.Config(c.config)
        val nutritionMacros = dbConfig.nutritionMacros?.let {
            val dbMacros = DbPresetChallenge.NutritionMacros(it)
            val dbFemale = DbPresetChallenge.NutritionDetails(dbMacros.female)
            val dbMale = DbPresetChallenge.NutritionDetails(dbMacros.male)

            val female = PresetChallenge.NutritionDetails(
                caloriesPerKg = dbFemale.caloriesPerKg,
                proteinPerKg = dbFemale.proteinPerKg,
                carbohydratesPerKg = dbFemale.carbohydratesPerKg,
                fatPerKg = dbFemale.fatPerKg
            )

            val male = PresetChallenge.NutritionDetails(
                caloriesPerKg = dbMale.caloriesPerKg,
                proteinPerKg = dbMale.proteinPerKg,
                carbohydratesPerKg = dbMale.carbohydratesPerKg,
                fatPerKg = dbMale.fatPerKg
            )
            PresetChallenge.NutritionMacros(
                female = female,
                male = male
            )
        }

        val defaultStartTime = dbConfig.defaultStartMinute?.let {
            Time.of(it.toInt())
        }

        val config = PresetChallenge.Config(
            defaultStartTime = defaultStartTime,
            nutritionMacros = nutritionMacros
        )

        val trackedValues = c.trackedValues.map {
            val id = UUID.randomUUID().toString()

            DbPresetChallenge.TrackedValue(it).let { tv ->
                when (DbPresetChallenge.TrackedValue.Type.valueOf(tv.type)) {
                    DbPresetChallenge.TrackedValue.Type.PROGRESS ->
                        Challenge.TrackedValue.Progress(
                            id = id,
                            history = emptyMap<LocalDate, Challenge.TrackedValue.Log>().toSortedMap()
                        )

                    DbPresetChallenge.TrackedValue.Type.TARGET ->
                        Challenge.TrackedValue.Target(
                            id = id,
                            name = tv.name!!,
                            units = tv.units!!,
                            startValue = tv.startValue!!.toDouble(),
                            targetValue = tv.targetValue!!.toDouble(),
                            currentValue = 0.0,
                            remainingValue = 0.0,
                            isCumulative = tv.isCumulative!!,
                            history = emptyMap<LocalDate, Challenge.TrackedValue.Log>().toSortedMap()
                        )

                    DbPresetChallenge.TrackedValue.Type.AVERAGE ->
                        Challenge.TrackedValue.Average(
                            id = id,
                            name = tv.name!!,
                            units = tv.units!!,
                            targetValue = tv.targetValue!!.toDouble(),
                            lowerBound = tv.lowerBound!!.toDouble(),
                            upperBound = tv.upperBound!!.toDouble(),
                            history = emptyMap<LocalDate, Challenge.TrackedValue.Log>().toSortedMap()
                        )
                }
            }
        }

        val author = c.playerId?.let {
            val a = dbPlayers[it]!!
            PresetChallenge.Author(
                id = a.id,
                displayName = a.displayName ?: "Unknown Hero",
                username = a.username!!,
                avatar = Avatar.valueOf(a.avatar),
                level = a.level.toInt()
            )
        }

        return PresetChallenge(
            id = c.id,
            name = c.name,
            color = Color.valueOf(c.color),
            icon = Icon.valueOf(c.icon),
            category = PresetChallenge.Category.valueOf(c.category),
            imageUrl = c.imageUrl,
            shortDescription = c.shortDescription,
            description = c.description,
            difficulty = Challenge.Difficulty.valueOf(c.difficulty),
            duration = c.duration.days,
            busynessPerWeek = c.busynessPerWeek.minutes,
            requirements = c.requirements,
            level = c.level?.toInt(),
            gemPrice = c.gemPrice.toInt(),
            expectedResults = c.expectedResults,
            note = c.note,
            trackedValues = trackedValues,
            config = config,
            schedule = schedule,
            status = Post.Status.valueOf(c.status),
            participantCount = c.participants.size,
            author = author
        )
    }

    data class DbPresetChallenge(val map: MutableMap<String, Any?> = mutableMapOf()) {
        var id: String by map
        var playerId: String? by map
        var name: String by map
        var color: String by map
        var icon: String by map
        var category: String by map
        var imageUrl: String by map
        var shortDescription: String by map
        var description: String by map
        var difficulty: String by map
        var requirements: List<String> by map
        var expectedResults: List<String> by map
        var duration: Long by map
        var trackedValues: List<MutableMap<String, Any?>> by map
        var busynessPerWeek: Long by map
        var level: Long? by map
        var gemPrice: Long by map
        var note: String by map
        var config: MutableMap<String, Any?> by map
        var schedule: MutableMap<String, Any?> by map
        var status: String by map
        var participants: Map<String, Any?> by map

        data class TrackedValue(val map: MutableMap<String, Any?> = mutableMapOf()) {
            var id: String by map
            var type: String by map
            var name: String? by map
            var units: String? by map
            var targetValue: Float? by map
            var startValue: Float? by map
            var isCumulative: Boolean? by map
            var lowerBound: Float? by map
            var upperBound: Float? by map
            var logs: List<Map<String, Any?>>? by map

            enum class Type {
                PROGRESS, TARGET, AVERAGE
            }
        }

        data class Config(val map: MutableMap<String, Any?> = mutableMapOf()) {
            var defaultStartMinute: Long? by map
            var nutritionMacros: MutableMap<String, Any?>? by map
        }

        data class NutritionMacros(val map: MutableMap<String, Any?> = mutableMapOf()) {
            var female: MutableMap<String, Any?> by map
            var male: MutableMap<String, Any?> by map
        }

        data class NutritionDetails(val map: MutableMap<String, Any?> = mutableMapOf()) {
            var caloriesPerKg: Float by map
            var proteinPerKg: Float by map
            var carbohydratesPerKg: Float by map
            var fatPerKg: Float by map
        }

        data class Schedule(val map: MutableMap<String, Any?> = mutableMapOf()) {
            var quests: List<MutableMap<String, Any?>> by map
            var habits: List<MutableMap<String, Any?>> by map

            data class Quest(val map: MutableMap<String, Any?> = mutableMapOf()) {
                var name: String by map
                var color: String by map
                var icon: String by map
                var day: Long by map
                var duration: Long by map
                var subQuests: List<String> by map
                var note: String by map
            }

            data class Habit(val map: MutableMap<String, Any?> = mutableMapOf()) {
                var name: String by map
                var color: String by map
                var icon: String by map
                var isGood: Boolean by map
                var timesADay: Long by map
            }
        }
    }
}