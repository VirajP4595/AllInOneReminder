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
import kotlinx.coroutines.launch
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
                    app.allinonereminder.ui.components.ProfileAvatar(
                        name = prof.name,
                        colorArgb = prof.colorArgb,
                        photoPath = prof.photoPath,
                        size = 48.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = prof.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${profileWithDetails.itemCount} active reminders",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!prof.allergies.isNullOrBlank()) {
                            Text(
                                text = "Allergies: ${prof.allergies}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
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
    return app.allinonereminder.ui.components.AppTime.format(mod ?: 8 * 60)
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
    var birthYear by remember { mutableStateOf(profile.birthYear?.toString() ?: "") }
    var allergies by remember { mutableStateOf(profile.allergies ?: "") }
    var notes by remember { mutableStateOf(profile.notes ?: "") }
    var photoPath by remember { mutableStateOf(profile.photoPath) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val id = app.allinonereminder.ui.util.PhotoStorageHelper.saveAndCompressPhoto(context, uri)
                    photoPath = app.allinonereminder.ui.util.PhotoStorageHelper.getPhotoFile(context, id).absolutePath
                }
            }
        }
    }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    app.allinonereminder.ui.components.ProfileAvatar(
                        name = name.ifBlank { "?" },
                        colorArgb = selectedColor.toArgb(),
                        photoPath = photoPath,
                        size = 56.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        TextButton(onClick = {
                            photoPicker.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }) { Text(if (photoPath == null) "Add photo" else "Change photo") }
                        if (photoPath != null) {
                            TextButton(onClick = { photoPath = null }) { Text("Remove photo") }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = birthYear,
                    onValueChange = { v -> birthYear = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Birth year (optional)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = allergies,
                    onValueChange = { allergies = it.take(200) },
                    label = { Text("Allergies (optional)") },
                    placeholder = { Text("e.g. Penicillin") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(500) },
                    label = { Text("Notes for the doctor (optional)") },
                    minLines = 2,
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
                Text("Daily routine", fontWeight = FontWeight.Bold)
                Text(
                    "Reminders like \"after breakfast\" or \"at bed time\" use these times.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                var routineDayType by remember { mutableStateOf(DayType.WEEKDAY) }
                app.allinonereminder.ui.components.SegmentedChoice(
                    options = listOf(DayType.WEEKDAY to "Weekdays", DayType.WEEKEND to "Weekends"),
                    selected = routineDayType,
                    onSelect = { routineDayType = it }
                )
                MealType.entries.forEach { meal ->
                    val anchor = currentAnchors.firstOrNull { it.meal == meal && it.dayType == routineDayType }
                    val minuteOfDay = anchor?.minuteOfDay ?: defaultRoutineMinute(meal)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(routineLabel(meal), style = MaterialTheme.typography.bodyMedium)
                        app.allinonereminder.ui.components.TimeChip(
                            minuteOfDay = minuteOfDay,
                            onChange = { newMinute ->
                                currentAnchors = setAnchorMinute(
                                    currentAnchors, profile.id, meal, routineDayType, newMinute
                                )
                            },
                            contentDescription = "Change ${routineLabel(meal).lowercase()} time"
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val year = birthYear.toIntOrNull()?.takeIf { it in 1900..java.time.Year.now().value }
                    val updated = profile.copy(
                        name = name.trim(),
                        colorArgb = selectedColor.toArgb(),
                        photoPath = photoPath,
                        birthYear = year,
                        allergies = allergies.trim().ifEmpty { null },
                        notes = notes.trim().ifEmpty { null },
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

private fun routineLabel(meal: MealType): String = when (meal) {
    MealType.WAKE -> "Wake up"
    MealType.BREAKFAST -> "Breakfast"
    MealType.LUNCH -> "Lunch"
    MealType.SNACK -> "Snack"
    MealType.DINNER -> "Dinner"
    MealType.BED -> "Bed time"
}

/** Same defaults as new profiles get (DefaultMealAnchors). */
private fun defaultRoutineMinute(meal: MealType): Int = when (meal) {
    MealType.WAKE -> 7 * 60
    MealType.BREAKFAST -> 8 * 60 + 30
    MealType.LUNCH -> 13 * 60 + 30
    MealType.SNACK -> 17 * 60
    MealType.DINNER -> 20 * 60 + 30
    MealType.BED -> 22 * 60 + 30
}

/** Sets one routine time, adding the anchor if this profile doesn't have it yet. */
private fun setAnchorMinute(
    anchors: List<MealAnchor>,
    profileId: String,
    meal: MealType,
    dayType: DayType,
    minuteOfDay: Int
): List<MealAnchor> {
    val exists = anchors.any { it.meal == meal && it.dayType == dayType }
    return if (exists) {
        anchors.map { a -> if (a.meal == meal && a.dayType == dayType) a.copy(minuteOfDay = minuteOfDay) else a }
    } else {
        anchors + MealAnchor(
            id = java.util.UUID.randomUUID().toString(),
            profileId = profileId,
            dayType = dayType,
            meal = meal,
            minuteOfDay = minuteOfDay,
            updatedAt = System.currentTimeMillis()
        )
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
