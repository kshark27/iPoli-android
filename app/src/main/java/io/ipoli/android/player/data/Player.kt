package io.ipoli.android.player.data

import android.net.Uri
import android.support.annotation.ColorRes
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import io.ipoli.android.Constants
import io.ipoli.android.R
import io.ipoli.android.achievement.Achievement
import io.ipoli.android.challenge.preset.PresetChallenge
import io.ipoli.android.common.Reward
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.datetime.TimeOfDay
import io.ipoli.android.pet.Food
import io.ipoli.android.pet.Pet
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.pet.PetItem
import io.ipoli.android.player.AttributePointsForLevelGenerator
import io.ipoli.android.player.ExperienceForLevelGenerator
import io.ipoli.android.player.Theme
import io.ipoli.android.quest.ColorPack
import io.ipoli.android.quest.Entity
import io.ipoli.android.quest.IconPack
import io.ipoli.android.quest.Quest
import io.ipoli.android.store.powerup.PowerUp
import io.ipoli.android.tag.Tag
import org.threeten.bp.DayOfWeek
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime

data class Player(
    override val id: String = "",
    val schemaVersion: Int = Constants.SCHEMA_VERSION,
    val username: String?,
    val displayName: String?,
    val bio: String?,
    val level: Int = 1,
    val attributes: Map<AttributeType, Attribute> = mapOf(
        AttributeType.STRENGTH to Attribute(
            type = AttributeType.STRENGTH,
            points = 0,
            level = Constants.DEFAULT_ATTRIBUTE_LEVEL,
            pointsForNextLevel = AttributePointsForLevelGenerator.forLevel(2),
            tags = emptyList()
        ),
        AttributeType.INTELLIGENCE to Attribute(
            type = AttributeType.INTELLIGENCE,
            points = 0,
            level = Constants.DEFAULT_ATTRIBUTE_LEVEL,
            pointsForNextLevel = AttributePointsForLevelGenerator.forLevel(2),
            tags = emptyList()
        ),
        AttributeType.CHARISMA to Attribute(
            type = AttributeType.CHARISMA,
            points = 0,
            level = Constants.DEFAULT_ATTRIBUTE_LEVEL,
            pointsForNextLevel = AttributePointsForLevelGenerator.forLevel(2),
            tags = emptyList()
        ),
        AttributeType.EXPERTISE to Attribute(
            type = AttributeType.EXPERTISE,
            points = 0,
            level = Constants.DEFAULT_ATTRIBUTE_LEVEL,
            pointsForNextLevel = AttributePointsForLevelGenerator.forLevel(2),
            tags = emptyList()
        ),
        AttributeType.WELL_BEING to Attribute(
            type = AttributeType.WELL_BEING,
            points = 0,
            level = Constants.DEFAULT_ATTRIBUTE_LEVEL,
            pointsForNextLevel = AttributePointsForLevelGenerator.forLevel(2),
            tags = emptyList()
        ),
        AttributeType.WILLPOWER to Attribute(
            type = AttributeType.WILLPOWER,
            points = 0,
            level = Constants.DEFAULT_ATTRIBUTE_LEVEL,
            pointsForNextLevel = AttributePointsForLevelGenerator.forLevel(2),
            tags = emptyList()
        )
    ),
    val health: Health = Health(Constants.DEFAULT_PLAYER_MAX_HP, Constants.DEFAULT_PLAYER_MAX_HP),
    val experience: Long = Constants.DEFAULT_PLAYER_XP,
    val coins: Int = Constants.DEFAULT_PLAYER_COINS,
    val gems: Int = Constants.DEFAULT_PLAYER_GEMS,
    val authProvider: AuthProvider?,
    val avatar: Avatar = Avatar.AVATAR_00,
    val membership: Membership = Membership.NONE,
    val pet: Pet = Pet(
        name = Constants.DEFAULT_PET_NAME,
        avatar = Constants.DEFAULT_PET_AVATAR
    ),
    val inventory: Inventory = Inventory(
        food = mapOf(
            Food.BANANA to 2
        ),
        avatars = setOf(Avatar.AVATAR_00),
        pets = setOf(InventoryPet.createFromPet(pet)),
        themes = setOf(Constants.DEFAULT_THEME),
        colorPacks = setOf(ColorPack.FREE),
        iconPacks = setOf(IconPack.FREE)
    ),
    val preferences: Preferences = Preferences(),
    val achievements: List<UnlockedAchievement> = listOf(),
    val statistics: Statistics = Statistics(inviteForFriendCount = 3L),
    val rank: Rank,
    val nextRank: Rank?,
    override val updatedAt: Instant = Instant.now(),
    override val createdAt: Instant = Instant.now(),
    val removedAt: Instant? = null
) : Entity {

    enum class Rank {
        NOVICE,
        APPRENTICE,
        ADEPT,
        SPECIALIST,
        EXPERT,
        MASTER,
        LEGEND,
        IMMORTAL,
        DEMIGOD,
        TITAN,
        DIVINITY
    }

    data class Health(val current: Int, val max: Int)

    enum class AttributeType {
        STRENGTH,
        INTELLIGENCE,
        WELL_BEING,
        WILLPOWER,
        EXPERTISE,
        CHARISMA
    }


    data class Attribute(
        val type: AttributeType,
        val points: Int,
        val level: Int,
        val pointsForNextLevel: Int,
        val tags: List<Tag>
    ) {
        val progressForLevel: Int
            get() {
                val thisLevelPoints = AttributePointsForLevelGenerator.forLevel(level).toInt()
                return points - thisLevelPoints
            }

        val progressForNextLevel: Int
            get() {
                val thisLevelPoints = AttributePointsForLevelGenerator.forLevel(level)
                return pointsForNextLevel - thisLevelPoints
            }
    }

    data class UnlockedAchievement(
        val achievement: Achievement,
        val unlockTime: Time = Time.now(),
        val unlockDate: LocalDate = LocalDate.now()
    )

    data class Preferences(
        val theme: Theme = Constants.DEFAULT_THEME,
        val syncCalendars: Set<SyncCalendar> = setOf(),
        val productiveTimesOfDay: Set<TimeOfDay> = Constants.DEFAULT_PLAYER_PRODUCTIVE_TIMES,
        val workDays: Set<DayOfWeek> = Constants.DEFAULT_PLAYER_WORK_DAYS,
        val workStartTime: Time = Constants.DEFAULT_PLAYER_WORK_START_TIME,
        val workEndTime: Time = Constants.DEFAULT_PLAYER_WORK_END_TIME,
        val sleepStartTime: Time = Constants.DEFAULT_PLAYER_SLEEP_START_TIME,
        val sleepEndTime: Time = Constants.DEFAULT_PLAYER_SLEEP_START_TIME,
        val timeFormat: TimeFormat = Constants.DEFAULT_TIME_FORMAT,
        val temperatureUnit: TemperatureUnit = Constants.DEFAULT_TEMPERATURE_UNIT,
        val planDays: Set<DayOfWeek> = Constants.DEFAULT_PLAN_DAYS,
        val planDayTime: Time = Time.of(Constants.DEFAULT_PLAN_DAY_REMINDER_START_MINUTE),
        val isQuickDoNotificationEnabled: Boolean = Constants.DEFAULT_QUICK_DO_NOTIFICATION_ENABLED,
        val resetDayTime: Time = Constants.RESET_DAY_TIME,
        val agendaStartScreen: AgendaScreen = Constants.DEFAULT_AGENDA_START_SCREEN,
        val reminderNotificationStyle: NotificationStyle = Constants.DEFAULT_REMINDER_NOTIFICATION_STYLE,
        val planDayNotificationStyle: NotificationStyle = Constants.DEFAULT_PLAN_DAY_NOTIFICATION_STYLE,
        val isAutoPostingEnabled: Boolean = Constants.DEFAULT_AUTO_POSTING_ENABLED
    ) {
        val nonWorkDays: Set<DayOfWeek>
            get() = DayOfWeek.values().toSet() - workDays

        enum class TimeFormat {
            TWELVE_HOURS, TWENTY_FOUR_HOURS, DEVICE_DEFAULT
        }

        enum class TemperatureUnit {
            CELSIUS, FAHRENHEIT
        }

        enum class NotificationStyle {
            NOTIFICATION, POPUP, ALL
        }

        enum class AgendaScreen {
            DAY, AGENDA
        }

        data class SyncCalendar(val id: String, val name: String)
    }

    fun updatePreferences(preferences: Preferences) =
        copy(preferences = preferences)

    fun addHealthPoints(healthPoints: Int) =
        copy(
            health = health.copy(
                current = Math.min(health.current + healthPoints, health.max)
            )
        )

    fun removeHealthPoints(points: Int) =
        copy(
            health = health.copy(current = Math.max(health.current - points, 0))
        )

    fun addExperience(experience: Int): Player {
        val newXp = experience + this.experience
        return copy(
            experience = newXp,
            level = nextLevel(newXp)
        )
    }

    private fun nextLevel(newXp: Long): Int {
        var newLevel = level
        while (newXp >= ExperienceForLevelGenerator.forLevel(newLevel + 1)) {
            newLevel++
        }
        return newLevel
    }

    fun removeExperience(experience: Int): Player {
        val newXp = Math.max(this.experience - experience, 0)
        return copy(
            experience = newXp,
            level = prevLevel(newXp)
        )
    }

    private fun prevLevel(newXp: Long): Int {
        var newLevel = level
        while (newLevel - 1 > 0 && newXp < ExperienceForLevelGenerator.forLevel(
                newLevel
            )
        ) {
            newLevel--
        }
        return newLevel
    }

    fun addCoins(coins: Int) = copy(
        coins = coins + this.coins
    )

    fun removeCoins(coins: Int) = copy(
        coins = Math.max(this.coins - coins, 0)
    )

    fun attribute(attributeType: AttributeType): Attribute =
        attributes[attributeType]!!

    fun attributeLevel(attributeType: AttributeType): Int =
        attributes[attributeType]!!.level

    fun addAttributePoints(attributePoints: Map<AttributeType, Int>) =
        copy(
            attributes = this.attributes.map {
                val attr = it.value
                if (attributePoints.contains(it.key)) {
                    val newPoints = attr.points + attributePoints[it.key]!!
                    val newLevel = nextAttrLevel(newPoints, attr.level)
                    Pair(
                        it.key, attr.copy(
                            points = newPoints,
                            level = newLevel,
                            pointsForNextLevel = AttributePointsForLevelGenerator.forLevel(newLevel + 1)
                        )
                    )
                } else Pair(it.key, attr)
            }.toMap()
        )

    private fun nextAttrLevel(newPoints: Int, currentLevel: Int): Int {
        var newLevel = currentLevel
        while (newPoints >= AttributePointsForLevelGenerator.forLevel(newLevel + 1)) {
            newLevel++
        }
        return newLevel
    }

    fun removeAttributePoints(attributePoints: Map<AttributeType, Int>) =
        copy(
            attributes = this.attributes.map {
                val attr = it.value
                if (attributePoints.contains(it.key)) {
                    val newPoints = Math.max(attr.points - attributePoints[it.key]!!, 0)
                    val newLevel = prevAttrLevel(newPoints, attr.level)
                    Pair(
                        it.key, attr.copy(
                            points = newPoints,
                            level = newLevel,
                            pointsForNextLevel = AttributePointsForLevelGenerator.forLevel(newLevel + 1)
                        )
                    )
                } else Pair(it.key, attr)
            }.toMap()
        )

    private fun prevAttrLevel(newPoints: Int, currentLevel: Int): Int {
        var newLevel = currentLevel
        while (newLevel - 1 > 0 && newPoints < AttributePointsForLevelGenerator.forLevel(newLevel)
        ) {
            newLevel--
        }
        return newLevel
    }

    fun addTagToAttribute(attribute: AttributeType, tag: Tag): Player {
        require(attributes[attribute]!!.tags.size < 3)
        val attrs = attributes.toMutableMap()
        attrs[attribute] = attrs[attribute]!!.let {
            it.copy(
                tags = it.tags + tag
            )
        }
        return copy(attributes = attrs)
    }

    fun removeTagFromAttribute(attribute: AttributeType, tag: Tag): Player {
        require(attributes[attribute]!!.tags.contains(tag))
        val attrs = attributes.toMutableMap()
        attrs[attribute] = attrs[attribute]!!.let {
            it.copy(
                tags = it.tags - tag
            )
        }
        return copy(attributes = attrs)
    }

    fun hasPet(petAvatar: PetAvatar) =
        inventory.hasPet(petAvatar)

    fun hasTheme(theme: Theme) =
        inventory.hasTheme(theme)

    fun hasIconPack(iconPack: IconPack) =
        inventory.hasIconPack(iconPack)

    fun hasColorPack(colorPack: ColorPack) =
        inventory.hasColorPack(colorPack)

    fun hasChallenge(challenge: PresetChallenge) = inventory.hasChallenge(challenge)

    fun isPowerUpEnabled(powerUp: PowerUp.Type) = inventory.isPowerUpEnabled(powerUp)

    val experienceProgressForLevel: Int
        get() {
            val thisLevelXP = ExperienceForLevelGenerator.forLevel(level).toInt()
            return experience.toInt() - thisLevelXP
        }

    val experienceForNextLevel: Int
        get() {
            val thisLevelXP = ExperienceForLevelGenerator.forLevel(level).toInt()
            val nextLevelXP = ExperienceForLevelGenerator.forLevel(level + 1)
                .toInt()
            return nextLevelXP - thisLevelXP
        }

    val isDead: Boolean
        get() = health.current == 0

    fun revive(): Player {
        require(isDead)
        val newLevel = Math.max(level - 1, Constants.DEFAULT_PLAYER_LEVEL)
        val newXP = ExperienceForLevelGenerator.forLevel(newLevel)
        return copy(
            health = health.copy(
                current = (health.max * Constants.PLAYER_REVIVE_HEALTH_PERCENTAGE).toInt()
            ),
            level = newLevel,
            experience = newXP,
            coins = 0
        )
    }

    fun isLoggedIn() =
        authProvider is AuthProvider.Google || authProvider is AuthProvider.Facebook

    fun currentDate(dateTime: LocalDateTime = LocalDateTime.now()) =
        currentDate(dateTime, preferences.resetDayTime)

    fun datesSpan(dateTime: LocalDateTime = LocalDateTime.now()) =
        datesSpan(dateTime, preferences.resetDayTime)

    fun addReward(reward: Reward) =
        addHealthPoints(reward.healthPoints)
            .addExperience(reward.experience)
            .addCoins(reward.coins)
            .addAttributePoints(reward.attributePoints)
            .copy(
                pet = if (pet.isDead) pet else pet.rewardWith(reward.experience),
                inventory = this.inventory.let {
                    if (reward.bounty is Quest.Bounty.Food) {
                        it.addFood(reward.bounty.food)
                    } else {
                        it
                    }
                }
            )

    fun removeReward(reward: Reward) =
        removeHealthPoints(reward.healthPoints)
            .removeExperience(reward.experience)
            .removeCoins(reward.coins)
            .removeAttributePoints(reward.attributePoints)
            .copy(pet = if (pet.isDead) pet else pet.removeReward(reward))

    companion object {
        fun currentDate(
            dateTime: LocalDateTime = LocalDateTime.now(),
            resetDayTime: Time
        ): LocalDate {

            val currentTime = Time.at(dateTime.toLocalTime().hour, dateTime.toLocalTime().minute)

            return when {
                resetDayTime.isBetween(
                    Time.atHours(0),
                    Time.at(11, 59)
                ) && currentTime < resetDayTime -> dateTime.minusDays(1).toLocalDate()

                resetDayTime.isBetween(
                    Time.atHours(12),
                    Time.at(23, 59)
                ) && currentTime >= resetDayTime -> dateTime.plusDays(1).toLocalDate()

                else -> dateTime.toLocalDate()
            }
        }

        fun datesSpan(
            dateTime: LocalDateTime = LocalDateTime.now(),
            resetDayTime: Time
        ): Pair<LocalDate, LocalDate?> {
            val currentTime = Time.at(dateTime.toLocalTime().hour, dateTime.toLocalTime().minute)

            return when {
                resetDayTime == Time.atHours(0) -> Pair(dateTime.toLocalDate(), null)

                resetDayTime.isBetween(
                    Time.atHours(0),
                    Time.at(11, 59)
                ) && currentTime < resetDayTime ->
                    Pair(dateTime.minusDays(1).toLocalDate(), dateTime.toLocalDate())

                resetDayTime.isBetween(
                    Time.atHours(0),
                    Time.at(11, 59)
                ) && currentTime >= resetDayTime ->
                    Pair(dateTime.toLocalDate(), dateTime.plusDays(1).toLocalDate())


                resetDayTime.isBetween(
                    Time.atHours(12),
                    Time.at(23, 59)
                ) && currentTime >= resetDayTime ->
                    Pair(dateTime.toLocalDate(), dateTime.plusDays(1).toLocalDate())

                resetDayTime.isBetween(
                    Time.atHours(12),
                    Time.at(23, 59)
                ) && currentTime < resetDayTime ->
                    Pair(dateTime.minusDays(1).toLocalDate(), dateTime.toLocalDate())

                else -> throw IllegalStateException("Illegal resetDayTime and/or currentTime: $resetDayTime $currentTime ")
            }
        }
    }
}

data class InventoryPet(
    val name: String,
    val avatar: PetAvatar,
    val items: Set<PetItem> = setOf()
) {
    companion object {
        fun createFromPet(pet: Pet): InventoryPet {
            val equipment = pet.equipment
            return InventoryPet(
                pet.name, pet.avatar,
                listOfNotNull(
                    equipment.hat,
                    equipment.mask,
                    equipment.bodyArmor
                ).toSet()
            )
        }
    }

    fun hasItem(item: PetItem) = items.contains(item)
}

data class Inventory(
    val food: Map<Food, Int> = mapOf(),
    val avatars: Set<Avatar> = setOf(),
    val pets: Set<InventoryPet> = setOf(),
    val themes: Set<Theme> = setOf(),
    val colorPacks: Set<ColorPack> = setOf(),
    val iconPacks: Set<IconPack> = setOf(),
    val presetChallengeIds: Set<String> = setOf(),
    val powerUps: List<PowerUp> = listOf()
) {
    fun addFood(food: Food, quantity: Int = 1): Inventory {
        val qty = this.food.let {
            if (it.containsKey(food)) it[food]!! + 1 else quantity
        }
        return copy(
            food = this.food + Pair(food, qty)
        )
    }

    fun removeFood(food: Food): Inventory {
        requireNotNull(this.food[food])
        val quantity = this.food[food]!! - 1
        return copy(
            food = when (quantity) {
                0 -> this.food.minus(food)
                else -> this.food + Pair(food, quantity)
            }
        )
    }

    fun hasFood(food: Food) = this.food.containsKey(food)

    fun addPet(pet: Pet) =
        copy(
            pets = this.pets + InventoryPet.createFromPet(pet)
        )

    fun hasPet(petAvatar: PetAvatar) =
        pets.any { it.avatar == petAvatar }

    fun getPet(petAvatar: PetAvatar) =
        pets.first { it.avatar == petAvatar }

    fun hasAvatar(avatar: Avatar) = avatars.contains(avatar)

    fun addAvatar(avatar: Avatar) = copy(avatars = this.avatars + avatar)

    fun hasTheme(theme: Theme) = themes.contains(theme)

    fun addTheme(theme: Theme) = copy(themes = this.themes + theme)

    fun changePetName(name: String, petAvatar: PetAvatar): Inventory {

        val currentPet = getPet(petAvatar)

        return copy(
            pets = this.pets - currentPet + InventoryPet(
                name,
                currentPet.avatar,
                currentPet.items
            )
        )
    }

    fun hasIconPack(iconPack: IconPack) =
        iconPacks.contains(iconPack)

    fun addIconPack(iconPack: IconPack) =
        copy(iconPacks = this.iconPacks + iconPack)

    fun hasColorPack(colorPack: ColorPack) =
        colorPacks.contains(colorPack)

    fun addColorPack(colorPack: ColorPack) =
        copy(colorPacks = this.colorPacks + colorPack)

    fun addPetItem(itemPair: Pair<PetItem, PetAvatar>): Inventory {

        val (item, petAvatar) = itemPair

        val currentPet = getPet(petAvatar)

        return copy(
            pets = this.pets - currentPet + InventoryPet(
                currentPet.name,
                currentPet.avatar,
                currentPet.items + item
            )
        )
    }

    fun hasChallenge(presetChallenge: PresetChallenge) =
        presetChallengeIds.contains(presetChallenge.id)

    fun addChallenge(presetChallenge: PresetChallenge) =
        copy(presetChallengeIds = this.presetChallengeIds + presetChallenge.id)

    fun addPowerUp(powerUp: PowerUp): Inventory {
        require(!isPowerUpEnabled(powerUp.type))
        return copy(powerUps = powerUps + powerUp)
    }

    fun isPowerUpEnabled(powerUpType: PowerUp.Type) =
        getPowerUp(powerUpType) != null

    fun getPowerUp(powerUpType: PowerUp.Type) =
        powerUps.firstOrNull { it.type == powerUpType }

    fun removePowerUp(powerUp: PowerUp): Inventory {
        require(isPowerUpEnabled(powerUp.type))
        return copy(powerUps = powerUps - powerUp)
    }

    fun setPowerUps(powerUps: List<PowerUp>) =
        copy(powerUps = powerUps)
}

sealed class AuthProvider {
    data class Facebook(
        val userId: String,
        val displayName: String?,
        val email: String?,
        val imageUrl: Uri?
    ) : AuthProvider()

    data class Google(
        val userId: String,
        val displayName: String?,
        val email: String?,
        val imageUrl: Uri?
    ) : AuthProvider()
}

enum class Membership(
    val dailyHighRewardQuestCap: Int,
    val dailyHighRewardHabitCap: Int,
    val dailyRewardedChallengeCap: Int,
    val monthlyConvertedGemCap: Int
) {
    NONE(6, 6, 2, 5),
    MONTHLY(8, 8, 2, 8),
    QUARTERLY(10, 10, 2, 12),
    YEARLY(15, 15, 2, Membership.UNLIMITED);

    companion object {
        const val UNLIMITED = -1
    }
}

enum class AndroidAttribute(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @ColorRes val colorPrimary: Int,
    @ColorRes val colorPrimaryDark: Int,
    @DrawableRes val background: Int,
    @DrawableRes val whiteIcon: Int,
    val bonusNames: Map<Player.Rank, Int>,
    val bonusDescriptions: Map<Player.Rank, Int>
) {
    STRENGTH(
        R.string.attribute_strength,
        R.string.attribute_strength_desc,
        R.color.md_red_400,
        R.color.md_red_900,
        R.drawable.attribute_strength_hexagon,
        R.drawable.ic_strength_white,
        mapOf(
            Player.Rank.APPRENTICE to R.string.strength_apprentice_bonus_name,
            Player.Rank.ADEPT to R.string.strength_adept_bonus_name,
            Player.Rank.SPECIALIST to R.string.strength_specialist_bonus_name,
            Player.Rank.EXPERT to R.string.attribute_strength,
            Player.Rank.MASTER to R.string.attribute_strength,
            Player.Rank.LEGEND to R.string.attribute_strength,
            Player.Rank.IMMORTAL to R.string.attribute_strength,
            Player.Rank.DEMIGOD to R.string.attribute_strength,
            Player.Rank.TITAN to R.string.attribute_strength,
            Player.Rank.DIVINITY to R.string.attribute_strength
        ),
        mapOf(
            Player.Rank.APPRENTICE to R.string.strength_apprentice_bonus_desc,
            Player.Rank.ADEPT to R.string.strength_adept_bonus_desc,
            Player.Rank.SPECIALIST to R.string.strength_specialist_bonus_desc,
            Player.Rank.EXPERT to R.string.attribute_strength,
            Player.Rank.MASTER to R.string.attribute_strength,
            Player.Rank.LEGEND to R.string.attribute_strength,
            Player.Rank.IMMORTAL to R.string.attribute_strength,
            Player.Rank.DEMIGOD to R.string.attribute_strength,
            Player.Rank.TITAN to R.string.attribute_strength,
            Player.Rank.DIVINITY to R.string.attribute_strength
        )
    ),
    INTELLIGENCE(
        R.string.attribute_intelligence,
        R.string.attribute_intelligence_desc,
        R.color.md_blue_500,
        R.color.md_blue_800,
        R.drawable.attribute_intelligence_hexagon,
        R.drawable.ic_intelligence_white,
        mapOf(
            Player.Rank.APPRENTICE to R.string.intelligence_apprentice_bonus_name,
            Player.Rank.ADEPT to R.string.intelligence_adept_bonus_name,
            Player.Rank.SPECIALIST to R.string.intelligence_specialist_bonus_name,
            Player.Rank.EXPERT to R.string.attribute_strength,
            Player.Rank.MASTER to R.string.attribute_strength,
            Player.Rank.LEGEND to R.string.attribute_strength,
            Player.Rank.IMMORTAL to R.string.attribute_strength,
            Player.Rank.DEMIGOD to R.string.attribute_strength,
            Player.Rank.TITAN to R.string.attribute_strength,
            Player.Rank.DIVINITY to R.string.attribute_strength
        ),
        mapOf(
            Player.Rank.APPRENTICE to R.string.intelligence_apprentice_bonus_desc,
            Player.Rank.ADEPT to R.string.intelligence_adept_bonus_desc,
            Player.Rank.SPECIALIST to R.string.intelligence_specialist_bonus_desc,
            Player.Rank.EXPERT to R.string.attribute_strength,
            Player.Rank.MASTER to R.string.attribute_strength,
            Player.Rank.LEGEND to R.string.attribute_strength,
            Player.Rank.IMMORTAL to R.string.attribute_strength,
            Player.Rank.DEMIGOD to R.string.attribute_strength,
            Player.Rank.TITAN to R.string.attribute_strength,
            Player.Rank.DIVINITY to R.string.attribute_strength
        )
    ),
    CHARISMA(
        R.string.attribute_charisma,
        R.string.attribute_charisma_desc,
        R.color.md_purple_300,
        R.color.md_purple_600,
        R.drawable.attribute_charisma_hexagon,
        R.drawable.ic_charisma_white,
        mapOf(
            Player.Rank.APPRENTICE to R.string.charisma_apprentice_bonus_name,
            Player.Rank.ADEPT to R.string.charisma_adept_bonus_name,
            Player.Rank.SPECIALIST to R.string.charisma_specialist_bonus_name,
            Player.Rank.EXPERT to R.string.attribute_strength,
            Player.Rank.MASTER to R.string.attribute_strength,
            Player.Rank.LEGEND to R.string.attribute_strength,
            Player.Rank.IMMORTAL to R.string.attribute_strength,
            Player.Rank.DEMIGOD to R.string.attribute_strength,
            Player.Rank.TITAN to R.string.attribute_strength,
            Player.Rank.DIVINITY to R.string.attribute_strength
        ),
        mapOf(
            Player.Rank.APPRENTICE to R.string.charisma_apprentice_bonus_desc,
            Player.Rank.ADEPT to R.string.charisma_adept_bonus_desc,
            Player.Rank.SPECIALIST to R.string.charisma_specialist_bonus_desc,
            Player.Rank.EXPERT to R.string.attribute_strength,
            Player.Rank.MASTER to R.string.attribute_strength,
            Player.Rank.LEGEND to R.string.attribute_strength,
            Player.Rank.IMMORTAL to R.string.attribute_strength,
            Player.Rank.DEMIGOD to R.string.attribute_strength,
            Player.Rank.TITAN to R.string.attribute_strength,
            Player.Rank.DIVINITY to R.string.attribute_strength
        )
    ),
    EXPERTISE(
        R.string.attribute_expertise,
        R.string.attribute_expertise_desc,
        R.color.md_indigo_400,
        R.color.md_indigo_700,
        R.drawable.attribute_expertise_hexagon,
        R.drawable.ic_expertise_white,
        mapOf(
            Player.Rank.APPRENTICE to R.string.expertise_apprentice_bonus_name,
            Player.Rank.ADEPT to R.string.expertise_adept_bonus_name,
            Player.Rank.SPECIALIST to R.string.expertise_specialist_bonus_name,
            Player.Rank.EXPERT to R.string.attribute_strength,
            Player.Rank.MASTER to R.string.attribute_strength,
            Player.Rank.LEGEND to R.string.attribute_strength,
            Player.Rank.IMMORTAL to R.string.attribute_strength,
            Player.Rank.DEMIGOD to R.string.attribute_strength,
            Player.Rank.TITAN to R.string.attribute_strength,
            Player.Rank.DIVINITY to R.string.attribute_strength
        ),
        mapOf(
            Player.Rank.APPRENTICE to R.string.expertise_apprentice_bonus_desc,
            Player.Rank.ADEPT to R.string.expertise_adept_bonus_desc,
            Player.Rank.SPECIALIST to R.string.expertise_specialist_bonus_desc,
            Player.Rank.EXPERT to R.string.attribute_strength,
            Player.Rank.MASTER to R.string.attribute_strength,
            Player.Rank.LEGEND to R.string.attribute_strength,
            Player.Rank.IMMORTAL to R.string.attribute_strength,
            Player.Rank.DEMIGOD to R.string.attribute_strength,
            Player.Rank.TITAN to R.string.attribute_strength,
            Player.Rank.DIVINITY to R.string.attribute_strength
        )
    ),
    WELL_BEING(
        R.string.attribute_well_being,
        R.string.attribute_well_being_desc,
        R.color.md_green_600,
        R.color.md_green_800,
        R.drawable.attribute_well_beaing_hexagon,
        R.drawable.ic_well_being_white,
        mapOf(
            Player.Rank.APPRENTICE to R.string.well_being_apprentice_bonus_name,
            Player.Rank.ADEPT to R.string.well_being_adept_bonus_name,
            Player.Rank.SPECIALIST to R.string.well_being_specialist_bonus_name,
            Player.Rank.EXPERT to R.string.attribute_strength,
            Player.Rank.MASTER to R.string.attribute_strength,
            Player.Rank.LEGEND to R.string.attribute_strength,
            Player.Rank.IMMORTAL to R.string.attribute_strength,
            Player.Rank.DEMIGOD to R.string.attribute_strength,
            Player.Rank.TITAN to R.string.attribute_strength,
            Player.Rank.DIVINITY to R.string.attribute_strength
        ),
        mapOf(
            Player.Rank.APPRENTICE to R.string.well_being_apprentice_bonus_desc,
            Player.Rank.ADEPT to R.string.well_being_adept_bonus_desc,
            Player.Rank.SPECIALIST to R.string.well_being_specialist_bonus_desc,
            Player.Rank.EXPERT to R.string.attribute_strength,
            Player.Rank.MASTER to R.string.attribute_strength,
            Player.Rank.LEGEND to R.string.attribute_strength,
            Player.Rank.IMMORTAL to R.string.attribute_strength,
            Player.Rank.DEMIGOD to R.string.attribute_strength,
            Player.Rank.TITAN to R.string.attribute_strength,
            Player.Rank.DIVINITY to R.string.attribute_strength
        )
    ),
    WILLPOWER(
        R.string.attribute_willpower,
        R.string.attribute_willpower_desc,
        R.color.md_yellow_700,
        R.color.md_yellow_900,
        R.drawable.attribute_willpower_hexagon,
        R.drawable.ic_willpower_white,
        mapOf(
            Player.Rank.APPRENTICE to R.string.willpower_apprentice_bonus_name,
            Player.Rank.ADEPT to R.string.willpower_adept_bonus_name,
            Player.Rank.SPECIALIST to R.string.willpower_specialist_bonus_name,
            Player.Rank.EXPERT to R.string.attribute_strength,
            Player.Rank.MASTER to R.string.attribute_strength,
            Player.Rank.LEGEND to R.string.attribute_strength,
            Player.Rank.IMMORTAL to R.string.attribute_strength,
            Player.Rank.DEMIGOD to R.string.attribute_strength,
            Player.Rank.TITAN to R.string.attribute_strength,
            Player.Rank.DIVINITY to R.string.attribute_strength
        ),
        mapOf(
            Player.Rank.APPRENTICE to R.string.willpower_apprentice_bonus_desc,
            Player.Rank.ADEPT to R.string.willpower_adept_bonus_desc,
            Player.Rank.SPECIALIST to R.string.willpower_specialist_bonus_desc,
            Player.Rank.EXPERT to R.string.attribute_strength,
            Player.Rank.MASTER to R.string.attribute_strength,
            Player.Rank.LEGEND to R.string.attribute_strength,
            Player.Rank.IMMORTAL to R.string.attribute_strength,
            Player.Rank.DEMIGOD to R.string.attribute_strength,
            Player.Rank.TITAN to R.string.attribute_strength,
            Player.Rank.DIVINITY to R.string.attribute_strength
        )
    )
}

enum class AndroidRank(@StringRes val title: Int) {
    NOVICE(R.string.novice),
    APPRENTICE(R.string.apprentice),
    ADEPT(R.string.adept),
    SPECIALIST(R.string.specialist),
    EXPERT(R.string.expert),
    MASTER(R.string.master),
    LEGEND(R.string.legend),
    IMMORTAL(R.string.immortal),
    DEMIGOD(R.string.demigod),
    TITAN(R.string.titan),
    DIVINITY(R.string.divinity)
}