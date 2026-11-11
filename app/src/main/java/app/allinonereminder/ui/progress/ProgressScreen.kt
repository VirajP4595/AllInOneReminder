package app.allinonereminder.ui.progress

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.allinonereminder.gamify.Badge
import app.allinonereminder.gamify.BadgeAward
import app.allinonereminder.gamify.DailyRings
import app.allinonereminder.gamify.ItemStreak
import app.allinonereminder.gamify.RingProgress
import app.allinonereminder.model.Item
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import app.allinonereminder.model.ItemType
import app.allinonereminder.ui.theme.AllInOneReminderTheme
import app.allinonereminder.ui.components.*

@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel,
    onNavigateToHistory: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    ProgressContent(
        state = state,
        onSelectProfile = viewModel::selectProfile,
        onNavigateToHistory = onNavigateToHistory
    )
}

private const val POINTS_PER_LEVEL = 100

private fun levelTitle(level: Int): String = when (level) {
    1 -> "Getting started"
    2 -> "Finding your rhythm"
    3 -> "Building habits"
    4 -> "Steady"
    5 -> "Consistent"
    else -> "Health champion"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressContent(
    state: ProgressUiState,
    onSelectProfile: (String?) -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rewards", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    TextButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("History")
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
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 48.dp)
        ) {
            if (state.profiles.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            ChoiceChip(
                                selected = state.selectedProfileId == null,
                                onClick = { onSelectProfile(null) },
                                label = { Text("Everyone") }
                            )
                        }
                        items(state.profiles) { profile ->
                            ChoiceChip(
                                selected = state.selectedProfileId == profile.id,
                                onClick = { onSelectProfile(profile.id) },
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

            item { LevelHero(totalPoints = state.totalPoints) }

            state.todayRings?.let { rings -> item { TodayRingsCard(rings) } }

            item {
                SectionCard(title = "Streaks", icon = Icons.Default.LocalFireDepartment) {
                    if (state.streaks.isEmpty()) {
                        Text(
                            text = "Take a reminder on time two days in a row to start a streak.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.streaks.sortedByDescending { it.first.current }.forEach { (streak, item) ->
                            StreakCard(streak = streak, item = item)
                        }
                    }
                }
            }

            item { BadgesSection(earnedBadges = state.badges) }

            item {
                SectionCard(title = "How points work", icon = Icons.Default.Star) {
                    Text(
                        text = "Medicines: 5 points when taken within 30 minutes of the time.\n" +
                            "Water, exercise and habits: 10 points on time, 5 if a little late.\n" +
                            "Every 100 points is a new level. Miss a day? One streak freeze a week keeps your streak alive.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelHero(totalPoints: Int) {
    val level = totalPoints / POINTS_PER_LEVEL + 1
    val into = totalPoints % POINTS_PER_LEVEL
    val white = Color.White
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF00796B), Color(0xFF00463E))))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFD54F)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFF5D4037), modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Level $level", style = MaterialTheme.typography.headlineMedium, color = white)
                    Text(levelTitle(level), style = MaterialTheme.typography.titleSmall, color = white.copy(alpha = 0.85f))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$totalPoints", style = MaterialTheme.typography.headlineSmall, color = white)
                    Text("points", style = MaterialTheme.typography.labelMedium, color = white.copy(alpha = 0.85f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { into / POINTS_PER_LEVEL.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = Color(0xFFFFD54F),
                trackColor = white.copy(alpha = 0.22f),
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${POINTS_PER_LEVEL - into} points to level ${level + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = white.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
fun TodayRingsCard(rings: DailyRings) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Today's Activity Rings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Concentric Rings Graphic
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(110.dp)) {
                        val strokeWidth = 10.dp.toPx()
                        val padding = strokeWidth + 4.dp.toPx()

                        // Outer ring: Meds
                        drawRing(
                            percent = rings.meds.percent,
                            radius = size.minDimension / 2 - strokeWidth / 2,
                            strokeWidth = strokeWidth,
                            trackColor = Color(0xFFE57373).copy(alpha = 0.25f),
                            fillColor = Color(0xFFE53935)
                        )

                        // Middle ring: Hydration
                        drawRing(
                            percent = rings.hydration.percent,
                            radius = size.minDimension / 2 - strokeWidth / 2 - padding,
                            strokeWidth = strokeWidth,
                            trackColor = Color(0xFF4FC3F7).copy(alpha = 0.25f),
                            fillColor = Color(0xFF0288D1)
                        )

                        // Inner ring: Movement
                        drawRing(
                            percent = rings.movement.percent,
                            radius = size.minDimension / 2 - strokeWidth / 2 - padding * 2,
                            strokeWidth = strokeWidth,
                            trackColor = Color(0xFF81C784).copy(alpha = 0.25f),
                            fillColor = Color(0xFF388E3C)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Ring progress breakdown
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RingLegendRow(
                        label = "Medications",
                        color = Color(0xFFE53935),
                        progress = rings.meds
                    )
                    RingLegendRow(
                        label = "Hydration",
                        color = Color(0xFF0288D1),
                        progress = rings.hydration
                    )
                    RingLegendRow(
                        label = "Movement",
                        color = Color(0xFF388E3C),
                        progress = rings.movement
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRing(
    percent: Int,
    radius: Float,
    strokeWidth: Float,
    trackColor: Color,
    fillColor: Color
) {
    val center = Offset(size.width / 2, size.height / 2)
    val sweepAngle = (percent.coerceIn(0, 100) / 100f) * 360f

    // Background track
    drawArc(
        color = trackColor,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = strokeWidth)
    )

    // Progress arc
    if (sweepAngle > 0f) {
        drawArc(
            color = fillColor,
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun RingLegendRow(
    label: String,
    color: Color,
    progress: RingProgress
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Text(
            text = if (progress.total == 0) "None due" else "${progress.onTime}/${progress.total} (${progress.percent}%)",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StreakCard(streak: ItemStreak, item: Item) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TypeBadge(item.type, size = 40.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Best: ${streak.longest} days" + if (streak.freezesUsed > 0) " · ${streak.freezesUsed} freeze used" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xFFF4511E).copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFF4511E), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${streak.current}",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFE64A19)
            )
        }
    }
}

private data class BadgeInfo(
    val title: String,
    val howTo: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)

private fun Badge.info(): BadgeInfo = when (this) {
    Badge.STREAK_7 -> BadgeInfo("One week", "7 days in a row", Icons.Default.LocalFireDepartment, Color(0xFFF4511E))
    Badge.STREAK_30 -> BadgeInfo("One month", "30 days in a row", Icons.Default.Whatshot, Color(0xFFE53935))
    Badge.STREAK_100 -> BadgeInfo("Hundred", "100 days in a row", Icons.Default.MilitaryTech, Color(0xFF8E24AA))
    Badge.HYDRATION_HERO -> BadgeInfo("Hydration hero", "All water on time, 7 days", Icons.Default.WaterDrop, Color(0xFF1E88E5))
    Badge.PERFECT_WEEK -> BadgeInfo("Perfect week", "Everything on time, 7 days", Icons.Default.Verified, Color(0xFF00897B))
    Badge.STEPS_10K -> BadgeInfo("10k steps", "10,000 steps in a day", Icons.Default.DirectionsWalk, Color(0xFF43A047))
}

@Composable
fun BadgesSection(earnedBadges: List<BadgeAward>) {
    val earned = earnedBadges.map { it.badge }.toSet()
    SectionCard(
        title = "Badges",
        icon = Icons.Default.MilitaryTech,
        action = {
            Text(
                text = "${earned.size} of ${Badge.entries.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    ) {
        Badge.entries.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { badge ->
                    BadgeTile(badge = badge, unlocked = badge in earned, modifier = Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: Badge, unlocked: Boolean, modifier: Modifier = Modifier) {
    val info = badge.info()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (unlocked) info.color.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainer)
            .semantics(mergeDescendants = true) {
                contentDescription = "${info.title}: " + if (unlocked) "earned" else "locked, ${info.howTo}"
            }
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (unlocked) info.color else MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (unlocked) info.icon else Icons.Default.Lock,
                contentDescription = null,
                tint = if (unlocked) Color.White else muted,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = info.title,
            style = MaterialTheme.typography.labelLarge,
            color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = info.howTo,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
fun ProgressScreenPreview() {
    val now = java.time.LocalDate.now()
    fun item(id: String, type: ItemType, name: String) = Item(
        id = id, profileId = "p1", type = type, name = name, dosageAmount = "1", dosageUnit = "tablet",
        homeZoneId = "UTC", startDate = "2026-01-01", createdAt = 0L, updatedAt = 0L
    )
    AllInOneReminderTheme {
        ProgressContent(
            state = ProgressUiState(
                totalPoints = 245,
                todayRings = DailyRings(
                    date = now,
                    meds = RingProgress(onTime = 2, total = 3, percent = 67),
                    hydration = RingProgress(onTime = 4, total = 8, percent = 50),
                    movement = RingProgress(onTime = 1, total = 1, percent = 100)
                ),
                streaks = listOf(
                    ItemStreak("i1", current = 6, longest = 12, freezesUsed = 0) to item("i1", ItemType.MEDICINE, "Metformin"),
                    ItemStreak("i2", current = 3, longest = 3, freezesUsed = 1) to item("i2", ItemType.WATER_DRINK, "Water")
                ),
                badges = listOf(BadgeAward(Badge.STREAK_7, "i1")),
                isLoading = false
            )
        )
    }
}
