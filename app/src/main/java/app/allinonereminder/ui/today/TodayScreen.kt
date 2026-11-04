package app.allinonereminder.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.allinonereminder.model.*
import app.allinonereminder.ui.theme.AllInOneReminderTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    uiState: TodayUiState,
    onProfileSelected: (String?) -> Unit = {},
    onTake: (OccurrenceUiModel) -> Unit = {},
    onTakeConfirmed: (OccurrenceUiModel) -> Unit = {},
    onDismissDoubleDoseWarning: () -> Unit = {},
    onSnooze: (OccurrenceUiModel) -> Unit = {},
    onSkip: (OccurrenceUiModel) -> Unit = {},
    onStartMeal: (MealType) -> Unit = {},
    onEndMeal: (MealSession) -> Unit = {},
    onOccurrenceClick: (OccurrenceUiModel) -> Unit = {}
) {
    var showMealDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today's Schedule", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(
                        onClick = { showMealDialog = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restaurant,
                            contentDescription = "Starting a meal"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Profile selector chip row
            ProfileFilterRow(
                profiles = uiState.profiles,
                selectedProfileId = uiState.selectedProfileId,
                onProfileSelected = onProfileSelected
            )

            // Header card: Next alarm countdown + "Starting a meal" quick button
            NextAlarmCountdownHeader(
                nextAlarmEpochMillis = uiState.nextAlarmEpochMillis,
                onStartMealClick = { showMealDialog = true }
            )

            // Active meal session banner if in progress
            if (uiState.activeMealSession != null) {
                ActiveMealSessionCard(
                    session = uiState.activeMealSession,
                    onDoneEating = { onEndMeal(uiState.activeMealSession) }
                )
            }

            // Timeline of occurrences
            if (uiState.occurrences.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No doses or events scheduled for today",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'Items' below to add medications or appointments.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.occurrences, key = { it.occurrence.id }) { occUi ->
                        OccurrenceTimelineCard(
                            uiModel = occUi,
                            showProfileBadge = uiState.selectedProfileId == null,
                            onTake = { onTake(occUi) },
                            onSnooze = { onSnooze(occUi) },
                            onSkip = { onSkip(occUi) },
                            onClick = { onOccurrenceClick(occUi) }
                        )
                    }
                }
            }
        }
    }

    // Double-dose guard confirmation dialog
    val warningOcc = uiState.doubleDoseWarningOccurrence
    if (warningOcc != null) {
        AlertDialog(
            onDismissRequest = onDismissDoubleDoseWarning,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Double-Dose Warning") },
            text = {
                Text(
                    text = "A dose of ${warningOcc.item.name} was already taken recently. Taking another dose within ${warningOcc.item.minGapMinutes ?: 240} minutes could result in accidental double-dosing.\n\nAre you sure you want to proceed?"
                )
            },
            confirmButton = {
                Button(
                    onClick = { onTakeConfirmed(warningOcc) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Take Anyway")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissDoubleDoseWarning) {
                    Text("Cancel")
                }
            }
        )
    }

    // Meal Selection Dialog
    if (showMealDialog) {
        AlertDialog(
            onDismissRequest = { showMealDialog = false },
            icon = { Icon(Icons.Default.Restaurant, contentDescription = null) },
            title = { Text("Starting a Meal") },
            text = {
                Column {
                    Text("Select which meal you are starting now. Meal-relative doses will adapt immediately:")
                    Spacer(modifier = Modifier.height(16.dp))
                    MealType.entries.forEach { meal ->
                        Button(
                            onClick = {
                                onStartMeal(meal)
                                showMealDialog = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text("Starting ${meal.name.lowercase().replaceFirstChar { it.uppercase() }} Now")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMealDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProfileFilterRow(
    profiles: List<Profile>,
    selectedProfileId: String?,
    onProfileSelected: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedProfileId == null,
                onClick = { onProfileSelected(null) },
                label = { Text("All") },
                leadingIcon = {
                    Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
        }
        items(profiles) { profile ->
            FilterChip(
                selected = selectedProfileId == profile.id,
                onClick = { onProfileSelected(profile.id) },
                label = { Text(profile.name) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(profile.colorArgb))
                    )
                }
            )
        }
    }
}

@Composable
private fun NextAlarmCountdownHeader(
    nextAlarmEpochMillis: Long?,
    onStartMealClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(12.dp)
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
                    text = "Next Alarm",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                val timeText = if (nextAlarmEpochMillis != null) {
                    val time = Instant.ofEpochMilli(nextAlarmEpochMillis).atZone(ZoneId.systemDefault())
                    time.format(DateTimeFormatter.ofPattern("hh:mm a"))
                } else {
                    "None pending today"
                }
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            OutlinedButton(
                onClick = onStartMealClick,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
            ) {
                Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Starting meal")
            }
        }
    }
}

@Composable
private fun OccurrenceTimelineCard(
    uiModel: OccurrenceUiModel,
    showProfileBadge: Boolean,
    onTake: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    onClick: () -> Unit
) {
    val occ = uiModel.occurrence
    val item = uiModel.item
    val dueTime = Instant.ofEpochMilli(occ.dueAt).atZone(ZoneId.systemDefault())
    val formattedTime = dueTime.format(DateTimeFormatter.ofPattern("hh:mm a"))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (item.type) {
                            ItemType.MEDICINE -> Icons.Default.Medication
                            ItemType.INJECTION -> Icons.Default.Vaccines
                            ItemType.WATER_DRINK -> Icons.Default.WaterDrop
                            ItemType.FOOD -> Icons.Default.Restaurant
                            ItemType.EXERCISE -> Icons.Default.FitnessCenter
                            ItemType.APPOINTMENT -> Icons.Default.Event
                            else -> Icons.Default.HealthAndSafety
                        },
                        contentDescription = item.type.name,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                StatusChip(status = occ.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${item.dosageAmount} ${item.dosageUnit} ${item.form ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (showProfileBadge && uiModel.profile != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(uiModel.profile.colorArgb))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = uiModel.profile.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Quick actions if pending, ringing, snoozed, or deferred
            if (occ.status in listOf(OccurrenceStatus.PENDING, OccurrenceStatus.RINGING, OccurrenceStatus.SNOOZED, OccurrenceStatus.DEFERRED_MEAL)) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onTake,
                        modifier = Modifier
                            .weight(1.2f)
                            .defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text("Take")
                    }
                    OutlinedButton(
                        onClick = onSnooze,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text("10m")
                    }
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier
                            .weight(0.8f)
                            .defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text("Skip")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: OccurrenceStatus) {
    val (bgColor, textColor, text) = when (status) {
        OccurrenceStatus.PENDING -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Pending")
        OccurrenceStatus.RINGING -> Triple(Color(0xFFFFCDD2), Color(0xFFB71C1C), "Ringing")
        OccurrenceStatus.TAKEN -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Taken")
        OccurrenceStatus.SNOOZED -> Triple(Color(0xFFFFF8E1), Color(0xFFF57F17), "Snoozed")
        OccurrenceStatus.DEFERRED_MEAL -> Triple(Color(0xFFE0F7FA), Color(0xFF00838F), "After Meal")
        OccurrenceStatus.MISSED -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "Missed")
        OccurrenceStatus.SKIPPED -> Triple(Color(0xFFEEEEEE), Color(0xFF616161), "Skipped")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ActiveMealSessionCard(
    session: MealSession,
    onDoneEating: () -> Unit
) {
    val mealName = session.meal.name.lowercase().replaceFirstChar { it.uppercase() }
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())
    val startedStr = timeFormatter.format(Instant.ofEpochMilli(session.startedAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Having $mealName",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Started at $startedStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Button(
                onClick = onDoneEating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Done")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TodayScreenPreview() {
    AllInOneReminderTheme {
        val sampleItem = Item(
            id = "item-1",
            profileId = "prof-1",
            type = ItemType.MEDICINE,
            name = "Metformin",
            dosageAmount = "500",
            dosageUnit = "mg",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = "UTC",
            startDate = "2026-01-01",
            createdAt = 0L,
            updatedAt = 0L
        )
        val sampleOcc = Occurrence(
            id = "occ-1",
            itemId = "item-1",
            scheduleId = "sched-1",
            profileId = "prof-1",
            plannedAt = System.currentTimeMillis(),
            dueAt = System.currentTimeMillis(),
            status = OccurrenceStatus.PENDING,
            deviceId = "dev",
            updatedAt = 0L
        )
        val sampleProfile = Profile(id = "prof-1", name = "Mom", colorArgb = 0xFF1E88E5.toInt(), createdAt = 0L, updatedAt = 0L)

        TodayScreen(
            uiState = TodayUiState(
                profiles = listOf(sampleProfile),
                occurrences = listOf(
                    OccurrenceUiModel(
                        occurrence = sampleOcc,
                        item = sampleItem,
                        profile = sampleProfile,
                        schedule = null
                    )
                ),
                nextAlarmEpochMillis = System.currentTimeMillis() + 3600_000L
            )
        )
    }
}
