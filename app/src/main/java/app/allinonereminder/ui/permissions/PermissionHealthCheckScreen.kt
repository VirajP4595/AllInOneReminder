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
    onTestAlarmSent: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(PermissionChecker.checkAll(context)) }
    val (oemTitle, oemText) = remember { PermissionChecker.getOemAutostartInstructions() }

    LaunchedEffect(Unit) {
        permissions = PermissionChecker.checkAll(context)
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
                    text = "Permission Health Check",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ensure all critical permissions are green so reminders ring reliably on a locked phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    }
                }
            }

            // Action Buttons
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                            val triggerTime = System.currentTimeMillis() + 60_000L // 1 minute
                            val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                                action = AlarmCoordinator.ACTION_ALARM_FIRE
                                putExtra(AlarmCoordinator.EXTRA_OCCURRENCE_IDS, arrayOf("test-alarm-id"))
                            }
                            val pendingIntent = PendingIntent.getBroadcast(
                                context,
                                9999,
                                alarmIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            val showIntent = Intent(context, MainActivity::class.java)
                            val showPendingIntent = PendingIntent.getActivity(
                                context,
                                9998,
                                showIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            alarmManager.setAlarmClock(
                                AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent),
                                pendingIntent
                            )
                            onTestAlarmSent?.invoke("Test alarm scheduled in 1 minute. Lock your screen to test!")
                        },
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Fix")
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

