package app.allinonereminder.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.allinonereminder.model.MealType
import app.allinonereminder.ui.permissions.PermissionItem
import app.allinonereminder.ui.theme.AllInOneReminderTheme

val PRESET_COLORS = listOf(
    Color(0xFF1E88E5), // Blue
    Color(0xFF43A047), // Green
    Color(0xFFE53935), // Red
    Color(0xFF8E24AA), // Purple
    Color(0xFFFB8C00), // Orange
    Color(0xFF00ACC1), // Cyan
    Color(0xFF5E35B1), // Deep Purple
    Color(0xFFD81B60)  // Pink
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    permissions: List<PermissionItem> = emptyList(),
    onRequestPermission: (PermissionItem) -> Unit = {},
    onSendTestAlarm: () -> Unit = {},
    onCompleteOnboarding: (name: String, colorArgb: Int, breakfastHour: Int, lunchHour: Int, dinnerHour: Int) -> Unit = { _, _, _, _, _ -> }
) {
    var step by remember { mutableStateOf(1) }
    var profileName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(PRESET_COLORS[0]) }

    var breakfastHour by remember { mutableStateOf(8) }
    var lunchHour by remember { mutableStateOf(13) }
    var dinnerHour by remember { mutableStateOf(19) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Welcome to All-in-One Reminder") },
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            AnimatedContent(targetState = step, label = "OnboardingStep") { currentStep ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (currentStep) {
                        1 -> WelcomeStep()
                        2 -> ProfileStep(
                            profileName = profileName,
                            onNameChange = { profileName = it },
                            selectedColor = selectedColor,
                            onColorSelected = { selectedColor = it }
                        )
                        3 -> MealTimesStep(
                            breakfastHour = breakfastHour,
                            onBreakfastChange = { breakfastHour = it },
                            lunchHour = lunchHour,
                            onLunchChange = { lunchHour = it },
                            dinnerHour = dinnerHour,
                            onDinnerChange = { dinnerHour = it }
                        )
                        4 -> PermissionsStep(
                            permissions = permissions,
                            onRequestPermission = onRequestPermission
                        )
                        5 -> TestAlarmAndBootNoteStep(onSendTestAlarm = onSendTestAlarm)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (step > 1) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .weight(1f)
                    ) {
                        Text("Back", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Button(
                    onClick = {
                        if (step < 5) {
                            step++
                        } else {
                            onCompleteOnboarding(
                                if (profileName.isBlank()) "Me" else profileName,
                                selectedColor.toArgb(),
                                breakfastHour,
                                lunchHour,
                                dinnerHour
                            )
                        }
                    },
                    enabled = step != 2 || profileName.isNotBlank(),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .weight(1f)
                ) {
                    Text(if (step == 5) "Get Started" else "Next", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.HealthAndSafety,
            contentDescription = "Health & Safety",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Reliable Family Health Reminders",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Never miss doses, meals, or health appointments. All-in-One Reminder rings full-screen alarms even when your phone is locked or on Do Not Disturb.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Medical Disclaimer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "This application is an organizational tool to support medication tracking. It does not provide medical advice. Always follow your prescribing physician's directions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun ProfileStep(
    profileName: String,
    onNameChange: (String) -> Unit,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = "Profile",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Create Your First Profile",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Enter a name or role (e.g., Mom, Dad, Viraj). You can add more profiles later for family members.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(
            value = profileName,
            onValueChange = onNameChange,
            label = { Text("Profile Name") },
            placeholder = { Text("e.g. Mom, Self, Dad") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Choose Profile Avatar Color",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PRESET_COLORS.take(4).forEach { color ->
                ColorCircle(color = color, isSelected = color == selectedColor) {
                    onColorSelected(color)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PRESET_COLORS.drop(4).take(4).forEach { color ->
                ColorCircle(color = color, isSelected = color == selectedColor) {
                    onColorSelected(color)
                }
            }
        }
    }
}

@Composable
private fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun MealTimesStep(
    breakfastHour: Int,
    onBreakfastChange: (Int) -> Unit,
    lunchHour: Int,
    onLunchChange: (Int) -> Unit,
    dinnerHour: Int,
    onDinnerChange: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Restaurant,
            contentDescription = "Meal Times",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Typical Meal Times",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Medications linked to meals (e.g. '30m before breakfast') adapt to these times. You can refine these per weekday or weekend later in Profiles.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))

        MealTimeRow(meal = "Breakfast", hour = breakfastHour, onHourChange = onBreakfastChange)
        Spacer(modifier = Modifier.height(12.dp))
        MealTimeRow(meal = "Lunch", hour = lunchHour, onHourChange = onLunchChange)
        Spacer(modifier = Modifier.height(12.dp))
        MealTimeRow(meal = "Dinner", hour = dinnerHour, onHourChange = onDinnerChange)
    }
}

@Composable
private fun MealTimeRow(
    meal: String,
    hour: Int,
    onHourChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = meal,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (hour > 0) onHourChange(hour - 1) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease $meal hour")
                }
                Text(
                    text = String.format("%02d:00", hour),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = { if (hour < 23) onHourChange(hour + 1) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase $meal hour")
                }
            }
        }
    }
}

@Composable
private fun PermissionsStep(
    permissions: List<PermissionItem>,
    onRequestPermission: (PermissionItem) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = "Permissions",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Alarm Permissions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "To guarantee alarms ring on time even when Doze mode is active or the screen is locked, ensure these permissions are granted.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        permissions.forEach { perm ->
            PermissionCard(item = perm, onRequest = { onRequestPermission(perm) })
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    item: PermissionItem,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isGranted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Button(
                    onClick = onRequest,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Enable")
                }
            }
        }
    }
}

@Composable
private fun TestAlarmAndBootNoteStep(onSendTestAlarm: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.AlarmOn,
            contentDescription = "Test Alarm",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Test Your Setup",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "You can fire a test alarm now to verify audio, vibration, and lock screen presentation.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onSendTestAlarm,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
        ) {
            Icon(Icons.Default.Alarm, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Send test alarm in 1 minute", fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Direct boot note from §11
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Encrypted storage note",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Important: After Phone Restart",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "All health data is encrypted at rest using Android Keystore. Until you unlock your phone once after a reboot, alarms cannot ring because your database remains securely locked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingScreenPreview() {
    AllInOneReminderTheme {
        OnboardingScreen()
    }
}

