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
import app.allinonereminder.ui.components.*
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
    onMaxPerDayChange: (String) -> Unit = {},
    onCurrentStockChange: (String) -> Unit = {},
    onUnitsPerDoseChange: (String) -> Unit = {},
    onRefillThresholdChange: (String) -> Unit = {},
    onExpiryDateChange: (String?) -> Unit = {},
    onCategoryChange: (String) -> Unit = {},
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
            SectionCard(title = "Basics", icon = Icons.Default.Info) {
            // Profile selector
            Text("Who is it for?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.profiles) { prof ->
                    ChoiceChip(
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
            Text("What is it?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ItemType.entries) { type ->
                    ChoiceChip(
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
            AppTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                placeholder = { Text("e.g. Metformin, Morning Walk, Dentist") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            }

            SectionCard(title = "Dose & details", icon = Icons.Default.Medication) {
            // Dosage & Form
            if (uiState.type in listOf(ItemType.MEDICINE, ItemType.INJECTION, ItemType.WATER_DRINK, ItemType.FOOD, ItemType.SUPPLEMENT)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppTextField(
                        value = uiState.dosageAmount,
                        onValueChange = onDosageAmountChange,
                        label = { Text("Amount") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OptionDropdownField(
                        value = uiState.dosageUnit,
                        onValueChange = onDosageUnitChange,
                        // Units used before (including custom ones) come back as choices (#90).
                        options = (DoseOptions.units(uiState.type) + uiState.knownUnits).distinct(),
                        label = "Unit",
                        modifier = Modifier.weight(1.4f)
                    )
                }
                if (DoseOptions.forms(uiState.type).isNotEmpty()) {
                    OptionDropdownField(
                        value = uiState.form,
                        onValueChange = onFormChange,
                        options = DoseOptions.forms(uiState.type),
                        label = "Form",
                        modifier = Modifier.fillMaxWidth()
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
                    Text("Target duration", style = MaterialTheme.typography.bodyLarge)
                    StepperPill(
                        valueText = "${uiState.exerciseMinutes} min",
                        onMinus = { if (uiState.exerciseMinutes > 5) onExerciseMinutesChange(uiState.exerciseMinutes - 5) },
                        onPlus = { onExerciseMinutesChange(uiState.exerciseMinutes + 5) },
                        minusDescription = "Decrease duration",
                        plusDescription = "Increase duration",
                        minusEnabled = uiState.exerciseMinutes > 5
                    )
                }
            }

            // Instructions
            AppTextField(
                value = uiState.instructions,
                onValueChange = onInstructionsChange,
                label = { Text("Instructions (optional)") },
                placeholder = { Text("e.g. Take with a large glass of water") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            // Own grouping, e.g. "Heart" or "Vitamins" (#90)
            AppTextField(
                value = uiState.category,
                onValueChange = onCategoryChange,
                label = { Text("Category (optional)") },
                placeholder = { Text("e.g. Heart, Vitamins") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (uiState.knownCategories.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(uiState.knownCategories) { c ->
                        ChoiceChip(
                            selected = uiState.category.equals(c, ignoreCase = true),
                            onClick = { onCategoryChange(c) },
                            label = { Text(c) }
                        )
                    }
                }
            }
            }

            // Stock & Refill Tracking
            if (uiState.type in listOf(ItemType.MEDICINE, ItemType.INJECTION, ItemType.SUPPLEMENT, ItemType.WATER_DRINK)) SectionCard(
                title = "Stock & refills",
                icon = Icons.Default.Inventory2
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppTextField(
                        value = uiState.currentStock,
                        onValueChange = onCurrentStockChange,
                        label = { Text("Current Stock") },
                        placeholder = { Text("e.g. 30") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    AppTextField(
                        value = uiState.unitsPerDose,
                        onValueChange = onUnitsPerDoseChange,
                        label = { Text("Units / Dose") },
                        placeholder = { Text("1.0") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    AppTextField(
                        value = uiState.refillThreshold,
                        onValueChange = onRefillThresholdChange,
                        label = { Text("Refill Alert At") },
                        placeholder = { Text("e.g. 5") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                // Expiry date: Today and the item page warn 30 days before (#87)
                app.allinonereminder.ui.components.DateChip(
                    label = "Expiry date",
                    isoDate = uiState.expiryDate,
                    onChange = onExpiryDateChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Criticality
            SectionCard(title = "How important is it?", icon = Icons.Default.NotificationsActive) {
                SegmentedChoice(
                    options = listOf(
                        Criticality.NORMAL to "Normal",
                        Criticality.CRITICAL to "Must not miss"
                    ),
                    selected = uiState.criticality,
                    onSelect = onCriticalityChange
                )
                Text(
                    text = if (uiState.criticality == Criticality.CRITICAL) {
                        "Rings loudly and keeps reminding until you act."
                    } else {
                        "A normal reminder with the usual repeat."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(title = "Photos", icon = Icons.Default.PhotoCamera) {
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
            }

            // Schedules Builder
            SectionCard(
                title = "Schedule",
                icon = Icons.Default.Schedule,
                action = {
                    TextButton(onClick = onAddSchedule) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            ) {
                uiState.schedules.forEachIndexed { index, draft ->
                    ScheduleDraftCard(
                        index = index + 1,
                        draft = draft,
                        onUpdate = { update -> onUpdateSchedule(draft.id, update) },
                        onRemove = { onRemoveSchedule(draft.id) }
                    )
                }

                if (uiState.schedules.any { it.recurrence == RecurrenceType.PRN }) {
                    AppTextField(
                        value = uiState.maxPerDay?.toString() ?: "",
                        onValueChange = onMaxPerDayChange,
                        label = { Text("Max doses per day (as needed)") },
                        placeholder = { Text("e.g. 3") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

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
                    text = "More timing options",
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
                    Text("When travelling, ring by", fontWeight = FontWeight.SemiBold)
                    SegmentedChoice(
                        options = listOf(
                            TimePolicy.LOCAL_WALL_CLOCK to "Local time",
                            TimePolicy.HOME_ZONE to "Home time zone"
                        ),
                        selected = uiState.timePolicy,
                        onSelect = onTimePolicyChange
                    )

                    TimingChoice(
                        title = "Ring again every",
                        subtitle = "If nobody answers the alarm",
                        options = listOf(2, 5, 10, 15),
                        selected = uiState.reRingMinutes,
                        label = ::minutesLabel,
                        onSelect = onReRingMinutesChange
                    )
                    TimingChoice(
                        title = "Count as missed after",
                        subtitle = "How long after the time a dose still counts",
                        options = listOf(30, 60, 120, 240),
                        selected = uiState.graceMinutes,
                        label = ::minutesLabel,
                        onSelect = onGraceMinutesChange
                    )
                    TimingChoice(
                        title = "Minimum time between doses",
                        subtitle = "Warns you if a dose is taken sooner than this",
                        options = listOf(60, 120, 240, 480),
                        selected = uiState.minGapMinutes,
                        label = ::minutesLabel,
                        onSelect = onMinGapMinutesChange
                    )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Schedule #$index", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete schedule", tint = MaterialTheme.colorScheme.error)
                }
            }

            // Anchor Type selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(
                    selected = draft.anchorType == ScheduleAnchorType.FIXED,
                    onClick = { onUpdate { it.copy(anchorType = ScheduleAnchorType.FIXED) } },
                    label = { Text("Fixed Time") }
                )
                ChoiceChip(
                    selected = draft.anchorType == ScheduleAnchorType.MEAL,
                    onClick = { onUpdate { it.copy(anchorType = ScheduleAnchorType.MEAL) } },
                    label = { Text("Meal Relative") }
                )
            }

            if (draft.anchorType == ScheduleAnchorType.FIXED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Time of day", style = MaterialTheme.typography.bodyMedium)
                    app.allinonereminder.ui.components.TimeChip(
                        minuteOfDay = draft.minuteOfDay,
                        onChange = { newMod -> onUpdate { it.copy(minuteOfDay = newMod) } },
                        contentDescription = "Change reminder time"
                    )
                }
            } else {
                // Meal Relative options
                Text("Meal & Timing", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MealType.entries) { m ->
                        ChoiceChip(
                            selected = draft.meal == m,
                            onClick = { onUpdate { it.copy(meal = m) } },
                            label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealRelation.entries.forEach { r ->
                        ChoiceChip(
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
                item {
                    ChoiceChip(
                        selected = draft.recurrence == RecurrenceType.DAILY,
                        onClick = { onUpdate { it.copy(recurrence = RecurrenceType.DAILY) } },
                        label = { Text("Daily") }
                    )
                }
                item {
                    ChoiceChip(
                        selected = draft.recurrence == RecurrenceType.EVERY_N_WEEKS && (draft.intervalN ?: 1) == 1,
                        onClick = {
                            onUpdate {
                                it.copy(
                                    recurrence = RecurrenceType.EVERY_N_WEEKS,
                                    intervalN = 1,
                                    weekdayMask = it.weekdayMask?.takeIf { m -> m != 0 } ?: todayWeekdayBit()
                                )
                            }
                        },
                        label = { Text("Specific days") }
                    )
                }
                item {
                    ChoiceChip(
                        selected = draft.recurrence == RecurrenceType.EVERY_N_DAYS,
                        onClick = {
                            onUpdate {
                                it.copy(
                                    recurrence = RecurrenceType.EVERY_N_DAYS,
                                    intervalN = it.intervalN?.coerceAtLeast(1) ?: 1
                                )
                            }
                        },
                        label = { Text("Every N days") }
                    )
                }
                item {
                    ChoiceChip(
                        selected = draft.recurrence == RecurrenceType.EVERY_N_WEEKS && (draft.intervalN ?: 1) != 1,
                        onClick = {
                            onUpdate {
                                it.copy(
                                    recurrence = RecurrenceType.EVERY_N_WEEKS,
                                    intervalN = if ((it.intervalN ?: 1) <= 1) 2 else it.intervalN,
                                    weekdayMask = it.weekdayMask?.takeIf { m -> m != 0 } ?: todayWeekdayBit()
                                )
                            }
                        },
                        label = { Text("Every N weeks") }
                    )
                }
                item {
                    ChoiceChip(
                        selected = draft.recurrence == RecurrenceType.MONTHLY,
                        onClick = { onUpdate { it.copy(recurrence = RecurrenceType.MONTHLY) } },
                        label = { Text("Monthly") }
                    )
                }
                item {
                    ChoiceChip(
                        selected = draft.recurrence == RecurrenceType.INTERVAL,
                        onClick = { onUpdate { it.copy(recurrence = RecurrenceType.INTERVAL) } },
                        label = { Text("Every N hours") }
                    )
                }
                item {
                    ChoiceChip(
                        selected = draft.recurrence == RecurrenceType.PRN,
                        onClick = { onUpdate { it.copy(recurrence = RecurrenceType.PRN) } },
                        label = { Text("As needed") }
                    )
                }
                item {
                    ChoiceChip(
                        selected = draft.recurrence == RecurrenceType.ONCE,
                        onClick = { onUpdate { it.copy(recurrence = RecurrenceType.ONCE) } },
                        label = { Text("Once") }
                    )
                }
            }

            when (draft.recurrence) {
                RecurrenceType.EVERY_N_DAYS -> {
                    NumberStepper(
                        label = "Repeat every",
                        value = draft.intervalN ?: 1,
                        suffix = if ((draft.intervalN ?: 1) == 1) "day" else "days",
                        min = 1,
                        onChange = { value -> onUpdate { it.copy(intervalN = value) } }
                    )
                }
                RecurrenceType.EVERY_N_WEEKS -> {
                    NumberStepper(
                        label = "Repeat every",
                        value = draft.intervalN ?: 1,
                        suffix = if ((draft.intervalN ?: 1) == 1) "week" else "weeks",
                        min = 1,
                        onChange = { value -> onUpdate { it.copy(intervalN = value) } }
                    )
                    WeekdayChips(
                        mask = draft.weekdayMask?.takeIf { it != 0 } ?: todayWeekdayBit(),
                        onToggle = { bit ->
                            onUpdate {
                                val current = it.weekdayMask?.takeIf { m -> m != 0 } ?: todayWeekdayBit()
                                val next = current xor bit
                                // Keep at least one day selected.
                                it.copy(weekdayMask = if (next == 0) current else next)
                            }
                        }
                    )
                }
                RecurrenceType.MONTHLY -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChoiceChip(
                            selected = !draft.monthlyByWeekday,
                            onClick = { onUpdate { it.copy(monthlyByWeekday = false) } },
                            label = { Text("On a day") }
                        )
                        ChoiceChip(
                            selected = draft.monthlyByWeekday,
                            onClick = {
                                onUpdate {
                                    it.copy(
                                        monthlyByWeekday = true,
                                        weekdayMask = it.weekdayMask?.takeIf { mask -> mask != 0 } ?: 2
                                    )
                                }
                            },
                            label = { Text("On the Nth weekday") }
                        )
                    }
                    if (draft.monthlyByWeekday) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", 5 to "Last").forEach { (week, label) ->
                                ChoiceChip(
                                    selected = draft.nthWeek == week,
                                    onClick = { onUpdate { it.copy(nthWeek = week) } },
                                    label = { Text(label) }
                                )
                            }
                        }
                        WeekdayChips(
                            mask = draft.weekdayMask?.takeIf { it != 0 } ?: 2,
                            singleSelect = true,
                            onToggle = { bit -> onUpdate { it.copy(weekdayMask = bit) } }
                        )
                    } else {
                        NumberStepper(
                            label = "Day of the month",
                            value = draft.dayOfMonth,
                            min = 1,
                            max = 31,
                            onChange = { value -> onUpdate { it.copy(dayOfMonth = value) } }
                        )
                        Text(
                            text = "31st is treated as the last day in shorter months.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                RecurrenceType.INTERVAL -> {
                    NumberStepper(
                        label = "Hours between doses",
                        value = draft.intervalHours,
                        suffix = "h",
                        min = 1,
                        max = 24,
                        onChange = { value -> onUpdate { it.copy(intervalHours = value) } }
                    )
                    MinuteOfDayStepper(
                        label = "First dose",
                        value = draft.windowStartMinute,
                        onChange = { value -> onUpdate { it.copy(windowStartMinute = value) } }
                    )
                    MinuteOfDayStepper(
                        label = "Last dose",
                        value = draft.windowEndMinute,
                        onChange = { value -> onUpdate { it.copy(windowEndMinute = value) } }
                    )
                }
                RecurrenceType.PRN -> {
                    Text(
                        text = "No fixed times. Use Take now in the Today screen when needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RecurrenceType.ONCE -> {
                    Text(
                        text = "Rings once on the item start date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> Unit
            }

            if (draft.recurrence == RecurrenceType.DAILY) {
                // On/off cycle, e.g. 21 days on, 7 days off, counted from the item's start date (#86)
                var cycleOn by remember(draft.id) { mutableStateOf(draft.cycleOnDays?.toString() ?: "") }
                var cycleOff by remember(draft.id) { mutableStateOf(draft.cycleOffDays?.toString() ?: "") }
                Text("On/off cycle (optional)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppTextField(
                        value = cycleOn,
                        onValueChange = { v ->
                            cycleOn = v.filter { it.isDigit() }.take(3)
                            onUpdate { it.copy(cycleOnDays = cycleOn.toIntOrNull()) }
                        },
                        label = { Text("Days on") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    AppTextField(
                        value = cycleOff,
                        onValueChange = { v ->
                            cycleOff = v.filter { it.isDigit() }.take(3)
                            onUpdate { it.copy(cycleOffDays = cycleOff.toIntOrNull()) }
                        },
                        label = { Text("Days off") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (draft.anchorType == ScheduleAnchorType.FIXED && draft.recurrence != RecurrenceType.INTERVAL) {
                // Weekend shift: e.g. an hour later on weekends (#86)
                Text("On weekends", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Same time", 30 to "+30 min", 60 to "+1 h", 120 to "+2 h").forEach { (shift, label) ->
                        ChoiceChip(
                            selected = (draft.weekendShiftMinutes ?: 0) == shift,
                            onClick = { onUpdate { it.copy(weekendShiftMinutes = shift) } },
                            label = { Text(label) }
                        )
                    }
                }
            }

            Text("Only between dates (optional)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                app.allinonereminder.ui.components.DateChip(
                    label = "From",
                    isoDate = draft.validFrom,
                    onChange = { value -> onUpdate { it.copy(validFrom = value) } },
                    modifier = Modifier.weight(1f)
                )
                app.allinonereminder.ui.components.DateChip(
                    label = "Until",
                    isoDate = draft.validUntil,
                    onChange = { value -> onUpdate { it.copy(validUntil = value) } },
                    modifier = Modifier.weight(1f)
                )
            }
            AppTextField(
                value = draft.doseAmountOverride ?: "",
                onValueChange = { value -> onUpdate { it.copy(doseAmountOverride = value.ifBlank { null }) } },
                label = { Text("Dose for this schedule (optional)") },
                placeholder = { Text("e.g. 10 mg") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun NumberStepper(
    label: String,
    value: Int,
    min: Int = 1,
    max: Int = 99,
    suffix: String = "",
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        StepperPill(
            valueText = if (suffix.isEmpty()) "$value" else "$value $suffix",
            onMinus = { onChange((value - 1).coerceAtLeast(min)) },
            onPlus = { onChange((value + 1).coerceAtMost(max)) },
            minusDescription = "Decrease $label",
            plusDescription = "Increase $label",
            minusEnabled = value > min,
            plusEnabled = value < max
        )
    }
}

@Composable
private fun MinuteOfDayStepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        TimeChip(
            minuteOfDay = value,
            onChange = onChange,
            contentDescription = "Change $label"
        )
    }
}

private fun todayWeekdayBit(): Int = 1 shl (java.time.LocalDate.now().dayOfWeek.value - 1)

@Composable
private fun WeekdayChips(
    mask: Int,
    singleSelect: Boolean = false,
    onToggle: (Int) -> Unit
) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    val names = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    // Seven equal cells so every day fits on any screen width (no horizontal scrolling).
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            val bit = 1 shl index
            val selected = (mask and bit) != 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clickable(
                        onClickLabel = names[index],
                        role = androidx.compose.ui.semantics.Role.Checkbox
                    ) { onToggle(bit) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    if (!singleSelect) {
        Text(
            text = "Pick the days this reminder repeats.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

/** A labelled row of chips for one timing option; a non-standard current value is kept as a chip. */
@Composable
private fun TimingChoice(
    title: String,
    subtitle: String,
    options: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val all = (options + selected).distinct().sorted()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            all.forEach { value ->
                app.allinonereminder.ui.components.ChoiceChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(label(value)) }
                )
            }
        }
    }
}

private fun minutesLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}
