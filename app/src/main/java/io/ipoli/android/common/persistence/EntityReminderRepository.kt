package io.ipoli.android.common.persistence

import android.arch.persistence.room.*
import io.ipoli.android.common.datetime.*
import io.ipoli.android.common.datetime.Duration
import org.jetbrains.annotations.NotNull
import org.threeten.bp.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 06/26/2018.
 */

interface EntityReminderRepository {
    fun findNextReminderTime(afterTime: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())): LocalDateTime?
    fun snooze(
        date: LocalDate,
        time: Time,
        entityType: EntityReminder.EntityType,
        entityId: String,
        duration: Duration<Minute>
    )

    fun save(entities: List<EntityReminder>)
}

class RoomEntityReminderRepository(private val dao: EntityReminderDao) : EntityReminderRepository {

    override fun findNextReminderTime(afterTime: ZonedDateTime): LocalDateTime? {
        val currentDateMillis = afterTime.toLocalDate().startOfDayUTC()

        val millisOfDay = afterTime.toLocalTime().toSecondOfDay().seconds.millisValue

        val r = dao.findAfter(currentDateMillis, millisOfDay) ?: return null

        return LocalDateTime.of(
            r.date.startOfDayUTC,
            LocalTime.ofSecondOfDay(r.millisOfDay.milliseconds.asSeconds.longValue)
        )
    }

    override fun snooze(
        date: LocalDate,
        time: Time,
        entityType: EntityReminder.EntityType,
        entityId: String,
        duration: Duration<Minute>
    ) {
        val newTime = time.plus(duration.intValue)
        val newDate = if (newTime < time)
            date.plusDays(1)
        else
            date

        dao.snooze(
            entityId = entityId,
            entityType = entityType.name,
            oldDate = date.startOfDayUTC(),
            oldMillisOfDay = time.toMillisOfDay(),
            newDate = newDate.startOfDayUTC(),
            newMillisOfDay = newTime.toMillisOfDay()
        )
    }

    override fun save(entities: List<EntityReminder>) {
        dao.saveAll(entities.map { toDatabaseObject(it) })
    }

    private fun toDatabaseObject(entityReminder: EntityReminder) =
        RoomEntityReminder(
            id = entityReminder.id,
            date = entityReminder.date.startOfDayUTC(),
            millisOfDay = entityReminder.time.toMillisOfDay(),
            entityType = entityReminder.entityType.name,
            entityId = entityReminder.entityId
        )
}

@Dao
interface EntityReminderDao {

    @Insert(onConflict = OnConflictStrategy.FAIL)
    fun save(entity: RoomEntityReminder)

    @Insert(onConflict = OnConflictStrategy.FAIL)
    fun saveAll(entities: List<RoomEntityReminder>)

    @Query("DELETE FROM entity_reminders WHERE entityId = :entityId")
    fun purgeForEntity(entityId: String)

    @Query("DELETE FROM entity_reminders WHERE entityId IN (:entityIds)")
    fun purgeForEntities(entityIds: List<String>)

    @Query(
        """
        UPDATE entity_reminders
        SET date = :newDate, millisOfDay = :newMillisOfDay
        WHERE entityType = :entityType AND entityId = :entityId AND date = :oldDate AND millisOfDay = :oldMillisOfDay
        """
    )
    fun snooze(
        entityId: String,
        entityType: String,
        oldDate: Long,
        oldMillisOfDay: Long,
        newDate: Long,
        newMillisOfDay: Long
    )

    @Query(
        """
        SELECT *
        FROM entity_reminders
        WHERE date >= :date AND millisOfDay > :millisOfDay
        ORDER BY date ASC, millisOfDay ASC
        LIMIT 1
        """
    )
    fun findAfter(date: Long, millisOfDay: Long): RoomEntityReminder?
}

@Entity(
    tableName = "entity_reminders",
    indices = [
        Index("date"),
        Index("millisOfDay"),
        Index("entityType"),
        Index("entityId")
    ]
)
data class RoomEntityReminder(
    @NotNull
    @PrimaryKey(autoGenerate = false)
    val id: String,
    val date: Long,
    val millisOfDay: Long,
    val entityType: String,
    val entityId: String
)

data class EntityReminder(
    val id: String,
    val date: LocalDate,
    val time: Time,
    val entityType: EntityType,
    val entityId: String
) {
    enum class EntityType { QUEST, HABIT }
}