package app.allinonereminder.ui.items

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.allinonereminder.model.*
import app.allinonereminder.ui.theme.AllInOneReminderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditorScreen(
    uiState: ItemEditorUiState,
    onNameChange: (String) -> Unit = {},
    onTypeChange: (ItemType) -> Unit = {},
    onProfileChange: (String) -> Unit = {},
    onDosageAmountChange: (String) -> Unit = {},
    onDosageUnitChange: (String) -> Unit = {},
    onFormChange: (String) -> Unit = {},
    onInstructionsChange: (String) -> Unit = {},
    onCriticalityChange: (Criticality) -> Unit = {},
    onExerciseMinutesChange: (Int) -> Unit = {},
    onTimePolicyChange: (TimePolicy) -> Unit = {},
    onGraceMinutesChange: (Int) -> Unit = {},
    onReRingMinutesChange: (Int) -> Unit = {},
    onMinGapMinutesChange: (Int) -> Unit = {},
    onAddSchedule: () -> Unit = {},
    onRemoveSchedule: (String) -> Unit = {},
    onUpdateSchedule: (String, (ScheduleDraft) -> ScheduleDraft) -> Unit = { _, _ -> },
    onPhotoSelected: (Uri) -> Unit = {},
    onRemovePhoto: (String) -> Unit = {},
    onSaveClick: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var showAdvanced by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { onPhotoSelected(it) }
    }

    LaunchedEffect(uiState.saveCompleted) {
        if (uiState.saveCompleted) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "Edit Item" else "New Reminder") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = onSaveClick,
                        enabled = uiState.name.isNotBlank() && uiState.selectedProfileId.isNotBlank() && !uiState.isSaving,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(if (uiState.isSaving) "Saving..." else "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile selector
            Text("Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.profiles) { prof ->
                    FilterChip(
                        selected = uiState.selectedProfileId == prof.id,
                        onClick = { onProfileChange(prof.id) },
                        label = { Text(prof.name) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(prof.colorArgb))
                            )
                        }
                    )
                }
            }

            // Item Type selector
            Text("Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ItemType.entries) { type ->
                    FilterChip(
                        selected = uiState.type == type,
                        onClick = { onTypeChange(type) },
                        label = { Text(type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (type) {
                                    ItemType.MEDICINE -> Icons.Default.Medication
                                    ItemType.INJECTION -> Icons.Default.Vaccines
                                    ItemType.WATER_DRINK -> Icons.Default.WaterDrop
                                    ItemType.FOOD -> Icons.Default.Restaurant
                                    ItemType.EXERCISE -> Icons.Default.FitnessCenter
                                    ItemType.APPOINTMENT -> Icons.Default.Event
                                    else -> Icons.Default.HealthAndSafety
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            // Name
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                placeholder = { Text("e.g. Metformin, Morning Walk, Dentist") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Dosage & Form
            if (uiState.type in listOf(ItemType.MEDICINE, ItemType.INJECTION, ItemType.WATER_DRINK, ItemType.FOOD, ItemType.SUPPLEMENT)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.dosageAmount,
                        onValueChange = onDosageAmountChange,
                        label = { Text("Amount") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.dosageUnit,
                        onValueChange = onDosageUnitChange,
                        label = { Text("Unit") },
                        placeholder = { Text("mg, ml, glasses") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.form,
                        onValueChange = onFormChange,
                        label = { Text("Form") },
                        placeholder = { Text("tablet, syrup") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            // Target minutes for exercise
            if (uiState.type == ItemType.EXERCISE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Target Exercise Duration", style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (uiState.exerciseMinutes > 5) onExerciseMinutesChange(uiState.exerciseMinutes - 5) }
                        ) {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease")
                        }
                        Text("${uiState.exerciseMinutes} min", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(
                            onClick = { onExerciseMinutesChange(uiState.exerciseMinutes + 5) }
                        ) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase")
                        }
                    }
                }
            }

            // Instructions
            OutlinedTextField(
                value = uiState.instructions,
                onValueChange = onInstructionsChange,
                label = { Text("Instructions (optional)") },
                placeholder = { Text("e.g. Take with a large glass of water") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            // Criticality
            Text("Criticality", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = uiState.criticality == Criticality.NORMAL,
                    onClick = { onCriticalityChange(Criticality.NORMAL) },
                    label = { Text("Normal Priority") }
                )
                FilterChip(
                    selected = uiState.criticality == Criticality.CRITICAL,
                    onClick = { onCriticalityChange(Criticality.CRITICAL) },
                    label = { Text("Critical (Must Not Miss)") }
                )
            }

            // Photos section
            Text("Photos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Photo")
                }

                Text(
                    text = "${uiState.photos.size} photo(s) attached",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Schedules Builder
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Schedules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onAddSchedule) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Schedule")
                }
            }

            uiState.schedules.forEachIndexed { index, draft ->
                ScheduleDraftCard(
                    index = index + 1,
                    draft = draft,
                    onUpdate = { update -> onUpdateSchedule(draft.id, update) },
                    onRemove = { onRemoveSchedule(draft.id) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Advanced Settings Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Advanced Timing & Guards",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Time Policy
                    Text("Timezone Policy", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.timePolicy == TimePolicy.LOCAL_WALL_CLOCK,
                            onClick = { onTimePolicyChange(TimePolicy.LOCAL_WALL_CLOCK) },
                            label = { Text("Local Wall Clock") }
                        )
                        FilterChip(
                            selected = uiState.timePolicy == TimePolicy.HOME_ZONE,
                            onClick = { onTimePolicyChange(TimePolicy.HOME_ZONE) },
                            label = { Text("Lock to Home Zone") }
                        )
                    }

                    // Grace Minutes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Grace Period", fontWeight = FontWeight.SemiBold)
                            Text("Mark missed after this window", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${uiState.graceMinutes} min", fontWeight = FontWeight.Bold)
                    }

                    // Double-dose Min Gap
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Double-Dose Guard Gap", fontWeight = FontWeight.SemiBold)
                            Text("Warn if taken closer than this interval", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${uiState.minGapMinutes} min (${uiState.minGapMinutes / 60}h)", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ScheduleDraftCard(
    index: Int,
    draft: ScheduleDraft,
    onUpdate: ((ScheduleDraft) -> ScheduleDraft) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Schedule #$index", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete schedule", tint = MaterialTheme.colorScheme.error)
                }
            }

            // Anchor Type selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.anchorType == ScheduleAnchorType.FIXED,
                    onClick = { onUpdate { it.copy(anchorType = ScheduleAnchorType.FIXED) } },
                    label = { Text("Fixed Time") }
                )
                FilterChip(
                    selected = draft.anchorType == ScheduleAnchorType.MEAL,
                    onClick = { onUpdate { it.copy(anchorType = ScheduleAnchorType.MEAL) } },
                    label = { Text("Meal Relative") }
                )
            }

            if (draft.anchorType == ScheduleAnchorType.FIXED) {
                val hour = draft.minuteOfDay / 60
                val min = draft.minuteOfDay % 60
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Time of Day", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            val newMod = (draft.minuteOfDay - 30 + 1440) % 1440
                            onUpdate { it.copy(minuteOfDay = newMod) }
                        }) {
                            Icon(Icons.Default.Remove, contentDescription = "-30m")
                        }
                        val ampm = if (hour >= 12) "PM" else "AM"
                        val hour12 = if (hour % 12 == 0) 12 else hour % 12
                        Text(
                            text = String.format("%d:%02d %s", hour12, min, ampm),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(onClick = {
                            val newMod = (draft.minuteOfDay + 30) % 1440
                            onUpdate { it.copy(minuteOfDay = newMod) }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "+30m")
                        }
                    }
                }
            } else {
                // Meal Relative options
                Text("Meal & Timing", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MealType.entries) { m ->
                        FilterChip(
                            selected = draft.meal == m,
                            onClick = { onUpdate { it.copy(meal = m) } },
                            label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealRelation.entries.forEach { r ->
                        FilterChip(
                            selected = draft.relation == r,
                            onClick = { onUpdate { it.copy(relation = r) } },
                            label = { Text(r.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }

            // Recurrence
            Text("Recurrence", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf(RecurrenceType.DAILY, RecurrenceType.WEEKDAYS, RecurrenceType.EVERY_N_DAYS, RecurrenceType.ONCE)) { rec ->
                    FilterChip(
                        selected = draft.recurrence == rec,
                        onClick = { onUpdate { it.copy(recurrence = rec) } },
                        label = {
                            Text(when (rec) {
                                RecurrenceType.DAILY -> "Daily"
                                RecurrenceType.WEEKDAYS -> "Weekdays"
                                RecurrenceType.EVERY_N_DAYS -> "Every N Days"
                                RecurrenceType.EVERY_N_WEEKS -> "Every N Weeks"
                                RecurrenceType.ONCE -> "Once"
                            })
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ItemEditorScreenPreview() {
    AllInOneReminderTheme {
        ItemEditorScreen(
            uiState = ItemEditorUiState(
                profiles = listOf(Profile(id = "1", name = "Mom", colorArgb = 0xFF1E88E5.toInt(), createdAt = 0L, updatedAt = 0L)),
                selectedProfileId = "1",
                name = "Aspirin",
                dosageAmount = "100",
                dosageUnit = "mg"
            )
        )
    }
}
