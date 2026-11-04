package app.allinonereminder.data

import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.ScheduleRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.model.Criticality
import app.allinonereminder.model.Item
import app.allinonereminder.model.ItemType
import app.allinonereminder.model.MealRelation
import app.allinonereminder.model.MealType
import app.allinonereminder.model.RecurrenceType
import app.allinonereminder.model.Schedule
import app.allinonereminder.model.ScheduleAnchorType
import app.allinonereminder.model.TimePolicy
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SampleDataSeeder @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val itemRepository: ItemRepository,
    private val scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository,
    private val alarmCoordinator: AlarmCoordinator
) {
    suspend fun seedSampleData(): String {
        val deviceId = settingsRepository.getOrCreateDeviceId()
        val zone = ZoneId.systemDefault()
        val todayStr = LocalDate.now(zone).toString()
        val nowTime = LocalTime.now(zone)

        val profiles = profileRepository.getActiveProfiles().first()
        val profile = if (profiles.isNotEmpty()) {
            profiles.first()
        } else {
            profileRepository.createProfile(
                name = "Mom",
                colorArgb = 0xFFE91E63.toInt(),
                birthYear = 1968,
                allergies = "Penicillin",
                notes = "Take medicines with water",
                deviceId = deviceId
            )
        }

        // Alarm 1: Critical Insulin due in 1 minute
        val item1Id = UUID.randomUUID().toString()
        val item1 = Item(
            id = item1Id,
            profileId = profile.id,
            type = ItemType.INJECTION,
            name = "Insulin Glargine",
            dosageAmount = "10",
            dosageUnit = "units",
            form = "pen",
            instructions = "Subcutaneous injection before meal",
            criticality = Criticality.CRITICAL,
            graceMinutes = 15,
            reRingMinutes = 5,
            minGapMinutes = 240,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = zone.id,
            startDate = todayStr,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        itemRepository.createItem(item1, deviceId)

        val sched1Minute = (nowTime.hour * 60 + nowTime.minute + 1) % 1440
        val sched1 = Schedule(
            id = UUID.randomUUID().toString(),
            itemId = item1Id,
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = sched1Minute,
            recurrence = RecurrenceType.DAILY,
            anchorDate = todayStr,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        scheduleRepository.createSchedule(sched1, deviceId)

        // Alarm 2: Metformin due after breakfast
        val item2Id = UUID.randomUUID().toString()
        val item2 = Item(
            id = item2Id,
            profileId = profile.id,
            type = ItemType.MEDICINE,
            name = "Metformin",
            dosageAmount = "500",
            dosageUnit = "mg",
            form = "tablet",
            instructions = "Take with or right after food",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            minGapMinutes = 240,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = zone.id,
            startDate = todayStr,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        itemRepository.createItem(item2, deviceId)

        val sched2 = Schedule(
            id = UUID.randomUUID().toString(),
            itemId = item2Id,
            anchorType = ScheduleAnchorType.MEAL,
            meal = MealType.BREAKFAST,
            relation = MealRelation.AFTER,
            offsetMinutes = 15,
            recurrence = RecurrenceType.DAILY,
            anchorDate = todayStr,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        scheduleRepository.createSchedule(sched2, deviceId)

        // Alarm 3: Drink Water due in 2 minutes
        val item3Id = UUID.randomUUID().toString()
        val item3 = Item(
            id = item3Id,
            profileId = profile.id,
            type = ItemType.WATER_DRINK,
            name = "Drink Water",
            dosageAmount = "250",
            dosageUnit = "ml",
            form = "glass",
            instructions = "Stay hydrated",
            criticality = Criticality.GENTLE,
            graceMinutes = 60,
            reRingMinutes = 10,
            minGapMinutes = null,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = zone.id,
            startDate = todayStr,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        itemRepository.createItem(item3, deviceId)

        val sched3Minute = (nowTime.hour * 60 + nowTime.minute + 2) % 1440
        val sched3 = Schedule(
            id = UUID.randomUUID().toString(),
            itemId = item3Id,
            anchorType = ScheduleAnchorType.FIXED,
            minuteOfDay = sched3Minute,
            recurrence = RecurrenceType.DAILY,
            anchorDate = todayStr,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        scheduleRepository.createSchedule(sched3, deviceId)

        val result = alarmCoordinator.reschedule("SampleDataSeeder")
        return "Sample data seeded: 3 items created for ${profile.name}. Next alarm registered: ${result.registered}."
    }
}

