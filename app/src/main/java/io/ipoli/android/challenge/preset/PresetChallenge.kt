package io.ipoli.android.challenge.preset

import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.common.datetime.Day
import io.ipoli.android.common.datetime.Duration
import io.ipoli.android.common.datetime.Minute
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.friends.feed.data.Post
import io.ipoli.android.player.data.Avatar
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Icon

data class PresetChallenge(
    val id: String = "",
    val name: String,
    val color: Color,
    val icon: Icon,
    val shortDescription: String,
    val description: String,
    val category: Category,
    val imageUrl: String,
    val duration: Duration<Day>,
    val busynessPerWeek: Duration<Minute>,
    val difficulty: Challenge.Difficulty,
    val requirements: List<String>,
    val level: Int?,
    val trackedValues: List<Challenge.TrackedValue>,
    val expectedResults: List<String>,
    val gemPrice: Int,
    val note: String,
    val config: Config,
    val schedule: Schedule,
    val status: Post.Status,
    val participantCount: Int,
    val author: Author?
) {

    data class Author(
        val id: String,
        val displayName: String,
        val username: String,
        val avatar: Avatar,
        val level: Int
    )

    enum class Category(val color: Color) {
        FITNESS(Color.GREEN),
        HEALTH(Color.ORANGE),
        LEARNING(Color.BLUE),
        ORGANIZE(Color.TEAL),
        ADVENTURE(Color.PURPLE)
    }

    data class Config(
        val defaultStartTime: Time? = null,
        val nutritionMacros: NutritionMacros? = null
    )

    data class NutritionMacros(val female: NutritionDetails, val male: NutritionDetails)

    data class NutritionDetails(
        val caloriesPerKg: Float,
        val proteinPerKg: Float,
        val carbohydratesPerKg: Float,
        val fatPerKg: Float
    )

    data class Schedule(val quests: List<Quest>, val habits: List<Habit>)

    data class Quest(
        val name: String,
        val color: Color,
        val icon: Icon,
        val day: Int,
        val duration: Duration<Minute>,
        val subQuests: List<String>,
        val note: String
    )

    data class Habit(
        val name: String,
        val isGood: Boolean,
        val timesADay: Int,
        val color: Color,
        val icon: Icon,
        val isSelected: Boolean
    )
}