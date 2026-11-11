package app.allinonereminder.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.allinonereminder.model.DayType
import app.allinonereminder.model.MealAnchor
import app.allinonereminder.model.MealType
import app.allinonereminder.model.Profile
import app.allinonereminder.ui.onboarding.PRESET_COLORS
import app.allinonereminder.ui.theme.AllInOneReminderTheme
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    uiState: ProfilesUiState,
    onSaveProfile: (Profile, List<MealAnchor>) -> Unit = { _, _ -> },
    onDeleteProfile: (String) -> Unit = {}
) {
    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    var editingAnchors by remember { mutableStateOf<List<MealAnchor>>(emptyList()) }
    var isNewProfile by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family Profiles & Meal Times", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val newId = UUID.randomUUID().toString()
                    editingProfile = Profile(
                        id = newId,
                        name = "",
                        colorArgb = 0xFF1E88E5.toInt(),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    editingAnchors = createDefaultAnchors(newId)
                    isNewProfile = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Profile")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Profiles allow managing medications for yourself and family members. Each profile has individual meal schedules for meal-anchored doses.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(uiState.profiles, key = { it.profile.id }) { item ->
                ProfileCard(
                    profileWithDetails = item,
                    canDelete = uiState.profiles.size > 1,
                    onEdit = {
                        editingProfile = item.profile
                        editingAnchors = if (item.mealAnchors.isNotEmpty()) item.mealAnchors else createDefaultAnchors(item.profile.id)
                        isNewProfile = false
                    },
                    onDelete = { onDeleteProfile(item.profile.id) }
                )
            }
        }
    }

    // Profile Editor Dialog
    val activeEditProfile = editingProfile
    if (activeEditProfile != null) {
        ProfileEditDialog(
            profile = activeEditProfile,
            anchors = editingAnchors,
            isNew = isNewProfile,
            onDismiss = { editingProfile = null },
            onSave = { updatedProf, updatedAnchors ->
                onSaveProfile(updatedProf, updatedAnchors)
                editingProfile = null
            }
        )
    }
}

@Composable
private fun ProfileCard(
    profileWithDetails: ProfileWithDetails,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val prof = profileWithDetails.profile
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(prof.colorArgb)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = prof.name.take(1).uppercase(),
                            color = app.allinonereminder.ui.components.readableTextColor(Color(prof.colorArgb)),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = prof.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${profileWithDetails.itemCount} active reminders",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit profile")
                    }
                    if (canDelete) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete profile", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Meal Times Summary
            Text("Meal Schedule (Weekday / Weekend):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            val anchors = profileWithDetails.mealAnchors
            listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER).forEach { meal ->
                val weekday = anchors.firstOrNull { it.meal == meal && it.dayType == DayType.WEEKDAY }
                val weekend = anchors.firstOrNull { it.meal == meal && it.dayType == DayType.WEEKEND }

                val weekdayStr = formatAnchorTime(weekday?.minuteOfDay)
                val weekendStr = formatAnchorTime(weekend?.minuteOfDay)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(meal.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
                    Text("$weekdayStr (weekday) | $weekendStr (weekend)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun formatAnchorTime(mod: Int?): String {
    if (mod == null) return "08:00 AM"
    val h = mod / 60
    val m = mod % 60
    val ampm = if (h >= 12) "PM" else "AM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return String.format("%d:%02d %s", h12, m, ampm)
}

private fun createDefaultAnchors(profileId: String): List<MealAnchor> {
    val now = System.currentTimeMillis()
    return listOf(
        MealAnchor(UUID.randomUUID().toString(), profileId, DayType.WEEKDAY, MealType.BREAKFAST, 8 * 60, now),
        MealAnchor(UUID.randomUUID().toString(), profileId, DayType.WEEKEND, MealType.BREAKFAST, 9 * 60, now),
        MealAnchor(UUID.randomUUID().toString(), profileId, DayType.WEEKDAY, MealType.LUNCH, 13 * 60, now),
        MealAnchor(UUID.randomUUID().toString(), profileId, DayType.WEEKEND, MealType.LUNCH, 13 * 60 + 30, now),
        MealAnchor(UUID.randomUUID().toString(), profileId, DayType.WEEKDAY, MealType.DINNER, 19 * 60, now),
        MealAnchor(UUID.randomUUID().toString(), profileId, DayType.WEEKEND, MealType.DINNER, 20 * 60, now)
    )
}

@Composable
private fun ProfileEditDialog(
    profile: Profile,
    anchors: List<MealAnchor>,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Profile, List<MealAnchor>) -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var selectedColor by remember { mutableStateOf(Color(profile.colorArgb)) }
    var currentAnchors by remember { mutableStateOf(anchors) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add Profile" else "Edit Profile") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Avatar Color", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PRESET_COLORS.take(5).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = color }
                                .border(
                                    width = if (color == selectedColor) 3.dp else 0.dp,
                                    color = if (color == selectedColor) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("Meal Anchor Times (Hour)", fontWeight = FontWeight.Bold)

                listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER).forEach { meal ->
                    Text(meal.name.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    listOf(DayType.WEEKDAY, DayType.WEEKEND).forEach { dayType ->
                        val anchor = currentAnchors.firstOrNull { it.meal == meal && it.dayType == dayType }
                        val currentHour = (anchor?.minuteOfDay ?: 480) / 60

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(dayType.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 13.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    if (currentHour > 0) {
                                        val newHour = currentHour - 1
                                        currentAnchors = updateAnchorHour(currentAnchors, meal, dayType, newHour)
                                    }
                                }) {
                                    Icon(Icons.Default.Remove, contentDescription = "-1h")
                                }
                                Text(String.format("%02d:00", currentHour), fontWeight = FontWeight.Bold)
                                IconButton(onClick = {
                                    if (currentHour < 23) {
                                        val newHour = currentHour + 1
                                        currentAnchors = updateAnchorHour(currentAnchors, meal, dayType, newHour)
                                    }
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = "+1h")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = profile.copy(
                        name = name.trim(),
                        colorArgb = selectedColor.toArgb(),
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(updated, currentAnchors)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun updateAnchorHour(
    anchors: List<MealAnchor>,
    meal: MealType,
    dayType: DayType,
    newHour: Int
): List<MealAnchor> {
    return anchors.map { a ->
        if (a.meal == meal && a.dayType == dayType) {
            a.copy(minuteOfDay = newHour * 60)
        } else a
    }
}

@Preview(showBackground = true)
@Composable
fun ProfilesScreenPreview() {
    AllInOneReminderTheme {
        ProfilesScreen(
            uiState = ProfilesUiState(
                profiles = listOf(
                    ProfileWithDetails(
                        profile = Profile(id = "1", name = "Mom", colorArgb = 0xFF43A047.toInt(), createdAt = 0L, updatedAt = 0L),
                        itemCount = 3,
                        mealAnchors = emptyList()
                    )
                )
            )
        )
    }
}
