package io.ipoli.android.repeatingquest.persistence

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey.CASCADE
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import io.ipoli.android.common.datetime.*
import io.ipoli.android.common.persistence.*
import io.ipoli.android.quest.*
import io.ipoli.android.quest.data.persistence.DbEmbedTag
import io.ipoli.android.quest.data.persistence.DbReminder
import io.ipoli.android.quest.data.persistence.DbSubQuest
import io.ipoli.android.quest.subquest.SubQuest
import io.ipoli.android.repeatingquest.entity.RepeatPattern
import io.ipoli.android.repeatingquest.persistence.DbRepeatPattern.Type.*
import io.ipoli.android.tag.Tag
import io.ipoli.android.tag.persistence.RoomTag
import io.ipoli.android.tag.persistence.RoomTagMapper
import io.ipoli.android.tag.persistence.TagDao
import org.jetbrains.annotations.NotNull
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate
import org.threeten.bp.Month
import java.util.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 2/16/18.
 */
interface RepeatingQuestRepository : CollectionRepository<RepeatingQuest> {
    fun findAllActive(currentDate: LocalDate = LocalDate.now()): List<RepeatingQuest>
    fun findActiveNotForChallenge(
        challengeId: String,
        currentDate: LocalDate = LocalDate.now()
    ): List<RepeatingQuest>

    fun findAllForChallenge(challengeId: String): List<RepeatingQuest>
    fun generateId(): String
    fun remove(ids: List<String>)
    fun removeFromChallenge(repeatingQuest: RepeatingQuest): RepeatingQuest
}

@Dao
abstract class RepeatingQuestDao : BaseDao<RoomRepeatingQuest>() {

    @Query("SELECT * FROM repeating_quests")
    abstract fun findAll(): List<RoomRepeatingQuest>

    @Query("SELECT * FROM repeating_quests WHERE id = :id")
    abstract fun findById(id: String): RoomRepeatingQuest

    @Query("SELECT * FROM repeating_quests WHERE removedAt IS NULL")
    abstract fun listenForNotRemoved(): LiveData<List<RoomRepeatingQuest>>

    @Query("SELECT * FROM repeating_quests WHERE id = :id")
    abstract fun listenById(id: String): LiveData<RoomRepeatingQuest>

    @Query("UPDATE repeating_quests $REMOVE_QUERY")
    abstract fun remove(id: String, currentTimeMillis: Long = System.currentTimeMillis())

    @Query("UPDATE repeating_quests $UNDO_REMOVE_QUERY")
    abstract fun undoRemove(id: String, currentTimeMillis: Long = System.currentTimeMillis())

    @Query("SELECT * FROM repeating_quests WHERE removedAt IS NULL AND challengeId = :challengeId")
    abstract fun findAllForChallenge(challengeId: String): List<RoomRepeatingQuest>

    @Query("SELECT * FROM repeating_quests WHERE removedAt IS NULL AND (repeatPattern_endDate IS NULL OR repeatPattern_endDate >= :date)")
    abstract fun findAllActive(date: Long): List<RoomRepeatingQuest>

    @Query("SELECT * FROM repeating_quests WHERE removedAt IS NULL AND (repeatPattern_endDate IS NULL OR repeatPattern_endDate >= :date) AND (challengeId IS NULL OR challengeId != :challengeId)")
    abstract fun findActiveNotForChallenge(
        challengeId: String,
        date: Long
    ): List<RoomRepeatingQuest>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun saveTags(joins: List<RoomRepeatingQuest.Companion.RoomTagJoin>)

    @Query("DELETE FROM repeating_quest_tag_join WHERE repeatingQuestId = :repeatingQuestId")
    abstract fun deleteAllTags(repeatingQuestId: String)

    @Query("DELETE FROM repeating_quest_tag_join WHERE repeatingQuestId IN (:repeatingQuestIds)")
    abstract fun deleteAllTags(repeatingQuestIds: List<String>)

    @Query("SELECT * FROM repeating_quests $FIND_SYNC_QUERY")
    abstract fun findAllForSync(lastSync: Long): List<RoomRepeatingQuest>

    @Query("UPDATE repeating_quests SET removedAt = :currentTimeMillis, updatedAt = :currentTimeMillis WHERE id IN (:ids)")
    abstract fun remove(ids: List<String>, currentTimeMillis: Long = System.currentTimeMillis())

    @Query("UPDATE repeating_quests SET updatedAt = :currentTimeMillis, challengeId = NULL WHERE id = :id")
    abstract fun removeFromChallenge(
        id: String,
        currentTimeMillis: Long = System.currentTimeMillis()
    )
}

class RoomRepeatingQuestRepository(dao: RepeatingQuestDao, private val tagDao: TagDao) :
    RepeatingQuestRepository,
    BaseRoomRepositoryWithTags<RepeatingQuest, RoomRepeatingQuest, RepeatingQuestDao, RoomRepeatingQuest.Companion.RoomTagJoin>(
        dao
    ) {

    override fun findAllForSync(lastSync: Duration<Millisecond>) =
        dao.findAllForSync(lastSync.millisValue).map { toEntityObject(it) }

    override fun createTagJoin(
        entityId: String,
        tagId: String
    ) = RoomRepeatingQuest.Companion.RoomTagJoin(entityId, tagId)

    override fun newIdForEntity(id: String, entity: RepeatingQuest) = entity.copy(id = id)

    override fun saveTags(joins: List<RoomRepeatingQuest.Companion.RoomTagJoin>) =
        dao.saveTags(joins)

    override fun deleteAllTags(entityId: String) = dao.deleteAllTags(entityId)

    override fun findAllActive(currentDate: LocalDate) =
        dao.findAllActive(currentDate.startOfDayUTC()).map { toEntityObject(it) }

    override fun findActiveNotForChallenge(
        challengeId: String,
        currentDate: LocalDate
    ) =
        dao.findActiveNotForChallenge(
            challengeId,
            currentDate.startOfDayUTC()
        ).map { toEntityObject(it) }

    override fun findAllForChallenge(challengeId: String) =
        dao.findAllForChallenge(challengeId).map { toEntityObject(it) }

    override fun generateId() = UUID.randomUUID().toString()

    override fun remove(ids: List<String>) {
        dao.remove(ids)
    }

    override fun findById(id: String) = toEntityObject(dao.findById(id))

    override fun findAll() = dao.findAll().map { toEntityObject(it) }

    override fun listenById(
        id: String
    ) =
        dao.listenById(id).notifySingle()

    override fun listenForAll() =
        dao.listenForNotRemoved().notify()

    override fun remove(entity: RepeatingQuest) {
        remove(entity.id)
    }

    override fun remove(id: String) {
        dao.remove(id)
    }

    override fun undoRemove(id: String) {
        dao.undoRemove(id)
    }

    override fun removeFromChallenge(repeatingQuest: RepeatingQuest): RepeatingQuest {
        val currentTime = System.currentTimeMillis()
        dao.removeFromChallenge(repeatingQuest.id, currentTime)
        return repeatingQuest.copy(
            challengeId = null,
            updatedAt = currentTime.instant
        )
    }

    private val tagMapper = RoomTagMapper()

    override fun toEntityObject(dbObject: RoomRepeatingQuest) =
        RepeatingQuest(
            id = dbObject.id,
            name = dbObject.name,
            color = Color.valueOf(dbObject.color),
            icon = dbObject.icon?.let {
                Icon.valueOf(it)
            },
            tags = tagDao.findForRepeatingQuest(dbObject.id).map { tagMapper.toEntityObject(it) },
            startTime = dbObject.startMinute?.let { Time.of(it.toInt()) },
            duration = dbObject.duration.toInt(),
            priority = Priority.valueOf(dbObject.priority),
            preferredStartTime = TimePreference.valueOf(dbObject.preferredStartTime),
            reminders = dbObject.reminders.map {
                val cr = DbReminder(it)
                val type = DbReminder.Type.valueOf(cr.type)
                when (type) {
                    DbReminder.Type.RELATIVE ->
                        Reminder.Relative(cr.message, cr.minutesFromStart!!.toLong())

                    DbReminder.Type.FIXED ->
                        Reminder.Fixed(
                            cr.message,
                            cr.date!!.startOfDayUTC,
                            Time.of(cr.minute!!.toInt())
                        )
                }

            },
            repeatPattern = createRepeatPattern(dbObject.repeatPattern),
            subQuests = dbObject.subQuests.map {
                val dsq = DbSubQuest(it)
                SubQuest(
                    name = dsq.name,
                    completedAtDate = dsq.completedAtDate?.startOfDayUTC,
                    completedAtTime = dsq.completedAtMinute?.let { Time.of(it.toInt()) }
                )
            },
            challengeId = dbObject.challengeId,
            note = dbObject.note,
            updatedAt = dbObject.updatedAt.instant,
            createdAt = dbObject.createdAt.instant,
            removedAt = dbObject.removedAt?.instant
        )

    private fun createRepeatPattern(rp: RoomRepeatPattern): RepeatPattern {
        val type = valueOf(rp.type)

        return when (type) {
            DAILY -> {
                RepeatPattern.Daily(
                    startDate = rp.startDate.startOfDayUTC,
                    endDate = rp.endDate?.startOfDayUTC
                )
            }
            WEEKLY -> {
                RepeatPattern.Weekly(
                    daysOfWeek = rp.daysOfWeek.map { DayOfWeek.valueOf(it) }.toSet(),
                    startDate = rp.startDate.startOfDayUTC,
                    endDate = rp.endDate?.startOfDayUTC
                )
            }
            MONTHLY -> {
                RepeatPattern.Monthly(
                    daysOfMonth = rp.daysOfMonth.map { it.toInt() }.toSet(),
                    startDate = rp.startDate.startOfDayUTC,
                    endDate = rp.endDate?.startOfDayUTC
                )
            }

            YEARLY -> {
                RepeatPattern.Yearly(
                    dayOfMonth = rp.dayOfMonth!!.toInt(),
                    month = Month.valueOf(rp.month!!),
                    startDate = rp.startDate.startOfDayUTC,
                    endDate = rp.endDate?.startOfDayUTC
                )
            }

            EVERY_X_DAYS -> {
                RepeatPattern.EveryXDays(
                    xDays = rp.xDays!!.toInt(),
                    startDate = rp.startDate.startOfDayUTC,
                    endDate = rp.endDate?.startOfDayUTC
                )
            }

            FLEXIBLE_WEEKLY -> {
                RepeatPattern.Flexible.Weekly(
                    timesPerWeek = rp.timesPerWeek!!.toInt(),
                    preferredDays = rp.preferredDays.map { DayOfWeek.valueOf(it) }.toSet(),
                    scheduledPeriods = rp.scheduledPeriods.entries
                        .associate { it.key.toLong().startOfDayUTC to it.value.map { it.startOfDayUTC } },
                    startDate = rp.startDate.startOfDayUTC,
                    endDate = rp.endDate?.startOfDayUTC
                )
            }

            FLEXIBLE_MONTHLY -> {
                RepeatPattern.Flexible.Monthly(
                    timesPerMonth = rp.timesPerMonth!!.toInt(),
                    preferredDays = rp.preferredDays.map { it.toInt() }.toSet(),
                    scheduledPeriods = rp.scheduledPeriods.entries
                        .associate { it.key.toLong().startOfDayUTC to it.value.map { it.startOfDayUTC } },
                    startDate = rp.startDate.startOfDayUTC,
                    endDate = rp.endDate?.startOfDayUTC
                )
            }
        }
    }

    override fun toDatabaseObject(entity: RepeatingQuest) =
        RoomRepeatingQuest(
            id = if (entity.id.isEmpty()) UUID.randomUUID().toString() else entity.id,
            name = entity.name,
            color = entity.color.name,
            icon = entity.icon?.name,
            duration = entity.duration.toLong(),
            priority = entity.priority.name,
            preferredStartTime = entity.preferredStartTime.name,
            startMinute = entity.startTime?.toMinuteOfDay()?.toLong(),
            reminders = entity.reminders.map {
                createDbReminder(it).map
            },
            subQuests = entity.subQuests.map {
                DbSubQuest().apply {
                    name = it.name
                    completedAtDate = it.completedAtDate?.startOfDayUTC()
                    completedAtMinute = it.completedAtTime?.toMinuteOfDay()?.toLong()
                }.map
            },
            repeatPattern = createRoomRepeatingPattern(entity.repeatPattern),
            challengeId = entity.challengeId,
            note = entity.note,
            updatedAt = System.currentTimeMillis(),
            createdAt = entity.createdAt.toEpochMilli(),
            removedAt = entity.removedAt?.toEpochMilli()
        )

    private fun createRoomRepeatingPattern(repeatPattern: RepeatPattern) =

        when (repeatPattern) {
            is RepeatPattern.Daily -> {
                RoomRepeatPattern(
                    type = DAILY.name,
                    startDate = repeatPattern.startDate.startOfDayUTC(),
                    endDate = repeatPattern.endDate?.startOfDayUTC()
                )
            }
            is RepeatPattern.Weekly -> {

                RoomRepeatPattern(
                    type = WEEKLY.name,
                    startDate = repeatPattern.startDate.startOfDayUTC(),
                    endDate = repeatPattern.endDate?.startOfDayUTC(),
                    daysOfWeek = repeatPattern.daysOfWeek.map {
                        it.name
                    }
                )
            }
            is RepeatPattern.Monthly -> {
                RoomRepeatPattern(
                    type = MONTHLY.name,
                    startDate = repeatPattern.startDate.startOfDayUTC(),
                    endDate = repeatPattern.endDate?.startOfDayUTC(),
                    daysOfMonth = repeatPattern.daysOfMonth.map { it.toLong() }
                )
            }
            is RepeatPattern.Yearly -> {
                RoomRepeatPattern(
                    type = YEARLY.name,
                    startDate = repeatPattern.startDate.startOfDayUTC(),
                    endDate = repeatPattern.endDate?.startOfDayUTC(),
                    dayOfMonth = repeatPattern.dayOfMonth.toLong(),
                    month = repeatPattern.month.name
                )
            }

            is RepeatPattern.EveryXDays -> {
                RoomRepeatPattern(
                    type = EVERY_X_DAYS.name,
                    xDays = repeatPattern.xDays.toLong(),
                    startDate = repeatPattern.startDate.startOfDayUTC(),
                    endDate = repeatPattern.endDate?.startOfDayUTC()
                )
            }

            is RepeatPattern.Flexible.Weekly -> {
                RoomRepeatPattern(
                    type = FLEXIBLE_WEEKLY.name,
                    startDate = repeatPattern.startDate.startOfDayUTC(),
                    endDate = repeatPattern.endDate?.startOfDayUTC(),
                    timesPerWeek = repeatPattern.timesPerWeek.toLong(),
                    preferredDays = repeatPattern.preferredDays.map {
                        it.name
                    },
                    scheduledPeriods = repeatPattern.scheduledPeriods.entries
                        .associate { it.key.startOfDayUTC().toString() to it.value.map { it.startOfDayUTC() } }
                        .toMutableMap()
                )
            }

            is RepeatPattern.Flexible.Monthly -> {
                RoomRepeatPattern(
                    type = FLEXIBLE_MONTHLY.name,
                    startDate = repeatPattern.startDate.startOfDayUTC(),
                    endDate = repeatPattern.endDate?.startOfDayUTC(),
                    timesPerMonth = repeatPattern.timesPerMonth.toLong(),
                    preferredDays = repeatPattern.preferredDays.map { it.toString() },
                    scheduledPeriods = repeatPattern.scheduledPeriods.entries
                        .associate { it.key.startOfDayUTC().toString() to it.value.map { it.startOfDayUTC() } }
                        .toMutableMap()
                )
            }
        }

    private fun createDbReminder(reminder: Reminder): DbReminder {
        val cr = DbReminder()
        cr.message = reminder.message
        when (reminder) {

            is Reminder.Fixed -> {
                cr.type = DbReminder.Type.FIXED.name
                cr.date = reminder.date.startOfDayUTC()
                cr.minute = reminder.time.toMinuteOfDay().toLong()
            }

            is Reminder.Relative -> {
                cr.type = DbReminder.Type.RELATIVE.name
                cr.minutesFromStart = reminder.minutesFromStart
            }
        }
        return cr
    }
}

@Entity(
    tableName = "repeating_quests",
    indices = [
        Index("challengeId"),
        Index("repeatPattern_endDate"),
        Index("updatedAt"),
        Index("removedAt")
    ]
)
data class RoomRepeatingQuest(
    @NotNull
    @PrimaryKey(autoGenerate = false)
    override val id: String,
    val name: String,
    val color: String,
    val icon: String?,
    val startMinute: Long?,
    val duration: Long,
    val priority: String,
    val preferredStartTime: String,
    val reminders: List<MutableMap<String, Any?>>,
    @Embedded(prefix = "repeatPattern_")
    val repeatPattern: RoomRepeatPattern,
    val subQuests: List<MutableMap<String, Any?>>,
    val challengeId: String?,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val removedAt: Long?
) : RoomEntity {
    companion object {
        @Entity(
            tableName = "repeating_quest_tag_join",
            primaryKeys = ["repeatingQuestId", "tagId"],
            foreignKeys = [
                ForeignKey(
                    entity = RoomRepeatingQuest::class,
                    parentColumns = ["id"],
                    childColumns = ["repeatingQuestId"],
                    onDelete = CASCADE
                ),
                ForeignKey(
                    entity = RoomTag::class,
                    parentColumns = ["id"],
                    childColumns = ["tagId"],
                    onDelete = CASCADE
                )
            ],
            indices = [Index("repeatingQuestId"), Index("tagId")]
        )
        data class RoomTagJoin(val repeatingQuestId: String, val tagId: String)
    }
}

data class DbRepeatingQuest(override val map: MutableMap<String, Any?> = mutableMapOf()) :
    FirestoreModel {
    override var id: String by map
    var name: String by map
    var color: String by map
    var icon: String? by map
    var tags: Map<String, MutableMap<String, Any?>> by map
    var startMinute: Long? by map
    var duration: Long by map
    var priority: String by map
    var preferredStartTime: String by map
    var reminders: List<MutableMap<String, Any?>> by map
    var repeatPattern: MutableMap<String, Any?> by map
    var subQuests: List<MutableMap<String, Any?>> by map
    var challengeId: String? by map
    var note: String by map
    override var createdAt: Long by map
    override var updatedAt: Long by map
    override var removedAt: Long? by map
}

data class RoomRepeatPattern(
    val type: String,
    val startDate: Long,
    val endDate: Long?,
    val dayOfMonth: Long? = null,
    val month: String? = null,
    val daysOfWeek: List<String> = emptyList(),
    val daysOfMonth: List<Long> = emptyList(),
    val timesPerWeek: Long? = null,
    val timesPerMonth: Long? = null,
    val preferredDays: List<String> = emptyList(),
    val scheduledPeriods: MutableMap<String, List<Long>> = mutableMapOf(),
    val xDays: Long? = null
)

data class DbRepeatPattern(val map: MutableMap<String, Any?> = mutableMapOf()) {
    var type: String by map
    var startDate: Long by map
    var endDate: Long? by map
    var dayOfMonth: Long by map
    var month: String by map
    var daysOfWeek: List<String> by map
    var daysOfMonth: List<Long> by map
    var timesPerWeek: Long? by map
    var timesPerMonth: Long? by map
    var preferredDays: List<String> by map
    var scheduledPeriods: MutableMap<String, List<Long>> by map
    var xDays: Long? by map

    enum class Type {
        DAILY, WEEKLY, MONTHLY, YEARLY, FLEXIBLE_WEEKLY, FLEXIBLE_MONTHLY, EVERY_X_DAYS
    }
}

class FirestoreRepeatingQuestRepository(
    database: FirebaseFirestore
) : BaseCollectionFirestoreRepository<RepeatingQuest, DbRepeatingQuest>(
    database
) {

    override val collectionReference: CollectionReference
        get() {
            return database.collection("players").document(playerId).collection("repeatingQuests")
        }

    override fun toEntityObject(dataMap: MutableMap<String, Any?>): RepeatingQuest {

        val rq = DbRepeatingQuest(dataMap.withDefault {
            null
        })

        return RepeatingQuest(
            id = rq.id,
            name = rq.name,
            color = Color.valueOf(rq.color),
            icon = rq.icon?.let {
                Icon.valueOf(it)
            },
            tags = rq.tags.values.map {
                createTag(it)
            },
            startTime = rq.startMinute?.let { Time.of(it.toInt()) },
            duration = rq.duration.toInt(),
            priority = Priority.valueOf(rq.priority),
            preferredStartTime = TimePreference.valueOf(rq.preferredStartTime),
            reminders = rq.reminders.map {
                val cr = DbReminder(it)
                val type = DbReminder.Type.valueOf(cr.type)
                when (type) {
                    DbReminder.Type.RELATIVE ->
                        Reminder.Relative(cr.message, cr.minutesFromStart!!.toLong())

                    DbReminder.Type.FIXED ->
                        Reminder.Fixed(
                            cr.message,
                            cr.date!!.startOfDayUTC,
                            Time.of(cr.minute!!.toInt())
                        )
                }

            },
            repeatPattern = createRepeatPattern(DbRepeatPattern(rq.repeatPattern)),
            subQuests = rq.subQuests.map {
                val dsq = DbSubQuest(it)
                SubQuest(
                    name = dsq.name,
                    completedAtDate = dsq.completedAtDate?.startOfDayUTC,
                    completedAtTime = dsq.completedAtMinute?.let { Time.of(it.toInt()) }
                )
            },
            challengeId = rq.challengeId,
            note = rq.note,
            updatedAt = rq.updatedAt.instant,
            createdAt = rq.createdAt.instant,
            removedAt = rq.removedAt?.instant
        )
    }

    private fun createRepeatPattern(rp: DbRepeatPattern): RepeatPattern {
        val type = valueOf(rp.type)

        return when (type) {
            DAILY -> {
                RepeatPattern.Daily(
                    startDate = rp.startDate.startOfDayUTC,
                    endDate = rp.endDate?.startOfDayUTC
                )
            }
            WEEKLY -> {
                RepeatPattern.Weekly(
                    daysOfWeek = rp.daysOfWeek.map { DayOfWeek.valueOf(it) }.toSet(),
                    startDate = rp.startDate.startOfDayUTC,
                    endDate = rp.endDate?.startOfDayUTC
                )
            }
            MONTHLY -> {
                RepeatPattern.Monthly(
                    daysOfMonth = rp.daysOfMonth.map { it.toInt() }.toSet(),
                    startDate = rp.startDate.startOfDayUTC,
                    endDate = rp.endDate?.startOfDayUTC
                )
            }

            YEARLY -> {
                RepeatPattern.Yearly(
                    dayOfMonth = rp.dayOfMonth.toInt(),
                    month = Month.valueOf(rp.month),
                    startDate = rp.startDate.startOfDayUTC,
                    endDate = rp.endDate?.startOfDayUTC
                )
            }

            EVERY_X_DAYS -> {
                RepeatPattern.EveryXDays(
                    xDays = rp.xDays!!.toInt(),
                    startDate = rp.startDate.startOfDayUTC,
                    endDate = rp.endDate?.startOfDayUTC
                )
            }

            FLEXIBLE_WEEKLY -> {
                RepeatPattern.Flexible.Weekly(
                    timesPerWeek = rp.timesPerWeek!!.toInt(),
                    preferredDays = rp.preferredDays.map { DayOfWeek.valueOf(it) }.toSet(),
                    scheduledPeriods = rp.scheduledPeriods.entries
                        .associate { it.key.toLong().startOfDayUTC to it.value.map { it.startOfDayUTC } },
                    startDate = rp.startDate.startOfDayUTC,
                    endDate = rp.endDate?.startOfDayUTC
                )
            }

            FLEXIBLE_MONTHLY -> {
                RepeatPattern.Flexible.Monthly(
                    timesPerMonth = rp.timesPerMonth!!.toInt(),
                    preferredDays = rp.preferredDays.map { it.toInt() }.toSet(),
                    scheduledPeriods = rp.scheduledPeriods.entries
                        .associate { it.key.toLong().startOfDayUTC to it.value.map { it.startOfDayUTC } },
                    startDate = rp.startDate.startOfDayUTC,
                    endDate = rp.endDate?.startOfDayUTC
                )
            }
        }
    }

    override fun toDatabaseObject(entity: RepeatingQuest): DbRepeatingQuest {
        val rq = DbRepeatingQuest()
        rq.id = entity.id
        rq.name = entity.name
        rq.tags = entity.tags.map { it.id to createDbTag(it).map }.toMap()
        rq.color = entity.color.name
        rq.icon = entity.icon?.name
        rq.duration = entity.duration.toLong()
        rq.priority = entity.priority.name
        rq.preferredStartTime = entity.preferredStartTime.name
        rq.startMinute = entity.startTime?.toMinuteOfDay()?.toLong()
        rq.reminders = entity.reminders.map {
            createDbReminder(it).map
        }
        rq.subQuests = entity.subQuests.map {
            DbSubQuest().apply {
                name = it.name
                completedAtDate = it.completedAtDate?.startOfDayUTC()
                completedAtMinute = it.completedAtTime?.toMinuteOfDay()?.toLong()
            }.map
        }
        rq.repeatPattern = createDbRepeatingPattern(entity.repeatPattern).map
        rq.challengeId = entity.challengeId
        rq.note = entity.note
        rq.updatedAt = entity.updatedAt.toEpochMilli()
        rq.createdAt = entity.createdAt.toEpochMilli()
        rq.removedAt = entity.removedAt?.toEpochMilli()
        return rq
    }

    private fun createDbRepeatingPattern(repeatPattern: RepeatPattern): DbRepeatPattern {
        val rp = DbRepeatPattern()
        rp.startDate = repeatPattern.startDate.startOfDayUTC()
        rp.endDate = repeatPattern.endDate?.startOfDayUTC()

        when (repeatPattern) {
            is RepeatPattern.Daily -> {
                rp.type = DAILY.name
            }
            is RepeatPattern.Weekly -> {
                rp.type = WEEKLY.name
                rp.daysOfWeek = repeatPattern.daysOfWeek.map {
                    it.name
                }
            }
            is RepeatPattern.Monthly -> {
                rp.type = MONTHLY.name
                rp.daysOfMonth = repeatPattern.daysOfMonth.map { it.toLong() }
            }
            is RepeatPattern.Yearly -> {
                rp.type = YEARLY.name
                rp.dayOfMonth = repeatPattern.dayOfMonth.toLong()
                rp.month = repeatPattern.month.name
            }

            is RepeatPattern.EveryXDays -> {
                rp.type = EVERY_X_DAYS.name
                rp.xDays = repeatPattern.xDays.toLong()
            }

            is RepeatPattern.Flexible.Weekly -> {
                rp.type = FLEXIBLE_WEEKLY.name
                rp.timesPerWeek = repeatPattern.timesPerWeek.toLong()
                rp.preferredDays = repeatPattern.preferredDays.map {
                    it.name
                }
                rp.scheduledPeriods = repeatPattern.scheduledPeriods.entries
                    .associate { it.key.startOfDayUTC().toString() to it.value.map { it.startOfDayUTC() } }
                    .toMutableMap()
            }
            is RepeatPattern.Flexible.Monthly -> {
                rp.type = FLEXIBLE_MONTHLY.name
                rp.timesPerMonth = repeatPattern.timesPerMonth.toLong()
                rp.preferredDays = repeatPattern.preferredDays.map { it.toString() }

                rp.scheduledPeriods = repeatPattern.scheduledPeriods.entries
                    .associate { it.key.startOfDayUTC().toString() to it.value.map { it.startOfDayUTC() } }
                    .toMutableMap()
            }
        }
        return rp
    }

    private fun createDbReminder(reminder: Reminder): DbReminder {
        val cr = DbReminder()
        cr.message = reminder.message
        when (reminder) {

            is Reminder.Fixed -> {
                cr.type = DbReminder.Type.FIXED.name
                cr.date = reminder.date.startOfDayUTC()
                cr.minute = reminder.time.toMinuteOfDay().toLong()
            }

            is Reminder.Relative -> {
                cr.type = DbReminder.Type.RELATIVE.name
                cr.minutesFromStart = reminder.minutesFromStart
            }
        }
        return cr
    }

    private fun createDbTag(tag: Tag) =
        DbEmbedTag().apply {
            id = tag.id
            name = tag.name
            isFavorite = tag.isFavorite
            color = tag.color.name
            icon = tag.icon?.name
        }

    private fun createTag(dataMap: MutableMap<String, Any?>) =
        with(
            DbEmbedTag(dataMap.withDefault {
                null
            })
        ) {
            Tag(
                id = id,
                name = name,
                color = Color.valueOf(color),
                icon = icon?.let {
                    Icon.valueOf(it)
                },
                isFavorite = isFavorite
            )
        }
}