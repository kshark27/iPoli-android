package io.ipoli.android.friends.feed.persistence

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.firestore.EventListener
import io.ipoli.android.achievement.Achievement
import io.ipoli.android.common.ErrorLogger
import io.ipoli.android.common.datetime.days
import io.ipoli.android.common.datetime.instant
import io.ipoli.android.common.datetime.minutes
import io.ipoli.android.common.datetime.startOfDayUTC
import io.ipoli.android.common.persistence.documents
import io.ipoli.android.common.persistence.getAsync
import io.ipoli.android.friends.feed.data.Post
import io.ipoli.android.player.data.Avatar
import io.ipoli.android.player.persistence.model.DbPlayer
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.ConflatedChannel
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import java.util.*
import java.util.concurrent.ExecutorService
import android.arch.persistence.room.Query as RoomQuery

interface PostRepository {

    fun save(post: Post)

    fun listenForAll(limit: Int): Channel<List<Post>>

    fun listen(postId: String): Channel<Post>

    fun listenForPlayer(playerId: String, limit: Int): Channel<List<Post>>

    fun react(postId: String, reactionType: Post.ReactionType)

    fun saveDescription(postId: String, description: String?)

    fun saveComment(postId: String, text: String)

    fun hasPostForQuest(questId: String): Boolean

    fun hasPostedQuests(questIds: List<String>): Map<String, Boolean>

    fun hasPostForHabit(habitId: String, date: LocalDate = LocalDate.now()): Boolean

    fun hasPostedHabits(
        habitIds: List<String>,
        date: LocalDate = LocalDate.now()
    ): Map<String, Boolean>

    fun delete(id: String)

}

class AndroidPostRepository(
    private val remoteDatabase: FirebaseFirestore,
    private val executorService: ExecutorService
) : PostRepository {

    override fun listenForAll(limit: Int): Channel<List<Post>> {

        val currentPlayerId = FirebaseAuth.getInstance().currentUser?.uid
        val channel = FirestoreSnapshotChannel<List<Post>>()
        val snapshotListener = EventListener<QuerySnapshot> { s, exception ->

            if (exception != null) {
                ErrorLogger.log(exception)
                return@EventListener
            }

            val dbPosts = s!!
                .documents
                .map { DbPost(it.data!!) }

            val playerIds = dbPosts.map { it.playerId }.toSet()

            GlobalScope.launch(Dispatchers.IO) {
                val dbPlayers = getStringToPlayerMap(playerIds)

                val entities = dbPosts.map {
                    val dbPlayer = dbPlayers[it.playerId]!!
                    toEntityObject(it, dbPlayer, currentPlayerId == it.playerId)
                }

                if (!channel.isClosedForSend) {
                    channel.offer(entities)
                }
            }
        }

        val listenerRegistration = postsReference
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener(executorService, snapshotListener)

        channel.listenerRegistration = listenerRegistration
        return channel
    }

    private suspend fun getStringToPlayerMap(playerIds: Set<String>): Map<String, DbPlayer> {
        val dbJobs = playerIds.map {
            GlobalScope.async(Dispatchers.IO) {
                it to DbPlayer(playerRef(it).getAsync().data!!)
            }
        }

        return dbJobs.map { it.await() }.toMap()
    }

    override fun listenForPlayer(playerId: String, limit: Int): Channel<List<Post>> {
        val currentPlayerId = FirebaseAuth.getInstance().currentUser?.uid

        val channel = FirestoreSnapshotChannel<List<Post>>()
        val snapshotListener = EventListener<QuerySnapshot> { s, exception ->

            if (exception != null) {
                ErrorLogger.log(exception)
                return@EventListener
            }

            val dbPosts = s!!
                .documents
                .map { DbPost(it.data!!) }

            val playerIds = dbPosts.map { it.playerId }.toSet()

            GlobalScope.launch(Dispatchers.IO) {

                val dbPlayers = getStringToPlayerMap(playerIds)

                val entities = dbPosts.map {
                    val dbPlayer = dbPlayers[it.playerId]!!
                    toEntityObject(it, dbPlayer, currentPlayerId == it.playerId)
                }

                if (!channel.isClosedForSend) {
                    channel.offer(entities)
                }
            }
        }

        val listenerRegistration = postsReference
            .whereEqualTo("playerId", playerId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener(executorService, snapshotListener)

        channel.listenerRegistration = listenerRegistration
        return channel
    }

    override fun listen(postId: String): Channel<Post> {

        val channel = FirestoreSnapshotChannel<Post>()

        val currentPlayerId = FirebaseAuth.getInstance().currentUser?.uid

        val snapshotListener = EventListener<DocumentSnapshot> { s, exception ->

            if (exception != null) {
                ErrorLogger.log(exception)
                return@EventListener
            }

            if (!s!!.exists()) {
                return@EventListener
            }

            val dbPost = DbPost(s.data!!)

            val commentPlayerIds = dbPost.comments.map {
                val dbComment = DbPost.Comment(it.value.toMutableMap())
                dbComment.playerId
            }

            val playerIds = setOf(dbPost.playerId) + commentPlayerIds

            GlobalScope.launch(Dispatchers.IO) {
                val dbPlayers = getStringToPlayerMap(playerIds)

                val dbPlayer = dbPlayers[dbPost.playerId]!!

                val post =
                    toEntityObject(dbPost, dbPlayer, currentPlayerId == dbPost.playerId, dbPlayers)

                if (!channel.isClosedForSend) {
                    channel.offer(post)
                }
            }
        }

        val listenerRegistration = postsReference
            .document(postId)
            .addSnapshotListener(executorService, snapshotListener)

        channel.listenerRegistration = listenerRegistration
        return channel
    }

    override fun save(post: Post) {
        try {
            saveToRemoteDatabase(post)
        } catch (e: Throwable) {
            ErrorLogger.log(e)
        }
    }

    private fun saveToRemoteDatabase(post: Post) {

        val data = post.data

        val shouldNotSave = when (data) {
            is Post.Data.QuestShared ->
                hasPostForQuest(data.questId)

            is Post.Data.QuestWithPomodoroShared ->
                hasPostForQuest(data.questId)

            is Post.Data.QuestFromChallengeCompleted ->
                hasPostForQuest(data.questId)

            is Post.Data.QuestWithPomodoroFromChallengeCompleted ->
                hasPostForQuest(data.questId)

            is Post.Data.HabitCompleted ->
                hasPostForHabit(data.habitId)

            else -> false
        }

        if (shouldNotSave) {
            return
        }

        val postRef = postsReference.document()

        val newEntity = post.copy(id = postRef.id)

        Tasks.await(postRef.set(toDatabaseObject(newEntity).map))
    }

    override fun react(postId: String, reactionType: Post.ReactionType) {
        val playerId = FirebaseAuth.getInstance().currentUser!!.uid

        val updateTime = System.currentTimeMillis()

        Tasks.await(
            postsReference
                .document(postId)
                .update(
                    mapOf(
                        "reactions.$playerId" to mapOf(reactionType.name to Instant.now().toEpochMilli()),
                        "updatedAt" to updateTime
                    )
                )
        )
    }

    override fun saveDescription(postId: String, description: String?) {
        val updateTime = System.currentTimeMillis()

        Tasks.await(
            postsReference
                .document(postId)
                .update(
                    mapOf(
                        "description" to description,
                        "updatedAt" to updateTime
                    )
                )
        )
    }

    override fun saveComment(postId: String, text: String) {
        val playerId = FirebaseAuth.getInstance().currentUser!!.uid
        val commentId = UUID.randomUUID().toString()

        val updateTime = System.currentTimeMillis()

        val dbComment = DbPost.Comment().apply {
            id = commentId
            this.playerId = playerId
            this.text = text
            createdAt = updateTime
        }.map

        Tasks.await(
            postsReference
                .document(postId)
                .update(
                    mapOf(
                        "comments.$commentId" to dbComment,
                        "updatedAt" to updateTime
                    )
                )
        )
    }

    override fun hasPostForQuest(questId: String): Boolean {
        val playerId = FirebaseAuth.getInstance().currentUser!!.uid

        return postsReference
            .whereEqualTo("playerId", playerId)
            .whereEqualTo("questId", questId)
            .documents
            .isNotEmpty()
    }

    private val postsReference
        get() = remoteDatabase.collection("posts")

    override fun hasPostForHabit(habitId: String, date: LocalDate): Boolean {
        val playerId = FirebaseAuth.getInstance().currentUser!!.uid
        val habitDate = date.startOfDayUTC()

        return postsReference
            .whereEqualTo("playerId", playerId)
            .whereEqualTo("habitId", habitId)
            .whereEqualTo("habitDate", habitDate)
            .documents
            .isNotEmpty()
    }

    override fun hasPostedQuests(questIds: List<String>): Map<String, Boolean> {
        val result = mutableMapOf<String, Boolean>()

        questIds.forEach {
            result[it] = false
        }

        val playerId = FirebaseAuth.getInstance().currentUser!!.uid

        val coroutineDispatcher = executorService.asCoroutineDispatcher()

        val postJobs = questIds.map {
            GlobalScope.async(coroutineDispatcher) {
                postsReference
                    .whereEqualTo("playerId", playerId)
                    .whereEqualTo("questId", it)
                    .limit(1)
                    .documents
            }
        }

        return runBlocking(coroutineDispatcher) {
            postJobs.forEach {
                val docs = it.await()
                if (docs.isNotEmpty()) {
                    result[docs.first().getString("questId")!!] = true
                }
            }

            result
        }
    }

    override fun hasPostedHabits(habitIds: List<String>, date: LocalDate): Map<String, Boolean> {
        val result = mutableMapOf<String, Boolean>()

        habitIds.forEach {
            result[it] = false
        }

        val playerId = FirebaseAuth.getInstance().currentUser!!.uid

        val coroutineDispatcher = executorService.asCoroutineDispatcher()

        val postJobs = habitIds.map {
            GlobalScope.async(coroutineDispatcher) {
                postsReference
                    .whereEqualTo("playerId", playerId)
                    .whereEqualTo("habitId", it)
                    .whereEqualTo("habitDate", date.startOfDayUTC())
                    .limit(1)
                    .documents
            }
        }

        return runBlocking(coroutineDispatcher) {
            postJobs.forEach {
                val docs = it.await()
                if (docs.isNotEmpty()) {
                    result[docs.first().getString("habitId")!!] = true
                }
            }

            result
        }
    }

    override fun delete(id: String) {
        Tasks.await(postsReference.document(id).delete())
    }

    class FirestoreSnapshotChannel<D> :
        ConflatedChannel<D>() {

        var listenerRegistration: ListenerRegistration? = null

        override fun afterClose(cause: Throwable?) {
            listenerRegistration?.remove()
        }
    }

    private fun playerRef(playerId: String) =
        remoteDatabase
            .collection("players")
            .document(playerId)

    private fun toDatabaseObject(post: Post) =
        DbPost().apply {
            id = post.id
            playerId = post.playerId
            playerLevel = post.playerLevel.toLong()
            description = post.description
            imageUrl = post.imageUrl
            comments = post.comments.map { c ->
                c.id to DbPost.Comment().apply {
                    id = c.id
                    playerId = c.playerId
                    text = c.text
                    createdAt = c.createdAt.toEpochMilli()
                }.map
            }.toMap().toMutableMap()
            reactions = post.reactions.map {
                it.playerId to mutableMapOf(it.reactionType.name to it.createdAt.toEpochMilli())
            }.toMap().toMutableMap()
            status = post.status.name
            createdAt = post.createdAt.toEpochMilli()
            updatedAt = post.updatedAt.toEpochMilli()

            with(post) {
                when (data) {

                    is Post.Data.DailyChallengeCompleted -> {
                        type = DbPost.PostType.DAILY_CHALLENGE_COMPLETED.name
                        streak = data.streak.toLong()
                        bestStreak = data.bestStreak.toLong()
                    }

                    is Post.Data.LevelUp -> {
                        type = DbPost.PostType.LEVEL_UP.name
                        level = data.level.toLong()
                    }

                    is Post.Data.AchievementUnlocked -> {
                        type = DbPost.PostType.ACHIEVEMENT_UNLOCKED.name
                        achievement = data.achievement.name
                    }

                    is Post.Data.QuestShared -> {
                        type = DbPost.PostType.QUEST_SHARED.name
                        questId = data.questId
                        questName = data.questName
                        duration = data.durationTracked.longValue
                    }

                    is Post.Data.QuestWithPomodoroShared -> {
                        type = DbPost.PostType.QUEST_WITH_POMODORO_SHARED.name
                        questId = data.questId
                        questName = data.questName
                        pomodoroCount = data.pomodoroCount.toLong()
                    }

                    is Post.Data.ChallengeShared -> {
                        type = DbPost.PostType.CHALLENGE_SHARED.name
                        challengeId = data.challengeId
                        challengeName = data.name
                    }

                    is Post.Data.ChallengeCompleted -> {
                        type = DbPost.PostType.CHALLENGE_COMPLETED.name
                        challengeId = data.challengeId
                        challengeName = data.name
                        duration = data.duration.longValue
                    }

                    is Post.Data.QuestFromChallengeCompleted -> {
                        type = DbPost.PostType.QUEST_FROM_CHALLENGE_COMPLETED.name
                        questId = data.questId
                        questName = data.questName
                        challengeId = data.challengeId
                        challengeName = data.challengeName
                        duration = data.durationTracked.longValue
                    }

                    is Post.Data.QuestWithPomodoroFromChallengeCompleted -> {
                        type = DbPost.PostType.QUEST_WITH_POMODORO_FROM_CHALLENGE_COMPLETED.name
                        questId = data.questId
                        questName = data.questName
                        challengeId = data.challengeId
                        challengeName = data.challengeName
                        pomodoroCount = data.pomodoroCount.toLong()
                    }

                    is Post.Data.HabitCompleted -> {
                        type = DbPost.PostType.HABIT_COMPLETED.name
                        habitId = data.habitId
                        habitName = data.habitName
                        habitDate = data.habitDate.startOfDayUTC()
                        challengeId = data.challengeId
                        challengeName = data.challengeName
                        isGood = data.isGood
                        streak = data.streak.toLong()
                        bestStreak = data.bestStreak.toLong()
                    }
                }
            }
        }

    private fun toEntityObject(
        dbPost: DbPost,
        dbPlayer: DbPlayer,
        isFromCurrentPlayer: Boolean,
        commentDbPlayers: Map<String, DbPlayer> = emptyMap()
    ): Post {

        val comments = if (commentDbPlayers.isEmpty()) {
            emptyList()
        } else {
            dbPost.comments.map {
                val dbComment = DbPost.Comment(it.value.toMutableMap())
                val dbCommentPlayer = commentDbPlayers[dbComment.playerId]!!
                Post.Comment(
                    id = it.key,
                    playerId = dbComment.playerId,
                    playerAvatar = Avatar.valueOf(dbCommentPlayer.avatar),
                    playerDisplayName = if (dbCommentPlayer.displayName.isNullOrBlank()) "Unknown Hero" else dbCommentPlayer.displayName!!,
                    playerUsername = dbCommentPlayer.username!!,
                    playerLevel = dbPost.playerLevel.toInt(),
                    text = dbComment.text,
                    createdAt = dbComment.createdAt.instant
                )
            }
        }

        return Post(
            id = dbPost.id,
            playerId = dbPlayer.id,
            playerAvatar = Avatar.valueOf(dbPlayer.avatar),
            playerDisplayName =
            if (dbPlayer.displayName.isNullOrBlank()) "Unknown Hero"
            else dbPlayer.displayName!!,
            playerUsername = dbPlayer.username!!,
            playerLevel = dbPost.playerLevel.toInt(),
            data = createData(dbPost),
            reactions = dbPost.reactions.map {
                @Suppress("UNCHECKED_CAST")
                val v = (it.value as MutableMap<String, Long>).entries.first()
                Post.Reaction(
                    it.key,
                    Post.ReactionType.valueOf(v.key),
                    v.value.instant
                )
            },
            imageUrl = dbPost.imageUrl,
            description = dbPost.description,
            comments = comments,
            commentCount = dbPost.comments.size,
            status = Post.Status.valueOf(dbPost.status),
            isFromCurrentPlayer = isFromCurrentPlayer,
            createdAt = dbPost.createdAt.instant,
            updatedAt = dbPost.updatedAt.instant
        )
    }

    private fun createData(dbPost: DbPost) =
        with(dbPost) {
            when (DbPost.PostType.valueOf(type)) {

                DbPost.PostType.DAILY_CHALLENGE_COMPLETED ->
                    Post.Data.DailyChallengeCompleted(streak!!.toInt(), bestStreak!!.toInt())

                DbPost.PostType.ACHIEVEMENT_UNLOCKED ->
                    Post.Data.AchievementUnlocked(Achievement.valueOf(achievement!!))

                DbPost.PostType.LEVEL_UP ->
                    Post.Data.LevelUp(level!!.toInt())

                DbPost.PostType.QUEST_SHARED ->
                    Post.Data.QuestShared(questId!!, questName!!, duration!!.minutes)

                DbPost.PostType.QUEST_WITH_POMODORO_SHARED ->
                    Post.Data.QuestWithPomodoroShared(
                        questId!!,
                        questName!!,
                        pomodoroCount!!.toInt()
                    )

                DbPost.PostType.CHALLENGE_SHARED ->
                    Post.Data.ChallengeShared(challengeId!!, challengeName!!)

                DbPost.PostType.CHALLENGE_COMPLETED ->
                    Post.Data.ChallengeCompleted(challengeId!!, challengeName!!, duration!!.days)

                DbPost.PostType.QUEST_FROM_CHALLENGE_COMPLETED ->
                    Post.Data.QuestFromChallengeCompleted(
                        questId!!,
                        challengeId!!,
                        questName!!,
                        challengeName!!,
                        duration!!.minutes
                    )

                DbPost.PostType.QUEST_WITH_POMODORO_FROM_CHALLENGE_COMPLETED ->
                    Post.Data.QuestWithPomodoroFromChallengeCompleted(
                        questId!!,
                        challengeId!!,
                        questName!!,
                        challengeName!!,
                        pomodoroCount!!.toInt()
                    )

                DbPost.PostType.HABIT_COMPLETED ->
                    Post.Data.HabitCompleted(
                        habitId!!,
                        habitName!!,
                        habitDate!!.startOfDayUTC,
                        challengeId,
                        challengeName,
                        isGood!!,
                        streak!!.toInt(),
                        bestStreak!!.toInt()
                    )

            }
        }
}

class DbPost(val map: MutableMap<String, Any?> = mutableMapOf()) {
    var id: String by map
    var type: String by map
    var playerId: String by map
    var playerLevel: Long by map
    var imageUrl: String? by map
    var description: String? by map
    var questId: String? by map
    var questName: String? by map
    var habitId: String? by map
    var habitName: String? by map
    var habitDate: Long? by map
    var challengeId: String? by map
    var challengeName: String? by map
    var streak: Long? by map
    var bestStreak: Long? by map
    var level: Long? by map
    var isGood: Boolean? by map
    var duration: Long? by map
    var pomodoroCount: Long? by map
    var achievement: String? by map
    var reactions: MutableMap<String, Any?> by map
    var comments: MutableMap<String, Map<String, Any?>> by map
    var status: String by map
    var createdAt: Long by map
    var updatedAt: Long by map

    enum class PostType {
        DAILY_CHALLENGE_COMPLETED,
        ACHIEVEMENT_UNLOCKED,
        LEVEL_UP,
        QUEST_SHARED,
        QUEST_WITH_POMODORO_SHARED,
        CHALLENGE_SHARED,
        CHALLENGE_COMPLETED,
        QUEST_FROM_CHALLENGE_COMPLETED,
        QUEST_WITH_POMODORO_FROM_CHALLENGE_COMPLETED,
        HABIT_COMPLETED
    }

    data class Comment(val map: MutableMap<String, Any?> = mutableMapOf()) {
        var id: String by map
        var playerId: String by map
        var text: String by map
        var createdAt: Long by map
    }
}