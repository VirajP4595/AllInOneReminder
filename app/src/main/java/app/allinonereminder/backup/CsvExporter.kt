package app.allinonereminder.backup

import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.OccurrenceRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.ScheduleRepository
import app.allinonereminder.model.OccurrenceStatus
import app.allinonereminder.model.isTestAlarm
import app.allinonereminder.ui.items.ItemsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plain CSV exports for a doctor visit (#91): the current medicine list and recent dose history.
 * Written to a file the user picks; nothing is sent anywhere by the app.
 */
@Singleton
class CsvExporter @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val itemRepository: ItemRepository,
    private val scheduleRepository: ScheduleRepository,
    private val occurrenceRepository: OccurrenceRepository
) {

    suspend fun writeMedicineList(out: OutputStream) = withContext(Dispatchers.IO) {
        val profiles = profileRepository.getActiveProfiles().first().associateBy { it.id }
        val schedules = scheduleRepository.getAllActiveSchedulesSync().groupBy { it.itemId }
        val rows = itemRepository.getAllActiveItemsSync()
            .filter { it.appointmentId == null && !it.isTestAlarm }
            .sortedWith(compareBy({ profiles[it.profileId]?.name ?: "" }, { it.name.lowercase() }))
            .map { item ->
                listOf(
                    profiles[item.profileId]?.name ?: "",
                    item.name,
                    item.type.name.lowercase().replace('_', ' '),
                    item.dosageAmount,
                    item.dosageUnit,
                    item.form ?: "",
                    item.category ?: "",
                    ItemsViewModel.formatSchedulesSummary(schedules[item.id] ?: emptyList(), item),
                    item.instructions ?: "",
                    item.expiryDate ?: "",
                    if (item.active) "active" else "paused"
                )
            }
        write(
            out,
            listOf("Person", "Name", "Type", "Dose", "Unit", "Form", "Category", "When", "Instructions", "Expiry", "Status"),
            rows
        )
    }

    suspend fun writeDoseHistory(out: OutputStream, days: Long = 90) = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val from = LocalDate.now(zone).minusDays(days).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = System.currentTimeMillis()
        val profiles = profileRepository.getActiveProfiles().first().associateBy { it.id }
        val items = itemRepository.getAllActiveItemsSync().associateBy { it.id }
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
        val rows = occurrenceRepository.getOccurrencesBetweenSync(from, to)
            .filter { it.status != OccurrenceStatus.SUPERSEDED && items[it.itemId]?.isTestAlarm != true }
            .sortedBy { it.dueAt }
            .map { occ ->
                val due = Instant.ofEpochMilli(occ.dueAt).atZone(zone)
                val done = occ.actedAt?.let { Instant.ofEpochMilli(it).atZone(zone).format(timeFmt) } ?: ""
                listOf(
                    due.format(dateFmt),
                    due.format(timeFmt),
                    profiles[occ.profileId]?.name ?: "",
                    items[occ.itemId]?.name ?: "(deleted)",
                    occ.status.name.lowercase(),
                    done,
                    occ.skipReason ?: "",
                    occ.markedBy ?: ""
                )
            }
        write(out, listOf("Date", "Due", "Person", "Name", "Status", "Done at", "Skip reason", "Marked by"), rows)
    }

    private fun write(out: OutputStream, header: List<String>, rows: List<List<String>>) {
        val writer = out.bufferedWriter(Charsets.UTF_8)
        writer.write(0xFEFF) // byte-order mark, so Excel opens UTF-8 (e.g. Hindi names) correctly
        writer.write(header.joinToString(",") { escape(it) })
        writer.write("\r\n")
        rows.forEach { row ->
            writer.write(row.joinToString(",") { escape(it) })
            writer.write("\r\n")
        }
        writer.flush()
    }

    companion object {
        /** RFC 4180 quoting; a leading = + - @ is prefixed so spreadsheets don't run it as a formula. */
        fun escape(value: String): String {
            val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
            return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                "\"" + safe.replace("\"", "\"\"") + "\""
            } else {
                safe
            }
        }
    }
}
