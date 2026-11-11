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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
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
    onRetryReschedule: () -> Unit = {},
    onRewardShown: () -> Unit = {}
) {
    var showMealDialog by remember { mutableStateOf(false) }
    var skippingOcc by remember { mutableStateOf<OccurrenceUiModel?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.rewardMessage) {
        val msg = uiState.rewardMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            onRewardShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Today", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
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
        val visible = uiState.occurrences.filter { it.occurrence.status != OccurrenceStatus.SUPERSEDED }
        val open = listOf(
            OccurrenceStatus.PENDING, OccurrenceStatus.RINGING,
            OccurrenceStatus.SNOOZED, OccurrenceStatus.DEFERRED_MEAL
        )
        val upNext = visible.filter { it.occurrence.status in open }
        val finished = visible.filter { it.occurrence.status !in open }
        val doneCount = visible.count { it.occurrence.status == OccurrenceStatus.TAKEN }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
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

                }
            }

            item {
                ProfileFilterRow(
                    profiles = uiState.profiles,
                    selectedProfileId = uiState.selectedProfileId,
                    onProfileSelected = onProfileSelected
                )
            }

            item {
                TodayHero(
                    progress = uiState.progress,
                    done = doneCount,
                    total = visible.size,
                    nextAlarmEpochMillis = uiState.nextAlarmEpochMillis
                )
            }

            if (uiState.activeMealSession != null) {
                item {
                    ActiveMealSessionCard(
                        session = uiState.activeMealSession,
                        onDoneEating = { onEndMeal(uiState.activeMealSession) }
                    )
                }
            }

            if (uiState.asNeededItems.isNotEmpty()) {
                item {
                    AsNeededSection(
                        items = uiState.asNeededItems,
                        showProfileBadge = uiState.selectedProfileId == null,
                        onTakeNow = onTakeAsNeeded
                    )
                }
            }

            if (visible.isEmpty() && uiState.asNeededItems.isEmpty()) {
                item { TodayEmptyState() }
            }

            if (upNext.isNotEmpty()) {
                item { SectionHeader(title = "Up next", count = upNext.size) }
                items(upNext, key = { it.occurrence.id }) { occUi ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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

            if (finished.isNotEmpty()) {
                item { SectionHeader(title = "Done today", count = finished.size) }
                items(finished, key = { it.occurrence.id }) { occUi ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
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
private fun TodayHero(
    progress: app.allinonereminder.ui.today.TodayProgress?,
    done: Int,
    total: Int,
    nextAlarmEpochMillis: Long?
) {
    val onHero = Color.White
    val hour = java.time.LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val allDone = total > 0 && done >= total
    val fraction = if (total == 0) 0f else done.toFloat() / total
    val headline = when {
        total == 0 -> "Nothing due today"
        allDone -> "All done for today!"
        else -> "$done of $total done"
    }
    val subline = when {
        allDone -> "Great job. See you tomorrow."
        nextAlarmEpochMillis != null -> "Next at " + Instant.ofEpochMilli(nextAlarmEpochMillis)
            .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))
        total == 0 -> "Add a reminder from Items."
        else -> "Nothing else scheduled."
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.large)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color(0xFF00796B), Color(0xFF00463E))
                )
            )
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(greeting, style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.85f))
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(headline, style = MaterialTheme.typography.headlineSmall, color = onHero)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(subline, style = MaterialTheme.typography.bodyMedium, color = onHero.copy(alpha = 0.85f))
                }
                Spacer(modifier = Modifier.width(12.dp))
                ProgressRing(
                    fraction = fraction,
                    label = if (allDone) "100%" else "${(fraction * 100).toInt()}%",
                    color = onHero,
                    track = onHero.copy(alpha = 0.22f)
                )
            }

            if (progress != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill(
                        icon = Icons.Default.LocalFireDepartment,
                        text = if (progress.bestStreakDays == 1) "1 day" else "${progress.bestStreakDays} days",
                        contentDescription = "Streak: ${progress.bestStreakDays} days",
                        modifier = Modifier.weight(1f)
                    )
                    StatPill(
                        icon = Icons.Default.Star,
                        text = "${progress.totalPoints} pts",
                        contentDescription = "${progress.totalPoints} points",
                        modifier = Modifier.weight(1f)
                    )
                    StatPill(
                        icon = Icons.Default.EmojiEvents,
                        text = "Level ${progress.level}",
                        contentDescription = "Level ${progress.level}",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress.levelProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = onHero,
                    trackColor = onHero.copy(alpha = 0.22f),
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${progress.pointsToNextLevel} points to level ${progress.level + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = onHero.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun ProgressRing(
    fraction: Float,
    label: String,
    color: Color,
    track: Color,
    size: androidx.compose.ui.unit.Dp = 84.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .androidx_semantics_progress(fraction),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
            val style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawArc(track, -90f, 360f, false, topLeft = topLeft, size = arcSize, style = style)
            if (fraction > 0f) {
                drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft = topLeft, size = arcSize, style = style)
            }
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

private fun Modifier.androidx_semantics_progress(fraction: Float): Modifier =
    this.then(
        Modifier.semantics(mergeDescendants = true) {
            progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
        }
    )

@Composable
private fun StatPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 10.dp, vertical = 2.dp)
        ) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun TodayEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.WbSunny,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Your day is clear", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Add medicines, water, walks or visits from the Items tab and they will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
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
                    app.allinonereminder.ui.components.TypeBadge(item.type, size = 40.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                if (occ.status != OccurrenceStatus.PENDING) StatusChip(status = occ.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    val doseText = if (item.type == ItemType.APPOINTMENT) {
                        effectiveDoseAmount(uiModel.schedule, item)
                    } else {
                        doseLabel(uiModel.schedule, item)
                    }
                    Text(
                        text = doseText,
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
                        occurrence = sampleOcc.copy(id = "occ-0", status = OccurrenceStatus.TAKEN, dueAt = sampleOcc.dueAt - 4 * 3600_000L),
                        item = sampleItem.copy(id = "item-0", name = "Vitamin D3", dosageAmount = "1", dosageUnit = "capsule"),
                        profile = sampleProfile,
                        schedule = null
                    ),
                    OccurrenceUiModel(
                        occurrence = sampleOcc,
                        item = sampleItem,
                        profile = sampleProfile,
                        schedule = null
                    ),
                    OccurrenceUiModel(
                        occurrence = sampleOcc.copy(id = "occ-2", dueAt = sampleOcc.dueAt + 3 * 3600_000L),
                        item = sampleItem.copy(id = "item-2", type = ItemType.WATER_DRINK, name = "Water", dosageAmount = "1", dosageUnit = "glass"),
                        profile = sampleProfile,
                        schedule = null
                    )
                ),
                nextAlarmEpochMillis = System.currentTimeMillis() + 3600_000L,
                progress = TodayProgress(
                    totalPoints = 245,
                    level = 3,
                    levelProgress = 0.45f,
                    pointsToNextLevel = 55,
                    bestStreakDays = 6,
                    medsPercent = 50,
                    hydrationPercent = 0,
                    movementPercent = null
                )
            )
        )
    }
}
