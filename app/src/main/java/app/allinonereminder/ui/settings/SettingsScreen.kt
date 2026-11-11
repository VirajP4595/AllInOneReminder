package app.allinonereminder.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.allinonereminder.health.HealthAvailability
import app.allinonereminder.health.HealthConnectActivitySource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import app.allinonereminder.data.repository.ThemeMode
import app.allinonereminder.ui.components.*
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
    onSetThemeMode: (String) -> Unit = {},
    onUpdateSnooze: (Int) -> Unit = {},
    onUpdateRingDuration: (Int) -> Unit = {},
    onUpdateSpeakReminders: (Boolean) -> Unit = {},
    onUpdateVibration: (Boolean) -> Unit = {},
    onSetSound: (String, String) -> Unit = { _, _ -> },
    onRefreshHealthStatus: () -> Unit = {},
    onSetHealthVerification: (Boolean) -> Unit = {},
    onHealthPermissionResult: (Set<String>) -> Unit = {},
    onSeedData: () -> Unit = {},
    onCreateBackup: (Uri, String) -> Unit = { _, _ -> },
    onEnableAutoBackup: (Uri, String) -> Unit = { _, _ -> },
    onDisableAutoBackup: () -> Unit = {},
    onBackUpNow: () -> Unit = {},
    onInspectRestore: (Uri, String) -> Unit = { _, _ -> },
    onConfirmRestore: (Uri, String) -> Unit = { _, _ -> },
    onDismissRestorePreview: () -> Unit = {},
    onClearMessages: () -> Unit = {}
) {
    var showBackupPassphraseDialog by remember { mutableStateOf(false) }
    var showRestorePassphraseDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPassphrase by remember { mutableStateOf("") }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefreshHealthStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted -> onHealthPermissionResult(granted) }

    var pickingSoundFor by remember { mutableStateOf<String?>(null) }
    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val level = pickingSoundFor
        pickingSoundFor = null
        if (level != null && result.resultCode == android.app.Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val picked = result.data?.getParcelableExtra<Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            onSetSound(level, picked?.toString() ?: "default")
        }
    }
    fun pickSound(level: String, current: String) {
        val isAlarm = level != "GENTLE"
        val intent = android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(
                android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,
                if (isAlarm) android.media.RingtoneManager.TYPE_ALARM else android.media.RingtoneManager.TYPE_NOTIFICATION
            )
            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Sound for ${soundLevelLabel(level).lowercase()} reminders")
            val existing = current.takeIf { it.contains("://") }?.let { Uri.parse(it) }
            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
        }
        pickingSoundFor = level
        runCatching { ringtoneLauncher.launch(intent) }.onFailure { pickingSoundFor = null }
    }

    var showAutoBackupDialog by remember { mutableStateOf(false) }
    var autoBackupPassphrase by remember { mutableStateOf("") }
    val autoBackupFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null && autoBackupPassphrase.isNotEmpty()) onEnableAutoBackup(uri, autoBackupPassphrase)
        autoBackupPassphrase = ""
    }

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
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
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
                        IconButton(onClick = onClearMessages) {
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
                        IconButton(onClick = onClearMessages) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            SettingsNavCard(
                icon = Icons.Default.People,
                title = "Profiles & family members",
                subtitle = "Add and manage profiles and colours",
                onClick = onNavigateToProfiles
            )

            SettingsNavCard(
                icon = Icons.Default.HealthAndSafety,
                title = "Permission health check",
                subtitle = "Verify alarms, notifications and battery settings",
                onClick = onNavigateToPermissions
            )

            SectionCard(title = "Appearance", icon = Icons.Default.DarkMode) {
                Text(
                    text = "Theme",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Dark mode is easier on the eyes at night. \"Phone\" follows your phone's setting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                SegmentedChoice(
                    options = listOf(
                        ThemeMode.SYSTEM to "Phone",
                        ThemeMode.LIGHT to "Light",
                        ThemeMode.DARK to "Dark"
                    ),
                    selected = uiState.themeMode,
                    onSelect = onSetThemeMode
                )
            }

            // Sounds & Alarm Timing
            Text("Alarm & Sound Preferences", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Default Snooze
                    Column {
                        Text("Default Snooze Duration", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SNOOZE_CHOICES.forEach { mins ->
                                ChoiceChip(
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
                                ChoiceChip(
                                    selected = uiState.ringDurationSeconds == sec,
                                    onClick = { onUpdateRingDuration(sec) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Vibration
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = uiState.vibrationEnabled,
                                role = androidx.compose.ui.semantics.Role.Switch,
                                onValueChange = onUpdateVibration
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Vibrate when an alarm rings", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Switch(checked = uiState.vibrationEnabled, onCheckedChange = null)
                    }

                    HorizontalDivider()

                    // Sounds per importance level
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Alarm sounds", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Each item's importance (in the item editor) picks which sound it uses.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        listOf(
                            "CRITICAL" to uiState.soundCritical,
                            "NORMAL" to uiState.soundNormal,
                            "GENTLE" to uiState.soundGentle
                        ).forEach { (level, value) ->
                            val title = remember(value) { soundTitle(context, value) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClickLabel = "Change sound") { pickSound(level, value) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (level == "GENTLE") Icons.Default.NotificationsNone else Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(soundLevelLabel(level), style = MaterialTheme.typography.bodyLarge)
                                    Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }

                    HorizontalDivider()

                    // Spoken Alarms Toggle (Opt-in)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = uiState.speakRemindersEnabled,
                                role = androidx.compose.ui.semantics.Role.Switch,
                                onValueChange = onUpdateSpeakReminders
                            ),
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
                            onCheckedChange = null
                        )
                    }

                    HorizontalDivider()

                    // Health Connect verification (opt-in, read-only, on-device)
                    val healthReady = uiState.healthAvailability == HealthAvailability.AVAILABLE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Verify exercise with Health Connect", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = when (uiState.healthAvailability) {
                                    HealthAvailability.AVAILABLE ->
                                        "If your phone already recorded a walk or workout of the target length, the exercise reminder is ticked off instead of ringing. Read-only; nothing leaves this phone."
                                    HealthAvailability.NOT_INSTALLED ->
                                        "Health Connect is not installed on this phone. Install it to use this. Reminders work as normal without it."
                                    HealthAvailability.UPDATE_REQUIRED ->
                                        "Health Connect needs an update before it can be used. Reminders work as normal without it."
                                    HealthAvailability.UNSUPPORTED ->
                                        "Health Connect is not available on this phone. Reminders work as normal without it."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (healthReady && uiState.healthConnectVerifyEnabled.not() && !uiState.healthPermissionGranted) {
                                Text(
                                    text = "Turning this on asks for permission to read exercise and steps.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (uiState.healthAvailability == HealthAvailability.NOT_INSTALLED ||
                                uiState.healthAvailability == HealthAvailability.UPDATE_REQUIRED
                            ) {
                                TextButton(onClick = {
                                    val pkg = HealthConnectActivitySource.PROVIDER_PACKAGE
                                    try {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    } catch (e: ActivityNotFoundException) {
                                        try {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        } catch (_: ActivityNotFoundException) {
                                        }
                                    }
                                }) {
                                    Text(if (uiState.healthAvailability == HealthAvailability.NOT_INSTALLED) "Install Health Connect" else "Update Health Connect")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = uiState.healthConnectVerifyEnabled && healthReady,
                            enabled = healthReady,
                            onCheckedChange = { wantOn ->
                                if (!wantOn) {
                                    onSetHealthVerification(false)
                                } else if (uiState.healthPermissionGranted) {
                                    onSetHealthVerification(true)
                                } else {
                                    healthPermissionLauncher.launch(HealthConnectActivitySource.PERMISSIONS)
                                }
                            }
                        )
                    }
                }
            }

            // Backup & Restore Section (§17 / M6)
            Text("Backup & Restore", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            // Automatic weekly backup (issue #27)
            SectionCard(title = "Automatic weekly backup", icon = Icons.Default.CloudSync) {
                val auto = uiState.autoBackup
                if (auto?.enabled == true) {
                    Text("Saving to: ${auto.folderName ?: "chosen folder"}", style = MaterialTheme.typography.bodyMedium)
                    val last = auto.lastSuccessAt?.let {
                        val time = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())
                        time.format(java.time.format.DateTimeFormatter.ofPattern("d MMM")) + ", " +
                            time.format(app.allinonereminder.ui.components.AppTime.formatter())
                    }
                    Text(
                        text = if (last != null) "Last backup: $last" else "No backup yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (auto.lastError != null) {
                        Text(
                            text = "Problem: ${auto.lastError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onBackUpNow) { Text("Back up now") }
                        TextButton(onClick = onDisableAutoBackup) { Text("Turn off") }
                    }
                } else {
                    Text(
                        text = "Once a week the app saves an encrypted backup to a folder you choose (phone storage, SD card or Google Drive). The 4 newest are kept. Without a backup, a lost or reset phone loses everything.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showAutoBackupDialog = true }) { Text("Set up") }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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

            // Developer helpers: only in debuggable (debug) builds, never in the release APK.
            val isDebuggable = remember(context) {
                (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            }
            if (isDebuggable) {
                Text("Debug Tools", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
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
                    val versionName = remember(context) {
                        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                            .getOrNull() ?: "?"
                    }
                    Text("Version $versionName", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    if (showAutoBackupDialog) {
        var pass by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        val tooShort = pass.length < 8
        val mismatch = confirm.isNotEmpty() && pass != confirm
        AlertDialog(
            onDismissRequest = { showAutoBackupDialog = false },
            title = { Text("Backup passphrase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Backups are encrypted with this passphrase. You'll need it to restore, so write it down. " +
                            "So backups can run by themselves, it is stored on this phone, locked by the phone's secure key store.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    AppTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Passphrase (8+ characters)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("Repeat passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        isError = mismatch,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (mismatch) Text("The passphrases don't match", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    enabled = !tooShort && pass == confirm,
                    onClick = {
                        autoBackupPassphrase = pass
                        showAutoBackupDialog = false
                        autoBackupFolderLauncher.launch(null)
                    }
                ) { Text("Choose folder") }
            },
            dismissButton = { TextButton(onClick = { showAutoBackupDialog = false }) { Text("Cancel") } }
        )
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
                    AppTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppTextField(
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
                        if (password.length < 8) {
                            errorMessage = "Use at least 8 characters. A short phrase of words is best."
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
                    AppTextField(
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

private fun soundLevelLabel(level: String): String = when (level) {
    "CRITICAL" -> "Must not miss"
    "GENTLE" -> "Gentle"
    else -> "Normal"
}

/** Human title of a stored sound value ("Phone default" for the default or an unreadable URI). */
private fun soundTitle(context: android.content.Context, value: String): String {
    if (!value.contains("://")) return "Phone default"
    return runCatching {
        android.media.RingtoneManager.getRingtone(context, Uri.parse(value))?.getTitle(context)
    }.getOrNull() ?: "Phone default"
}
