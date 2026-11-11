package app.allinonereminder.ui

import app.allinonereminder.backup.CsvExporter
import app.allinonereminder.model.Criticality
import app.allinonereminder.model.Item
import app.allinonereminder.model.ItemType
import app.allinonereminder.model.Occurrence
import app.allinonereminder.model.OccurrenceStatus
import app.allinonereminder.model.Profile
import app.allinonereminder.model.TimePolicy
import app.allinonereminder.ui.today.OccurrenceUiModel
import app.allinonereminder.ui.today.expiryWarning
import app.allinonereminder.ui.today.groupPendingByPerson
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/** Pure helpers behind CSV export (#91), expiry warnings (#87) and Today grouped by person (#90). */
class RoadmapHelpersTest {

    @Test
    fun csvEscape_quotesCommasQuotesAndNewlines() {
        assertThat(CsvExporter.escape("plain")).isEqualTo("plain")
        assertThat(CsvExporter.escape("a,b")).isEqualTo("\"a,b\"")
        assertThat(CsvExporter.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"")
        assertThat(CsvExporter.escape("two\nlines")).isEqualTo("\"two\nlines\"")
    }

    @Test
    fun csvEscape_neutralisesSpreadsheetFormulas() {
        assertThat(CsvExporter.escape("=1+1")).isEqualTo("'=1+1")
        assertThat(CsvExporter.escape("@SUM(A1)")).isEqualTo("'@SUM(A1)")
        assertThat(CsvExporter.escape("+91 phone")).isEqualTo("'+91 phone")
        assertThat(CsvExporter.escape("")).isEqualTo("")
    }

    @Test
    fun expiryWarning_onlyWithinThirtyDays() {
        val today = LocalDate.of(2026, 11, 11)
        assertThat(expiryWarning(null, today)).isNull()
        assertThat(expiryWarning("not a date", today)).isNull()
        assertThat(expiryWarning("2027-01-30", today)).isNull()
        assertThat(expiryWarning("2026-11-21", today)).isEqualTo("Expires in 10 days" to false)
        assertThat(expiryWarning("2026-11-12", today)).isEqualTo("Expires in 1 day" to false)
        assertThat(expiryWarning("2026-11-11", today)).isEqualTo("Expires today" to true)
        assertThat(expiryWarning("2026-11-01", today)?.second).isTrue()
    }

    private fun ui(id: String, person: String, dueAt: Long, status: OccurrenceStatus = OccurrenceStatus.PENDING): OccurrenceUiModel {
        val profile = Profile(id = person, name = person, colorArgb = 0, createdAt = 0, updatedAt = 0)
        val item = Item(
            id = "i-$id", profileId = person, type = ItemType.MEDICINE, name = id, dosageAmount = "1",
            dosageUnit = "tablet", criticality = Criticality.NORMAL, graceMinutes = 60, reRingMinutes = 5,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK, homeZoneId = "UTC", startDate = "2026-01-01",
            createdAt = 0, updatedAt = 0
        )
        val occ = Occurrence(
            id = id, itemId = item.id, scheduleId = "s", profileId = person, plannedAt = dueAt,
            dueAt = dueAt, status = status, deviceId = "d", updatedAt = 0
        )
        return OccurrenceUiModel(occ, item, profile, null)
    }

    @Test
    fun groupByPerson_overdueFirstThenPeopleAlphabetically() {
        val now = 10_000L
        val groups = groupPendingByPerson(
            listOf(
                ui("late", "Papa", 5_000L),
                ui("ringing", "Papa", 5_000L, OccurrenceStatus.RINGING),
                ui("m2", "Mom", 20_000L),
                ui("m1", "Mom", 15_000L),
                ui("p1", "Papa", 30_000L)
            ),
            now
        )
        assertThat(groups.map { it.label }).containsExactly("Overdue", "Mom", "Papa").inOrder()
        assertThat(groups[0].items.map { it.occurrence.id }).containsExactly("late")
        assertThat(groups[1].items.map { it.occurrence.id }).containsExactly("m1", "m2").inOrder()
        // A ringing dose is "now", not overdue.
        assertThat(groups[2].items.map { it.occurrence.id }).containsExactly("ringing", "p1").inOrder()
    }
}
