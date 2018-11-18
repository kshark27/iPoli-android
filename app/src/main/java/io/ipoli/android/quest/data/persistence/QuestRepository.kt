package io.ipoli.android.quest.data.persistence

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*
import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey.CASCADE
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import io.ipoli.android.common.Reward
import io.ipoli.android.common.datetime.*
import io.ipoli.android.common.distinct
import io.ipoli.android.common.persistence.*
import io.ipoli.android.dailychallenge.data.DailyChallenge
import io.ipoli.android.pet.Food
import io.ipoli.android.player.data.Player
import io.ipoli.android.quest.*
import io.ipoli.android.quest.subquest.SubQuest
import io.ipoli.android.tag.Tag
import io.ipoli.android.tag.persistence.RoomTag
import io.ipoli.android.tag.persistence.RoomTagMapper
import io.ipoli.android.tag.persistence.TagDao
import kotlinx.coroutines.experimental.channels.Channel
import org.jetbrains.annotations.NotNull
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.LocalTime
import java.util.*

interface QuestRepository : CollectionRepository<Quest> {

    fun listenForScheduledBetween(
        startDate: LocalDate,
        endDate: LocalDate
    ): Channel<List<Quest>>

    fun listenForScheduledAt(
        date: LocalDate
    ): Channel<List<Quest>>

    fun listenByTag(tagId: String): Channel<List<Quest>>

    fun listenForAllUnscheduled(): Channel<List<Quest>>

    fun findRandomUnscheduledAndUncompleted(count: Int): List<Quest>

    fun findScheduledAt(date: LocalDate): List<Quest>

    fun findScheduledBetween(startDate: LocalDate, endDate: LocalDate): List<Quest>

    fun findScheduledForRepeatingQuestBetween(
        repeatingQuestId: String,
        start: LocalDate,
        end: LocalDate
    ): List<Quest>

    fun findQuestsToRemind(remindTime: LocalDateTime): List<Quest>
    fun findCompletedForDate(date: LocalDate): List<Quest>
    fun findStartedQuests(): List<Quest>
    fun findLastScheduledDate(currentDate: LocalDate, maxQuests: Int): LocalDate?
    fun findFirstScheduledDate(currentDate: LocalDate, maxQuests: Int): LocalDate?
    fun findNextScheduledNotCompletedForRepeatingQuest(
        repeatingQuestId: String,
        currentDate: LocalDate
    ): Quest?

    fun findLastCompletedForRepeatingQuest(repeatingQuestId: String): Quest?

    fun findNextScheduledNotCompletedForChallenge(
        challengeId: String,
        currentDate: LocalDate
    ): Quest?

    fun findAllForRepeatingQuest(
        repeatingQuestId: String,
        includeRemoved: Boolean = true
    ): List<Quest>

    fun findOriginalScheduledForRepeatingQuestAtDate(
        repeatingQuestId: String,
        currentDate: LocalDate
    ): Quest?

    fun findCompletedCountForRepeatingQuestInPeriod(
        repeatingQuestId: String,
        start: LocalDate,
        end: LocalDate?
    ): Int

    fun findScheduledForRepeatingQuestInPeriod(
        repeatingQuestId: String,
        start: LocalDate,
        end: LocalDate? = null
    ): List<Quest>

    fun removeAllNotCompletedForRepeating(
        repeatingQuestId: String,
        startDate: LocalDate = LocalDate.now()
    )

    fun findNotCompletedNotForChallengeNotRepeating(
        challengeId: String,
        start: LocalDate = LocalDate.now()
    ): List<Quest>

    fun findAllForChallengeNotRepeating(challengeId: String): List<Quest>

    fun findAllForChallenge(challengeId: String): List<Quest>
    fun findNotRemovedForChallenge(challengeId: String): List<Quest>
    fun findAllForRepeatingQuestAfterDate(
        repeatingQuestId: String,
        includeRemoved: Boolean,
        currentDate: LocalDate = LocalDate.now()
    ): List<Quest>

    fun findCountForTag(tagId: String): Int

    fun findQuestsForDailyChallenge(dailyChallenge: DailyChallenge): List<Quest>

    fun findOriginallyScheduledOrCompletedInPeriod(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Quest>

    fun findCompletedInPeriod(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Quest>

    fun remove(questIds: List<String>)

    fun removeFromRepeatingQuest(questId: String, newRepeatingQuestId: String)

    fun removeFromChallenge(quest: Quest): Quest

    fun removeFromChallenge(quests: List<Quest>): List<Quest>

    fun findCompletedInPeriodOfFriend(
        friendId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Quest>

    fun findCountForRepeatingQuestInPeriod(
        repeatingQuestId: String,
        start: LocalDate,
        end: LocalDate
    ): Int
}

@Dao
abstract class QuestDao : BaseDao<RoomQuest>() {
    @Query("SELECT * FROM quests")
    abstract fun findAll(): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE id = :id")
    abstract fun findById(id: String): RoomQuest

    @Query("SELECT * FROM quests WHERE challengeId = :challengeId")
    abstract fun findAllForChallenge(challengeId: String): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE challengeId = :challengeId AND removedAt IS NULL")
    abstract fun findNotRemovedForChallenge(challengeId: String): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE removedAt IS NULL")
    abstract fun listenForNotRemoved(): LiveData<List<RoomQuest>>

    @Query("SELECT * FROM quests WHERE id = :id")
    abstract fun listenById(id: String): LiveData<RoomQuest>

    @Query(
        """
        SELECT *
        FROM quests
        WHERE removedAt IS NULL AND scheduledDate >= :startDate AND scheduledDate <= :endDate
        ORDER BY scheduledDate ASC, startMinute ASC
        """
    )
    abstract fun listenForScheduledBetween(
        startDate: Long,
        endDate: Long
    ): LiveData<List<RoomQuest>>

    @Query("SELECT * FROM quests WHERE removedAt IS NULL AND scheduledDate = :date ORDER BY scheduledDate ASC, startMinute ASC")
    abstract fun listenForScheduledAt(
        date: Long
    ): LiveData<List<RoomQuest>>

    @Query("SELECT * FROM quests WHERE removedAt IS NULL AND scheduledDate = :date ORDER BY scheduledDate ASC, startMinute ASC")
    abstract fun findScheduledAt(
        date: Long
    ): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE removedAt IS NULL AND scheduledDate > :date ORDER BY scheduledDate ASC LIMIT :maxQuests")
    abstract fun findScheduledAfter(
        date: Long,
        maxQuests: Int
    ): List<RoomQuest>


    @Query("SELECT * FROM quests WHERE removedAt IS NULL AND scheduledDate < :date ORDER BY scheduledDate DESC LIMIT :maxQuests")
    abstract fun findScheduledBefore(
        date: Long,
        maxQuests: Int
    ): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE removedAt IS NULL AND completedAtDate IS NULL ORDER BY RANDOM() LIMIT :count")
    abstract fun findRandomUnscheduledAndUncompleted(count: Int): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE removedAt IS NULL AND completedAtDate = :date")
    abstract fun findCompletedForDate(date: Long): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE removedAt IS NULL AND completedAtDate IS NULL AND timeRangeCount > 0")
    abstract fun findStarted(): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE removedAt IS NULL AND completedAtDate >= :startDate AND completedAtDate <= :endDate")
    abstract fun findCompletedInPeriod(startDate: Long, endDate: Long): List<RoomQuest>

    @Query("SELECT DISTINCT * FROM quests WHERE removedAt IS NULL AND ((completedAtDate >= :startDate AND completedAtDate <= :endDate) OR (originalScheduledDate >= :startDate AND originalScheduledDate <= :endDate))")
    abstract fun findOriginallyScheduledOrCompletedInPeriod(
        startDate: Long,
        endDate: Long
    ): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE repeatingQuestId LIKE :repeatingQuestId AND scheduledDate >= :startDate AND scheduledDate <= :endDate")
    abstract fun findScheduledForRepeatingQuestBetween(
        repeatingQuestId: String,
        startDate: Long,
        endDate: Long
    ): List<RoomQuest>

    @Query(
        """
        SELECT *
        FROM quests
        WHERE removedAt IS NULL AND repeatingQuestId = :repeatingQuestId AND scheduledDate >= :date AND completedAtDate IS NULL
        ORDER BY scheduledDate ASC
        LIMIT 1
        """
    )
    abstract fun findNextScheduledNotCompletedForRepeatingQuest(
        repeatingQuestId: String,
        date: Long
    ): List<RoomQuest>

    @Query(
        """
        SELECT *
        FROM quests
        WHERE removedAt IS NULL AND repeatingQuestId = :repeatingQuestId AND completedAtDate IS NOT NULL AND scheduledDate IS NOT NULL
        ORDER BY scheduledDate DESC
        LIMIT 1
        """
    )
    abstract fun findLastCompletedForRepeatingQuest(repeatingQuestId: String): List<RoomQuest>

    @Query(
        """
        SELECT *
        FROM quests
        WHERE removedAt IS NULL AND challengeId = :challengeId AND scheduledDate >= :date AND completedAtDate IS NULL
        ORDER BY scheduledDate ASC
        LIMIT 1
        """
    )
    abstract fun findNextScheduledNotCompletedForChallenge(
        challengeId: String,
        date: Long
    ): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE repeatingQuestId = :repeatingQuestId")
    abstract fun findAllForRepeatingQuestIncludingRemoved(repeatingQuestId: String): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE removedAt is NULL AND repeatingQuestId = :repeatingQuestId")
    abstract fun findAllForRepeatingQuest(repeatingQuestId: String): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE repeatingQuestId = :repeatingQuestId AND originalScheduledDate = :date LIMIT 1")
    abstract fun findOriginalScheduledForRepeatingQuestAtDate(
        repeatingQuestId: String,
        date: Long
    ): List<RoomQuest>

    @Query("SELECT COUNT(*) FROM quests WHERE repeatingQuestId = :repeatingQuestId AND completedAtDate >= :startDate")
    abstract fun findCompletedCountForRepeatingQuestAfter(
        repeatingQuestId: String,
        startDate: Long
    ): Int

    @Query("SELECT COUNT(*) FROM quests WHERE repeatingQuestId = :repeatingQuestId AND completedAtDate >= :startDate AND completedAtDate <= :endDate")
    abstract fun findCompletedCountForRepeatingQuestInPeriod(
        repeatingQuestId: String,
        startDate: Long,
        endDate: Long
    ): Int

    @Query("SELECT COUNT(*) FROM quests WHERE repeatingQuestId = :repeatingQuestId AND scheduledDate >= :startDate AND scheduledDate <= :endDate")
    abstract fun findCountForRepeatingQuestInPeriod(
        repeatingQuestId: String,
        startDate: Long,
        endDate: Long
    ): Int

    @Query("SELECT * FROM quests WHERE repeatingQuestId = :repeatingQuestId AND scheduledDate >= :startDate AND removedAt IS NULL")
    abstract fun findScheduledForRepeatingQuestAfter(
        repeatingQuestId: String,
        startDate: Long
    ): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE repeatingQuestId = :repeatingQuestId AND scheduledDate >= :startDate AND scheduledDate <= :endDate AND removedAt IS NULL")
    abstract fun findScheduledForRepeatingQuestInPeriod(
        repeatingQuestId: String,
        startDate: Long,
        endDate: Long
    ): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE removedAt IS NULL and repeatingQuestId IS NULL and completedAtDate IS NULL AND scheduledDate >= :startDate AND (challengeId IS NULL OR challengeId != :challengeId)")
    abstract fun findNotCompletedNotForChallengeNotRepeating(
        challengeId: String,
        startDate: Long
    ): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE removedAt IS NULL and repeatingQuestId IS NULL and challengeId = :challengeId")
    abstract fun findAllForChallengeNotRepeating(challengeId: String): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE repeatingQuestId = :repeatingQuestId AND scheduledDate >= :date")
    abstract fun findAllForRepeatingQuestAfterDateWithRemoved(
        repeatingQuestId: String,
        date: Long
    ): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE removedAt IS NULL AND repeatingQuestId = :repeatingQuestId AND scheduledDate >= :date")
    abstract fun findAllForRepeatingQuestAfterDate(
        repeatingQuestId: String,
        date: Long
    ): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE removedAt IS NULL AND id IN (:ids)")
    abstract fun findAll(ids: List<String>): List<RoomQuest>

    @Query("SELECT * FROM quests WHERE removedAt IS NULL AND scheduledDate IS NULL ORDER BY dueDate ASC")
    abstract fun listenForAllUnscheduled(): LiveData<List<RoomQuest>>

    @Query("UPDATE quests $REMOVE_QUERY")
    abstract fun remove(id: String, currentTimeMillis: Long = System.currentTimeMillis())

    @Query("UPDATE quests SET removedAt = null, updatedAt = :currentTimeMillis, repeatingQuestId = :newRepeatingQuestId WHERE id = :id")
    abstract fun undoRemove(
        id: String,
        newRepeatingQuestId: String?,
        currentTimeMillis: Long = System.currentTimeMillis()
    )

    @Query("UPDATE quests SET removedAt = :currentTimeMillis, updatedAt = :currentTimeMillis WHERE id IN (:ids)")
    abstract fun remove(ids: List<String>, currentTimeMillis: Long = System.currentTimeMillis())

    @Query("UPDATE quests SET removedAt = :currentTimeMillis, updatedAt = :currentTimeMillis, repeatingQuestId = NULL WHERE id IN (:ids)")
    abstract fun removeAndClearRepeatingQuestId(
        ids: List<String>,
        currentTimeMillis: Long = System.currentTimeMillis()
    )

    @Query("UPDATE quests SET updatedAt = :currentTimeMillis, challengeId = NULL WHERE id = :id")
    abstract fun removeFromChallenge(
        id: String,
        currentTimeMillis: Long = System.currentTimeMillis()
    )

    @Query("UPDATE quests SET updatedAt = :currentTimeMillis, challengeId = NULL WHERE id IN (:ids)")
    abstract fun removeFromChallenge(
        ids: List<String>,
        currentTimeMillis: Long = System.currentTimeMillis()
    )

    @Query("SELECT * FROM quests WHERE repeatingQuestId = :repeatingQuestId AND completedAtDate IS NULL AND scheduledDate >= :date ")
    abstract fun findAllNotCompletedForRepeating(
        repeatingQuestId: String,
        date: Long
    ): List<RoomQuest>

    @Query(
        """
        SELECT quests.*
        FROM quests
        INNER JOIN entity_reminders ON quests.id = entity_reminders.entityId
        WHERE entity_reminders.entityType = 'QUEST' AND entity_reminders.date = :date AND entity_reminders.millisOfDay = :millisOfDay AND quests.removedAt IS NULL
        """
    )
    abstract fun findAllToRemindAt(date: Long, millisOfDay: Long): List<RoomQuest>

    @Query(
        """
        SELECT quests.*
        FROM quests
        INNER JOIN quest_tag_join ON quests.id = quest_tag_join.questId
        WHERE quest_tag_join.tagId = :tagId AND quests.removedAt IS NULL
        """
    )
    abstract fun listenByTag(tagId: String): LiveData<List<RoomQuest>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun saveTags(joins: List<RoomQuest.Companion.RoomTagJoin>)

    @Query("DELETE FROM quest_tag_join WHERE questId = :questId")
    abstract fun deleteAllTags(questId: String)

    @Query("DELETE FROM quest_tag_join WHERE questId IN (:questIds)")
    abstract fun deleteAllTags(questIds: List<String>)

    @Query(
        """
        SELECT COUNT(*)
        FROM quest_tag_join
        INNER JOIN quests ON quests.id = quest_tag_join.questId
        WHERE tagId = :tagId AND quests.completedAtDate is NULL AND quests.removedAt is NULL
        """
    )
    abstract fun countForTag(tagId: String): Int

    @Query("SELECT * FROM quests $FIND_SYNC_QUERY")
    abstract fun findAllForSync(lastSync: Long): List<RoomQuest>

    @Query(
        """
        UPDATE quests
        SET removedAt = :currentTimeMillis, updatedAt = :currentTimeMillis, repeatingQuestId = :newRepeatingQuestId
        WHERE id = :id
        """
    )
    abstract fun removeFromRepeatingQuest(
        id: String,
        newRepeatingQuestId: String,
        currentTimeMillis: Long = System.currentTimeMillis()
    )

    @Query(
        """
        SELECT *
        FROM quests
        WHERE removedAt IS NULL AND scheduledDate >= :startDate AND scheduledDate <= :endDate
        ORDER BY scheduledDate ASC, startMinute ASC
        """
    )
    abstract fun findScheduledBetween(startDate: Long, endDate: Long): List<RoomQuest>
}

class RoomQuestRepository(
    dao: QuestDao,
    private val entityReminderDao: EntityReminderDao,
    private val tagDao: TagDao,
    private val remoteDatabase: FirebaseFirestore
) : BaseRoomRepositoryWithTags<Quest, RoomQuest, QuestDao, RoomQuest.Companion.RoomTagJoin>(dao),
    QuestRepository {

    override fun findAllForSync(lastSync: Duration<Millisecond>) =
        dao.findAllForSync(lastSync.millisValue).map { toEntityObject(it) }

    override fun createTagJoin(entityId: String, tagId: String) =
        RoomQuest.Companion.RoomTagJoin(entityId, tagId)

    override fun newIdForEntity(id: String, entity: Quest) = entity.copy(id = id)

    override fun saveTags(joins: List<RoomQuest.Companion.RoomTagJoin>) = dao.saveTags(joins)

    override fun deleteAllTags(entityId: String) = dao.deleteAllTags(entityId)

    override fun listenForScheduledBetween(
        startDate: LocalDate,
        endDate: LocalDate
    ) =
        dao.listenForScheduledBetween(startDate.startOfDayUTC(), endDate.startOfDayUTC())
            .notify()

    override fun listenForScheduledAt(
        date: LocalDate
    ) =
        dao.listenForScheduledAt(date.startOfDayUTC())
            .notify()

    override fun listenByTag(
        tagId: String
    ) =
        dao.listenByTag(tagId).notify()

    override fun listenForAllUnscheduled() =
        dao.listenForAllUnscheduled().notify()

    override fun findRandomUnscheduledAndUncompleted(count: Int) =
        dao.findRandomUnscheduledAndUncompleted(count).map { toEntityObject(it) }

    override fun findScheduledAt(date: LocalDate) =
        dao.findScheduledAt(date.startOfDayUTC()).map { toEntityObject(it) }

    override fun findScheduledBetween(startDate: LocalDate, endDate: LocalDate) =
        dao.findScheduledBetween(
            startDate.startOfDayUTC(),
            endDate.startOfDayUTC()
        ).map { toEntityObject(it) }

    override fun findScheduledForRepeatingQuestBetween(
        repeatingQuestId: String,
        start: LocalDate,
        end: LocalDate
    ) =
        dao.findScheduledForRepeatingQuestBetween(
            "$repeatingQuestId%",
            start.startOfDayUTC(),
            end.startOfDayUTC()
        ).map { toEntityObject(it) }

    override fun findQuestsToRemind(remindTime: LocalDateTime): List<Quest> {
        val date = remindTime.toLocalDate().startOfDayUTC()
        val millisOfDay = remindTime.toLocalTime().toSecondOfDay().seconds.millisValue
        return dao.findAllToRemindAt(date, millisOfDay).map { toEntityObject(it) }
    }

    override fun findCompletedForDate(date: LocalDate) =
        dao.findCompletedForDate(date.startOfDayUTC()).map { toEntityObject(it) }

    override fun findStartedQuests() = dao.findStarted().map { toEntityObject(it) }

    override fun findLastScheduledDate(currentDate: LocalDate, maxQuests: Int) =
        dao
            .findScheduledAfter(currentDate.startOfDayUTC(), maxQuests)
            .lastOrNull()?.let {
                it.scheduledDate!!.startOfDayUTC
            }

    override fun findFirstScheduledDate(currentDate: LocalDate, maxQuests: Int) =
        dao
            .findScheduledBefore(currentDate.startOfDayUTC(), maxQuests)
            .lastOrNull()?.let {
                it.scheduledDate!!.startOfDayUTC
            }

    override fun findNextScheduledNotCompletedForRepeatingQuest(
        repeatingQuestId: String,
        currentDate: LocalDate
    ) =
        dao.findNextScheduledNotCompletedForRepeatingQuest(
            repeatingQuestId,
            currentDate.startOfDayUTC()
        ).firstOrNull()?.let { toEntityObject(it) }

    override fun findLastCompletedForRepeatingQuest(repeatingQuestId: String) =
        dao
            .findLastCompletedForRepeatingQuest(repeatingQuestId)
            .firstOrNull()?.let { toEntityObject(it) }

    override fun findNextScheduledNotCompletedForChallenge(
        challengeId: String,
        currentDate: LocalDate
    ) =
        dao.findNextScheduledNotCompletedForChallenge(
            challengeId,
            currentDate.startOfDayUTC()
        ).firstOrNull()?.let { toEntityObject(it) }

    override fun findAllForRepeatingQuest(
        repeatingQuestId: String,
        includeRemoved: Boolean
    ) =
        if (includeRemoved) {
            dao.findAllForRepeatingQuestIncludingRemoved(repeatingQuestId)
        } else {
            dao.findAllForRepeatingQuest(repeatingQuestId)
        }.map { toEntityObject(it) }

    override fun findOriginalScheduledForRepeatingQuestAtDate(
        repeatingQuestId: String,
        currentDate: LocalDate
    ) =
        dao.findOriginalScheduledForRepeatingQuestAtDate(
            repeatingQuestId,
            currentDate.startOfDayUTC()
        ).firstOrNull()?.let {
            toEntityObject(it)
        }

    override fun findCompletedCountForRepeatingQuestInPeriod(
        repeatingQuestId: String,
        start: LocalDate,
        end: LocalDate?
    ) =
        if (end == null) {
            dao.findCompletedCountForRepeatingQuestAfter(repeatingQuestId, start.startOfDayUTC())
        } else {
            dao.findCompletedCountForRepeatingQuestInPeriod(
                repeatingQuestId,
                start.startOfDayUTC(),
                end.startOfDayUTC()
            )
        }

    override fun findCountForRepeatingQuestInPeriod(
        repeatingQuestId: String,
        start: LocalDate,
        end: LocalDate
    ) = dao.findCountForRepeatingQuestInPeriod(
        repeatingQuestId,
        start.startOfDayUTC(),
        end.startOfDayUTC()
    )

    override fun findScheduledForRepeatingQuestInPeriod(
        repeatingQuestId: String,
        start: LocalDate,
        end: LocalDate?
    ) =
        if (end == null) {
            dao.findScheduledForRepeatingQuestAfter(repeatingQuestId, start.startOfDayUTC())
        } else {
            dao.findScheduledForRepeatingQuestInPeriod(
                repeatingQuestId,
                start.startOfDayUTC(),
                end.startOfDayUTC()
            )
        }.map { toEntityObject(it) }

    override fun removeAllNotCompletedForRepeating(repeatingQuestId: String, startDate: LocalDate) {
        val rqs = dao.findAllNotCompletedForRepeating(repeatingQuestId, startDate.startOfDayUTC())
        removeWithRepeatingQuestIdAndReminders(rqs.map { it.id })
    }

    @Transaction
    private fun removeWithRepeatingQuestIdAndReminders(questIds: List<String>) {
        dao.removeAndClearRepeatingQuestId(questIds)
        purgeReminders(questIds)
    }

    override fun removeFromRepeatingQuest(questId: String, newRepeatingQuestId: String) {
        dao.removeFromRepeatingQuest(questId, newRepeatingQuestId)
    }

    override fun removeFromChallenge(quest: Quest): Quest {
        val currentTime = System.currentTimeMillis()
        dao.removeFromChallenge(quest.id, currentTime)
        return quest.copy(
            challengeId = null,
            updatedAt = currentTime.instant
        )
    }

    override fun removeFromChallenge(quests: List<Quest>): List<Quest> {
        val currentTime = System.currentTimeMillis()
        dao.removeFromChallenge(quests.map { it.id })
        return quests.map {
            it.copy(
                challengeId = null,
                updatedAt = currentTime.instant
            )
        }
    }

    override fun findNotCompletedNotForChallengeNotRepeating(
        challengeId: String,
        start: LocalDate
    ) =
        dao
            .findNotCompletedNotForChallengeNotRepeating(challengeId, start.startOfDayUTC())
            .map { toEntityObject(it) }

    override fun findAllForChallengeNotRepeating(challengeId: String) =
        dao.findAllForChallengeNotRepeating(challengeId).map { toEntityObject(it) }

    override fun findAllForChallenge(challengeId: String) =
        dao.findAllForChallenge(challengeId).map { toEntityObject(it) }

    override fun findNotRemovedForChallenge(challengeId: String) =
        dao.findNotRemovedForChallenge(challengeId).map { toEntityObject(it) }

    override fun findAllForRepeatingQuestAfterDate(
        repeatingQuestId: String,
        includeRemoved: Boolean,
        currentDate: LocalDate
    ) =
        if (includeRemoved) {
            dao.findAllForRepeatingQuestAfterDateWithRemoved(
                repeatingQuestId,
                currentDate.startOfDayUTC()
            )
        } else {
            dao.findAllForRepeatingQuestAfterDate(repeatingQuestId, currentDate.startOfDayUTC())
        }
            .map { toEntityObject(it) }

    override fun findCountForTag(tagId: String): Int {
        return dao.countForTag(tagId)
    }

    override fun findQuestsForDailyChallenge(dailyChallenge: DailyChallenge): List<Quest> {
        if (dailyChallenge.questIds.isEmpty()) {
            return emptyList()
        }
        return dao
            .findAll(dailyChallenge.questIds)
            .map { toEntityObject(it) }
    }

    override fun findOriginallyScheduledOrCompletedInPeriod(
        startDate: LocalDate,
        endDate: LocalDate
    ) =
        dao.findOriginallyScheduledOrCompletedInPeriod(
            startDate.startOfDayUTC(),
            endDate.startOfDayUTC()
        ).map { toEntityObject(it) }

    override fun findCompletedInPeriod(startDate: LocalDate, endDate: LocalDate): List<Quest> =
        dao.findCompletedInPeriod(
            startDate.startOfDayUTC(),
            endDate.startOfDayUTC()
        ).map { toEntityObject(it) }

    override fun findCompletedInPeriodOfFriend(
        friendId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ) =
        FirestoreQuestRepository(remoteDatabase).findCompletedInPeriod(friendId, startDate, endDate)

    override fun remove(questIds: List<String>) {
        dao.remove(questIds)
        purgeReminders(questIds)
    }

    override fun save(entities: List<Quest>): List<Quest> {
        val roomQs = entities.map { toDatabaseObject(it) }
        return bulkSave(roomQs, entities)
    }

    @Transaction
    private fun bulkSave(
        roomQs: List<RoomQuest>,
        entities: List<Quest>
    ): List<Quest> {

        dao.saveAll(roomQs)

        val newEntities = entities.mapIndexed { index, quest ->
            quest.copy(
                id = roomQs[index].id
            )
        }

        val joins = newEntities.map { e ->
            e.tags.map { t ->
                createTagJoin(e.id, t.id)
            }
        }.flatten()

        saveTags(joins)

        val questToReminder = newEntities.map { q -> q.reminders.map { Pair(q, it) } }.flatten()
        bulkPurgeReminders(newEntities.map { it.id }, questToReminder)
        return newEntities
    }

    private fun bulkPurgeReminders(
        questIds: List<String>,
        questToReminder: List<Pair<Quest, Reminder>>
    ) {

        purgeReminders(questIds)

        val rems = questToReminder.filter { !it.first.isCompleted && !it.first.isStarted }
            .mapNotNull { createReminderData(it.second, it.first) }

        entityReminderDao.saveAll(rems)
    }

    override fun save(entity: Quest): Quest {
        val rq = toDatabaseObject(entity)
        return save(rq, entity)
    }

    @Transaction
    private fun save(
        rq: RoomQuest,
        entity: Quest
    ): Quest {
        if (entity.id.isNotBlank()) {
            deleteAllTags(entity.id)
        }
        dao.save(rq)
        val joins = entity.tags.map {
            createTagJoin(rq.id, it.id)
        }
        saveTags(joins)

        val newQuest = entity.copy(id = rq.id)
        saveReminders(newQuest, newQuest.reminders)
        return newQuest
    }

    private fun purgeReminders(
        questIds: List<String>
    ) {
        val limit = 999
        if (questIds.size > limit) {
            val rangeCount = questIds.size / limit
            for (i in 0..rangeCount) {
                val fromIndex = i * limit
                val toIndex = Math.min(fromIndex + limit, questIds.lastIndex + 1)
                entityReminderDao.purgeForEntities(questIds.subList(fromIndex, toIndex))
            }
        } else {
            entityReminderDao.purgeForEntities(questIds)
        }
    }

    private fun saveReminders(quest: Quest, reminders: List<Reminder>) {
        purgeQuestReminders(quest.id)
        if (!quest.isCompleted && !quest.isStarted) {
            addReminders(reminders, quest)
        }
    }

    private fun addReminders(
        reminders: List<Reminder>,
        quest: Quest
    ) {
        val ers = reminders.mapNotNull {
            createReminderData(it, quest)
        }
        entityReminderDao.saveAll(ers)
    }

    private fun createReminderData(reminder: Reminder, quest: Quest) =
        when (reminder) {
            is Reminder.Fixed ->
                createRoomEntityReminder(quest.id, reminder.date, reminder.time)
            is Reminder.Relative ->
                if (quest.isScheduled) {

                    val questDateTime =
                        LocalDateTime.of(
                            quest.scheduledDate!!,
                            LocalTime.of(quest.startTime!!.hours, quest.startTime.getMinutes())
                        )
                    val reminderDateTime =
                        questDateTime.minusMinutes(reminder.minutesFromStart)
                    val toLocalTime = reminderDateTime.toLocalTime()

                    createRoomEntityReminder(
                        quest.id,
                        reminderDateTime.toLocalDate(),
                        Time.at(toLocalTime.hour, toLocalTime.minute)
                    )
                } else null

        }

    private fun createRoomEntityReminder(
        questId: String,
        date: LocalDate,
        time: Time
    ) =
        RoomEntityReminder(
            id = UUID.randomUUID().toString(),
            date = date.startOfDayUTC(),
            millisOfDay = time.toMillisOfDay(),
            entityType = EntityReminder.EntityType.QUEST.name,
            entityId = questId
        )

    private fun purgeQuestReminders(questId: String) {
        entityReminderDao.purgeForEntity(questId)
    }

    override fun findById(id: String) =
        toEntityObject(dao.findById(id))

    override fun findAll() =
        dao.findAll().map { toEntityObject(it) }

    override fun listenById(id: String): Channel<Quest?> =
        dao.listenById(id).distinct().notifySingle()

    override fun listenForAll(): Channel<List<Quest>> =
        dao.listenForNotRemoved().notify()

    override fun remove(entity: Quest) {
        remove(entity.id)
    }

    override fun remove(id: String) {
        dao.remove(id)
    }

    override fun undoRemove(id: String) {
        val newRepeatingQuestId = dao.findById(id).repeatingQuestId?.replace("*", "")
        dao.undoRemove(id, newRepeatingQuestId)
    }

    private val tagMapper = RoomTagMapper()

    override fun toEntityObject(dbObject: RoomQuest) =
        Quest(
            id = dbObject.id,
            name = dbObject.name,
            color = Color.valueOf(dbObject.color),
            icon = dbObject.icon?.let {
                Icon.valueOf(it)
            },
            tags = tagDao.findForQuest(dbObject.id).map { tagMapper.toEntityObject(it) },
            startDate = dbObject.startDate?.startOfDayUTC,
            dueDate = dbObject.dueDate?.startOfDayUTC,
            scheduledDate = dbObject.scheduledDate?.startOfDayUTC,
            originalScheduledDate = dbObject.originalScheduledDate?.startOfDayUTC,
            startTime = dbObject.startMinute?.let { Time.of(it.toInt()) },
            duration = dbObject.duration.toInt(),
            priority = Priority.valueOf(dbObject.priority),
            preferredStartTime = TimePreference.valueOf(dbObject.preferredStartTime),
            reward = dbObject.coins?.let {

                val bounty = dbObject.bounty?.let { b ->
                    val dbBounty = DbBounty(b.toMutableMap())
                    when {
                        dbBounty.type == DbBounty.Type.NONE.name -> Quest.Bounty.None
                        dbBounty.type == DbBounty.Type.FOOD.name -> Quest.Bounty.Food(
                            Food.valueOf(
                                dbBounty.name!!
                            )
                        )
                        else -> throw IllegalArgumentException("Unknown bounty type ${dbBounty.type}")
                    }
                } ?: Quest.Bounty.None

                Reward(
                    attributePoints = dbObject.attributePoints!!.map { a ->
                        Player.AttributeType.valueOf(
                            a.key
                        ) to a.value.toInt()
                    }.toMap(),
                    healthPoints = dbObject.healthPoints!!.toInt(),
                    experience = dbObject.experience!!.toInt(),
                    coins = dbObject.coins.toInt(),
                    bounty = bounty
                )
            },
            completedAtDate = dbObject.completedAtDate?.startOfDayUTC,
            completedAtTime = dbObject.completedAtMinute?.let {
                Time.of(it.toInt())
            },
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
            subQuests = dbObject.subQuests.map {
                val dsq = DbSubQuest(it)
                SubQuest(
                    name = dsq.name,
                    completedAtDate = dsq.completedAtDate?.startOfDayUTC,
                    completedAtTime = dsq.completedAtMinute?.let { m -> Time.of(m.toInt()) }
                )
            },
            timeRanges = dbObject.timeRanges.map {
                val ctr = DbTimeRange(it)
                TimeRange(
                    TimeRange.Type.valueOf(ctr.type),
                    ctr.duration.toInt(),
                    ctr.start?.instant,
                    ctr.end?.instant
                )
            },
            repeatingQuestId = dbObject.repeatingQuestId,
            challengeId = dbObject.challengeId,
            note = dbObject.note,
            createdAt = dbObject.createdAt.instant,
            updatedAt = dbObject.updatedAt.instant,
            removedAt = dbObject.removedAt?.instant
        )

    override fun toDatabaseObject(entity: Quest) =
        RoomQuest(
            id = if (entity.id.isEmpty()) UUID.randomUUID().toString() else entity.id,
            name = entity.name,
            color = entity.color.name,
            icon = entity.icon?.name,
            duration = entity.duration.toLong(),
            priority = entity.priority.name,
            preferredStartTime = entity.preferredStartTime.name,
            startDate = entity.startDate?.startOfDayUTC(),
            dueDate = entity.dueDate?.startOfDayUTC(),
            scheduledDate = entity.scheduledDate?.startOfDayUTC(),
            originalScheduledDate = entity.originalScheduledDate?.startOfDayUTC(),
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
            healthPoints = entity.reward?.healthPoints?.toLong(),
            experience = entity.reward?.experience?.toLong(),
            coins = entity.reward?.coins?.toLong(),
            attributePoints = entity.reward?.attributePoints?.map { a -> a.key.name to a.value.toLong() }?.toMap(),
            bounty = entity.reward?.let {
                DbBounty().apply {
                    type = when (it.bounty) {
                        is Quest.Bounty.None -> DbBounty.Type.NONE.name
                        is Quest.Bounty.Food -> DbBounty.Type.FOOD.name
                    }
                    name = if (it.bounty is Quest.Bounty.Food) it.bounty.food.name else null
                }.map
            },
            startMinute = entity.startTime?.toMinuteOfDay()?.toLong(),
            completedAtDate = entity.completedAtDate?.startOfDayUTC(),
            completedAtMinute = entity.completedAtTime?.toMinuteOfDay()?.toLong(),
            timeRanges = entity.timeRanges.map {
                createDbTimeRange(it).map
            },
            timeRangeCount = entity.timeRanges.size.toLong(),
            repeatingQuestId = entity.repeatingQuestId,
            challengeId = entity.challengeId,
            note = entity.note,
            createdAt = entity.createdAt.toEpochMilli(),
            updatedAt = System.currentTimeMillis(),
            removedAt = entity.removedAt?.toEpochMilli()
        )

    private fun createDbTimeRange(timeRange: TimeRange): DbTimeRange {
        val cTimeRange = DbTimeRange()
        cTimeRange.type = timeRange.type.name
        cTimeRange.duration = timeRange.duration.toLong()
        cTimeRange.start = timeRange.start?.toEpochMilli()
        cTimeRange.end = timeRange.end?.toEpochMilli()
        return cTimeRange
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
    tableName = "quests",
    indices = [
        Index("repeatingQuestId"),
        Index("challengeId"),
        Index("scheduledDate"),
        Index("completedAtDate"),
        Index("updatedAt"),
        Index("removedAt")
    ]
)
data class RoomQuest(
    @NotNull
    @PrimaryKey(autoGenerate = false)
    override val id: String,
    val name: String,
    val color: String,
    val icon: String?,
    val duration: Long,
    val priority: String,
    val preferredStartTime: String,
    val reminders: List<MutableMap<String, Any?>>,
    val startMinute: Long?,
    val healthPoints: Long?,
    val experience: Long?,
    val coins: Long?,
    val bounty: Map<String, Any?>?,
    val attributePoints: Map<String, Long>?,
    val startDate: Long?,
    val dueDate: Long?,
    val scheduledDate: Long?,
    val originalScheduledDate: Long?,
    val completedAtDate: Long?,
    val completedAtMinute: Long?,
    val subQuests: List<MutableMap<String, Any?>>,
    val timeRanges: List<MutableMap<String, Any?>>,
    val timeRangeCount: Long,
    val repeatingQuestId: String?,
    val challengeId: String?,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val removedAt: Long?
) : RoomEntity {
    companion object {

        @Entity(
            tableName = "quest_tag_join",
            primaryKeys = ["questId", "tagId"],
            foreignKeys = [
                ForeignKey(
                    entity = RoomQuest::class,
                    parentColumns = ["id"],
                    childColumns = ["questId"],
                    onDelete = CASCADE
                ),
                (ForeignKey(
                    entity = RoomTag::class,
                    parentColumns = ["id"],
                    childColumns = ["tagId"],
                    onDelete = CASCADE
                ))
            ],
            indices = [Index("questId"), Index("tagId")]
        )
        data class RoomTagJoin(val questId: String, val tagId: String)
    }
}


data class DbQuest(override val map: MutableMap<String, Any?> = mutableMapOf()) :
    FirestoreModel {
    override var id: String by map
    var name: String by map
    var color: String by map
    var icon: String? by map
    var tags: Map<String, MutableMap<String, Any?>> by map
    var duration: Long by map
    var priority: String by map
    var preferredStartTime: String by map
    var reminders: List<MutableMap<String, Any?>> by map
    var startMinute: Long? by map
    var healthPoints: Long? by map
    var experience: Long? by map
    var coins: Long? by map
    var bounty: Map<String, Any?>? by map
    var attributePoints: Map<String, Long>? by map
    var startDate: Long? by map
    var dueDate: Long? by map
    var scheduledDate: Long? by map
    var originalScheduledDate: Long? by map
    var completedAtDate: Long? by map
    var completedAtMinute: Long? by map
    var subQuests: List<MutableMap<String, Any?>> by map
    var timeRanges: List<MutableMap<String, Any?>> by map
    var timeRangeCount: Long by map
    var repeatingQuestId: String? by map
    var challengeId: String? by map
    var note: String by map
    override var createdAt: Long by map
    override var updatedAt: Long by map
    override var removedAt: Long? by map
}

data class DbSubQuest(val map: MutableMap<String, Any?> = mutableMapOf()) {
    var name: String by map
    var completedAtDate: Long? by map
    var completedAtMinute: Long? by map
}

data class DbReminder(val map: MutableMap<String, Any?> = mutableMapOf()) {
    var type: String by map
    var message: String by map
    var minute: Long? by map
    var date: Long? by map
    var minutesFromStart: Long? by map

    enum class Type {
        RELATIVE, FIXED
    }
}

data class DbBounty(val map: MutableMap<String, Any?> = mutableMapOf()) {
    var type: String by map
    var name: String? by map

    enum class Type {
        NONE, FOOD
    }
}

data class DbTimeRange(val map: MutableMap<String, Any?> = mutableMapOf()) {
    var type: String by map
    var duration: Long by map
    var start: Long? by map
    var end: Long? by map
}

class FirestoreQuestRepository(
    database: FirebaseFirestore
) : BaseCollectionFirestoreRepository<Quest, DbQuest>(
    database
) {

    override val collectionReference: CollectionReference
        get() {
            return database.collection("players").document(playerId).collection("quests")
        }

    fun findCompletedInPeriod(
        playerId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Quest> =
        database
            .collection("players")
            .document(playerId)
            .collection("quests")
            .whereGreaterThanOrEqualTo("completedAtDate", startDate.startOfDayUTC())
            .whereLessThanOrEqualTo("completedAtDate", endDate.startOfDayUTC())
            .notRemovedEntities

    override fun toEntityObject(dataMap: MutableMap<String, Any?>): Quest {

        if (dataMap["experience"] != null) {

            if (!dataMap.containsKey("healthPoints")) {
                dataMap["healthPoints"] = 0L
            }

            if (!dataMap.containsKey("attributePoints")) {
                dataMap["attributePoints"] = emptyMap<String, Long>()
            }
        }

        val cq = DbQuest(dataMap.withDefault {
            null
        })

        return Quest(
            id = cq.id,
            name = cq.name,
            color = Color.valueOf(cq.color),
            icon = cq.icon?.let {
                Icon.valueOf(it)
            },
            tags = cq.tags.values.map {
                createTag(it)
            },
            startDate = cq.startDate?.startOfDayUTC,
            dueDate = cq.dueDate?.startOfDayUTC,
            scheduledDate = cq.scheduledDate?.startOfDayUTC,
            originalScheduledDate = cq.originalScheduledDate?.startOfDayUTC,
            startTime = cq.startMinute?.let { Time.of(it.toInt()) },
            duration = cq.duration.toInt(),
            priority = Priority.valueOf(cq.priority),
            preferredStartTime = TimePreference.valueOf(cq.preferredStartTime),
            reward = cq.coins?.let {

                val bounty = cq.bounty?.let { b ->
                    val dbBounty = DbBounty(b.toMutableMap())
                    when {
                        dbBounty.type == DbBounty.Type.NONE.name -> Quest.Bounty.None
                        dbBounty.type == DbBounty.Type.FOOD.name -> Quest.Bounty.Food(
                            Food.valueOf(
                                dbBounty.name!!
                            )
                        )
                        else -> throw IllegalArgumentException("Unknown bounty type ${dbBounty.type}")
                    }
                } ?: Quest.Bounty.None

                Reward(
                    attributePoints = cq.attributePoints!!.map { a ->
                        Player.AttributeType.valueOf(
                            a.key
                        ) to a.value.toInt()
                    }.toMap(),
                    healthPoints = cq.healthPoints!!.toInt(),
                    experience = cq.experience!!.toInt(),
                    coins = cq.coins!!.toInt(),
                    bounty = bounty
                )
            },
            completedAtDate = cq.completedAtDate?.startOfDayUTC,
            completedAtTime = cq.completedAtMinute?.let {
                Time.of(it.toInt())
            },
            reminders = cq.reminders.map {
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
            subQuests = cq.subQuests.map {
                val dsq = DbSubQuest(it)
                SubQuest(
                    name = dsq.name,
                    completedAtDate = dsq.completedAtDate?.startOfDayUTC,
                    completedAtTime = dsq.completedAtMinute?.let { m -> Time.of(m.toInt()) }
                )
            },
            timeRanges = cq.timeRanges.map {
                val ctr = DbTimeRange(it)
                TimeRange(
                    TimeRange.Type.valueOf(ctr.type),
                    ctr.duration.toInt(),
                    ctr.start?.instant,
                    ctr.end?.instant
                )
            },
            repeatingQuestId = cq.repeatingQuestId,
            challengeId = cq.challengeId,
            note = cq.note,
            createdAt = cq.createdAt.instant,
            updatedAt = cq.updatedAt.instant,
            removedAt = cq.removedAt?.instant
        )
    }

    override fun toDatabaseObject(entity: Quest): DbQuest {
        val q = DbQuest()
        q.id = entity.id
        q.name = entity.name
        q.tags = entity.tags.map { it.id to createDbTag(it).map }.toMap()
        q.color = entity.color.name
        q.icon = entity.icon?.name
        q.duration = entity.duration.toLong()
        q.priority = entity.priority.name
        q.preferredStartTime = entity.preferredStartTime.name
        q.startDate = entity.startDate?.startOfDayUTC()
        q.dueDate = entity.dueDate?.startOfDayUTC()
        q.scheduledDate = entity.scheduledDate?.startOfDayUTC()
        q.originalScheduledDate = entity.originalScheduledDate?.startOfDayUTC()
        q.reminders = entity.reminders.map {
            createDbReminder(it).map
        }
        q.subQuests = entity.subQuests.map {
            DbSubQuest().apply {
                name = it.name
                completedAtDate = it.completedAtDate?.startOfDayUTC()
                completedAtMinute = it.completedAtTime?.toMinuteOfDay()?.toLong()
            }.map
        }
        q.healthPoints = entity.reward?.healthPoints?.toLong()
        q.experience = entity.reward?.experience?.toLong()
        q.coins = entity.reward?.coins?.toLong()
        q.attributePoints =
            entity.reward?.attributePoints?.map { a -> a.key.name to a.value.toLong() }?.toMap()
        q.bounty = entity.reward?.let {
            DbBounty().apply {
                type = when (it.bounty) {
                    is Quest.Bounty.None -> DbBounty.Type.NONE.name
                    is Quest.Bounty.Food -> DbBounty.Type.FOOD.name
                }
                name = if (it.bounty is Quest.Bounty.Food) it.bounty.food.name else null
            }.map
        }
        q.startMinute = entity.startTime?.toMinuteOfDay()?.toLong()
        q.completedAtDate = entity.completedAtDate?.startOfDayUTC()
        q.completedAtMinute = entity.completedAtTime?.toMinuteOfDay()?.toLong()
        q.timeRanges = entity.timeRanges.map {
            createDbTimeRange(it).map
        }
        q.timeRangeCount = q.timeRanges.size.toLong()
        q.repeatingQuestId = entity.repeatingQuestId
        q.challengeId = entity.challengeId
        q.note = entity.note
        q.createdAt = entity.createdAt.toEpochMilli()
        q.updatedAt = entity.updatedAt.toEpochMilli()
        q.removedAt = entity.removedAt?.toEpochMilli()
        return q
    }

    private fun createDbTimeRange(timeRange: TimeRange): DbTimeRange {
        val cTimeRange = DbTimeRange()
        cTimeRange.type = timeRange.type.name
        cTimeRange.duration = timeRange.duration.toLong()
        cTimeRange.start = timeRange.start?.toEpochMilli()
        cTimeRange.end = timeRange.end?.toEpochMilli()
        return cTimeRange
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

class DbEmbedTag(val map: MutableMap<String, Any?> = mutableMapOf()) {
    var id: String by map
    var name: String by map
    var color: String by map
    var icon: String? by map
    var isFavorite: Boolean by map
}