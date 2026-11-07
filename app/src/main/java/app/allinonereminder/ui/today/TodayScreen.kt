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
import app.allinonereminder.alarm.SkipReasonDialog
import app.allinonereminder.model.SkipReason
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
    onTakeAsNeeded: (AsNeededUiModel) -> Unit = {},
    onTakeAsNeededConfirmed: (AsNeededUiModel) -> Unit = {},
    onDismissAsNeededWarning: () -> Unit = {},
    onDismissAsNeededLimit: () -> Unit = {},
    onDismissDoubleDoseWarning: () -> Unit = {},
    onSnooze: (OccurrenceUiModel) -> Unit = {},
    onSkip: (OccurrenceUiModel, SkipReason) -> Unit = { _, _ -> },
    onFixExactAlarm: () -> Unit = {},
    onStartMeal: (MealType, String?) -> Unit = { _, _ -> },
    onEndMeal: (MealSession) -> Unit = {},
    onOccurrenceClick: (OccurrenceUiModel) -> Unit = {},
    onRetryReschedule: () -> Unit = {}
) {
    var showMealDialog by remember { mutableStateOf(false) }
    var skippingOcc by remember { mutableStateOf<OccurrenceUiModel?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today's Schedule", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
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
            // Exact alarm warning banner when permission is not granted
            if (uiState.exactAlarmsDenied) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Exact Alarms Disabled",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Reminders will not ring reliably on time without exact alarm permission.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(onClick = onFixExactAlarm) {
                            Text("Fix", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

                        // Reschedule error banner (R2-6)
            if (uiState.rescheduleError != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reminders may not ring — tap to retry",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Background scheduling failed: ${uiState.rescheduleError}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(onClick = onRetryReschedule) {
                            Text("Retry", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

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

            // As-needed (PRN) items have no planned times; show a Take now card for each.
            if (uiState.asNeededItems.isNotEmpty()) {
                AsNeededSection(
                    items = uiState.asNeededItems,
                    showProfileBadge = uiState.selectedProfileId == null,
                    onTakeNow = onTakeAsNeeded
                )
            }

            // Timeline of occurrences
            if (uiState.occurrences.isEmpty()) {
                if (uiState.asNeededItems.isEmpty()) {
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
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.occurrences, key = { it.occurrence.id }) { occUi ->
                        OccurrenceTimelineCard(
                            uiModel = occUi,
                            showProfileBadge = uiState.selectedProfileId == null,
                            onTake = { onTake(occUi) },
                            onSnooze = { onSnooze(occUi) },
                            onSkip = { skippingOcc = occUi },
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

    // Double-dose guard confirmation dialog for as-needed items
    val asNeededWarn = uiState.asNeededWarning
    if (asNeededWarn != null) {
        AlertDialog(
            onDismissRequest = onDismissAsNeededWarning,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Double-Dose Warning") },
            text = {
                Text(
                    text = "A dose of ${asNeededWarn.item.name} was already taken recently. Taking another dose within ${asNeededWarn.item.minGapMinutes ?: 240} minutes could result in accidental double-dosing.\n\nAre you sure you want to proceed?"
                )
            },
            confirmButton = {
                Button(
                    onClick = { onTakeAsNeededConfirmed(asNeededWarn) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Take Anyway")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissAsNeededWarning) {
                    Text("Cancel")
                }
            }
        )
    }

    // Daily cap reached for an as-needed item
    val asNeededLimit = uiState.asNeededLimitItem
    if (asNeededLimit != null) {
        AlertDialog(
            onDismissRequest = onDismissAsNeededLimit,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Daily Limit Reached") },
            text = {
                Text(
                    text = "${asNeededLimit.item.name} has already been taken ${asNeededLimit.maxPerDay ?: asNeededLimit.takenToday} time(s) today. The daily limit is set in the item's schedule."
                )
            },
            confirmButton = {
                Button(onClick = onDismissAsNeededLimit) {
                    Text("OK")
                }
            }
        )
    }

    // Skip Reason Dialog
    val occToSkip = skippingOcc
    if (occToSkip != null) {
        SkipReasonDialog(
            onDismiss = { skippingOcc = null },
            onConfirm = { reason ->
                skippingOcc = null
                onSkip(occToSkip, reason)
            }
        )
    }

    // Meal Selection Dialog with explicit profile choice when "All" is selected per §17/review B4
    if (showMealDialog) {
        var selectedProfileForMeal by remember(showMealDialog) {
            mutableStateOf(uiState.selectedProfileId ?: if (uiState.profiles.size == 1) uiState.profiles.first().id else null)
        }

        AlertDialog(
            onDismissRequest = { showMealDialog = false },
            icon = { Icon(Icons.Default.Restaurant, contentDescription = null) },
            title = { Text("Starting a Meal") },
            text = {
                Column {
                    if (uiState.selectedProfileId == null && uiState.profiles.size > 1) {
                        Text("Select person:", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        uiState.profiles.forEach { profile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedProfileForMeal == profile.id,
                                    onClick = { selectedProfileForMeal = profile.id }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(profile.name)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Text("Select which meal you are starting now. Meal-relative doses will adapt immediately:")
                    Spacer(modifier = Modifier.height(16.dp))
                    MealType.entries.forEach { meal ->
                        Button(
                            onClick = {
                                selectedProfileForMeal?.let { pid ->
                                    onStartMeal(meal, pid)
                                }
                                showMealDialog = false
                            },
                            enabled = selectedProfileForMeal != null,
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
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.large)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(primary, MaterialTheme.colorScheme.secondary)
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Next reminder",
                    style = MaterialTheme.typography.labelLarge,
                    color = onPrimary.copy(alpha = 0.85f)
                )
                val timeText = if (nextAlarmEpochMillis != null) {
                    val time = Instant.ofEpochMilli(nextAlarmEpochMillis).atZone(ZoneId.systemDefault())
                    time.format(DateTimeFormatter.ofPattern("hh:mm a"))
                } else {
                    "All done for today"
                }
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = onPrimary
                )
            }

            OutlinedButton(
                onClick = onStartMealClick,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = onPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, onPrimary.copy(alpha = 0.6f))
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
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
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                StatusChip(status = occ.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    val doseLabel = if (item.type == ItemType.APPOINTMENT) {
                        effectiveDoseAmount(uiModel.schedule, item)
                    } else {
                        "${effectiveDoseAmount(uiModel.schedule, item)} ${item.dosageUnit} ${item.form ?: ""}".trim()
                    }
                    Text(
                        text = doseLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiModel.stockStatus != null) {
                        Text(
                            text = uiModel.stockStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

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
                        Text(if (item.type == ItemType.APPOINTMENT) "Got it" else "Take")
                    }
                    FilledTonalButton(
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
private fun AsNeededSection(
    items: List<AsNeededUiModel>,
    showProfileBadge: Boolean,
    onTakeNow: (AsNeededUiModel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = "As needed",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.schedule.id }) { asNeeded ->
                AsNeededCard(
                    uiModel = asNeeded,
                    showProfileBadge = showProfileBadge,
                    onTakeNow = { onTakeNow(asNeeded) }
                )
            }
        }
    }
}

@Composable
private fun AsNeededCard(
    uiModel: AsNeededUiModel,
    showProfileBadge: Boolean,
    onTakeNow: () -> Unit
) {
    val item = uiModel.item
    Card(
        modifier = Modifier.width(260.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Medication,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${effectiveDoseAmount(uiModel.schedule, item)} ${item.dosageUnit} ${item.form ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (uiModel.stockStatus != null) {
                Text(
                    text = uiModel.stockStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
            Spacer(modifier = Modifier.height(6.dp))
            val max = uiModel.maxPerDay
            Text(
                text = if (max != null) {
                    "Taken today: ${uiModel.takenToday} of $max"
                } else {
                    "Taken today: ${uiModel.takenToday}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onTakeNow,
                enabled = uiModel.canTake,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
            ) {
                Text(if (uiModel.canTake) "Take now" else "Daily limit reached")
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
        OccurrenceStatus.SUPERSEDED -> Triple(Color(0xFFEEEEEE), Color(0xFF9E9E9E), "Superseded")
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
