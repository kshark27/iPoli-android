package io.ipoli.android.player.persistence.model

import io.ipoli.android.common.persistence.FirestoreModel

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 12/1/17.
 */

data class DbPlayer(override val map: MutableMap<String, Any?> = mutableMapOf()) :
    FirestoreModel {
    override var id: String by map
    var username: String? by map
    var displayName: String? by map
    var bio: String? by map
    var schemaVersion: Long by map
    var health: MutableMap<String, Any?> by map
    var attributes: MutableMap<String, Map<String, Any?>> by map
    var level: Long by map
    var coins: Long by map
    var gems: Long by map
    var experience: Long by map
    var authProvider: MutableMap<String, Any?>? by map
    var avatar: String by map
    var pet: MutableMap<String, Any?> by map
    var inventory: MutableMap<String, Any?> by map
    var membership: String by map
    var preferences: MutableMap<String, Any?> by map
    var achievements: List<MutableMap<String, Any?>> by map
    var statistics: MutableMap<String, Any?> by map
    override var createdAt: Long by map
    override var updatedAt: Long by map
    override var removedAt: Long? by map
}

data class DbHealth(val map: MutableMap<String, Any?> = mutableMapOf()) {
    var current: Long by map
    var max: Long by map
}

data class DbAttribute(val map: MutableMap<String, Any?> = mutableMapOf()) {
    var type: String by map
    var points: Long by map
    var level: Long by map
    var pointsForNextLevel: Long by map
    var tagIds: List<String> by map
}

data class DbAuthProvider(val map: MutableMap<String, Any?> = mutableMapOf()) {
    var userId: String by map
    var provider: String by map
    var displayName: String? by map
    var email: String? by map
    var image: String? by map
}

data class DbPet(val map: MutableMap<String, Any?> = mutableMapOf()) {
    var name: String by map
    var avatar: String by map
    var equipment: MutableMap<String, Any?> by map
    var moodPoints: Long by map
    var healthPoints: Long by map
    var experienceBonus: Float by map
    var coinBonus: Float by map
    var itemDropBonus: Float by map
}

data class DbPetEquipment(val map: MutableMap<String, Any?> = mutableMapOf()) {
    var hat: String? by map
    var mask: String? by map
    var bodyArmor: String? by map
}

data class DbInventory(val map: MutableMap<String, Any?> = mutableMapOf()) {
    var food: MutableMap<String, Long> by map
    var avatars: List<String> by map
    var pets: List<MutableMap<String, Any?>> by map
    var themes: List<String> by map
    var colorPacks: List<String> by map
    var iconPacks: List<String> by map
    var presetChallengeIds: List<String> by map
    var powerUps: MutableMap<String, Long> by map
}

data class DbInventoryPet(val map: MutableMap<String, Any?> = mutableMapOf()) {
    var name: String by map
    var avatar: String by map
    var items: List<String> by map
}

data class DbPreferences(val map: MutableMap<String, Any?> = mutableMapOf()) {
    var theme: String by map
    var syncCalendars: List<MutableMap<String, Any?>> by map
    var productiveTimesOfDay: List<String> by map
    var workDays: List<String> by map
    var workStartMinute: Long by map
    var workEndMinute: Long by map
    var sleepStartMinute: Long by map
    var sleepEndMinute: Long by map
    var timeFormat: String by map
    var temperatureUnit: String by map
    var planDays: List<String> by map
    var planDayStartMinute: Long by map
    var resetDayStartMinute: Long by map
    var isQuickDoNotificationEnabled: Boolean by map
    var agendaStartScreen: String by map
    var reminderNotificationStyle: String by map
    var planDayNotificationStyle: String by map
    var isAutoPostingEnabled: Boolean by map
}

data class DbSyncCalendar(val map: MutableMap<String, Any?> = mutableMapOf()) {
    var id: String by map
    var name: String by map
}

data class DbUnlockedAchievement(val map: MutableMap<String, Any?> = mutableMapOf()) {
    var achievement: String by map
    var unlockMinute: Long by map
    var unlockDate: Long by map
}