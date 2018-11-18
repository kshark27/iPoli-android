package io.ipoli.android.common.persistence

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.*
import android.arch.persistence.room.migration.Migration
import android.content.Context
import io.ipoli.android.Constants
import io.ipoli.android.R.string.quests
import io.ipoli.android.challenge.entity.SharingPreference
import io.ipoli.android.challenge.persistence.ChallengeDao
import io.ipoli.android.challenge.persistence.DbTrackedValue
import io.ipoli.android.challenge.persistence.RoomChallenge
import io.ipoli.android.common.datetime.startOfDayUTC
import io.ipoli.android.dailychallenge.data.persistence.DailyChallengeDao
import io.ipoli.android.dailychallenge.data.persistence.RoomDailyChallenge
import io.ipoli.android.habit.persistence.HabitDao
import io.ipoli.android.habit.persistence.RoomHabit
import io.ipoli.android.player.AttributePointsForLevelGenerator
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.persistence.PlayerDao
import io.ipoli.android.player.persistence.RoomPlayer
import io.ipoli.android.player.persistence.model.DbAttribute
import io.ipoli.android.player.persistence.model.DbHealth
import io.ipoli.android.quest.data.persistence.DbBounty
import io.ipoli.android.quest.data.persistence.QuestDao
import io.ipoli.android.quest.data.persistence.RoomQuest
import io.ipoli.android.repeatingquest.persistence.RepeatingQuestDao
import io.ipoli.android.repeatingquest.persistence.RoomRepeatingQuest
import io.ipoli.android.tag.persistence.RoomTag
import io.ipoli.android.tag.persistence.TagDao
import org.json.JSONArray
import org.json.JSONObject
import org.threeten.bp.LocalDate
import java.util.*


class Converters {

    @TypeConverter
    fun fromListOfStrings(data: List<String>?): String? {
        return data?.let {
            JSONArray(it).toString()
        }
    }

    @TypeConverter
    fun toListOfStrings(data: String?): List<String>? {
        return data?.let {
            @Suppress("unchecked_cast")
            toList(JSONArray(it)) as List<String>
        }
    }

    @TypeConverter
    fun fromListOfLongs(data: List<Long>?): String? {
        return data?.let {
            JSONArray(it).toString()
        }
    }

    @TypeConverter
    fun toListOfLongs(data: String?): List<Long>? {
        return data?.let {
            @Suppress("unchecked_cast")
            toList(JSONArray(it)) as List<Long>
        }
    }

    @TypeConverter
    fun fromMapOfStringToObject(data: Map<String, MutableMap<String, Any?>>?): String? {
        return data?.let {
            JSONObject(it).toString()
        }
    }

    @TypeConverter
    fun toMapOfStringToObject(data: String?): Map<String, MutableMap<String, Any?>>? {
        return data?.let {
            @Suppress("unchecked_cast")
            toMap(JSONObject(it)) as Map<String, MutableMap<String, Any?>>?
        }
    }

    @TypeConverter
    fun fromObjectMap(data: Map<String, Any?>?): String? {
        return data?.let {
            JSONObject(it).toString()
        }
    }

    @TypeConverter
    fun toObjectMap(data: String?): Map<String, Any?>? {
        return data?.let {
            toMap(JSONObject(it))
        }
    }

    @TypeConverter
    fun fromListOfObjectMap(data: List<Map<String, Any?>>?): String? {
        return data?.let {
            JSONArray(it).toString()
        }
    }

    @TypeConverter
    fun toListOfObjectMap(data: String?): List<Map<String, Any?>>? {
        return data?.let {
            @Suppress("unchecked_cast")
            toList(JSONArray(it)) as List<Map<String, Any?>>?
        }
    }

    @TypeConverter
    fun fromMapStringToLong(data: Map<String, Long>?): String? {
        return data?.let {
            JSONObject(it).toString()
        }
    }

    @TypeConverter
    fun toMapStringToLong(data: String?) =
        data?.let {
            @Suppress("unchecked_cast")
            toMap(JSONObject(it)) as Map<String, Long>
        }

    @TypeConverter
    fun fromMapStringToList(data: Map<String, List<Long>>?): String? {
        return data?.let {
            JSONObject(it).toString()
        }
    }

    @TypeConverter
    fun toMapStringToList(data: String?) =
        data?.let {
            @Suppress("unchecked_cast")
            toMap(JSONObject(it)) as Map<String, List<Long>>
        }

    fun toMap(jsonObject: JSONObject): Map<String, Any?> {
        val map = HashMap<String, Any?>()

        if (jsonObject === JSONObject.NULL) {
            return map
        }

        val keysItr = jsonObject.keys()
        while (keysItr.hasNext()) {
            val key = keysItr.next()
            var value = jsonObject.get(key)

            when {
                value is Int -> value = value.toLong()
                value === JSONObject.NULL -> value = null
                value is JSONArray -> value = toList(value)
                value is JSONObject -> value = toMap(value)
            }
            map[key] = value
        }
        return map
    }

    fun toList(array: JSONArray): List<Any> {
        val list = ArrayList<Any>()
        for (i in 0 until array.length()) {
            var value = array.get(i)
            when {
                value is Int -> value = value.toLong()
                value === JSONObject.NULL -> value = null
                value is JSONArray -> value = toList(value)
                value is JSONObject -> value = toMap(value)
            }
            list.add(value)
        }
        return list
    }
}

object TriggerCreator {

    fun createTriggers(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
                    CREATE TRIGGER IF NOT EXISTS refresh_repeating_quest_quest_insert
                    AFTER INSERT ON quests WHEN new.repeatingQuestId IS NOT NULL
                    BEGIN
                        UPDATE repeating_quests SET updatedAt = strftime('%s', 'now') * 1000 WHERE id = new.repeatingQuestId;
                    END
                    """
        )

        db.execSQL(
            """
                    CREATE TRIGGER IF NOT EXISTS refresh_repeating_quest_quest_update
                    AFTER UPDATE ON quests WHEN new.repeatingQuestId IS NOT NULL OR old.repeatingQuestId IS NOT NULL
                    BEGIN
                        UPDATE repeating_quests SET updatedAt = strftime('%s', 'now') * 1000 WHERE id IN (new.repeatingQuestId, old.repeatingQuestId);
                    END
                    """
        )

        db.execSQL(
            """
                    CREATE TRIGGER IF NOT EXISTS refresh_challenge_quest_insert
                    AFTER INSERT ON quests WHEN new.challengeId IS NOT NULL
                    BEGIN
                        UPDATE challenges SET updatedAt = strftime('%s', 'now') * 1000 WHERE id = new.challengeId;
                    END
                    """
        )

        db.execSQL(
            """
                    CREATE TRIGGER IF NOT EXISTS refresh_challenge_quest_update
                    AFTER UPDATE ON quests WHEN new.challengeId IS NOT NULL OR old.challengeId IS NOT NULL
                    BEGIN
                        UPDATE challenges SET updatedAt = strftime('%s', 'now') * 1000 WHERE id IN (new.challengeId, old.challengeId);
                    END
                    """
        )

        db.execSQL(
            """
                    CREATE TRIGGER IF NOT EXISTS refresh_challenge_quest_delete
                    AFTER DELETE ON quests WHEN old.challengeId IS NOT NULL
                    BEGIN
                        UPDATE challenges SET updatedAt = strftime('%s', 'now') * 1000 WHERE id = old.challengeId;
                    END
                    """
        )

        db.execSQL(
            """
                    CREATE TRIGGER IF NOT EXISTS refresh_challenge_habit_insert
                    AFTER INSERT ON habits WHEN new.challengeId IS NOT NULL
                    BEGIN
                        UPDATE challenges SET updatedAt = strftime('%s', 'now') * 1000 WHERE id = new.challengeId;
                    END
                    """
        )

        db.execSQL(
            """
                    CREATE TRIGGER IF NOT EXISTS refresh_challenge_habit_update
                    AFTER UPDATE ON habits WHEN new.challengeId IS NOT NULL OR old.challengeId IS NOT NULL
                    BEGIN
                        UPDATE challenges SET updatedAt = strftime('%s', 'now') * 1000 WHERE id IN (new.challengeId, old.challengeId);
                    END
                    """
        )

        db.execSQL(
            """
                    CREATE TRIGGER IF NOT EXISTS refresh_challenge_habit_delete
                    AFTER DELETE ON habits WHEN old.challengeId IS NOT NULL
                    BEGIN
                        UPDATE challenges SET updatedAt = strftime('%s', 'now') * 1000 WHERE id = old.challengeId;
                    END
                    """
        )

        db.execSQL(
            """
                    CREATE TRIGGER IF NOT EXISTS refresh_tag_quest_insert
                    AFTER INSERT ON quest_tag_join
                    BEGIN
                        UPDATE tags SET updatedAt = strftime('%s', 'now') * 1000 WHERE id = new.tagId;
                    END
                    """
        )

        db.execSQL(
            """
                    CREATE TRIGGER IF NOT EXISTS refresh_tag_quest_delete
                    AFTER DELETE ON quest_tag_join
                    BEGIN
                        UPDATE tags SET updatedAt = strftime('%s', 'now') * 1000 WHERE id = old.tagId;
                        UPDATE quests SET updatedAt = strftime('%s', 'now') * 1000 WHERE id = old.questId;
                    END
                    """
        )

        db.execSQL(
            """
                    CREATE TRIGGER IF NOT EXISTS refresh_tag_repeating_quest_insert
                    AFTER INSERT ON repeating_quest_tag_join
                    BEGIN
                        UPDATE tags SET updatedAt = strftime('%s', 'now') * 1000 WHERE id = new.tagId;
                    END
                    """
        )

        db.execSQL(
            """
                    CREATE TRIGGER IF NOT EXISTS refresh_tag_repeating_quest_delete
                    AFTER DELETE ON repeating_quest_tag_join
                    BEGIN
                        UPDATE tags SET updatedAt = strftime('%s', 'now') * 1000 WHERE id = old.tagId;
                        UPDATE repeating_quests SET updatedAt = strftime('%s', 'now') * 1000 WHERE id = old.repeatingQuestId;
                    END
                    """
        )

        db.execSQL(
            """
                    CREATE TRIGGER IF NOT EXISTS refresh_tag_challenge_insert
                    AFTER INSERT ON challenge_tag_join
                    BEGIN
                        UPDATE tags SET updatedAt = strftime('%s', 'now') * 1000 WHERE id = new.tagId;
                    END
                    """
        )

        db.execSQL(
            """
                    CREATE TRIGGER IF NOT EXISTS refresh_tag_challenge_delete
                    AFTER DELETE ON challenge_tag_join
                    BEGIN
                        UPDATE tags SET updatedAt = strftime('%s', 'now') * 1000 WHERE id = old.tagId;
                        UPDATE challenges SET updatedAt = strftime('%s', 'now') * 1000 WHERE id = old.challengeId;
                    END
                    """
        )

        db.execSQL(
            """
                    CREATE TRIGGER IF NOT EXISTS refresh_tag_habit_insert
                    AFTER INSERT ON habit_tag_join
                    BEGIN
                        UPDATE tags SET updatedAt = strftime('%s', 'now') * 1000 WHERE id = new.tagId;
                    END
                    """
        )

        db.execSQL(
            """
                    CREATE TRIGGER IF NOT EXISTS refresh_tag_habit_delete
                    AFTER DELETE ON habit_tag_join
                    BEGIN
                        UPDATE tags SET updatedAt = strftime('%s', 'now') * 1000 WHERE id = old.tagId;
                        UPDATE habits SET updatedAt = strftime('%s', 'now') * 1000 WHERE id = old.habitId;
                    END
                    """
        )
    }
}

object Migration1To2 : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS posts(`id` TEXT NOT NULL, `type` TEXT NOT NULL, `playerLevel` INTEGER NOT NULL, `description` TEXT, `questId` TEXT, `questName` TEXT, `habitId` TEXT, `habitName` TEXT, `challengeId` TEXT, `challengeName` TEXT, `streak` INTEGER, `bestStreak` INTEGER, `level` INTEGER, `isGood` INTEGER, `duration` INTEGER, `pomodoroCount` INTEGER, `achievement` TEXT, `reactions` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        database.execSQL("CREATE INDEX index_posts_questId ON posts (questId)")
        database.execSQL("ALTER TABLE challenges ADD COLUMN `sharingPreference` TEXT NOT NULL DEFAULT '${SharingPreference.PRIVATE.name}'")
    }
}

object Migration2To3 : Migration(2, 3) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE dailyChallenges RENAME TO daily_challenges;")
        TriggerCreator.createTriggers(database)
    }
}

object Migration3To4 : Migration(3, 4) {

    override fun migrate(database: SupportSQLiteDatabase) {
        val trackedValue = DbTrackedValue()
        trackedValue.id = UUID.randomUUID().toString()
        trackedValue.type = DbTrackedValue.Type.PROGRESS.name
        val default = Converters().fromListOfObjectMap(listOf(trackedValue.map))

        database.execSQL("ALTER TABLE challenges ADD COLUMN `trackedValues` TEXT NOT NULL DEFAULT '$default'")
    }
}

object Migration4To5 : Migration(4, 5) {

    override fun migrate(database: SupportSQLiteDatabase) {
        val defaultHealth = Converters().fromObjectMap(
            DbHealth().apply {
                current = Constants.DEFAULT_PLAYER_MAX_HP.toLong()
                max = Constants.DEFAULT_PLAYER_MAX_HP.toLong()
            }.map
        )

        database.execSQL("ALTER TABLE players ADD COLUMN `health` TEXT NOT NULL DEFAULT '$defaultHealth'")

        val defaultAttributes = Converters().fromObjectMap(
            mapOf(
                Player.AttributeType.STRENGTH.name to DbAttribute().apply {
                    type = Player.AttributeType.STRENGTH.name
                    points = 0
                    level = Constants.DEFAULT_ATTRIBUTE_LEVEL.toLong()
                    pointsForNextLevel =
                        AttributePointsForLevelGenerator.forLevel(Constants.DEFAULT_ATTRIBUTE_LEVEL + 1)
                            .toLong()
                    tagIds = emptyList()
                }.map,
                Player.AttributeType.INTELLIGENCE.name to DbAttribute().apply {
                    type = Player.AttributeType.INTELLIGENCE.name
                    points = 0
                    level = Constants.DEFAULT_ATTRIBUTE_LEVEL.toLong()
                    pointsForNextLevel =
                        AttributePointsForLevelGenerator.forLevel(Constants.DEFAULT_ATTRIBUTE_LEVEL + 1)
                            .toLong()
                    tagIds = emptyList()
                }.map,
                Player.AttributeType.CHARISMA.name to DbAttribute().apply {
                    type = Player.AttributeType.CHARISMA.name
                    points = 0
                    level = Constants.DEFAULT_ATTRIBUTE_LEVEL.toLong()
                    pointsForNextLevel =
                        AttributePointsForLevelGenerator.forLevel(Constants.DEFAULT_ATTRIBUTE_LEVEL + 1)
                            .toLong()
                    tagIds = emptyList()
                }.map,
                Player.AttributeType.EXPERTISE.name to DbAttribute().apply {
                    type = Player.AttributeType.EXPERTISE.name
                    points = 0
                    level = Constants.DEFAULT_ATTRIBUTE_LEVEL.toLong()
                    pointsForNextLevel =
                        AttributePointsForLevelGenerator.forLevel(Constants.DEFAULT_ATTRIBUTE_LEVEL + 1)
                            .toLong()
                    tagIds = emptyList()
                }.map,
                Player.AttributeType.WELL_BEING.name to DbAttribute().apply {
                    type = Player.AttributeType.WELL_BEING.name
                    points = 0
                    level = Constants.DEFAULT_ATTRIBUTE_LEVEL.toLong()
                    pointsForNextLevel =
                        AttributePointsForLevelGenerator.forLevel(Constants.DEFAULT_ATTRIBUTE_LEVEL + 1)
                            .toLong()
                    tagIds = emptyList()
                }.map,
                Player.AttributeType.WILLPOWER.name to DbAttribute().apply {
                    type = Player.AttributeType.WILLPOWER.name
                    points = 0
                    level = Constants.DEFAULT_ATTRIBUTE_LEVEL.toLong()
                    pointsForNextLevel =
                        AttributePointsForLevelGenerator.forLevel(Constants.DEFAULT_ATTRIBUTE_LEVEL + 1)
                            .toLong()
                    tagIds = emptyList()
                }.map

            )
        )
        database.execSQL("ALTER TABLE players ADD COLUMN `attributes` TEXT NOT NULL DEFAULT '$defaultAttributes'")

        database.execSQL("ALTER TABLE quests ADD COLUMN `healthPoints` INTEGER DEFAULT 0")
        database.execSQL("ALTER TABLE quests ADD COLUMN `attributePoints` TEXT DEFAULT NULL")

        database.execSQL("UPDATE quests SET attributePoints='{}' WHERE coins IS NOT NULL")

        val defaultBounty = Converters().fromObjectMap(
            DbBounty().apply {
                type = DbBounty.Type.NONE.name
                name = null
            }.map
        )

        database.execSQL("ALTER TABLE challenges ADD COLUMN `bounty` TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE challenges ADD COLUMN `attributePoints` TEXT DEFAULT NULL")
        database.execSQL("UPDATE challenges SET attributePoints='{}' WHERE coins IS NOT NULL")
        database.execSQL("UPDATE challenges SET bounty='$defaultBounty' WHERE coins IS NOT NULL")

        database.execSQL("ALTER TABLE daily_challenges ADD COLUMN `coins` INTEGER DEFAULT NULL")
        database.execSQL("ALTER TABLE daily_challenges ADD COLUMN `experience` INTEGER DEFAULT NULL")
        database.execSQL("ALTER TABLE daily_challenges ADD COLUMN `bounty` TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE daily_challenges ADD COLUMN `attributePoints` TEXT DEFAULT NULL")
    }
}

object Migration5To6 : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE challenges ADD COLUMN `presetChallengeId` TEXT DEFAULT NULL")
    }
}

object Migration6To7 : Migration(6, 7) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE habits ADD COLUMN `preferenceHistory` TEXT NOT NULL DEFAULT '{}'")
    }
}

object Migration7To8 : Migration(7, 8) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE posts")
    }
}

object Migration8To9 : Migration(8, 9) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE repeating_quests ADD COLUMN `repeatPattern_xDays` INTEGER DEFAULT NULL")
    }
}

object Migration9To10 : Migration(9, 10) {

    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE habits ADD COLUMN `reminders` TEXT NOT NULL DEFAULT '[]'")
    }
}

object Migration10To11 : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE repeating_quests ADD COLUMN `repeatPattern_skipEveryXPeriods` INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE repeating_quests ADD COLUMN `repeatPattern_lastScheduledPeriodStart` INTEGER DEFAULT NULL")
        database.execSQL(
            """
            UPDATE repeating_quests
            SET `repeatPattern_type` = 'DAILY', `repeatPattern_skipEveryXPeriods` = repeatPattern_xDays - 1
            WHERE repeatPattern_xDays IS NOT NULL
            """
        )

        val currentTimeMillis: Long = System.currentTimeMillis()
        val currentDate = LocalDate.now().startOfDayUTC()

        database.execSQL(
            """
            UPDATE quests
            SET removedAt = $currentTimeMillis, updatedAt = $currentTimeMillis
            WHERE repeatingQuestId IS NOT NULL AND completedAtDate IS NULL AND scheduledDate >= $currentDate
            """
        )
    }
}

@Database(
    entities = [
        RoomPlayer::class,
        RoomQuest::class,
        RoomQuest.Companion.RoomTagJoin::class,
        RoomRepeatingQuest::class,
        RoomRepeatingQuest.Companion.RoomTagJoin::class,
        RoomChallenge::class,
        RoomChallenge.Companion.RoomTagJoin::class,
        RoomTag::class,
        RoomHabit::class,
        RoomDailyChallenge::class,
        RoomHabit.Companion.RoomTagJoin::class,
        RoomEntityReminder::class
    ],
    version = 11,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MyPoliRoomDatabase : RoomDatabase() {

    abstract fun playerDao(): PlayerDao

    abstract fun questDao(): QuestDao

    abstract fun repeatingQuestDao(): RepeatingQuestDao

    abstract fun challengeDao(): ChallengeDao

    abstract fun tagDao(): TagDao

    abstract fun habitDao(): HabitDao

    abstract fun dailyChallengeDao(): DailyChallengeDao

    abstract fun entityReminderDao(): EntityReminderDao

    companion object {

        @Volatile
        private var INSTANCE: MyPoliRoomDatabase? = null

        fun getInstance(context: Context): MyPoliRoomDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context) =
            Room
                .databaseBuilder(
                    context.applicationContext,
                    MyPoliRoomDatabase::class.java, "myPoli.db"
                )
                .addMigrations(
                    Migration1To2,
                    Migration2To3,
                    Migration3To4,
                    Migration4To5,
                    Migration5To6,
                    Migration6To7,
                    Migration7To8,
                    Migration8To9,
                    Migration9To10,
                    Migration10To11
                )
                .addCallback(CALLBACK)
                .build()

        private val CALLBACK = object : RoomDatabase.Callback() {

            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                TriggerCreator.createTriggers(db)
            }
        }
    }
}