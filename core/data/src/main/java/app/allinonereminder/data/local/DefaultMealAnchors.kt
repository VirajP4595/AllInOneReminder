package app.allinonereminder.data.local

import app.allinonereminder.data.local.entity.MealAnchorEntity
import app.allinonereminder.model.DayType
import app.allinonereminder.model.MealType
import java.util.UUID

object DefaultMealAnchors {
    val DEFAULTS = listOf(
        MealType.WAKE to 7 * 60,         // 07:00 = 420
        MealType.BREAKFAST to 8 * 60 + 30, // 08:30 = 510
        MealType.LUNCH to 13 * 60 + 30,    // 13:30 = 810
        MealType.SNACK to 17 * 60,        // 17:00 = 1020
        MealType.DINNER to 20 * 60 + 30,   // 20:30 = 1230
        MealType.BED to 22 * 60 + 30       // 22:30 = 1350
    )

    fun createDefaultAnchors(profileId: String, timestamp: Long = System.currentTimeMillis()): List<MealAnchorEntity> {
        val list = mutableListOf<MealAnchorEntity>()
        for (dayType in listOf(DayType.WEEKDAY, DayType.WEEKEND)) {
            for ((meal, minute) in DEFAULTS) {
                list.add(
                    MealAnchorEntity(
                        id = UUID.randomUUID().toString(),
                        profileId = profileId,
                        dayType = dayType,
                        meal = meal,
                        minuteOfDay = minute,
                        updatedAt = timestamp
                    )
                )
            }
        }
        return list
    }
}

