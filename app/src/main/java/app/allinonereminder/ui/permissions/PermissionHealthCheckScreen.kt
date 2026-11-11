package app.allinonereminder.ui.permissions

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.allinonereminder.MainActivity
import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.alarm.AlarmReceiver

@Composable
fun PermissionHealthCheckScreen(
    onSeedSampleData: (() -> Unit)? = null,
    onSendTestAlarm: (() -> Unit)? = null,
    nextAlarmAt: Long? = null,
    onRescheduleNow: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(PermissionChecker.checkAll(context)) }
    val (oemTitle, oemText) = remember { PermissionChecker.getOemAutostartInstructions() }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissions = PermissionChecker.checkAll(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Alarm setup",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                val ready = permissions.count { it.isGranted }
                Text(
                    text = if (ready == permissions.size) "All set. Reminders will ring reliably." else "$ready of ${permissions.size} ready",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (ready == permissions.size) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { if (permissions.isEmpty()) 0f else ready / permissions.size.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .height(8.dp)
                        .clip(CircleShape),
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                Text(
                    text = "Turn these on so reminders ring on time, even on a locked phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Alarm self-check: is the next reminder really registered with the system?
            item {
                val status = remember(nextAlarmAt, permissions) { AlarmSelfCheck.check(context, nextAlarmAt) }
                AlarmSelfCheckCard(status = status, onRescheduleNow = onRescheduleNow)
            }

            // Permission items
            items(permissions) { item ->
                PermissionItemCard(item = item, onFix = {
                    item.fixIntent?.let { intent ->
                        context.startActivity(intent)
                    }
                })
            }

            // Direct boot warning card (§11 & §16)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Reboot Notice (Direct Boot)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Health data is encrypted at rest. After rebooting your phone, alarms resume only after the first unlock.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // OEM Autostart Guide card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Autostart Guide: $oemTitle",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = oemText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.FilledTonalButton(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(android.net.Uri.fromParts("package", context.packageName, null))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: android.content.ActivityNotFoundException) {
                                }
                            }
                        ) {
                            Text("Open app settings")
                        }
                    }
                }
            }

            // Action Buttons
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onSendTestAlarm?.invoke() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Send Test Alarm in 1 Minute")
                    }

                    if (onSeedSampleData != null) {
                        OutlinedButton(
                            onClick = onSeedSampleData,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Seed Sample Data")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionItemCard(item: PermissionItem, onFix: () -> Unit) {
    val statusColor = if (item.isGranted) Color(0xFF2E7D32) else Color(0xFFC62828)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(statusColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isGranted) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = if (item.isGranted) "Granted" else "Missing",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    text = item.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!item.isGranted && item.fixIntent != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onFix,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Turn on")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PermissionHealthCheckScreenPreview() {
    MaterialTheme {
        PermissionHealthCheckScreen()
    }
}

/** Result of comparing the reminder we expect to ring next with what the system has registered. */
enum class AlarmSelfCheckStatus { NOTHING_DUE, REGISTERED, NOT_REGISTERED }

object AlarmSelfCheck {
    /**
     * The system only reports the earliest alarm clock of any app. If that is at or before our
     * expected time, ours may be hidden behind another app's alarm, which still counts as fine.
     */
    fun check(context: android.content.Context, expectedAt: Long?): AlarmSelfCheckStatus {
        if (expectedAt == null) return AlarmSelfCheckStatus.NOTHING_DUE
        val am = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val next = runCatching { am.nextAlarmClock }.getOrNull() ?: return AlarmSelfCheckStatus.NOT_REGISTERED
        return if (next.triggerTime <= expectedAt + 60_000L) AlarmSelfCheckStatus.REGISTERED
        else AlarmSelfCheckStatus.NOT_REGISTERED
    }
}

@Composable
private fun AlarmSelfCheckCard(status: AlarmSelfCheckStatus, onRescheduleNow: (() -> Unit)?) {
    val ok = status != AlarmSelfCheckStatus.NOT_REGISTERED
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when (status) {
                        AlarmSelfCheckStatus.NOTHING_DUE -> "No reminders waiting"
                        AlarmSelfCheckStatus.REGISTERED -> "Next alarm is set"
                        AlarmSelfCheckStatus.NOT_REGISTERED -> "Next alarm is not set"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (ok) "The phone has the next reminder's alarm." else "The phone dropped the next reminder. Tap Fix to set it again.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!ok && onRescheduleNow != null) {
                TextButton(onClick = onRescheduleNow) { Text("Fix") }
            }
        }
    }
}
