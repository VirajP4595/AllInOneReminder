package app.allinonereminder.ui.items

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.allinonereminder.model.*
import app.allinonereminder.ui.theme.AllInOneReminderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemsScreen(
    uiState: ItemsUiState,
    onProfileSelected: (String?) -> Unit = {},
    onTypeFilterSelected: (ItemType?) -> Unit = {},
    onAddItemClick: () -> Unit = {},
    onItemClick: (Item) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health Reminders & Items", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddItemClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Profile Filter Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedProfileId == null,
                        onClick = { onProfileSelected(null) },
                        label = { Text("All Profiles") }
                    )
                }
                items(uiState.profiles) { profile ->
                    FilterChip(
                        selected = uiState.selectedProfileId == profile.id,
                        onClick = { onProfileSelected(profile.id) },
                        label = { Text(profile.name) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(profile.colorArgb))
                            )
                        }
                    )
                }
            }

            // Type Filter Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedTypeFilter == null,
                        onClick = { onTypeFilterSelected(null) },
                        label = { Text("All Types") }
                    )
                }
                items(ItemType.entries) { type ->
                    FilterChip(
                        selected = uiState.selectedTypeFilter == type,
                        onClick = { onTypeFilterSelected(type) },
                        label = { Text(type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Items List
            if (uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MedicalServices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No items found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap the + button below to add your first medication, injection, water, exercise, or appointment.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.items, key = { it.item.id }) { itemUi ->
                        ItemCard(
                            itemUi = itemUi,
                            showProfile = uiState.selectedProfileId == null,
                            onClick = { onItemClick(itemUi.item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemCard(
    itemUi: ItemWithSchedulesUiModel,
    showProfile: Boolean,
    onClick: () -> Unit
) {
    val item = itemUi.item
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (item.type) {
                        ItemType.MEDICINE -> Icons.Default.Medication
                        ItemType.INJECTION -> Icons.Default.Vaccines
                        ItemType.WATER_DRINK -> Icons.Default.WaterDrop
                        ItemType.FOOD -> Icons.Default.Restaurant
                        ItemType.EXERCISE -> Icons.Default.FitnessCenter
                        ItemType.APPOINTMENT -> Icons.Default.Event
                        else -> Icons.Default.HealthAndSafety
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${item.dosageAmount} ${item.dosageUnit} ${item.form ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = itemUi.scheduleSummaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                if (itemUi.isLowStock && itemUi.stockStatusText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Low stock: ${itemUi.stockStatusText}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (showProfile && itemUi.profile != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(itemUi.profile.colorArgb))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = itemUi.profile.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View Details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ItemsScreenPreview() {
    AllInOneReminderTheme {
        val sampleProfile = Profile(id = "p-1", name = "Mom", colorArgb = 0xFF43A047.toInt(), createdAt = 0L, updatedAt = 0L)
        val sampleItem = Item(
            id = "i-1",
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

        ItemsScreen(
            uiState = ItemsUiState(
                profiles = listOf(sampleProfile),
                items = listOf(
                    ItemWithSchedulesUiModel(
                        item = sampleItem,
                        profile = sampleProfile,
                        schedules = emptyList(),
                        scheduleSummaryText = "at 08:00 AM, Every day; with dinner, Every day"
                    )
                )
            )
        )
    }
}
