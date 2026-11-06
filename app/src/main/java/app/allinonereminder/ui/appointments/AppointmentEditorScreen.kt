package app.allinonereminder.ui.appointments

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.allinonereminder.model.AppointmentStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentEditorScreen(
    viewModel: AppointmentEditorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.saveCompleted) {
        if (uiState.saveCompleted) {
            onBack()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addPendingAttachmentUri(uri)
        }
    }

    var customPrepText by remember { mutableStateOf("") }
    var customPrepMinutes by remember { mutableIntStateOf(60) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "Edit Appointment" else "New Appointment") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.saveAppointment() },
                        enabled = uiState.doctor.isNotBlank() && uiState.selectedProfileId.isNotBlank() && !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)
        ) {
            // Profile Selector
            item {
                Text("Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.profiles) { profile ->
                        FilterChip(
                            selected = uiState.selectedProfileId == profile.id,
                            onClick = { viewModel.onProfileSelected(profile.id) },
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

            // Doctor / Provider Name
            item {
                OutlinedTextField(
                    value = uiState.doctor,
                    onValueChange = { viewModel.onDoctorChange(it) },
                    label = { Text("Doctor / Specialist Name *") },
                    placeholder = { Text("e.g. Dr. Sarah Smith") },
                    leadingIcon = { Icon(Icons.Default.MedicalServices, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Location / Clinic
            item {
                OutlinedTextField(
                    value = uiState.place,
                    onValueChange = { viewModel.onPlaceChange(it) },
                    label = { Text("Clinic / Hospital / Location") },
                    placeholder = { Text("e.g. City Health Clinic, Room 302") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Purpose / Specialty
            item {
                OutlinedTextField(
                    value = uiState.purpose,
                    onValueChange = { viewModel.onPurposeChange(it) },
                    label = { Text("Purpose / Specialty") },
                    placeholder = { Text("e.g. Cardiology follow-up, Blood test") },
                    leadingIcon = { Icon(Icons.Default.Assignment, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Date & Time Picker Section
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Date & Time",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Date
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Date", style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewModel.onDateChange(uiState.date.minusDays(1)) }) {
                                    Icon(Icons.Default.Remove, contentDescription = "Previous Day")
                                }
                                val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
                                TextButton(onClick = {
                                    val d = uiState.date
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, dayOfMonth ->
                                            viewModel.onDateChange(LocalDate.of(y, m + 1, dayOfMonth))
                                        },
                                        d.year,
                                        d.monthValue - 1,
                                        d.dayOfMonth
                                    ).show()
                                }) {
                                    Text(
                                        text = uiState.date.format(dateFormatter),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                IconButton(onClick = { viewModel.onDateChange(uiState.date.plusDays(1)) }) {
                                    Icon(Icons.Default.Add, contentDescription = "Next Day")
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Time
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Time", style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    var totalMin = uiState.timeHour * 60 + uiState.timeMinute - 30
                                    if (totalMin < 0) totalMin += 1440
                                    viewModel.onTimeChange(totalMin / 60, totalMin % 60)
                                }) {
                                    Icon(Icons.Default.Remove, contentDescription = "-30m")
                                }

                                val ampm = if (uiState.timeHour >= 12) "PM" else "AM"
                                val hour12 = if (uiState.timeHour % 12 == 0) 12 else uiState.timeHour % 12
                                val formattedTime = String.format("%d:%02d %s", hour12, uiState.timeMinute, ampm)

                                TextButton(onClick = {
                                    TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            viewModel.onTimeChange(h, m)
                                        },
                                        uiState.timeHour,
                                        uiState.timeMinute,
                                        false
                                    ).show()
                                }) {
                                    Text(
                                        text = formattedTime,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }

                                IconButton(onClick = {
                                    val totalMin = (uiState.timeHour * 60 + uiState.timeMinute + 30) % 1440
                                    viewModel.onTimeChange(totalMin / 60, totalMin % 60)
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = "+30m")
                                }
                            }
                        }
                    }
                }
            }

            // Reminders Section
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Visit Reminders",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Alarms fire beforehand to remind you of the visit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val reminderOptions = listOf(
                            1440 to "1 day before",
                            120 to "2 hours before",
                            60 to "1 hour before",
                            30 to "30 min before",
                            15 to "15 min before"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            reminderOptions.take(3).forEach { (minutes, label) ->
                                FilterChip(
                                    selected = uiState.remindMinutesList.contains(minutes),
                                    onClick = { viewModel.toggleRemindMinute(minutes) },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            reminderOptions.drop(3).forEach { (minutes, label) ->
                                FilterChip(
                                    selected = uiState.remindMinutesList.contains(minutes),
                                    onClick = { viewModel.toggleRemindMinute(minutes) },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }
            }

            // Preparation Instructions & Alarms
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Preparation Instructions & Alerts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Fasting, reports, and medications needed before the appointment. An alarm will fire for each prep.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Presets
                        Text("Quick Add Suggestions:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        val prepPresets = listOf(
                            "Fast from 22:00 night before" to 600,
                            "Bring previous test reports" to 120,
                            "Drink 1L water 1 hour before" to 60,
                            "Wear loose comfortable clothing" to 60
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            prepPresets.forEach { (text, min) ->
                                val alreadyAdded = uiState.preps.any { it.text == text }
                                AssistChip(
                                    onClick = {
                                        if (!alreadyAdded) {
                                            viewModel.addPrep(text, min)
                                        }
                                    },
                                    label = { Text("+ $text (${formatMinutesBefore(min)})", fontSize = 12.sp) },
                                    enabled = !alreadyAdded
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        // Custom Prep Input
                        Text("Add Custom Prep Step:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = customPrepText,
                            onValueChange = { customPrepText = it },
                            label = { Text("Prep Instruction") },
                            placeholder = { Text("e.g. Stop taking aspirin 24h prior") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Reminder time before appointment:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        val timeOptions = listOf(
                            1440 to "24h prior",
                            720 to "12h prior",
                            120 to "2h prior",
                            60 to "1h prior",
                            30 to "30m prior"
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(timeOptions) { (min, label) ->
                                FilterChip(
                                    selected = customPrepMinutes == min,
                                    onClick = { customPrepMinutes = min },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                if (customPrepText.isNotBlank()) {
                                    viewModel.addPrep(customPrepText, customPrepMinutes)
                                    customPrepText = ""
                                }
                            },
                            enabled = customPrepText.isNotBlank(),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Instruction")
                        }

                        // Added Preps List
                        if (uiState.preps.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Scheduled Preps (${uiState.preps.size}):", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                uiState.preps.forEach { prep ->
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(prep.text, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                                Text(
                                                    "Alarm: ${formatMinutesBefore(prep.minutesBefore)} before visit",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(onClick = { viewModel.removePrep(prep.id) }) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Remove",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Prescriptions & Document Attachments
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Attachments (${uiState.existingAttachments.size + uiState.pendingAttachmentUris.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Button(onClick = { filePickerLauncher.launch("*/*") }) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Attach File")
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Prescriptions, lab reports, discharge summaries or doctor cards (PDF or Image).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Existing attachments
                        if (uiState.existingAttachments.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            uiState.existingAttachments.forEach { att ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            if (att.mimeType.contains("pdf")) Icons.Default.PictureAsPdf else Icons.Default.Image,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = att.filePath.substringAfterLast('/'),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteExistingAttachment(att) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        // Pending attachments
                        if (uiState.pendingAttachmentUris.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            uiState.pendingAttachmentUris.forEach { uri ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Selected: ${uri.lastPathSegment ?: "document"}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1
                                        )
                                    }
                                    IconButton(onClick = { viewModel.removePendingAttachmentUri(uri) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Notes and Prescriptions Notes
            item {
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = { viewModel.onNotesChange(it) },
                    label = { Text("Doctor Visit Notes / Summary (optional)") },
                    placeholder = { Text("Summary of symptoms or doctor advice...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.prescriptionNotes,
                    onValueChange = { viewModel.onPrescriptionNotesChange(it) },
                    label = { Text("Prescription & Follow-up Notes (optional)") },
                    placeholder = { Text("New medicines prescribed, follow-up after 2 weeks...") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Status Selector if in edit mode
            if (uiState.isEditMode) {
                item {
                    Text("Appointment Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppointmentStatus.entries.forEach { s ->
                            FilterChip(
                                selected = uiState.status == s,
                                onClick = { viewModel.onStatusChange(s) },
                                label = { Text(s.name) }
                            )
                        }
                    }
                }
            }

            // Save button
            item {
                Button(
                    onClick = { viewModel.saveAppointment() },
                    enabled = uiState.doctor.isNotBlank() && uiState.selectedProfileId.isNotBlank() && !uiState.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Save Appointment", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


