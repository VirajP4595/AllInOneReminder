package app.allinonereminder.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.allinonereminder.model.OccurrenceStatus
import app.allinonereminder.model.doseLabel
import app.allinonereminder.ui.components.AppTextField
import app.allinonereminder.ui.components.AppTime
import app.allinonereminder.ui.components.ProfileAvatar
import java.time.Instant
import java.time.ZoneId

/**
 * Simple mode (#79): one person, large text, only what is due, one huge "Taken" button each.
 * No points, filters or settings. A caregiver PIN is needed to leave.
 */
@Composable
fun SimpleModeScreen(
    uiState: TodayUiState,
    onTake: (OccurrenceUiModel) -> Unit,
    onExit: (pin: String) -> Unit,
    exitError: Boolean
) {
    var showExit by remember { mutableStateOf(false) }
    val open = listOf(OccurrenceStatus.PENDING, OccurrenceStatus.RINGING, OccurrenceStatus.SNOOZED, OccurrenceStatus.DEFERRED_MEAL)
    val due = uiState.occurrences.filter { it.occurrence.status in open }.sortedBy { it.occurrence.dueAt }
    val doneCount = uiState.occurrences.count { it.occurrence.status == OccurrenceStatus.TAKEN }
    val person = uiState.profiles.firstOrNull { it.id == uiState.selectedProfileId }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (person != null) {
                        ProfileAvatar(person.name, person.colorArgb, person.photoPath, size = 72.dp)
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            person?.name ?: "Today",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            if (due.isEmpty()) "All done for now" else "$doneCount done · ${due.size} to go",
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showExit = true }) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Exit")
                    }
                }
            }
            if (due.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text(
                            "Nothing to take right now.",
                            fontSize = 26.sp,
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            items(due, key = { it.occurrence.id }) { occ ->
                val time = Instant.ofEpochMilli(occ.occurrence.dueAt).atZone(ZoneId.systemDefault()).format(AppTime.formatter())
                val late = occ.occurrence.dueAt < System.currentTimeMillis()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (late) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(occ.item.name, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text(doseLabel(occ.schedule, occ.item), fontSize = 24.sp)
                        Text(if (late) "Was due at $time" else "At $time", fontSize = 22.sp)
                        occ.item.instructions?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 20.sp) }
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { onTake(occ) },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 80.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Taken", fontSize = 28.sp)
                        }
                    }
                }
            }
        }
    }

    if (showExit) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showExit = false },
            title = { Text("Leave simple mode") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the caregiver PIN.")
                    AppTextField(
                        value = pin,
                        onValueChange = { v -> pin = v.filter { it.isDigit() }.take(8) },
                        label = { Text("PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = exitError
                    )
                    if (exitError) Text("Wrong PIN", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { Button(onClick = { onExit(pin) }, enabled = pin.length >= 4) { Text("Unlock") } },
            dismissButton = { TextButton(onClick = { showExit = false }) { Text("Cancel") } }
        )
    }
}
