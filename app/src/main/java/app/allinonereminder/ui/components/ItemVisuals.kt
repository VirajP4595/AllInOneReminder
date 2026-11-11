package app.allinonereminder.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.allinonereminder.model.ItemType

/** One icon per item type, used everywhere an item is shown. */
fun ItemType.icon(): ImageVector = when (this) {
    ItemType.MEDICINE -> Icons.Default.Medication
    ItemType.INJECTION -> Icons.Default.Vaccines
    ItemType.WATER_DRINK -> Icons.Default.WaterDrop
    ItemType.FOOD -> Icons.Default.Restaurant
    ItemType.SUPPLEMENT -> Icons.Default.Spa
    ItemType.EXERCISE -> Icons.Default.FitnessCenter
    ItemType.APPOINTMENT -> Icons.Default.Event
    ItemType.LAB_TEST -> Icons.Default.Science
    ItemType.HABIT -> Icons.Default.SelfImprovement
    ItemType.CUSTOM -> Icons.Default.HealthAndSafety
}

/** One accent colour per item type, so lists are easy to scan. Readable in light and dark. */
fun ItemType.accent(): Color = when (this) {
    ItemType.MEDICINE -> Color(0xFF00897B)
    ItemType.INJECTION -> Color(0xFF7E57C2)
    ItemType.WATER_DRINK -> Color(0xFF1E88E5)
    ItemType.FOOD -> Color(0xFF43A047)
    ItemType.SUPPLEMENT -> Color(0xFFC0A000)
    ItemType.EXERCISE -> Color(0xFFF4511E)
    ItemType.APPOINTMENT -> Color(0xFF5C6BC0)
    ItemType.LAB_TEST -> Color(0xFF8D6E63)
    ItemType.HABIT -> Color(0xFFEC407A)
    ItemType.CUSTOM -> Color(0xFF607D8B)
}

/** Friendly plural label for filters ("Water & drinks", not "Water drink"). */
fun ItemType.label(): String = when (this) {
    ItemType.MEDICINE -> "Medicines"
    ItemType.INJECTION -> "Injections"
    ItemType.WATER_DRINK -> "Water & drinks"
    ItemType.FOOD -> "Meals"
    ItemType.SUPPLEMENT -> "Supplements"
    ItemType.EXERCISE -> "Exercise"
    ItemType.APPOINTMENT -> "Visits"
    ItemType.LAB_TEST -> "Lab tests"
    ItemType.HABIT -> "Habits"
    ItemType.CUSTOM -> "Other"
}

/** Singular label for pickers ("Water", "Medicine"). */
fun ItemType.singular(): String = when (this) {
    ItemType.MEDICINE -> "Medicine"
    ItemType.INJECTION -> "Injection"
    ItemType.WATER_DRINK -> "Water"
    ItemType.FOOD -> "Meal"
    ItemType.SUPPLEMENT -> "Supplement"
    ItemType.EXERCISE -> "Exercise"
    ItemType.APPOINTMENT -> "Visit"
    ItemType.LAB_TEST -> "Lab test"
    ItemType.HABIT -> "Habit"
    ItemType.CUSTOM -> "Other"
}

/** Round badge with the type's icon on a tint of its accent colour. */
@Composable
fun TypeBadge(type: ItemType, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val accent = type.accent()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = type.icon(),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}
