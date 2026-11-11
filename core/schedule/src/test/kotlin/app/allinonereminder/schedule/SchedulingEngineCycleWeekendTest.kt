package app.allinonereminder.schedule

import app.allinonereminder.model.Criticality
import app.allinonereminder.model.DayType
import app.allinonereminder.model.Item
import app.allinonereminder.model.ItemType
import app.allinonereminder.model.MealAnchor
import app.allinonereminder.model.MealRelation
import app.allinonereminder.model.MealType
import app.allinonereminder.model.RecurrenceType
import app.allinonereminder.model.Schedule
import app.allinonereminder.model.ScheduleAnchorType
import app.allinonereminder.model.TimePolicy
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** On/off cycles, weekend time shift and configurable weekend days (issue #86). */
class SchedulingEngineCycleWeekendTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private val item = Item(
        id = "item-1",
        profileId = "p1",
        type = ItemType.MEDICINE,
        name = "Pill",
        dosageAmount = "1",
        dosageUnit = "tablet",
        criticality = Criticality.NORMAL,
        graceMinutes = 60,
        reRingMinutes = 5,
        timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
        homeZoneId = "Asia/Kolkata",
        startDate = "2026-01-01",
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun fixed(
        minute: Int = 9 * 60,
        anchorDate: String = "2026-01-01",
        on: Int? = null,
        off: Int? = null,
        weekendShift: Int? = null
    ) = Schedule(
        id = "s1",
        itemId = "item-1",
        anchorType = ScheduleAnchorType.FIXED,
        minuteOfDay = minute,
        recurrence = RecurrenceType.DAILY,
        anchorDate = anchorDate,
        cycleOnDays = on,
        cycleOffDays = off,
        weekendShiftMinutes = weekendShift,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun planDays(schedule: Schedule, start: LocalDate, days: Long, weekend: Set<DayOfWeek> = SchedulingEngine.DEFAULT_WEEKEND, anchors: List<MealAnchor> = emptyList()) =
        SchedulingEngine.plan(
            items = listOf(item),
            schedules = listOf(schedule),
            anchors = anchors,
            from = start.atStartOfDay(zone).toInstant(),
            to = start.plusDays(days).atStartOfDay(zone).toInstant(),
            deviceZone = zone,
            weekendDays = weekend
        )

    private fun localDates(list: List<PlannedOccurrence>) = list.map { it.dueAt.atZone(zone).toLocalDate() }

    @Test
    fun cycle_threeOnTwoOff_ringsOnlyOnOnDays() {
        val result = planDays(fixed(on = 3, off = 2), LocalDate.of(2026, 1, 1), 10)
        val days = localDates(result).map { it.dayOfMonth }
        assertThat(days).containsExactly(1, 2, 3, 6, 7, 8).inOrder()
    }

    @Test
    fun cycle_daysBeforeAnchor_areOff() {
        val s = fixed(on = 2, off = 1, anchorDate = "2026-01-05")
        assertThat(SchedulingEngine.isOnCycleDay(s, LocalDate.of(2026, 1, 4))).isFalse()
        assertThat(SchedulingEngine.isOnCycleDay(s, LocalDate.of(2026, 1, 5))).isTrue()
        assertThat(SchedulingEngine.isOnCycleDay(s, LocalDate.of(2026, 1, 7))).isFalse()
    }

    @Test
    fun cycle_missingOrZeroNumbers_meansNoCycle() {
        assertThat(SchedulingEngine.isOnCycleDay(fixed(on = 21, off = null), LocalDate.of(2026, 3, 1))).isTrue()
        assertThat(SchedulingEngine.isOnCycleDay(fixed(on = 0, off = 7), LocalDate.of(2026, 3, 1))).isTrue()
        assertThat(SchedulingEngine.isOnCycleDay(fixed(on = 5, off = 5).copy(anchorDate = "bad"), LocalDate.of(2026, 3, 1))).isTrue()
    }

    @Test
    fun weekendShift_movesFixedTimeOnWeekendOnly() {
        // 2026-01-02 is a Friday, 2026-01-03 a Saturday.
        val result = planDays(fixed(minute = 8 * 60, weekendShift = 90), LocalDate.of(2026, 1, 2), 2)
        val times = result.map { it.dueAt.atZone(zone).toLocalTime().toString() }
        assertThat(times).containsExactly("08:00", "09:30").inOrder()
    }

    @Test
    fun weekendShift_isClampedToTheSameDay() {
        val result = planDays(fixed(minute = 23 * 60, weekendShift = 120), LocalDate.of(2026, 1, 3), 1)
        assertThat(result.single().dueAt.atZone(zone).toLocalTime().toString()).isEqualTo("23:59")
    }

    @Test
    fun customWeekendDays_controlShiftAndMealRoutine() {
        val fridaySaturday = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
        // Friday 2026-01-02 counts as weekend now; Sunday 2026-01-04 does not.
        val shifted = planDays(fixed(minute = 8 * 60, weekendShift = 60), LocalDate.of(2026, 1, 2), 3, fridaySaturday)
        val times = shifted.map { it.dueAt.atZone(zone).toLocalTime().toString() }
        assertThat(times).containsExactly("09:00", "09:00", "08:00").inOrder()

        val anchors = listOf(
            MealAnchor("w", "p1", DayType.WEEKDAY, MealType.BREAKFAST, 8 * 60, 0L),
            MealAnchor("e", "p1", DayType.WEEKEND, MealType.BREAKFAST, 10 * 60, 0L)
        )
        val meal = Schedule(
            id = "s2", itemId = "item-1", anchorType = ScheduleAnchorType.MEAL,
            meal = MealType.BREAKFAST, relation = MealRelation.WITH,
            recurrence = RecurrenceType.DAILY, anchorDate = "2026-01-01", createdAt = 0L, updatedAt = 0L
        )
        val mealTimes = planDays(meal, LocalDate.of(2026, 1, 2), 3, fridaySaturday, anchors)
            .map { it.dueAt.atZone(zone).toLocalTime().toString() }
        assertThat(mealTimes).containsExactly("10:00", "10:00", "08:00").inOrder()
    }
}
