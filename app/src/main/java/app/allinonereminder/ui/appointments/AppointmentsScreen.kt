package app.allinonereminder.ui.appointments

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.allinonereminder.model.AppointmentStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentsScreen(
    viewModel: AppointmentsViewModel,
    onAddClick: () -> Unit,
    onAppointmentClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Visits", fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Appointment")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Profile Filter Row if more than 1 profile exists
            if (state.profiles.size > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedProfileId == null,
                            onClick = { viewModel.selectProfile(null) },
                            label = { Text("All") }
                        )
                    }
                    items(state.profiles, key = { it.id }) { profile ->
                        FilterChip(
                            selected = state.selectedProfileId == profile.id,
                            onClick = { viewModel.selectProfile(profile.id) },
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

            // Tabs: Upcoming vs Past
            TabRow(selectedTabIndex = state.selectedTab) {
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Upcoming (${state.upcoming.size})") }
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("Past (${state.past.size})") }
                )
            }

            val currentList = if (state.selectedTab == 0) state.upcoming else state.past

            if (currentList.isEmpty() && !state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (state.selectedTab == 0) "No upcoming appointments" else "No past appointments",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (state.selectedTab == 0) "Tap + to schedule a doctor visit with prep reminders" else "Completed or cancelled visits will appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(currentList, key = { it.appointment.id }) { item ->
                        AppointmentCard(
                            uiModel = item,
                            onClick = { onAppointmentClick(item.appointment.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppointmentCard(
    uiModel: AppointmentUiModel,
    onClick: () -> Unit
) {
    val appt = uiModel.appointment
    val zdt = Instant.ofEpochMilli(appt.startAt).atZone(ZoneId.systemDefault())
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    val timeFormatter = app.allinonereminder.ui.components.AppTime.formatter()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = appt.doctor,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                StatusBadge(status = appt.status)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Date & Time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${zdt.format(dateFormatter)} at ${zdt.format(timeFormatter)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Place
            val place = appt.place
            if (!place.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = place,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Purpose
            val purpose = appt.purpose
            if (!purpose.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = purpose,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Badges / Tags row
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiModel.profile != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                color = Color(uiModel.profile.colorArgb).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(uiModel.profile.colorArgb))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = uiModel.profile.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(uiModel.profile.colorArgb)
                        )
                    }
                }

                if (uiModel.prepCount > 0) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text("${uiModel.prepCount} prep${if (uiModel.prepCount > 1) "s" else ""}", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }

                if (uiModel.attachmentCount > 0) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text("${uiModel.attachmentCount} file${if (uiModel.attachmentCount > 1) "s" else ""}", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: AppointmentStatus) {
    val (bgColor, textColor, text) = when (status) {
        AppointmentStatus.UPCOMING -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "Upcoming")
        AppointmentStatus.DONE -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Done")
        AppointmentStatus.CANCELLED -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Cancelled")
    }

    Box(
        modifier = Modifier
            .background(bgColor, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

