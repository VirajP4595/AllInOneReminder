package app.allinonereminder.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.allinonereminder.data.repository.DayAdherence
import app.allinonereminder.data.repository.DayAdherenceBucket
import app.allinonereminder.model.Item
import app.allinonereminder.model.Occurrence
import app.allinonereminder.model.OccurrenceStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adherence History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)
            ) {
                // Profile selector chips
                if (state.profiles.size > 1) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = state.selectedProfileId == null,
                                    onClick = { viewModel.selectProfile(null) },
                                    label = { Text("All Profiles") }
                                )
                            }
                            items(state.profiles) { profile ->
                                FilterChip(
                                    selected = state.selectedProfileId == profile.id,
                                    onClick = { viewModel.selectProfile(profile.id) },
                                    label = { Text(profile.name) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color(profile.colorArgb))
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // Month Calendar Card
                item {
                    MonthCalendarCard(
                        yearMonth = state.yearMonth,
                        selectedDate = state.selectedDate,
                        monthAdherence = state.monthAdherence,
                        onPrevMonth = viewModel::previousMonth,
                        onNextMonth = viewModel::nextMonth,
                        onSelectDate = viewModel::selectDate
                    )
                }

                // Legend
                item {
                    AdherenceLegend()
                }

                // Selected Day Details Header
                item {
                    val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
                    val dayAdherence = state.monthAdherence[state.selectedDate]

                    Column {
                        Text(
                            text = state.selectedDate.format(formatter),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (dayAdherence != null && dayAdherence.totalDue > 0) {
                            Text(
                                text = "${dayAdherence.onTime} on-time of ${dayAdherence.totalDue} doses (${dayAdherence.taken} taken, ${dayAdherence.missed} missed, ${dayAdherence.skipped} skipped)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "No scheduled doses for this day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Selected Day Occurrences List
                if (state.selectedDateOccurrences.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "No doses recorded for ${state.selectedDate}",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(state.selectedDateOccurrences) { (occ, item) ->
                        HistoryOccurrenceCard(occ = occ, item = item)
                    }
                }
            }
        }
    }
}

@Composable
fun MonthCalendarCard(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    monthAdherence: Map<LocalDate, DayAdherence>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with month title and arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevMonth) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                }
                Text(
                    text = "${yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${yearMonth.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Day of week labels (Sun - Sat)
            Row(modifier = Modifier.fillMaxWidth()) {
                val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
                daysOfWeek.forEach { dayName ->
                    Text(
                        text = dayName,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Calendar Grid
            val firstDayOfMonth = yearMonth.atDay(1)
            val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7 // Sunday = 0
            val daysInMonth = yearMonth.lengthOfMonth()

            val totalSlots = firstDayOfWeek + daysInMonth
            val totalRows = (totalSlots + 6) / 7

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in 0 until totalRows) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (col in 0..6) {
                            val slotIndex = row * 7 + col
                            val dayNumber = slotIndex - firstDayOfWeek + 1

                            if (dayNumber in 1..daysInMonth) {
                                val date = yearMonth.atDay(dayNumber)
                                val adherence = monthAdherence[date]
                                val isSelected = date == selectedDate

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(bucketColor(adherence?.bucket ?: DayAdherenceBucket.NONE_DUE))
                                        .then(
                                            if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                            else Modifier
                                        )
                                        .clickable { onSelectDate(date) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$dayNumber",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (adherence?.bucket == DayAdherenceBucket.NONE_DUE) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else Color.White
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun bucketColor(bucket: DayAdherenceBucket): Color {
    return when (bucket) {
        DayAdherenceBucket.NONE_DUE -> Color(0xFFE0E0E0)
        DayAdherenceBucket.PERFECT -> Color(0xFF43A047)   // 100% on time
        DayAdherenceBucket.GOOD -> Color(0xFF66BB6A)      // >= 75% on time
        DayAdherenceBucket.PARTIAL -> Color(0xFFFFA726)   // partial
        DayAdherenceBucket.NONE_TAKEN -> Color(0xFFE53935)// 0% on time
    }
}

@Composable
fun AdherenceLegend() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(color = Color(0xFF43A047), label = "100%")
            LegendItem(color = Color(0xFF66BB6A), label = "75%+")
            LegendItem(color = Color(0xFFFFA726), label = "Partial")
            LegendItem(color = Color(0xFFE53935), label = "Missed")
            LegendItem(color = Color(0xFFE0E0E0), label = "None", textColor = Color.Gray)
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String, textColor: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = textColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun HistoryOccurrenceCard(occ: Occurrence, item: Item) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${item.dosageAmount} ${item.dosageUnit} • ${item.instructions.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val zone = ZoneId.systemDefault()
                val dueFormatter = DateTimeFormatter.ofPattern("hh:mm a")
                val dueTime = Instant.ofEpochMilli(occ.dueAt).atZone(zone).format(dueFormatter)

                Spacer(modifier = Modifier.height(4.dp))
                val actedAt = occ.actedAt
                if (actedAt != null) {
                    val actedTime = Instant.ofEpochMilli(actedAt).atZone(zone).format(dueFormatter)
                    Text(
                        text = "Scheduled $dueTime • Logged at $actedTime",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "Scheduled $dueTime",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when (occ.status) {
                    OccurrenceStatus.TAKEN -> Color(0xFFE8F5E9)
                    OccurrenceStatus.SKIPPED -> Color(0xFFFFF3E0)
                    OccurrenceStatus.MISSED -> Color(0xFFFFEBEE)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Text(
                    text = when (occ.status) {
                        OccurrenceStatus.TAKEN -> "Taken"
                        OccurrenceStatus.SKIPPED -> "Skipped"
                        OccurrenceStatus.MISSED -> "Missed"
                        else -> "Pending"
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (occ.status) {
                        OccurrenceStatus.TAKEN -> Color(0xFF2E7D32)
                        OccurrenceStatus.SKIPPED -> Color(0xFFE65100)
                        OccurrenceStatus.MISSED -> Color(0xFFC62828)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

