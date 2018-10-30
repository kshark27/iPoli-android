package io.ipoli.android

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.challenge.persistence.ChallengeRepository
import io.ipoli.android.common.Reward
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.event.Event
import io.ipoli.android.habit.data.Habit
import io.ipoli.android.habit.persistence.HabitRepository
import io.ipoli.android.pet.Pet
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.player.data.Player
import io.ipoli.android.player.persistence.PlayerRepository
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.RepeatingQuest
import io.ipoli.android.quest.data.persistence.QuestRepository
import io.ipoli.android.repeatingquest.entity.RepeatPattern
import io.ipoli.android.repeatingquest.persistence.RepeatingQuestRepository
import io.ipoli.android.tag.Tag
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDate

/**
 * Created by Venelin Valkov <venelin@io.ipoli.io>
 * on 12/1/17.
 */
object TestUtil {
    val player = Player(
        level = 1,
        coins = 10,
        gems = 0,
        username = "",
        displayName = "",
        bio = null,
        experience = 10,
        authProvider = null,
        pet = Pet(
            "",
            avatar = PetAvatar.ELEPHANT,
            healthPoints = 30,
            moodPoints = Pet.AWESOME_MIN_MOOD_POINTS - 1
        ),
        rank = Player.Rank.NOVICE,
        nextRank = Player.Rank.APPRENTICE
    )

    fun playerRepoMock(player: Player?) = mock<PlayerRepository> {
        on { find() } doReturn player
        on { save(any()) } doAnswer { invocation ->
            invocation.getArgument(0)
        }
        on { saveStatistics(any()) } doAnswer { invocation ->
            invocation.getArgument(0)
        }
    }

    fun questRepoMock() = mock<QuestRepository> {
        on { save(any<Quest>()) } doAnswer { invocation ->
            invocation.getArgument(0)
        }

        on { save(any<List<Quest>>()) } doAnswer { invocation ->
            invocation.getArgument(0)
        }
    }

    fun repeatingQuestRepoMock() = mock<RepeatingQuestRepository> {
        on { save(any<RepeatingQuest>()) } doAnswer { invocation ->
            invocation.getArgument(0)
        }

        on { save(any<List<RepeatingQuest>>()) } doAnswer { invocation ->
            invocation.getArgument(0)
        }
    }

    fun challengeRepoMock(challenge: Challenge? = null) = mock<ChallengeRepository> {
        on { findById(challenge?.id ?: "") } doReturn challenge
        on { save(any<Challenge>()) } doAnswer { invocation ->
            invocation.getArgument(0)
        }
    }

    fun habitRepoMock(habit: Habit?) = mock<HabitRepository> {
        on { findById(habit?.id ?: "") } doReturn habit
        on { save(any<Habit>()) } doAnswer { invocation ->
            invocation.getArgument(0)
        }

        on { save(any<List<Habit>>()) } doAnswer { invocation ->
            invocation.getArgument(0)
        }
    }

    val firstDateOfWeek: LocalDate = LocalDate.now().with(DayOfWeek.MONDAY)
    val lastDateOfWeek: LocalDate = LocalDate.now().with(DayOfWeek.SUNDAY)

    val quest = Quest(
        name = "Test",
        color = Color.BLUE,
        duration = 60,
        scheduledDate = LocalDate.now()
    )

    val event = Event(
        name = "Test",
        startTime = Time.now(),
        endTime = Time.now().plus(60),
        startDate = LocalDate.now(),
        endDate = LocalDate.now(),
        color = 123,
        isAllDay = false,
        isRepeating = false
    )

    val repeatingQuest = RepeatingQuest(
        name = "Test",
        repeatPattern = RepeatPattern.Daily(startDate = firstDateOfWeek),
        color = Color.BLUE,
        duration = 60
    )

    val challenge = Challenge(
        id = "123",
        name = "Test",
        color = Color.BLUE,
        icon = Icon.STAR,
        difficulty = Challenge.Difficulty.NORMAL,
        startDate = LocalDate.now(),
        endDate = LocalDate.now(),
        motivations = listOf()
    )

    val habit = Habit(
        name = "Eat a fruit",
        color = Color.GREEN,
        icon = Icon.FLOWER,
        days = DayOfWeek.values().toSet(),
        isGood = true,
        timesADay = 1,
        streak = Habit.Streak(0, 0),
        preferenceHistory = Habit.PreferenceHistory(
            days = sortedMapOf(LocalDate.now() to DayOfWeek.values().toSet()),
            timesADay = sortedMapOf(LocalDate.now() to 1)
        )
    )

    val tag = Tag(
        name = "wellness",
        color = Color.GREEN,
        icon = Icon.FLOWER,
        isFavorite = true
    )

    val reward = Reward(
        attributePoints = emptyMap(),
        healthPoints = 1,
        experience = 1,
        coins = 1,
        bounty = Quest.Bounty.None
    )
}