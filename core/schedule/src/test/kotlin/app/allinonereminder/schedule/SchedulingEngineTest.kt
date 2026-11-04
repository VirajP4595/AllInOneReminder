package app.allinonereminder.schedule

import app.allinonereminder.model.Criticality
import app.allinonereminder.model.DayType
import app.allinonereminder.model.Item
import app.allinonereminder.model.ItemType
import app.allinonereminder.model.MealAnchor
import app.allinonereminder.model.MealRelation
import app.allinonereminder.model.MealSession
import app.allinonereminder.model.MealType
import app.allinonereminder.model.OccurrenceStatus
import app.allinonereminder.model.RecurrenceType
import app.allinonereminder.model.Schedule
import app.allinonereminder.model.ScheduleAnchorType
import app.allinonereminder.model.TimePolicy
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class SchedulingEngineTest {

    private val kolkataZone = ZoneId.of("Asia/Kolkata")
    private val londonZone = ZoneId.of("Europe/London")
    private val nyZone = ZoneId.of("America/New_York")

    private fun createItem(
        id: String = "item-1",
        timePolicy: TimePolicy = TimePolicy.LOCAL_WALL_CLOCK,
        homeZoneId: String = "Asia/Kolkata",
        startDate: String = "2026-01-01",
        endDate: String? = null,
        minGapMinutes: Int? = 240,
        active: Boolean = true,
        deletedAt: Long? = null
    ) = Item(
        id = id,
        profileId = "profile-1",
        type = ItemType.MEDICINE,
        name = "Aspirin",
        dosageAmount = "100",
        dosageUnit = "mg",
        criticality = Criticality.NORMAL,
        graceMinutes = 60,
        reRingMinutes = 5,
        minGapMinutes = minGapMinutes,
        timePolicy = timePolicy,
        homeZoneId = homeZoneId,
        startDate = startDate,
        endDate = endDate,
        active = active,
        createdAt = 1000L,
        updatedAt = 1000L,
        deletedAt = deletedAt
    )

    private fun createFixedSchedule(
        id: String = "sched-1",
        itemId: String = "item-1",
        minuteOfDay: Int = 9 * 60, // 09:00
        recurrence: RecurrenceType = RecurrenceType.DAILY,
        weekdayMask: Int? = null,
        intervalN: Int? = null,
        anchorDate: String = "2026-01-01",
        onceDate: String? = null,
        deletedAt: Long? = null
    ) = Schedule(
        id = id,
        itemId = itemId,
        anchorType = ScheduleAnchorType.FIXED,
        minuteOfDay = minuteOfDay,
        recurrence = recurrence,
        weekdayMask = weekdayMask,
        intervalN = intervalN,
        anchorDate = anchorDate,
        onceDate = onceDate,
        createdAt = 1000L,
        updatedAt = 1000L,
        deletedAt = deletedAt
    )

    private fun createMealSchedule(
        id: String = "sched-meal-1",
        itemId: String = "item-1",
        meal: MealType = MealType.BREAKFAST,
        relation: MealRelation = MealRelation.WITH,
        offsetMinutes: Int = 0,
        recurrence: RecurrenceType = RecurrenceType.DAILY,
        anchorDate: String = "2026-01-01"
    ) = Schedule(
        id = id,
        itemId = itemId,
        anchorType = ScheduleAnchorType.MEAL,
        meal = meal,
        relation = relation,
        offsetMinutes = offsetMinutes,
        recurrence = recurrence,
        anchorDate = anchorDate,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private fun createDefaultAnchors(profileId: String = "profile-1"): List<MealAnchor> {
        val list = mutableListOf<MealAnchor>()
        for (dayType in listOf(DayType.WEEKDAY, DayType.WEEKEND)) {
            list.add(MealAnchor("a1", profileId, dayType, MealType.WAKE, 420, 1000L)) // 07:00
            list.add(MealAnchor("a2", profileId, dayType, MealType.BREAKFAST, 510, 1000L)) // 08:30
            list.add(MealAnchor("a3", profileId, dayType, MealType.LUNCH, 810, 1000L)) // 13:30
            list.add(MealAnchor("a4", profileId, dayType, MealType.SNACK, 1020, 1000L)) // 17:00
            list.add(MealAnchor("a5", profileId, dayType, MealType.DINNER, 1230, 1000L)) // 20:30
            list.add(MealAnchor("a6", profileId, dayType, MealType.BED, 1350, 1000L)) // 22:30
        }
        return list
    }

    // 1. Recurrence Types: DAILY, WEEKDAYS, EVERY_N_DAYS, EVERY_N_WEEKS, ONCE
    @Test
    fun plan_dailyRecurrence_generatesOccurrenceEveryDay() {
        val item = createItem(startDate = "2026-01-01")
        val sched = createFixedSchedule(minuteOfDay = 540) // 09:00
        val from = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, kolkataZone).toInstant()
        val to = ZonedDateTime.of(2026, 1, 6, 0, 0, 0, 0, kolkataZone).toInstant()

        val occurrences = SchedulingEngine.plan(listOf(item), listOf(sched), emptyList(), emptyList(), from, to, kolkataZone)
        assertThat(occurrences).hasSize(5) // Jan 1, 2, 3, 4, 5
    }

    @Test
    fun plan_weekdaysRecurrence_onlyGeneratesMondayThroughFriday() {
        // 2026-01-02 is Friday, 2026-01-03 is Saturday, 2026-01-04 is Sunday, 2026-01-05 is Monday
        val item = createItem(startDate = "2026-01-02")
        val sched = createFixedSchedule(recurrence = RecurrenceType.WEEKDAYS)
        val from = ZonedDateTime.of(2026, 1, 2, 0, 0, 0, 0, kolkataZone).toInstant()
        val to = ZonedDateTime.of(2026, 1, 7, 0, 0, 0, 0, kolkataZone).toInstant()

        val occurrences = SchedulingEngine.plan(listOf(item), listOf(sched), emptyList(), emptyList(), from, to, kolkataZone)
        assertThat(occurrences).hasSize(3) // Jan 2 (Fri), Jan 5 (Mon), Jan 6 (Tue)
    }

    @Test
    fun plan_everyNDays_generatesEveryNthDayFromAnchorDate() {
        val item = createItem(startDate = "2026-01-01")
        val sched = createFixedSchedule(
            recurrence = RecurrenceType.EVERY_N_DAYS,
            intervalN = 3,
            anchorDate = "2026-01-01"
        )
        val from = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, kolkataZone).toInstant()
        val to = ZonedDateTime.of(2026, 1, 11, 0, 0, 0, 0, kolkataZone).toInstant()

        val occurrences = SchedulingEngine.plan(listOf(item), listOf(sched), emptyList(), emptyList(), from, to, kolkataZone)
        // Jan 1 (0 days), Jan 4 (3 days), Jan 7 (6 days), Jan 10 (9 days)
        assertThat(occurrences).hasSize(4)
    }

    // 2. Weekday mask edges
    @Test
    fun plan_weekdayMask_mondayOnlyAndSundayOnly() {
        val item = createItem(startDate = "2026-01-01")
        // Mon only = bit 0 = 1
        val schedMon = createFixedSchedule(
            id = "sched-mon",
            recurrence = RecurrenceType.EVERY_N_WEEKS,
            intervalN = 1,
            weekdayMask = 1,
            anchorDate = "2026-01-01"
        )
        // Sun only = bit 6 = 64
        val schedSun = createFixedSchedule(
            id = "sched-sun",
            recurrence = RecurrenceType.EVERY_N_WEEKS,
            intervalN = 1,
            weekdayMask = 64,
            anchorDate = "2026-01-01"
        )
        val from = ZonedDateTime.of(2026, 1, 5, 0, 0, 0, 0, kolkataZone).toInstant() // Mon Jan 5
        val to = ZonedDateTime.of(2026, 1, 12, 0, 0, 0, 0, kolkataZone).toInstant() // Mon Jan 12

        val occMon = SchedulingEngine.plan(listOf(item), listOf(schedMon), emptyList(), emptyList(), from, to, kolkataZone)
        assertThat(occMon).hasSize(1) // Jan 5 only

        val occSun = SchedulingEngine.plan(listOf(item), listOf(schedSun), emptyList(), emptyList(), from, to, kolkataZone)
        assertThat(occSun).hasSize(1) // Jan 11 only
    }

    // 3. Every 2 weeks across month and year boundaries
    @Test
    fun plan_every2WeeksAcrossMonthAndYearBoundaries() {
        // Anchor: 2025-12-15 (Monday, week 0).
        // 2025-12-29 is week 2 (Monday)
        // 2026-01-12 is week 4 (Monday)
        val item = createItem(startDate = "2025-12-01")
        val sched = createFixedSchedule(
            recurrence = RecurrenceType.EVERY_N_WEEKS,
            intervalN = 2,
            weekdayMask = 1, // Mon
            anchorDate = "2025-12-15"
        )
        val from = ZonedDateTime.of(2025, 12, 15, 0, 0, 0, 0, kolkataZone).toInstant()
        val to = ZonedDateTime.of(2026, 1, 15, 0, 0, 0, 0, kolkataZone).toInstant()

        val occurrences = SchedulingEngine.plan(listOf(item), listOf(sched), emptyList(), emptyList(), from, to, kolkataZone)
        // Dec 15, Dec 29, Jan 12
        assertThat(occurrences).hasSize(3)
    }

    // 4. Start / End date inclusive
    @Test
    fun plan_respectsStartAndEndDateInclusively() {
        val item = createItem(startDate = "2026-01-03", endDate = "2026-01-05")
        val sched = createFixedSchedule()
        val from = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, kolkataZone).toInstant()
        val to = ZonedDateTime.of(2026, 1, 8, 0, 0, 0, 0, kolkataZone).toInstant()

        val occurrences = SchedulingEngine.plan(listOf(item), listOf(sched), emptyList(), emptyList(), from, to, kolkataZone)
        // Jan 3, 4, 5
        assertThat(occurrences).hasSize(3)
    }

    // 5. ONCE recurrence and past ONCE ignored
    @Test
    fun plan_onceRecurrence_ignoredIfBeforeWindow() {
        val item = createItem(startDate = "2026-01-01")
        val schedPast = createFixedSchedule(
            id = "sched-past",
            recurrence = RecurrenceType.ONCE,
            onceDate = "2025-12-31"
        )
        val schedFuture = createFixedSchedule(
            id = "sched-future",
            recurrence = RecurrenceType.ONCE,
            onceDate = "2026-01-03"
        )
        val from = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, kolkataZone).toInstant()
        val to = ZonedDateTime.of(2026, 1, 5, 0, 0, 0, 0, kolkataZone).toInstant()

        val occPast = SchedulingEngine.plan(listOf(item), listOf(schedPast), emptyList(), emptyList(), from, to, kolkataZone)
        assertThat(occPast).isEmpty()

        val occFuture = SchedulingEngine.plan(listOf(item), listOf(schedFuture), emptyList(), emptyList(), from, to, kolkataZone)
        assertThat(occFuture).hasSize(1)
    }

    // 6 & 7. Meal Anchors (BEFORE, WITH, AFTER) and Weekday vs Weekend
    @Test
    fun plan_mealRelativeAnchors_weekdayVsWeekend() {
        val item = createItem(startDate = "2026-01-01")
        // Weekday breakfast = 08:30 (510 min), Weekend breakfast = 09:30 (570 min)
        val anchors = listOf(
            MealAnchor("a1", "profile-1", DayType.WEEKDAY, MealType.BREAKFAST, 510, 1000L),
            MealAnchor("a2", "profile-1", DayType.WEEKEND, MealType.BREAKFAST, 570, 1000L)
        )
        // Before breakfast 30 min (offset 30) -> Weekday: 08:00 (480), Weekend: 09:00 (540)
        val sched = createMealSchedule(
            relation = MealRelation.BEFORE,
            offsetMinutes = 30
        )
        // 2026-01-02 (Friday = Weekday), 2026-01-03 (Saturday = Weekend)
        val from = ZonedDateTime.of(2026, 1, 2, 0, 0, 0, 0, kolkataZone).toInstant()
        val to = ZonedDateTime.of(2026, 1, 4, 0, 0, 0, 0, kolkataZone).toInstant()

        val occurrences = SchedulingEngine.plan(listOf(item), listOf(sched), anchors, emptyList(), from, to, kolkataZone)
        assertThat(occurrences).hasSize(2)

        val friOcc = occurrences[0]
        val friLocal = ZonedDateTime.ofInstant(friOcc.plannedAt, kolkataZone).toLocalTime()
        assertThat(friLocal.hour).isEqualTo(8)
        assertThat(friLocal.minute).isEqualTo(0)

        val satOcc = occurrences[1]
        val satLocal = ZonedDateTime.ofInstant(satOcc.plannedAt, kolkataZone).toLocalTime()
        assertThat(satLocal.hour).isEqualTo(9)
        assertThat(satLocal.minute).isEqualTo(0)
    }

    // 8. Meal sessions: before/with (due now), open session (DEFERRED_MEAL + fallback), ended session
    @Test
    fun plan_mealSession_beforeWithOverriddenIfDueLaterThanStart() {
        val item = createItem(startDate = "2026-01-01")
        // Anchor breakfast = 09:00 (540 min). Scheduled WITH breakfast (09:00).
        val anchors = listOf(MealAnchor("a1", "profile-1", DayType.WEEKDAY, MealType.BREAKFAST, 540, 1000L))
        val sched = createMealSchedule(meal = MealType.BREAKFAST, relation = MealRelation.WITH)

        // Session started early at 08:15
        val sessionStart = ZonedDateTime.of(2026, 1, 2, 8, 15, 0, 0, kolkataZone).toInstant()
        val session = MealSession("s1", "profile-1", MealType.BREAKFAST, "2026-01-02", sessionStart.toEpochMilli(), null)

        val from = ZonedDateTime.of(2026, 1, 2, 0, 0, 0, 0, kolkataZone).toInstant()
        val to = ZonedDateTime.of(2026, 1, 3, 0, 0, 0, 0, kolkataZone).toInstant()

        val occurrences = SchedulingEngine.plan(listOf(item), listOf(sched), anchors, listOf(session), from, to, kolkataZone)
        assertThat(occurrences).hasSize(1)
        assertThat(occurrences[0].dueAt).isEqualTo(sessionStart)
    }

    @Test
    fun plan_mealSession_openSessionHoldsAfterItemAsDeferredWithFallback() {
        val item = createItem(startDate = "2026-01-01")
        val anchors = listOf(MealAnchor("a1", "profile-1", DayType.WEEKDAY, MealType.LUNCH, 810, 1000L)) // 13:30
        // AFTER lunch 15 min
        val sched = createMealSchedule(meal = MealType.LUNCH, relation = MealRelation.AFTER, offsetMinutes = 15)

        // Session started at 13:00, still open
        val sessionStart = ZonedDateTime.of(2026, 1, 2, 13, 0, 0, 0, kolkataZone).toInstant()
        val session = MealSession("s1", "profile-1", MealType.LUNCH, "2026-01-02", sessionStart.toEpochMilli(), null)

        val from = ZonedDateTime.of(2026, 1, 2, 0, 0, 0, 0, kolkataZone).toInstant()
        val to = ZonedDateTime.of(2026, 1, 3, 0, 0, 0, 0, kolkataZone).toInstant()

        // Evaluated while session is open and before 90 min fallback
        val now = sessionStart.plus(30, ChronoUnit.MINUTES)
        val occurrences = SchedulingEngine.plan(listOf(item), listOf(sched), anchors, listOf(session), from, to, kolkataZone, now)
        assertThat(occurrences).hasSize(1)
        assertThat(occurrences[0].status).isEqualTo(OccurrenceStatus.DEFERRED_MEAL)
        // Fallback: startedAt + 90 min + 15 min offset
        val expectedFallback = sessionStart.plus(105, ChronoUnit.MINUTES)
        assertThat(occurrences[0].dueAt).isEqualTo(expectedFallback)
    }

    @Test
    fun plan_mealSession_endedSessionMovesAfterItemToEndedAtPlusOffset() {
        val item = createItem(startDate = "2026-01-01")
        val anchors = listOf(MealAnchor("a1", "profile-1", DayType.WEEKDAY, MealType.DINNER, 1230, 1000L)) // 20:30
        val sched = createMealSchedule(meal = MealType.DINNER, relation = MealRelation.AFTER, offsetMinutes = 20)

        val sessionStart = ZonedDateTime.of(2026, 1, 2, 20, 0, 0, 0, kolkataZone).toInstant()
        val sessionEnd = ZonedDateTime.of(2026, 1, 2, 20, 45, 0, 0, kolkataZone).toInstant()
        val session = MealSession("s1", "profile-1", MealType.DINNER, "2026-01-02", sessionStart.toEpochMilli(), sessionEnd.toEpochMilli())

        val from = ZonedDateTime.of(2026, 1, 2, 0, 0, 0, 0, kolkataZone).toInstant()
        val to = ZonedDateTime.of(2026, 1, 3, 0, 0, 0, 0, kolkataZone).toInstant()

        val occurrences = SchedulingEngine.plan(listOf(item), listOf(sched), anchors, listOf(session), from, to, kolkataZone)
        assertThat(occurrences).hasSize(1)
        assertThat(occurrences[0].dueAt).isEqualTo(sessionEnd.plus(20, ChronoUnit.MINUTES))
    }

    // 9. DST spring-forward and fall-back in Europe/London and America/New_York
    @Test
    fun plan_dst_springForwardInLondon_shiftsGap() {
        // Europe/London spring forward in 2026: March 29, 01:00 -> 02:00. Time 01:30 doesn't exist.
        val item = createItem(startDate = "2026-03-28")
        val sched = createFixedSchedule(minuteOfDay = 1 * 60 + 30) // 01:30 local

        val from = ZonedDateTime.of(2026, 3, 28, 0, 0, 0, 0, londonZone).toInstant()
        val to = ZonedDateTime.of(2026, 3, 31, 0, 0, 0, 0, londonZone).toInstant()

        val occurrences = SchedulingEngine.plan(listOf(item), listOf(sched), emptyList(), emptyList(), from, to, londonZone)
        assertThat(occurrences).hasSize(3) // Mar 28, 29, 30

        // On March 29, 01:30 is adjusted past the gap to 02:30
        val mar29Occ = occurrences[1]
        val localTime = ZonedDateTime.ofInstant(mar29Occ.plannedAt, londonZone).toLocalTime()
        assertThat(localTime.hour).isEqualTo(2)
        assertThat(localTime.minute).isEqualTo(30)
    }

    @Test
    fun plan_dst_fallBackInNewYork_usesEarlierOffset() {
        // America/New_York fall back in 2026: November 1, 02:00 -> 01:00. Time 01:30 is ambiguous.
        val item = createItem(startDate = "2026-10-31")
        val sched = createFixedSchedule(minuteOfDay = 1 * 60 + 30) // 01:30 local

        val from = ZonedDateTime.of(2026, 10, 31, 0, 0, 0, 0, nyZone).toInstant()
        val to = ZonedDateTime.of(2026, 11, 3, 0, 0, 0, 0, nyZone).toInstant()

        val occurrences = SchedulingEngine.plan(listOf(item), listOf(sched), emptyList(), emptyList(), from, to, nyZone)
        assertThat(occurrences).hasSize(3) // Oct 31, Nov 1, Nov 2

        // Nov 1 uses earlier offset (EDT UTC-4 instead of EST UTC-5)
        val nov1Occ = occurrences[1]
        val zdt = ZonedDateTime.ofInstant(nov1Occ.plannedAt, nyZone)
        assertThat(zdt.offset.totalSeconds).isEqualTo(-4 * 3600)
    }

    // 10. HOME_ZONE vs LOCAL_WALL_CLOCK after a zone change (Asia/Kolkata -> Europe/London)
    @Test
    fun plan_timePolicy_homeZoneKeepsHomeInstant() {
        // Home zone Asia/Kolkata 09:00 IST (UTC 03:30)
        val itemHome = createItem(
            id = "item-home",
            timePolicy = TimePolicy.HOME_ZONE,
            homeZoneId = "Asia/Kolkata",
            startDate = "2026-06-01"
        )
        // Local wall clock 09:00 in London (BST, UTC 08:00)
        val itemLocal = createItem(
            id = "item-local",
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = "Asia/Kolkata",
            startDate = "2026-06-01"
        )

        val schedHome = createFixedSchedule(id = "s-home", itemId = "item-home", minuteOfDay = 9 * 60)
        val schedLocal = createFixedSchedule(id = "s-local", itemId = "item-local", minuteOfDay = 9 * 60)

        // Device is now in London
        val from = ZonedDateTime.of(2026, 6, 1, 0, 0, 0, 0, londonZone).toInstant()
        val to = ZonedDateTime.of(2026, 6, 2, 0, 0, 0, 0, londonZone).toInstant()

        val occurrences = SchedulingEngine.plan(listOf(itemHome, itemLocal), listOf(schedHome, schedLocal), emptyList(), emptyList(), from, to, londonZone)
        val occHome = occurrences.first { it.itemId == "item-home" }
        val occLocal = occurrences.first { it.itemId == "item-local" }

        // Home item fires at 09:00 IST = 04:30 BST (03:30 UTC)
        val homeInLondon = ZonedDateTime.ofInstant(occHome.plannedAt, londonZone)
        assertThat(homeInLondon.hour).isEqualTo(4)
        assertThat(homeInLondon.minute).isEqualTo(30)

        // Local item fires at 09:00 BST (08:00 UTC)
        val localInLondon = ZonedDateTime.ofInstant(occLocal.plannedAt, londonZone)
        assertThat(localInLondon.hour).isEqualTo(9)
        assertThat(localInLondon.minute).isEqualTo(0)
    }

    // 11. Grouping
    @Test
    fun group_groupsOccurrencesByIdenticalDueMinute() {
        val now = Instant.parse("2026-11-04T08:30:15Z")
        val occ1 = PlannedOccurrence("1", "i1", "s1", "p1", now, now)
        val occ2 = PlannedOccurrence("2", "i2", "s2", "p1", now.plusSeconds(20), now.plusSeconds(20))
        val occ3 = PlannedOccurrence("3", "i3", "s3", "p1", now.plusSeconds(90), now.plusSeconds(90))

        val groups = SchedulingEngine.group(listOf(occ1, occ2, occ3))
        assertThat(groups).hasSize(2)
        // Group 1: 08:30 has occ1 and occ2
        assertThat(groups[0].occurrences).containsExactly(occ1, occ2)
        // Group 2: 08:31 has occ3
        assertThat(groups[1].occurrences).containsExactly(occ3)
    }

    // 12. Double-dose warning
    @Test
    fun doseWarning_warnsIfWithinMinGap() {
        val item = createItem(minGapMinutes = 240) // 4 hours
        val lastTaken = Instant.parse("2026-11-04T08:00:00Z")

        // 1 hour later: inside window
        val nowInside = lastTaken.plus(1, ChronoUnit.HOURS)
        val warning = SchedulingEngine.doseWarning(item, lastTaken, nowInside)
        assertThat(warning).isNotNull()
        assertThat(warning?.toMinutes()).isEqualTo(180) // 3 hours remaining

        // 5 hours later: outside window
        val nowOutside = lastTaken.plus(5, ChronoUnit.HOURS)
        val noWarning = SchedulingEngine.doseWarning(item, lastTaken, nowOutside)
        assertThat(noWarning).isNull()
    }

    // 13. Meal anchor change moves linked items
    @Test
    fun plan_mealAnchorChange_movesLinkedItemOccurrences() {
        val item = createItem(id = "item-meal", timePolicy = TimePolicy.LOCAL_WALL_CLOCK)
        // 30 min before breakfast
        val schedule = createMealSchedule(
            id = "sched-meal",
            itemId = item.id,
            meal = MealType.BREAKFAST,
            relation = MealRelation.BEFORE,
            offsetMinutes = 30,
            recurrence = RecurrenceType.DAILY,
            anchorDate = "2026-06-01"
        )

        // Initial Breakfast anchor at 08:00 (480 min)
        val anchorInitial = MealAnchor(
            id = "a1",
            profileId = item.profileId,
            dayType = DayType.WEEKDAY,
            meal = MealType.BREAKFAST,
            minuteOfDay = 8 * 60,
            updatedAt = 1000L
        )

        val from = ZonedDateTime.of(2026, 6, 1, 0, 0, 0, 0, londonZone).toInstant()
        val to = ZonedDateTime.of(2026, 6, 2, 0, 0, 0, 0, londonZone).toInstant()

        val planBefore = SchedulingEngine.plan(listOf(item), listOf(schedule), listOf(anchorInitial), emptyList(), from, to, londonZone)
        assertThat(planBefore).hasSize(1)
        val timeBefore = ZonedDateTime.ofInstant(planBefore[0].plannedAt, londonZone)
        // 08:00 - 30 min = 07:30
        assertThat(timeBefore.hour).isEqualTo(7)
        assertThat(timeBefore.minute).isEqualTo(30)

        // Updated Breakfast anchor at 09:15 (555 min)
        val anchorUpdated = MealAnchor(
            id = "a1",
            profileId = item.profileId,
            dayType = DayType.WEEKDAY,
            meal = MealType.BREAKFAST,
            minuteOfDay = 9 * 60 + 15,
            updatedAt = 2000L
        )

        val planAfter = SchedulingEngine.plan(listOf(item), listOf(schedule), listOf(anchorUpdated), emptyList(), from, to, londonZone)
        assertThat(planAfter).hasSize(1)
        val timeAfter = ZonedDateTime.ofInstant(planAfter[0].plannedAt, londonZone)
        // 09:15 - 30 min = 08:45
        assertThat(timeAfter.hour).isEqualTo(8)
        assertThat(timeAfter.minute).isEqualTo(45)

        // Verify difference is exactly 75 minutes shift
        assertThat(Duration.between(planBefore[0].plannedAt, planAfter[0].plannedAt)).isEqualTo(Duration.ofMinutes(75))
    }

    // 14. LOCAL_WALL_CLOCK after a zone change (Asia/Kolkata -> Europe/London)
    @Test
    fun plan_localWallClock_afterZoneChange_preservesLocalWallTimeAcrossZones() {
        val item = createItem(
            id = "item-local",
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = "Asia/Kolkata",
            startDate = "2026-06-01"
        )
        val schedule = createFixedSchedule(id = "s-local", itemId = item.id, minuteOfDay = 9 * 60) // 09:00

        // In Asia/Kolkata
        val fromKolkata = ZonedDateTime.of(2026, 6, 1, 0, 0, 0, 0, kolkataZone).toInstant()
        val toKolkata = ZonedDateTime.of(2026, 6, 2, 0, 0, 0, 0, kolkataZone).toInstant()
        val planKolkata = SchedulingEngine.plan(listOf(item), listOf(schedule), emptyList(), emptyList(), fromKolkata, toKolkata, kolkataZone)
        val occKolkata = planKolkata.first()
        val zdtKolkata = ZonedDateTime.ofInstant(occKolkata.plannedAt, kolkataZone)
        assertThat(zdtKolkata.hour).isEqualTo(9)
        assertThat(zdtKolkata.minute).isEqualTo(0)
        // 09:00 IST is 03:30 UTC
        assertThat(occKolkata.plannedAt).isEqualTo(Instant.parse("2026-06-01T03:30:00Z"))

        // After user travels to Europe/London (BST = UTC+1 in June)
        val fromLondon = ZonedDateTime.of(2026, 6, 1, 0, 0, 0, 0, londonZone).toInstant()
        val toLondon = ZonedDateTime.of(2026, 6, 2, 0, 0, 0, 0, londonZone).toInstant()
        val planLondon = SchedulingEngine.plan(listOf(item), listOf(schedule), emptyList(), emptyList(), fromLondon, toLondon, londonZone)
        val occLondon = planLondon.first()
        val zdtLondon = ZonedDateTime.ofInstant(occLondon.plannedAt, londonZone)
        // Stays 09:00 local wall clock in London!
        assertThat(zdtLondon.hour).isEqualTo(9)
        assertThat(zdtLondon.minute).isEqualTo(0)
        // 09:00 BST is 08:00 UTC (not 03:30 UTC)
        assertThat(occLondon.plannedAt).isEqualTo(Instant.parse("2026-06-01T08:00:00Z"))
    }

    // 15. DST spring-forward and fall-back in both Europe/London and America/New_York
    @Test
    fun plan_dstTransitions_londonAndNewYork_handlesSpringForwardAndFallBack() {
        val itemLondon = createItem(id = "item-lon", timePolicy = TimePolicy.LOCAL_WALL_CLOCK)
        val schedLondon = createFixedSchedule(id = "sched-lon", itemId = "item-lon", minuteOfDay = 9 * 60)

        val itemNY = createItem(id = "item-ny", timePolicy = TimePolicy.LOCAL_WALL_CLOCK)
        val schedNY = createFixedSchedule(id = "sched-ny", itemId = "item-ny", minuteOfDay = 9 * 60)

        // 1. Europe/London Spring Forward: 2026-03-29 (23h day, jumps 01:00 -> 02:00)
        val lonSpringFrom = ZonedDateTime.of(2026, 3, 28, 0, 0, 0, 0, londonZone).toInstant()
        val lonSpringTo = ZonedDateTime.of(2026, 3, 31, 0, 0, 0, 0, londonZone).toInstant()
        val planLonSpring = SchedulingEngine.plan(listOf(itemLondon), listOf(schedLondon), emptyList(), emptyList(), lonSpringFrom, lonSpringTo, londonZone)
        assertThat(planLonSpring).hasSize(3) // 28th, 29th (DST day), 30th
        for (occ in planLonSpring) {
            val zdt = ZonedDateTime.ofInstant(occ.plannedAt, londonZone)
            assertThat(zdt.hour).isEqualTo(9)
            assertThat(zdt.minute).isEqualTo(0)
        }

        // 2. Europe/London Fall Back: 2026-10-25 (25h day, jumps 02:00 -> 01:00)
        val lonFallFrom = ZonedDateTime.of(2026, 10, 24, 0, 0, 0, 0, londonZone).toInstant()
        val lonFallTo = ZonedDateTime.of(2026, 10, 27, 0, 0, 0, 0, londonZone).toInstant()
        val planLonFall = SchedulingEngine.plan(listOf(itemLondon), listOf(schedLondon), emptyList(), emptyList(), lonFallFrom, lonFallTo, londonZone)
        assertThat(planLonFall).hasSize(3) // 24th, 25th (DST day), 26th
        for (occ in planLonFall) {
            val zdt = ZonedDateTime.ofInstant(occ.plannedAt, londonZone)
            assertThat(zdt.hour).isEqualTo(9)
            assertThat(zdt.minute).isEqualTo(0)
        }

        // 3. America/New_York Spring Forward: 2026-03-08 (23h day, jumps 02:00 -> 03:00)
        val nySpringFrom = ZonedDateTime.of(2026, 3, 7, 0, 0, 0, 0, nyZone).toInstant()
        val nySpringTo = ZonedDateTime.of(2026, 3, 10, 0, 0, 0, 0, nyZone).toInstant()
        val planNySpring = SchedulingEngine.plan(listOf(itemNY), listOf(schedNY), emptyList(), emptyList(), nySpringFrom, nySpringTo, nyZone)
        assertThat(planNySpring).hasSize(3) // 7th, 8th (DST day), 9th
        for (occ in planNySpring) {
            val zdt = ZonedDateTime.ofInstant(occ.plannedAt, nyZone)
            assertThat(zdt.hour).isEqualTo(9)
            assertThat(zdt.minute).isEqualTo(0)
        }

        // 4. America/New_York Fall Back: 2026-11-01 (25h day, jumps 02:00 -> 01:00)
        val nyFallFrom = ZonedDateTime.of(2026, 10, 31, 0, 0, 0, 0, nyZone).toInstant()
        val nyFallTo = ZonedDateTime.of(2026, 11, 3, 0, 0, 0, 0, nyZone).toInstant()
        val planNyFall = SchedulingEngine.plan(listOf(itemNY), listOf(schedNY), emptyList(), emptyList(), nyFallFrom, nyFallTo, nyZone)
        assertThat(planNyFall).hasSize(3) // Oct 31, Nov 1 (DST day), Nov 2
        for (occ in planNyFall) {
            val zdt = ZonedDateTime.ofInstant(occ.plannedAt, nyZone)
            assertThat(zdt.hour).isEqualTo(9)
            assertThat(zdt.minute).isEqualTo(0)
        }
    }
}
