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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel,
    onNavigateToHistory: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Progress & Habits", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "History Calendar")
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
                // Profile Selector Chips
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

                // Total Points Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Total Health Points",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${state.totalPoints} pts",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Earned by taking doses and habits on time",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Today's Activity Rings Card
                state.todayRings?.let { rings ->
                    item {
                        TodayRingsCard(rings = rings)
                    }
                }

                // Link to History / Calendar Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToHistory)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "Adherence Calendar",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "View month-by-month on-time history",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }

                // Streaks Section
                item {
                    Text(
                        text = "Current Streaks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (state.streaks.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "No active streaks yet. Take your medications and habits on schedule to build streaks!",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(state.streaks) { (streak, item) ->
                        StreakCard(streak = streak, item = item)
                    }
                }

                // Badges Section
                item {
                    Text(
                        text = "Badges & Achievements",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    BadgesSection(earnedBadges = state.badges)
                }
            }
        }
    }
}

@Composable
fun TodayRingsCard(rings: DailyRings) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "${item.dosageAmount} ${item.dosageUnit} • ${item.type.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔥 ${streak.current} d", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFE65100))
                    Text("current", style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏆 ${streak.longest} d", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("longest", style = MaterialTheme.typography.labelSmall)
                }
                if (streak.freezesUsed > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("❄️ ${streak.freezesUsed}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0288D1))
                        Text("frozen", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun BadgesSection(earnedBadges: List<BadgeAward>) {
    val earnedSet = earnedBadges.map { it.badge }.toSet()
    val allBadges = listOf(
        Badge.STREAK_7 to ("7-Day Streak" to "Maintained a streak for 7 consecutive days"),
        Badge.STREAK_30 to ("30-Day Master" to "Maintained a streak for 30 consecutive days"),
        Badge.STREAK_100 to ("Century Club" to "Maintained a streak for 100 consecutive days"),
        Badge.HYDRATION_HERO to ("Hydration Hero" to "Met hydration goal for 7 consecutive days"),
        Badge.PERFECT_WEEK to ("Perfect Week" to "100% on-time across all habits for 7 days"),
        Badge.STEPS_10K to ("10k Steps" to "Logged 10,000 steps in a single day")
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        allBadges.forEach { (badge, info) ->
            val unlocked = earnedSet.contains(badge)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (unlocked) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (unlocked) Color(0xFFFFD54F) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (badge) {
                                Badge.STREAK_7 -> "🔥"
                                Badge.STREAK_30 -> "⭐"
                                Badge.STREAK_100 -> "👑"
                                Badge.HYDRATION_HERO -> "💧"
                                Badge.PERFECT_WEEK -> "🎯"
                                Badge.STEPS_10K -> "👟"
                            },
                            fontSize = 20.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = info.first,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = info.second,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (unlocked) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("Unlocked")
                        }
                    } else {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

