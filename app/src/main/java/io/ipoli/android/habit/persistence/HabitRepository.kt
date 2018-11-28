package io.ipoli.android.habit.persistence

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import android.arch.persistence.room.ForeignKey.CASCADE
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import io.ipoli.android.common.Reward
import io.ipoli.android.common.datetime.*
import io.ipoli.android.common.distinct
import io.ipoli.android.common.persistence.*
import io.ipoli.android.habit.data.CompletedEntry
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.habit.usecase.CalculateHabitStreakUseCase
import io.ipoli.android.habit.usecase.CalculateHabitSuccessRateUseCase
import io.ipoli.android.pet.Food
import io.ipoli.android.player.data.Player
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.data.persistence.DbBounty
import io.ipoli.android.quest.data.persistence.DbEmbedTag
import io.ipoli.android.tag.Tag
import io.ipoli.android.tag.persistence.RoomTag
import io.ipoli.android.tag.persistence.RoomTagMapper
import io.ipoli.android.tag.persistence.TagDao
import kotlinx.coroutines.experimental.channels.Channel
import org.jetbrains.annotations.NotNull
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import java.util.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 6/16/18.
 */
interface HabitRepository : CollectionRepository<Habit> {

    fun listenForAll(ids: List<String>): Channel<List<Habit>>
    fun findAllForChallenge(challengeId: String): List<Habit>
    fun findNotRemovedForChallenge(challengeId: String): List<Habit>
    fun findHabitsToRemind(remindTime: LocalDateTime): List<Habit>
    fun removeFromChallenge(habitId: String)
    fun findAllNotRemoved(): List<Habit>
}

data class DbHabit(override val map: MutableMap<String, Any?> = mutableMapOf()) :
    FirestoreModel {
    override var id: String by map
    var name: String by map
    var color: String by map
    var icon: String by map
    var tags: Map<String, MutableMap<String, Any?>> by map
    var days: List<String> by map
    var isGood: Boolean by map
    var timesADay: Long by map
    var challengeId: String? by map
    var reminders: List<MutableMap<String, Any?>> by map
    var note: String by map
    var preferenceHistory: Map<String, MutableMap<String, Any?>> by map
    var history: Map<String, MutableMap<String, Any?>> by map
    var currentStreak: Long by map
    var prevStreak: Long by map
    var bestStreak: Long by map
    override var createdAt: Long by map
    override var updatedAt: Long by map
    override var removedAt: Long? by map

    data class PreferenceHistory(val map: MutableMap<String, Any?> = mutableMapOf()) {
        var days: MutableMap<String, List<String>> by map
        var timesADay: MutableMap<String, Long> by map
    }

    data class Reminder(val map: MutableMap<String, Any?> = mutableMapOf()) {
        var message: String by map
        var time: Long by map
    }
}

data class DbCompletedEntry(val map: MutableMap<String, Any?> = mutableMapOf()) {
    var completedAtMinutes: List<Long> by map
    var experience: Long? by map
    var coins: Long? by map
    var bounty: Map<String, Any?>? by map
    var attributePoints: Map<String, Long>? by map
}

@Dao
abstract class HabitDao : BaseDao<RoomHabit>() {
    @Query("SELECT * FROM habits")
    abstract fun findAll(): List<RoomHabit>

    @Query("SELECT * FROM habits WHERE removedAt IS NULL")
    abstract fun findAllNotRemoved(): List<RoomHabit>

    @Query("SELECT * FROM habits WHERE id = :id")
    abstract fun findById(id: String): RoomHabit

    @Query("SELECT * FROM habits WHERE removedAt IS NULL AND id IN (:ids)")
    abstract fun listenForAll(ids: List<String>): LiveData<List<RoomHabit>>

    @Query("SELECT * FROM habits WHERE challengeId = :challengeId")
    abstract fun findAllForChallenge(challengeId: String): List<RoomHabit>

    @Query("SELECT * FROM habits WHERE challengeId = :challengeId AND removedAt IS NULL")
    abstract fun findNotRemovedForChallenge(challengeId: String): List<RoomHabit>

    @Query("SELECT * FROM habits WHERE removedAt IS NULL")
    abstract fun listenForNotRemoved(): LiveData<List<RoomHabit>>

    @Query("SELECT * FROM habits WHERE id = :id")
    abstract fun listenById(id: String): LiveData<RoomHabit>

    @Query(
        """
        SELECT habits.*
        FROM habits
        INNER JOIN entity_reminders ON habits.id = entity_reminders.entityId
        WHERE entity_reminders.entityType = 'HABIT' AND entity_reminders.date = :date AND entity_reminders.millisOfDay = :millisOfDay AND habits.removedAt IS NULL
        """
    )
    abstract fun findAllToRemindAt(date: Long, millisOfDay: Long): List<RoomHabit>

    @Query("UPDATE habits $REMOVE_QUERY")
    abstract fun remove(id: String, currentTimeMillis: Long = System.currentTimeMillis())

    @Query("UPDATE habits $UNDO_REMOVE_QUERY")
    abstract fun undoRemove(id: String, currentTimeMillis: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun saveTags(joins: List<RoomHabit.Companion.RoomTagJoin>)

    @Query("DELETE FROM habit_tag_join WHERE habitId = :habitId")
    abstract fun deleteAllTags(habitId: String)

    @Query("DELETE FROM habit_tag_join WHERE habitId IN (:habitIds)")
    abstract fun deleteAllTags(habitIds: List<String>)

    @Query("SELECT * FROM habits $FIND_SYNC_QUERY")
    abstract fun findAllForSync(lastSync: Long): List<RoomHabit>

    @Query("UPDATE habits SET updatedAt = :currentTimeMillis, challengeId = NULL WHERE id = :id")
    abstract fun removeFromChallenge(
        id: String,
        currentTimeMillis: Long = System.currentTimeMillis()
    )
}

class RoomHabitRepository(
    dao: HabitDao,
    private val entityReminderDao: EntityReminderDao,
    tagDao: TagDao
) : HabitRepository,
    BaseRoomRepositoryWithTags<Habit, RoomHabit, HabitDao, RoomHabit.Companion.RoomTagJoin>(dao) {

    override fun findAllNotRemoved() =
        dao.findAllNotRemoved().map { toEntityObject(it) }

    private val mapper = RoomHabitMapper(tagDao)

    override fun findAllForSync(lastSync: Duration<Millisecond>) =
        dao.findAllForSync(lastSync.millisValue).map { toEntityObject(it) }

    override fun createTagJoin(entityId: String, tagId: String) =
        RoomHabit.Companion.RoomTagJoin(entityId, tagId)

    override fun newIdForEntity(id: String, entity: Habit) = entity.copy(id = id)

    override fun saveTags(joins: List<RoomHabit.Companion.RoomTagJoin>) = dao.saveTags(joins)

    override fun deleteAllTags(entityId: String) = dao.deleteAllTags(entityId)

    override fun findAllForChallenge(challengeId: String) =
        dao.findAllForChallenge(challengeId).map { toEntityObject(it) }

    override fun findNotRemovedForChallenge(challengeId: String) =
        dao.findNotRemovedForChallenge(challengeId).map { toEntityObject(it) }

    override fun findById(id: String) =
        toEntityObject(dao.findById(id))

    override fun findAll() =
        dao.findAll().map { toEntityObject(it) }

    override fun findHabitsToRemind(remindTime: LocalDateTime): List<Habit> {
        val date = remindTime.toLocalDate().startOfDayUTC()
        val millisOfDay = remindTime.toLocalTime().toSecondOfDay().seconds.millisValue
        return dao.findAllToRemindAt(date, millisOfDay).map { toEntityObject(it) }
    }

    override fun listenById(id: String) =
        dao.listenById(id).distinct().notifySingle()

    override fun listenForAll() =
        dao.listenForNotRemoved().notify()

    override fun listenForAll(ids: List<String>) =
        dao.listenForAll(ids).notify()

    override fun removeFromChallenge(habitId: String) {
        val currentTime = System.currentTimeMillis()
        dao.removeFromChallenge(habitId, currentTime)
    }

    override fun remove(entity: Habit) {
        remove(entity.id)
    }

    override fun remove(id: String) {
        dao.remove(id)
    }

    override fun undoRemove(id: String) {
        TODO("Not used")
    }

    override fun save(entities: List<Habit>): List<Habit> {
        val roomHabits = entities.map { toDatabaseObject(it) }
        return bulkSave(roomHabits, entities)
    }

    @Transaction
    private fun bulkSave(
        roomHabits: List<RoomHabit>,
        entities: List<Habit>
    ): List<Habit> {

        dao.saveAll(roomHabits)

        val newEntities = entities.mapIndexed { index, habit ->
            habit.copy(
                id = roomHabits[index].id
            )
        }

        val joins = newEntities.map { e ->
            e.tags.map { t ->
                createTagJoin(e.id, t.id)
            }
        }.flatten()

        saveTags(joins)

        bulkSaveReminders(newEntities)
        return newEntities
    }

    private fun bulkSaveReminders(habits: List<Habit>) {

        val goodHabits = habits.filter { it.isGood }

        purgeReminders(goodHabits.map { it.id })

        val today = LocalDate.now()

        val rems = goodHabits.filter { it.shouldBeDoneOn(today) }
            .flatMap { h ->
                h.reminders.map { r ->
                    createReminderData(r, h)
                }
            }

        if (rems.isEmpty()) {
            return
        }

        entityReminderDao.saveAll(rems)
    }

    override fun save(entity: Habit): Habit {
        val rq = toDatabaseObject(entity)
        return save(rq, entity)
    }

    @Transaction
    private fun save(
        habit: RoomHabit,
        entity: Habit
    ): Habit {
        if (entity.id.isNotBlank()) {
            deleteAllTags(entity.id)
        }
        dao.save(habit)
        val joins = entity.tags.map {
            createTagJoin(habit.id, it.id)
        }
        saveTags(joins)

        val newHabit = entity.copy(id = habit.id)
        saveReminders(newHabit, newHabit.reminders)
        return newHabit
    }

    private fun purgeReminders(
        habitIds: List<String>
    ) {
        val limit = 999
        if (habitIds.size > limit) {
            val rangeCount = habitIds.size / limit
            for (i in 0..rangeCount) {
                val fromIndex = i * limit
                val toIndex = Math.min(fromIndex + limit, habitIds.lastIndex + 1)
                entityReminderDao.purgeForEntities(habitIds.subList(fromIndex, toIndex))
            }
        } else {
            entityReminderDao.purgeForEntities(habitIds)
        }
    }

    private fun saveReminders(habit: Habit, reminders: List<Habit.Reminder>) {
        if (!habit.isGood) {
            return
        }
        purgeHabitReminders(habit.id)
        if (habit.reminders.isNotEmpty() && habit.shouldBeDoneOn(LocalDate.now())) {
            addReminders(reminders, habit)
        }
    }

    private fun addReminders(
        reminders: List<Habit.Reminder>,
        habit: Habit
    ) {
        val rs = reminders.map {
            createReminderData(it, habit)
        }
        entityReminderDao.saveAll(rs)
    }

    private fun createReminderData(reminder: Habit.Reminder, habit: Habit) =
        RoomEntityReminder(
            id = UUID.randomUUID().toString(),
            date = LocalDate.now().startOfDayUTC(),
            millisOfDay = reminder.time.toMillisOfDay(),
            entityType = EntityReminder.EntityType.HABIT.name,
            entityId = habit.id
        )

    private fun purgeHabitReminders(habitId: String) {
        entityReminderDao.purgeForEntity(habitId)
    }

    override fun toEntityObject(dbObject: RoomHabit) =
        mapper.toEntityObject(dbObject)

    override fun toDatabaseObject(entity: Habit) =
        mapper.toDatabaseObject(entity)
}

class RoomHabitMapper(private val tagDao: TagDao) {

    private val tagMapper = RoomTagMapper()

    fun toEntityObject(dbObject: RoomHabit): Habit {
        val h = Habit(
            id = dbObject.id,
            name = dbObject.name,
            color = Color.valueOf(dbObject.color),
            icon = Icon.valueOf(dbObject.icon),
            tags = tagDao.findForHabit(dbObject.id).map { tagMapper.toEntityObject(it) },
            days = dbObject.days.map { DayOfWeek.valueOf(it) }.toSet(),
            isGood = dbObject.isGood,
            timesADay = dbObject.timesADay.toInt(),
            challengeId = dbObject.challengeId,
            reminders = dbObject.reminders.map {
                val dbReminder = DbHabit.Reminder(it)
                Habit.Reminder(dbReminder.message, Time.of(dbReminder.time.toInt()))
            },
            note = dbObject.note,
            preferenceHistory = dbObject.preferenceHistory.let {

                val dp = if (it.isEmpty()) {
                    DbHabit.PreferenceHistory(
                        mutableMapOf(
                            "days" to mapOf(
                                dbObject.createdAt.toString() to dbObject.days
                            ),
                            "timesADay" to mapOf(
                                dbObject.createdAt.toString() to dbObject.timesADay
                            )
                        )
                    )
                } else {
                    DbHabit.PreferenceHistory(it.toMutableMap())
                }

                Habit.PreferenceHistory(
                    days = dp.days.map { d ->
                        d.key.toLong().startOfDayUTC to d.value.map { dow -> DayOfWeek.valueOf(dow) }.toSet()
                    }.toMap().toSortedMap(),
                    timesADay = dp.timesADay.map { td ->
                        td.key.toLong().startOfDayUTC to td.value.toInt()
                    }.toMap().toSortedMap()
                )
            },
            history = dbObject.history.map {
                it.key.toLong().startOfDayUTC to createCompletedEntry(it.value)
            }.toMap(),
            streak = Habit.Streak(0, 0),
            successRate = 0,
            updatedAt = dbObject.updatedAt.instant,
            createdAt = dbObject.createdAt.instant,
            removedAt = dbObject.removedAt?.instant
        )

        return h.copy(
            streak = CalculateHabitStreakUseCase().execute(CalculateHabitStreakUseCase.Params(habit = h)),
            successRate = CalculateHabitSuccessRateUseCase().execute(
                CalculateHabitSuccessRateUseCase.Params(habit = h)
            )
        )
    }

    fun toDatabaseObject(entity: Habit) =
        RoomHabit(
            id = if (entity.id.isEmpty()) UUID.randomUUID().toString() else entity.id,
            name = entity.name,
            color = entity.color.name,
            icon = entity.icon.name,
            days = entity.days.map { it.name },
            isGood = entity.isGood,
            timesADay = entity.timesADay.toLong(),
            challengeId = entity.challengeId,
            note = entity.note,
            reminders = entity.reminders.map { r ->
                DbHabit.Reminder().apply {
                    message = r.message
                    time = r.time.toMinuteOfDay().toLong()
                }.map
            },
            preferenceHistory = entity.preferenceHistory.let {
                mapOf(
                    "days" to it.days.map { d ->
                        d.key.startOfDayUTC().toString() to d.value.map { dow -> dow.name }
                    }.toMap().toMutableMap<String, Any?>(),
                    "timesADay" to it.timesADay.map { td ->
                        td.key.startOfDayUTC().toString() to td.value.toLong()
                    }.toMap().toMutableMap<String, Any?>()
                )
            },
            history = entity.history.map {
                it.key.startOfDayUTC().toString() to createDbCompletedEntry(it.value).map
            }.toMap(),
            currentStreak = 0,
            prevStreak = 0,
            bestStreak = 0,
            updatedAt = System.currentTimeMillis(),
            createdAt = entity.createdAt.toEpochMilli(),
            removedAt = entity.removedAt?.toEpochMilli()
        )

    private fun createDbCompletedEntry(completedEntry: CompletedEntry) =
        DbCompletedEntry().apply {
            val reward = completedEntry.reward

            completedAtMinutes = completedEntry.completedAtTimes.map { it.toMinuteOfDay().toLong() }

            attributePoints =
                reward?.attributePoints?.map { a -> a.key.name to a.value.toLong() }?.toMap()
            experience = reward?.experience?.toLong()
            coins = reward?.coins?.toLong()
            bounty = reward?.let {
                DbBounty().apply {
                    type = when (it.bounty) {
                        is Quest.Bounty.None -> DbBounty.Type.NONE.name
                        is Quest.Bounty.Food -> DbBounty.Type.FOOD.name
                    }
                    name = if (it.bounty is Quest.Bounty.Food) it.bounty.food.name else null
                }.map
            }

        }

    private fun createCompletedEntry(dataMap: MutableMap<String, Any?>): CompletedEntry {
        if (dataMap["experience"] != null) {
            if (!dataMap.containsKey("bounty")) {
                dataMap["bounty"] = DbBounty().apply {
                    type = DbBounty.Type.NONE.name
                    name = null
                }.map
            }

            if (!dataMap.containsKey("attributePoints")) {
                dataMap["attributePoints"] = emptyMap<String, Long>()
            }
        }
        return with(
            DbCompletedEntry(dataMap.withDefault {
                null
            })
        ) {
            CompletedEntry(
                completedAtTimes = completedAtMinutes.map { Time.of(it.toInt()) },
                reward = coins?.let {
                    val dbBounty = DbBounty(bounty!!.toMutableMap())
                    Reward(
                        attributePoints = attributePoints!!.map { a ->
                            Player.AttributeType.valueOf(
                                a.key
                            ) to a.value.toInt()
                        }.toMap(),
                        healthPoints = 0,
                        experience = experience!!.toInt(),
                        coins = coins!!.toInt(),
                        bounty = when {
                            dbBounty.type == DbBounty.Type.NONE.name -> Quest.Bounty.None
                            dbBounty.type == DbBounty.Type.FOOD.name -> Quest.Bounty.Food(
                                Food.valueOf(
                                    dbBounty.name!!
                                )
                            )
                            else -> throw IllegalArgumentException("Unknown bounty type ${dbBounty.type}")
                        }
                    )
                }
            )
        }
    }
}


@Entity(
    tableName = "habits",
    indices = [
        Index("challengeId"),
        Index("updatedAt"),
        Index("removedAt")
    ]
)
data class RoomHabit(
    @NotNull
    @PrimaryKey(autoGenerate = false)
    override val id: String,
    val name: String,
    val color: String,
    val icon: String,
    val days: List<String>,
    val isGood: Boolean,
    val timesADay: Long,
    val challengeId: String?,
    val note: String,
    val reminders: List<MutableMap<String, Any?>>,
    val preferenceHistory: Map<String, MutableMap<String, Any?>>,
    val history: Map<String, MutableMap<String, Any?>>,
    val currentStreak: Long,
    val prevStreak: Long,
    val bestStreak: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val removedAt: Long?
) : RoomEntity {

    companion object {

        @Entity(
            tableName = "habit_tag_join",
            primaryKeys = ["habitId", "tagId"],
            foreignKeys = [
                ForeignKey(
                    entity = RoomHabit::class,
                    parentColumns = ["id"],
                    childColumns = ["habitId"],
                    onDelete = CASCADE
                ),
                ForeignKey(
                    entity = RoomTag::class,
                    parentColumns = ["id"],
                    childColumns = ["tagId"],
                    onDelete = CASCADE
                )
            ],
            indices = [Index("habitId"), Index("tagId")]
        )
        data class RoomTagJoin(val habitId: String, val tagId: String)
    }

}


class FirestoreHabitRepository(
    database: FirebaseFirestore
) : BaseCollectionFirestoreRepository<Habit, DbHabit>(
    database
) {

    override val collectionReference: CollectionReference
        get() = database.collection("players").document(playerId).collection("habits")

    override fun toEntityObject(dataMap: MutableMap<String, Any?>): Habit {

        if (!dataMap.containsKey("preferenceHistory")) {
            dataMap["preferenceHistory"] = mapOf(
                "days" to mapOf(
                    dataMap["createdAt"].toString() to dataMap["days"]
                ),
                "timesADay" to mapOf(
                    dataMap["createdAt"].toString() to dataMap["timesADay"]
                )
            )
        }

        if (!dataMap.containsKey("reminders")) {
            dataMap["reminders"] = emptyList<MutableMap<String, Any?>>()
        }

        val h = DbHabit(dataMap.withDefault {
            null
        })

        return Habit(
            id = h.id,
            name = h.name,
            color = Color.valueOf(h.color),
            icon = Icon.valueOf(h.icon),
            tags = h.tags.values.map {
                createTag(it)
            },
            days = h.days.map { DayOfWeek.valueOf(it) }.toSet(),
            isGood = h.isGood,
            timesADay = h.timesADay.toInt(),
            challengeId = h.challengeId,
            reminders = h.reminders.map {
                val dbReminder = DbHabit.Reminder(it)
                Habit.Reminder(dbReminder.message, Time.of(dbReminder.time.toInt()))
            },
            note = h.note,
            preferenceHistory = h.preferenceHistory.let {
                val dp = DbHabit.PreferenceHistory(it.toMutableMap())
                Habit.PreferenceHistory(
                    days = dp.days.map { d ->
                        d.key.toLong().startOfDayUTC to d.value.map { dow -> DayOfWeek.valueOf(dow) }.toSet()
                    }.toMap().toSortedMap(),
                    timesADay = dp.timesADay.map { td ->
                        td.key.toLong().startOfDayUTC to td.value.toInt()
                    }.toMap().toSortedMap()
                )
            },
            history = h.history.map {
                it.key.toLong().startOfDayUTC to createCompletedEntry(it.value)
            }.toMap(),
            streak = Habit.Streak(0, 0),
            successRate = 0,
            createdAt = h.createdAt.instant,
            updatedAt = h.updatedAt.instant,
            removedAt = h.removedAt?.instant
        )
    }

    override fun toDatabaseObject(entity: Habit): DbHabit {
        val h = DbHabit()
        h.id = entity.id
        h.name = entity.name
        h.color = entity.color.name
        h.icon = entity.icon.name
        h.tags = entity.tags.map { it.id to createDbTag(it).map }.toMap()
        h.days = entity.days.map { it.name }
        h.isGood = entity.isGood
        h.timesADay = entity.timesADay.toLong()
        h.challengeId = entity.challengeId
        h.reminders = entity.reminders.map { r ->
            DbHabit.Reminder().apply {
                message = r.message
                time = r.time.toMinuteOfDay().toLong()
            }.map
        }
        h.note = entity.note
        h.preferenceHistory = entity.preferenceHistory.let {
            mapOf(
                "days" to it.days.map { d ->
                    d.key.startOfDayUTC().toString() to d.value.map { dow -> dow.name }
                }.toMap().toMutableMap<String, Any?>(),
                "timesADay" to it.timesADay.map { td ->
                    td.key.startOfDayUTC().toString() to td.value.toLong()
                }.toMap().toMutableMap<String, Any?>()
            )
        }
        h.history = entity.history.map {
            it.key.startOfDayUTC().toString() to createDbCompletedEntry(it.value).map
        }.toMap()
        h.currentStreak = 0
        h.prevStreak = 0
        h.bestStreak = 0
        h.updatedAt = entity.updatedAt.toEpochMilli()
        h.createdAt = entity.createdAt.toEpochMilli()
        h.removedAt = entity.removedAt?.toEpochMilli()
        return h
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

    private fun createDbCompletedEntry(completedEntry: CompletedEntry) =
        DbCompletedEntry().apply {
            val reward = completedEntry.reward

            completedAtMinutes = completedEntry.completedAtTimes.map { it.toMinuteOfDay().toLong() }

            attributePoints =
                reward?.attributePoints?.map { a -> a.key.name to a.value.toLong() }?.toMap()
            experience = reward?.experience?.toLong()
            coins = reward?.coins?.toLong()
            bounty = reward?.let {
                DbBounty().apply {
                    type = when (it.bounty) {
                        is Quest.Bounty.None -> DbBounty.Type.NONE.name
                        is Quest.Bounty.Food -> DbBounty.Type.FOOD.name
                    }
                    name = if (it.bounty is Quest.Bounty.Food) it.bounty.food.name else null
                }.map
            }

        }

    private fun createCompletedEntry(dataMap: MutableMap<String, Any?>): CompletedEntry {
        if (dataMap["experience"] != null) {
            if (!dataMap.containsKey("bounty")) {
                dataMap["bounty"] = DbBounty().apply {
                    type = DbBounty.Type.NONE.name
                    name = null
                }.map
            }

            if (!dataMap.containsKey("attributePoints")) {
                dataMap["attributePoints"] = emptyMap<String, Long>()
            }
        }
        return with(
            DbCompletedEntry(dataMap.withDefault {
                null
            })
        ) {
            CompletedEntry(
                completedAtTimes = completedAtMinutes.map { Time.of(it.toInt()) },
                reward = coins?.let {
                    val dbBounty = DbBounty(bounty!!.toMutableMap())
                    Reward(
                        attributePoints = attributePoints!!.map { a ->
                            Player.AttributeType.valueOf(
                                a.key
                            ) to a.value.toInt()
                        }.toMap(),
                        healthPoints = 0,
                        experience = experience!!.toInt(),
                        coins = coins!!.toInt(),
                        bounty = when {
                            dbBounty.type == DbBounty.Type.NONE.name -> Quest.Bounty.None
                            dbBounty.type == DbBounty.Type.FOOD.name -> Quest.Bounty.Food(
                                Food.valueOf(
                                    dbBounty.name!!
                                )
                            )
                            else -> throw IllegalArgumentException("Unknown bounty type ${dbBounty.type}")
                        }
                    )
                }
            )
        }
    }
}