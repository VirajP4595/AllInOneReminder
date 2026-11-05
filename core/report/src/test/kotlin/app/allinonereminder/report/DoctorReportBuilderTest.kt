package app.allinonereminder.report

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

class DoctorReportBuilderTest {

    private val profile = ReportProfile(
        id = "profile-1",
        name = "Mom",
        birthYear = 1948,
        allergies = "Penicillin",
        notes = "Prefers morning appointments",
    )
    private val now = Instant.parse("2026-01-05T12:00:00Z")

    private fun medicine(
        id: String = "med-1",
        name: String = "Metformin",
        dosage: String? = "500 mg",
        active: Boolean = true,
        schedules: List<ReportSchedule> = emptyList(),
    ): ReportItem = ReportItem(
        id = id,
        type = ReportItemType.MEDICINE,
        name = name,
        dosage = dosage,
        active = active,
        schedules = schedules,
    )

    private fun occurrence(
        id: String,
        itemId: String = "med-1",
        dueAt: Instant,
        status: ReportOccurrenceStatus,
    ): ReportOccurrence = ReportOccurrence(
        occurrenceId = id,
        itemId = itemId,
        dueAt = dueAt,
        status = status,
    )

    private fun at(
        zone: ZoneId,
        date: LocalDate,
        hour: Int,
        minute: Int,
    ): Instant = date.atTime(hour, minute).atZone(zone).toInstant()

    private fun build(
        items: List<ReportItem> = emptyList(),
        occurrences: List<ReportOccurrence> = emptyList(),
        appointments: List<ReportAppointment> = emptyList(),
        range: ReportRange = ReportRange(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 10),
        ),
        zoneId: ZoneId = ZoneOffset.UTC,
        vitals: VitalsSummaryProvider? = null,
    ): DoctorReport = DoctorReportBuilder.build(
        DoctorReportInput(
            profile = profile,
            items = items,
            occurrences = occurrences,
            appointments = appointments,
            range = range,
            zoneId = zoneId,
            now = now,
            vitals = vitals,
        ),
    )

    @Test
    fun `empty data still produces a report with one row per day`() {
        val report = build(
            range = ReportRange(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 3),
            ),
        )

        assertTrue(report.medicines.isEmpty())
        assertEquals(3, report.daily.size)
        assertEquals(LocalDate.of(2026, 1, 1), report.daily.first().date)
        assertEquals(LocalDate.of(2026, 1, 3), report.daily.last().date)
        assertTrue(
            report.daily.all {
                it.taken == 0 &&
                    it.missed == 0 &&
                    it.skipped == 0 &&
                    it.percent == null
            },
        )
        assertTrue(report.vitals.isEmpty())
        assertTrue(report.pastAppointments.isEmpty())
        assertTrue(report.upcomingAppointments.isEmpty())
        assertEquals(now, report.generatedAt)
        assertEquals(profile, report.profile)
    }

    @Test
    fun `partial data keeps optional fields empty and includes only active medicines`() {
        val active = medicine(
            id = "active",
            name = "Aspirin",
            dosage = null,
            schedules = emptyList(),
        )
        val injection = ReportItem(
            id = "injection",
            type = ReportItemType.INJECTION,
            name = "Insulin",
            dosage = "10 units",
            schedules = listOf(
                ReportSchedule.MealRelative(Meal.DINNER, MealRelation.WITH),
            ),
        )
        val inactive = medicine(id = "inactive", name = "Old", active = false)
        val habit = ReportItem(
            id = "habit",
            type = ReportItemType.HABIT,
            name = "Walk",
        )

        val report = build(items = listOf(inactive, habit, injection, active))

        assertEquals(listOf("Aspirin", "Insulin"), report.medicines.map { it.name })
        assertEquals(null, report.medicines[0].dosage)
        assertEquals("no schedule", report.medicines[0].scheduleText)
        assertEquals("with dinner", report.medicines[1].scheduleText)
        assertNull(report.medicines[0].adherencePercent)
    }

    @Test
    fun `schedules are described in plain English in deterministic order`() {
        val item = medicine(
            schedules = listOf(
                ReportSchedule.MealRelative(Meal.BREAKFAST, MealRelation.AFTER, 30),
                ReportSchedule.Fixed(540),
                ReportSchedule.MealRelative(Meal.BREAKFAST, MealRelation.BEFORE, 15),
                ReportSchedule.MealRelative(Meal.LUNCH, MealRelation.WITH),
            ),
        )

        val report = build(items = listOf(item))

        assertEquals(
            "09:00; 15 min before breakfast; 30 min after breakfast; with lunch",
            report.medicines.single().scheduleText,
        )
    }

    @Test
    fun `adherence percent and missed doses are computed per medicine`() {
        val day = LocalDate.of(2026, 1, 5)
        val occurrences = listOf(
            occurrence("taken-1", dueAt = at(ZoneOffset.UTC, day, 8, 0), status = ReportOccurrenceStatus.TAKEN),
            occurrence("taken-2", dueAt = at(ZoneOffset.UTC, day, 9, 0), status = ReportOccurrenceStatus.TAKEN),
            occurrence("missed-late", dueAt = at(ZoneOffset.UTC, day, 11, 0), status = ReportOccurrenceStatus.MISSED),
            occurrence("missed-early", dueAt = at(ZoneOffset.UTC, day, 10, 0), status = ReportOccurrenceStatus.MISSED),
            occurrence("skipped", dueAt = at(ZoneOffset.UTC, day, 12, 0), status = ReportOccurrenceStatus.SKIPPED),
            occurrence("pending", dueAt = at(ZoneOffset.UTC, day, 13, 0), status = ReportOccurrenceStatus.PENDING),
            occurrence("duplicate", dueAt = at(ZoneOffset.UTC, day, 14, 0), status = ReportOccurrenceStatus.DUPLICATE_ACTION),
        )

        val report = build(
            items = listOf(
                medicine(id = "med-1"),
                medicine(id = "empty", name = "Empty"),
            ),
            occurrences = occurrences,
            range = ReportRange(day, day),
        )

        val med = report.medicines.first { it.itemId == "med-1" }
        assertEquals(2, med.taken)
        assertEquals(2, med.missed)
        assertEquals(1, med.skipped)
        assertEquals(40, med.adherencePercent)
        assertEquals(
            listOf("missed-early", "missed-late"),
            med.missedDoses.map { it.occurrenceId },
        )
        assertEquals(listOf(day, day), med.missedDoses.map { it.localDate })

        val empty = report.medicines.first { it.itemId == "empty" }
        assertNull(empty.adherencePercent)
        assertTrue(empty.missedDoses.isEmpty())
    }

    @Test
    fun `daily stats group by local date in the supplied zone`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val jan5 = LocalDate.of(2026, 1, 5)
        val jan6 = LocalDate.of(2026, 1, 6)
        val report = build(
            items = listOf(medicine()),
            occurrences = listOf(
                occurrence("first", dueAt = Instant.parse("2026-01-05T18:45:00Z"), status = ReportOccurrenceStatus.TAKEN),
                occurrence("second", dueAt = Instant.parse("2026-01-05T19:15:00Z"), status = ReportOccurrenceStatus.TAKEN),
                occurrence("missed", dueAt = Instant.parse("2026-01-06T03:00:00Z"), status = ReportOccurrenceStatus.MISSED),
            ),
            range = ReportRange(jan5, jan6),
            zoneId = zone,
        )

        val jan5Row = report.daily.first { it.date == jan5 }
        val jan6Row = report.daily.first { it.date == jan6 }
        assertEquals(0, jan5Row.taken)
        assertEquals(2, jan6Row.taken)
        assertEquals(1, jan6Row.missed)
        assertEquals(67, jan6Row.percent)
    }

    @Test
    fun `a London spring forward day is one daily row`() {
        val zone = ZoneId.of("Europe/London")
        val date = LocalDate.of(2026, 3, 29)
        val report = build(
            items = listOf(medicine()),
            occurrences = listOf(
                occurrence("start", dueAt = at(zone, date, 0, 30), status = ReportOccurrenceStatus.TAKEN),
                occurrence("end", dueAt = at(zone, date, 23, 30), status = ReportOccurrenceStatus.TAKEN),
            ),
            range = ReportRange(date, date),
            zoneId = zone,
        )

        assertEquals(1, report.daily.size)
        assertEquals(date, report.daily.single().date)
        assertEquals(2, report.daily.single().taken)
        assertEquals(100, report.daily.single().percent)
    }

    @Test
    fun `a London fall back day is one daily row`() {
        val zone = ZoneId.of("Europe/London")
        val date = LocalDate.of(2026, 10, 25)
        val report = build(
            items = listOf(medicine()),
            occurrences = listOf(
                occurrence("start", dueAt = Instant.parse("2026-10-25T00:30:00Z"), status = ReportOccurrenceStatus.TAKEN),
                occurrence("end", dueAt = Instant.parse("2026-10-25T22:30:00Z"), status = ReportOccurrenceStatus.TAKEN),
            ),
            range = ReportRange(date, date),
            zoneId = zone,
        )

        assertEquals(1, report.daily.size)
        assertEquals(date, report.daily.single().date)
        assertEquals(2, report.daily.single().taken)
        assertEquals(100, report.daily.single().percent)
    }

    @Test
    fun `a ninety day range produces one row per local day`() {
        val report = build(
            range = ReportRange(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
            ),
        )

        assertEquals(90, report.daily.size)
        assertEquals(LocalDate.of(2026, 1, 1), report.daily.first().date)
        assertEquals(LocalDate.of(2026, 3, 31), report.daily.last().date)

        report.daily.zipWithNext().forEach { (earlier, later) ->
            assertEquals(earlier.date.plusDays(1), later.date)
        }
    }

    @Test
    fun `appointments split into past and upcoming and exclude out of range`() {
        val range = ReportRange(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 10),
        )
        val appointments = listOf(
            ReportAppointment("past-early", "Early", Instant.parse("2026-01-03T09:00:00Z")),
            ReportAppointment("past-late", "Late", Instant.parse("2026-01-04T09:00:00Z")),
            ReportAppointment("now", "Now", now),
            ReportAppointment("future", "Future", Instant.parse("2026-01-06T09:00:00Z")),
            ReportAppointment("out-old", "Old", Instant.parse("2025-12-31T09:00:00Z")),
            ReportAppointment("out-new", "New", Instant.parse("2026-01-11T09:00:00Z")),
        )

        val report = build(appointments = appointments, range = range)

        assertEquals(
            listOf("past-late", "past-early"),
            report.pastAppointments.map { it.id },
        )
        assertEquals(
            listOf("now", "future"),
            report.upcomingAppointments.map { it.id },
        )
    }

    @Test
    fun `vitals summaries come from the supplied provider and are sorted`() {
        var capturedProfile: String? = null
        var capturedRange: ReportRange? = null
        var capturedZone: ZoneId? = null
        val provider = VitalsSummaryProvider { profileId, range, zoneId ->
            capturedProfile = profileId
            capturedRange = range
            capturedZone = zoneId
            listOf(
                VitalsSummary("weight", "Weight", "kg", 3, 70.0, 72.0, 75.0),
                VitalsSummary("glucose", "Glucose", "mg/dL", 2, 90.0, 100.0, 110.0),
            )
        }
        val range = ReportRange(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 10),
        )

        val report = build(range = range, vitals = provider)

        assertEquals("profile-1", capturedProfile)
        assertEquals(range, capturedRange)
        assertEquals(ZoneOffset.UTC, capturedZone)
        assertEquals(
            listOf("glucose", "weight"),
            report.vitals.map { it.kind },
        )
    }

    @Test
    fun `the report is independent of input list order`() {
        val items = listOf(
            medicine(id = "b", name = "B"),
            medicine(id = "a", name = "A"),
            medicine(id = "c", name = "C"),
        )
        val occurrences = listOf(
            occurrence("2", itemId = "b", dueAt = Instant.parse("2026-01-05T09:00:00Z"), status = ReportOccurrenceStatus.TAKEN),
            occurrence("1", itemId = "a", dueAt = Instant.parse("2026-01-05T08:00:00Z"), status = ReportOccurrenceStatus.MISSED),
            occurrence("3", itemId = "c", dueAt = Instant.parse("2026-01-05T10:00:00Z"), status = ReportOccurrenceStatus.SKIPPED),
        )
        val appointments = listOf(
            ReportAppointment("b", "B", Instant.parse("2026-01-06T09:00:00Z")),
            ReportAppointment("a", "A", Instant.parse("2026-01-07T09:00:00Z")),
        )
        val baseline = build(items, occurrences, appointments)

        val shuffled = build(
            items.shuffled(Random(42)),
            occurrences.shuffled(Random(7)),
            appointments.shuffled(Random(11)),
        )

        assertEquals(baseline, shuffled)
    }

    @Test
    fun `invalid ranges and schedules are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReportRange(
                LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 1),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReportSchedule.Fixed(-1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReportSchedule.Fixed(1440)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReportSchedule.MealRelative(
                Meal.BREAKFAST,
                MealRelation.BEFORE,
                offsetMinutes = -1,
            )
        }
        ReportSchedule.Fixed(0)
        ReportSchedule.Fixed(1439)
        ReportSchedule.MealRelative(Meal.SNACK, MealRelation.AFTER, 0)
    }
}
