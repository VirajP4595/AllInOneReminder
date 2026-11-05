package app.allinonereminder.ui.items

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.allinonereminder.model.*
import app.allinonereminder.ui.theme.AllInOneReminderTheme
import app.allinonereminder.ui.util.PhotoStorageHelper
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    uiState: ItemDetailUiState,
    onEditClick: (String) -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var fullScreenPhotoId by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.itemDeleted) {
        if (uiState.itemDeleted) {
            onNavigateBack()
        }
    }

    val item = uiState.item

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.name ?: "Item Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (item != null) {
                        IconButton(onClick = { onEditClick(item.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Item")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Item", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (item == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main info card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (item.criticality == Criticality.CRITICAL) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "CRITICAL",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${item.dosageAmount} ${item.dosageUnit} ${item.form ?: ""}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val prof = uiState.profile
                        if (prof != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(prof.colorArgb))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Assigned to: ${prof.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        val instructions = item.instructions
                        if (!instructions.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Instructions",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(text = instructions, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Photos Gallery
            if (uiState.photos.isNotEmpty()) {
                item {
                    Text("Photos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.photos) { photo ->
                            val photoFile = File(photo.filePath)
                            if (photoFile.exists()) {
                                val bitmap = remember(photo.id) { BitmapFactory.decodeFile(photoFile.absolutePath) }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Medication photo",
                                        modifier = Modifier
                                            .size(96.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { fullScreenPhotoId = photo.id }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Schedule Summary Card (Plain English)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Schedule",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.scheduleSummaryText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Occurrence History (last 30)
            item {
                Text(
                    text = "Recent History (Last 30 doses)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.recentOccurrences.isEmpty()) {
                item {
                    Text(
                        text = "No history recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(uiState.recentOccurrences) { occ ->
                    OccurrenceHistoryRow(occ = occ)
                }
            }

            // Change Log Section
            if (uiState.changeLogs.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Audit Change Log",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(uiState.changeLogs) { log ->
                    ChangeLogRow(log = log)
                }
            }
        }
    }

    // Full screen photo viewer dialog
    val activePhotoId = fullScreenPhotoId
    if (activePhotoId != null) {
        val photoItem = uiState.photos.firstOrNull { it.id == activePhotoId }
        val photoFile = if (photoItem != null) File(photoItem.filePath) else null
        Dialog(
            onDismissRequest = { fullScreenPhotoId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { fullScreenPhotoId = null },
                contentAlignment = Alignment.Center
            ) {
                if (photoFile != null && photoFile.exists()) {
                    val bitmap = remember(activePhotoId) { BitmapFactory.decodeFile(photoFile.absolutePath) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Full-screen medication photo",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                IconButton(
                    onClick = { fullScreenPhotoId = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        val itemName = item?.name ?: "this reminder"
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Reminder?") },
            text = { Text("Are you sure you want to delete $itemName? All future scheduled alarms and history will be removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun OccurrenceHistoryRow(occ: Occurrence) {
    val planned = Instant.ofEpochMilli(occ.plannedAt).atZone(ZoneId.systemDefault())
    val formattedDate = planned.format(DateTimeFormatter.ofPattern("MMM dd, hh:mm a"))
    val actedAt = occ.actedAt

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = formattedDate, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (actedAt != null) {
                    val actedTime = Instant.ofEpochMilli(actedAt).atZone(ZoneId.systemDefault())
                    Text(
                        text = "Acted: ${actedTime.format(DateTimeFormatter.ofPattern("hh:mm a"))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                color = when (occ.status) {
                    OccurrenceStatus.TAKEN -> Color(0xFFE8F5E9)
                    OccurrenceStatus.MISSED -> Color(0xFFFFEBEE)
                    OccurrenceStatus.SKIPPED -> Color(0xFFEEEEEE)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = occ.status.name,
                    color = when (occ.status) {
                        OccurrenceStatus.TAKEN -> Color(0xFF2E7D32)
                        OccurrenceStatus.MISSED -> Color(0xFFC62828)
                        else -> Color(0xFF616161)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ChangeLogRow(log: ChangeLog) {
    val time = Instant.ofEpochMilli(log.changedAt).atZone(ZoneId.systemDefault())
    val formatted = time.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${log.field}: ${log.newValue ?: "(removed)"}".trim(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = formatted,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ItemDetailScreenPreview() {
    AllInOneReminderTheme {
        val sampleItem = Item(
            id = "1",
            profileId = "p-1",
            type = ItemType.MEDICINE,
            name = "Metformin",
            dosageAmount = "500",
            dosageUnit = "mg",
            criticality = Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            timePolicy = TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = "UTC",
            startDate = "2026-01-01",
            createdAt = 0L,
            updatedAt = 0L
        )

        ItemDetailScreen(
            uiState = ItemDetailUiState(
                item = sampleItem,
                profile = Profile(id = "p-1", name = "Mom", colorArgb = 0xFF43A047.toInt(), createdAt = 0L, updatedAt = 0L),
                scheduleSummaryText = "30 min before breakfast, every day",
                isLoading = false
            )
        )
    }
}
