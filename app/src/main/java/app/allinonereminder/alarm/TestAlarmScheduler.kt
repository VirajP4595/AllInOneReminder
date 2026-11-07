package app.allinonereminder.alarm

import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.ScheduleRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.model.Criticality
import app.allinonereminder.model.Item
import app.allinonereminder.model.ItemType
import app.allinonereminder.model.RecurrenceType
import app.allinonereminder.model.Schedule
import app.allinonereminder.model.ScheduleAnchorType
import app.allinonereminder.model.TEST_ALARM_ID_PREFIX
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Send test alarm": creates a real one-off reminder about a minute away and lets the normal
 * [AlarmCoordinator] ring it, so the test covers the same path as a real dose (full-screen alarm,
 * sound, over other apps). The item is hidden from lists and stats (see Item.isTestAlarm).
 */
@Singleton
class TestAlarmScheduler @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val itemRepository: ItemRepository,
    private val scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository,
    private val alarmCoordinator: AlarmCoordinator
) {
    /** Returns a short message for the user. [profileId] null = the first profile. */
    suspend fun schedule(profileId: String? = null, inMinutes: Int = 1): String {
        val pid = profileId
            ?: profileRepository.getActiveProfiles().first().firstOrNull()?.id
            ?: return "Create a profile first, then send the test alarm from Settings > Permission health check."

        val deviceId = settingsRepository.getOrCreateDeviceId()
        val zone = ZoneId.systemDefault()
        val nowInstant = Instant.now()
        var planned = nowInstant.plusSeconds(inMinutes * 60L).atZone(zone).withSecond(0).withNano(0)
        if (planned.toInstant().isBefore(nowInstant.plusSeconds(20))) planned = planned.plusMinutes(1)

        val now = System.currentTimeMillis()
        val itemId = TEST_ALARM_ID_PREFIX + UUID.randomUUID()
        itemRepository.createItem(
            Item(
                id = itemId,
                profileId = pid,
                type = ItemType.CUSTOM,
                name = "Test alarm",
                dosageAmount = "",
                dosageUnit = "",
                instructions = "This is a test. Tap Taken to finish.",
                criticality = Criticality.NORMAL,
                graceMinutes = 30,
                homeZoneId = zone.id,
                startDate = planned.toLocalDate().toString(),
                createdAt = now,
                updatedAt = now
            ),
            deviceId
        )
        val date = planned.toLocalDate().toString()
        scheduleRepository.createSchedule(
            Schedule(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                anchorType = ScheduleAnchorType.FIXED,
                minuteOfDay = planned.hour * 60 + planned.minute,
                recurrence = RecurrenceType.ONCE,
                onceDate = date,
                anchorDate = date,
                createdAt = now,
                updatedAt = now
            ),
            deviceId
        )
        alarmCoordinator.reschedule("test_alarm")
        return "Test alarm set for ${planned.format(DateTimeFormatter.ofPattern("HH:mm"))}. Lock the screen or open another app and wait."
    }
}
