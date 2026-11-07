package app.allinonereminder.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.allinonereminder.backup.BackupPreview
import app.allinonereminder.ui.theme.AllInOneReminderTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToProfiles: () -> Unit = {},
    onUpdateSnooze: (Int) -> Unit = {},
    onUpdateRingDuration: (Int) -> Unit = {},
    onUpdateSpeakReminders: (Boolean) -> Unit = {},
    onSeedData: () -> Unit = {},
    onCreateBackup: (Uri, String) -> Unit = { _, _ -> },
    onInspectRestore: (Uri, String) -> Unit = { _, _ -> },
    onConfirmRestore: (Uri, String) -> Unit = { _, _ -> },
    onDismissRestorePreview: () -> Unit = {},
    onClearMessages: () -> Unit = {}
) {
    var showBackupPassphraseDialog by remember { mutableStateOf(false) }
    var showRestorePassphraseDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPassphrase by remember { mutableStateOf("") }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && pendingPassphrase.isNotEmpty()) {
            onCreateBackup(uri, pendingPassphrase)
            pendingPassphrase = ""
        }
    }

    val openRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestorePassphraseDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
            // Success / Error Feedback Cards
            if (uiState.message != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClearMessages, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (uiState.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClearMessages, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Profiles & Family Members Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToProfiles),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Profiles & Family Members",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Add and manage patient profiles and colors",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            // Permission Diagnostics Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToPermissions),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Permission Health Check",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Verify alarms, notifications, Doze & OEM settings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Sounds & Alarm Timing
            Text("Alarm & Sound Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Default Snooze
                    Column {
                        Text("Default Snooze Duration", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 10, 15, 20).forEach { mins ->
                                FilterChip(
                                    selected = uiState.defaultSnoozeMinutes == mins,
                                    onClick = { onUpdateSnooze(mins) },
                                    label = { Text("${mins}m") }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Ring Duration Auto-silence
                    Column {
                        Text("Ring Timeout (Auto-silence)", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Alarm automatically silences after this duration if not dismissed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(60 to "1m", 120 to "2m", 300 to "5m", 600 to "10m").forEach { (sec, label) ->
                                FilterChip(
                                    selected = uiState.ringDurationSeconds == sec,
                                    onClick = { onUpdateRingDuration(sec) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Spoken Alarms Toggle (Opt-in)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Speak reminders aloud", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Speaks reminder aloud when alarm starts (e.g. 'Dad, Metformin 500, one tablet, after breakfast'). Note: medicine names will be spoken aloud.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = uiState.speakRemindersEnabled,
                            onCheckedChange = onUpdateSpeakReminders
                        )
                    }
                }
            }

            // Backup & Restore Section (§17 / M6)
            Text("Backup & Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Encrypted Local Backup (.airbackup)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Creates a single password-protected backup file containing all family profiles, schedules, medication photos, and history. Backups use AES-256-GCM encryption with PBKDF2 key derivation (310k iterations).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showBackupPassphraseDialog = true },
                            enabled = !uiState.isProcessing,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Backup")
                        }
                        OutlinedButton(
                            onClick = { openRestoreLauncher.launch(arrayOf("*/*")) },
                            enabled = !uiState.isProcessing,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore")
                        }
                    }
                    if (uiState.isProcessing) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Processing encrypted archive...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Developer / Debug Helpers
            Text("Debug Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = onSeedData,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Default.DataObject, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Seed Sample Multi-Profile Data")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Medical Disclaimer & About
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Medical Disclaimer", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "All-in-One Reminder is a self-management reminder tool and not a medical device. It does not provide medical advice or diagnosis. Always verify prescription details with your healthcare provider.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Version 0.1.0-alpha (Offline MVP Phase 1)", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    // --- Passphrase Dialog for Backup ---
    if (showBackupPassphraseDialog) {
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showBackupPassphraseDialog = false },
            title = { Text("Set Backup Passphrase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter a passphrase to encrypt your backup with AES-256-GCM. You will need this password to restore your data.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (errorMessage != null) {
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (password.length < 6) {
                            errorMessage = "Passphrase must be at least 6 characters"
                        } else if (password != confirmPassword) {
                            errorMessage = "Passphrases do not match"
                        } else {
                            showBackupPassphraseDialog = false
                            pendingPassphrase = password
                            val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault()).format(Instant.now())
                            createBackupLauncher.launch("allinone_backup_$timestamp.airbackup")
                        }
                    }
                ) {
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupPassphraseDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Passphrase Dialog for Restore ---
    if (showRestorePassphraseDialog && pendingRestoreUri != null) {
        var restorePassword by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = {
                showRestorePassphraseDialog = false
                pendingRestoreUri = null
            },
            title = { Text("Enter Backup Passphrase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter the passphrase used when creating this .airbackup archive to decrypt and inspect the dataset.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = restorePassword,
                        onValueChange = { restorePassword = it },
                        label = { Text("Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingRestoreUri
                        if (uri != null && restorePassword.isNotEmpty()) {
                            pendingPassphrase = restorePassword
                            showRestorePassphraseDialog = false
                            onInspectRestore(uri, restorePassword)
                        }
                    }
                ) {
                    Text("Inspect")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestorePassphraseDialog = false
                    pendingRestoreUri = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Restore Preview Dialog ---
    if (uiState.restorePreview != null && pendingRestoreUri != null) {
        val preview = uiState.restorePreview
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
        val exportedStr = formatter.format(Instant.ofEpochMilli(preview.exportedAt))

        AlertDialog(
            onDismissRequest = onDismissRestorePreview,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Restore & Replace Data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup Content Preview:", fontWeight = FontWeight.Bold)
                    Text("• Profiles: ${preview.profilesCount}")
                    Text("• Items / Medicines: ${preview.itemsCount}")
                    Text("• Schedules: ${preview.schedulesCount}")
                    Text("• Doses / History: ${preview.historyCount}")
                    Text("• Attached Photos: ${preview.photosCount}")
                    Text("• Created: $exportedStr", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "⚠️ CAUTION: Restoring will completely replace all current family profiles, medication schedules, and alarm history on this device with the backup data. This action cannot be undone.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingRestoreUri
                        if (uri != null) {
                            onConfirmRestore(uri, pendingPassphrase)
                            pendingRestoreUri = null
                            pendingPassphrase = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Restore & Replace")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDismissRestorePreview()
                    pendingRestoreUri = null
                    pendingPassphrase = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    AllInOneReminderTheme {
        SettingsScreen(
            uiState = SettingsUiState(
                defaultSnoozeMinutes = 10,
                ringDurationSeconds = 120,
                vibrationEnabled = true
            )
        )
    }
}
