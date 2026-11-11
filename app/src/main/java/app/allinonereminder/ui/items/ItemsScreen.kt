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
import app.allinonereminder.ui.components.*
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
                title = {
                    Column {
                        Text("My reminders", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = when (uiState.items.size) {
                                0 -> "Nothing set up yet"
                                1 -> "1 reminder"
                                else -> "${uiState.items.size} reminders"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddItemClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add reminder") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.profiles.size > 1) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            ChoiceChip(
                                selected = uiState.selectedProfileId == null,
                                onClick = { onProfileSelected(null) },
                                label = { Text("Everyone") }
                            )
                        }
                        items(uiState.profiles) { profile ->
                            ChoiceChip(
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
                }
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        ChoiceChip(
                            selected = uiState.selectedTypeFilter == null,
                            onClick = { onTypeFilterSelected(null) },
                            label = { Text("All") }
                        )
                    }
                    items(ItemType.entries.filter { it != ItemType.APPOINTMENT }) { type ->
                        ChoiceChip(
                            selected = uiState.selectedTypeFilter == type,
                            onClick = { onTypeFilterSelected(type) },
                            label = { Text(type.label()) },
                            leadingIcon = {
                                Icon(
                                    type.icon(),
                                    contentDescription = null,
                                    tint = type.accent(),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }

            if (uiState.items.isEmpty()) {
                item { ItemsEmptyState(onAddItemClick) }
            } else {
                items(uiState.items, key = { it.item.id }) { itemUi ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ItemCard(
                            itemUi = itemUi,
                            showProfile = uiState.selectedProfileId == null && uiState.profiles.size > 1,
                            onClick = { onItemClick(itemUi.item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemsEmptyState(onAddItemClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeBadge(ItemType.MEDICINE, size = 56.dp)
            TypeBadge(ItemType.WATER_DRINK, size = 56.dp)
            TypeBadge(ItemType.EXERCISE, size = 56.dp)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("Let's set up your first reminder", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Medicines, water, walks, meals or anything you want to remember. Each one you keep up earns points.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onAddItemClick) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add reminder")
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
            .clickable(onClickLabel = "Open ${item.name}", onClick = onClick),
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
            TypeBadge(item.type, size = 48.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.criticality == Criticality.CRITICAL) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.PriorityHigh,
                            contentDescription = "Must not miss",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                val dose = doseLabel(null, item)
                if (dose.isNotBlank()) {
                    Text(
                        text = dose,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (itemUi.scheduleSummaryText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = itemUi.scheduleSummaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                if (itemUi.isLowStock && itemUi.stockStatusText != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Refill soon · ${itemUi.stockStatusText}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }

                if (showProfile && itemUi.profile != null) {
                    Spacer(modifier = Modifier.height(6.dp))
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
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
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
