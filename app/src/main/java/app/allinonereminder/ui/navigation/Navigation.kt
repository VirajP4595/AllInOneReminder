package app.allinonereminder.ui.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.data.SampleDataSeeder
import app.allinonereminder.data.repository.MealAnchorRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.model.DayType
import app.allinonereminder.model.MealAnchor
import app.allinonereminder.model.MealType
import app.allinonereminder.ui.appointments.*
import app.allinonereminder.ui.history.*
import app.allinonereminder.ui.items.*
import app.allinonereminder.ui.onboarding.OnboardingScreen
import app.allinonereminder.ui.permissions.PermissionChecker
import app.allinonereminder.ui.permissions.PermissionHealthCheckScreen
import app.allinonereminder.ui.profiles.ProfilesScreen
import app.allinonereminder.ui.profiles.ProfilesViewModel
import app.allinonereminder.ui.progress.*
import app.allinonereminder.ui.settings.SettingsScreen
import app.allinonereminder.ui.settings.SettingsViewModel
import app.allinonereminder.ui.today.TodayScreen
import app.allinonereminder.ui.today.TodayViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    data object Onboarding : Screen("onboarding", "Onboarding")
    data object Today : Screen("today", "Today", Icons.Default.CalendarToday)
    data object Items : Screen("items", "Items", Icons.Default.MedicalServices)
    data object Progress : Screen("progress", "Progress", Icons.Default.TrendingUp)
    data object Appointments : Screen("appointments", "Visits", Icons.Default.Event)
    data object Profiles : Screen("profiles", "Profiles", Icons.Default.People)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    data object ItemAdd : Screen("item/add", "Add Item")
    data object ItemEdit : Screen("item/edit/{itemId}", "Edit Item")
    data object ItemDetail : Screen("item/detail/{itemId}", "Item Detail")
    data object AppointmentAdd : Screen("appointment/add", "Add Appointment")
    data object AppointmentEdit : Screen("appointment/edit/{appointmentId}", "Edit Appointment")
    data object AppointmentDetail : Screen("appointment/detail/{appointmentId}", "Appointment Detail")
    data object History : Screen("history", "History")
    data object Permissions : Screen("permissions", "Permissions")
}

val BOTTOM_BAR_SCREENS = listOf(
    Screen.Today,
    Screen.Items,
    Screen.Progress,
    Screen.Appointments,
    Screen.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppNavigation(
    navController: NavHostController = rememberNavController(),
    settingsRepository: SettingsRepository,
    profileRepository: ProfileRepository,
    mealAnchorRepository: MealAnchorRepository,
    alarmCoordinator: AlarmCoordinator,
    sampleDataSeeder: SampleDataSeeder,
    testAlarmScheduler: app.allinonereminder.alarm.TestAlarmScheduler
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Check if onboarding completed
    val onboardingCompleted by remember(settingsRepository) {
        settingsRepository.settingsFlow.map { it.onboardingCompleted }
    }.collectAsState(initial = true)
    val startDestination = if (onboardingCompleted) Screen.Today.route else Screen.Onboarding.route

    val showBottomBar = currentRoute in BOTTOM_BAR_SCREENS.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BOTTOM_BAR_SCREENS.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon ?: Icons.Default.Circle, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            // 1. Onboarding
            composable(Screen.Onboarding.route) {
                var permList by remember { mutableStateOf(PermissionChecker.checkAll(context)) }
                var testAlarmRequested by remember { mutableStateOf(false) }
                val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            permList = PermissionChecker.checkAll(context)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                OnboardingScreen(
                    permissions = permList,
                    onRequestPermission = { item ->
                        item.fixIntent?.let { context.startActivity(it) }
                        permList = PermissionChecker.checkAll(context)
                    },
                    onSendTestAlarm = {
                        testAlarmRequested = true
                        Toast.makeText(
                            context,
                            "The test alarm will ring 1 minute after you finish setup.",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    onCompleteOnboarding = { name, colorArgb, bHour, lHour, dHour ->
                        coroutineScope.launch {
                            val devId = settingsRepository.getOrCreateDeviceId()
                            val prof = profileRepository.createProfile(name, colorArgb, deviceId = devId)
                            val now = System.currentTimeMillis()

                            mealAnchorRepository.updateAnchor(
                                MealAnchor(UUID.randomUUID().toString(), prof.id, DayType.WEEKDAY, MealType.BREAKFAST, bHour * 60, now),
                                devId
                            )
                            mealAnchorRepository.updateAnchor(
                                MealAnchor(UUID.randomUUID().toString(), prof.id, DayType.WEEKEND, MealType.BREAKFAST, (bHour + 1) * 60, now),
                                devId
                            )
                            mealAnchorRepository.updateAnchor(
                                MealAnchor(UUID.randomUUID().toString(), prof.id, DayType.WEEKDAY, MealType.LUNCH, lHour * 60, now),
                                devId
                            )
                            mealAnchorRepository.updateAnchor(
                                MealAnchor(UUID.randomUUID().toString(), prof.id, DayType.WEEKEND, MealType.LUNCH, (lHour * 60) + 30, now),
                                devId
                            )
                            mealAnchorRepository.updateAnchor(
                                MealAnchor(UUID.randomUUID().toString(), prof.id, DayType.WEEKDAY, MealType.DINNER, dHour * 60, now),
                                devId
                            )
                            mealAnchorRepository.updateAnchor(
                                MealAnchor(UUID.randomUUID().toString(), prof.id, DayType.WEEKEND, MealType.DINNER, (dHour + 1) * 60, now),
                                devId
                            )

                            settingsRepository.setOnboardingCompleted(true)
                            if (testAlarmRequested) {
                                val msg = testAlarmScheduler.schedule(prof.id)
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                            navController.navigate(Screen.Today.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        }
                    }
                )
            }

            // 2. Today
            composable(Screen.Today.route) {
                val viewModel: TodayViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                TodayScreen(
                    uiState = uiState,
                    onProfileSelected = viewModel::selectProfile,
                    onTake = { viewModel.onTakeClicked(it, force = false) },
                    onTakeConfirmed = { viewModel.onTakeClicked(it, force = true) },
                    onTakeAsNeeded = { viewModel.onTakeAsNeeded(it, force = false) },
                    onTakeAsNeededConfirmed = { viewModel.onTakeAsNeeded(it, force = true) },
                    onDismissAsNeededWarning = viewModel::dismissAsNeededWarning,
                    onDismissAsNeededLimit = viewModel::dismissAsNeededLimit,
                    onDismissDoubleDoseWarning = viewModel::dismissDoubleDoseWarning,
                    onSnooze = { viewModel.onSnoozeClicked(it, 10) },
                    onSkip = { occ, reason -> viewModel.onSkipClicked(occ, reason) },
                    onFixExactAlarm = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    },
                    onStartMeal = viewModel::onStartMealNow,
                    onEndMeal = viewModel::onEndMealNow,
                    onOccurrenceClick = { occ ->
                        val apptId = occ.item.appointmentId
                        if (apptId != null) {
                            navController.navigate("appointment/detail/$apptId")
                        } else {
                            navController.navigate("item/detail/${occ.item.id}")
                        }
                    },
                    onRetryReschedule = viewModel::retryReschedule,
                    onRewardShown = viewModel::consumeReward
                )
            }

            // 3. Items
            composable(Screen.Items.route) {
                val viewModel: ItemsViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                ItemsScreen(
                    uiState = uiState,
                    onProfileSelected = viewModel::selectProfile,
                    onTypeFilterSelected = viewModel::selectTypeFilter,
                    onAddItemClick = { navController.navigate(Screen.ItemAdd.route) },
                    onItemClick = { item -> navController.navigate("item/detail/${item.id}") }
                )
            }

            // 4. Item Add
            composable(Screen.ItemAdd.route) {
                val viewModel: ItemEditorViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                ItemEditorScreen(
                    uiState = uiState,
                    onNameChange = viewModel::updateName,
                    onTypeChange = viewModel::updateType,
                    onProfileChange = viewModel::updateProfile,
                    onDosageAmountChange = viewModel::updateDosageAmount,
                    onDosageUnitChange = viewModel::updateDosageUnit,
                    onFormChange = viewModel::updateForm,
                    onInstructionsChange = viewModel::updateInstructions,
                    onCriticalityChange = viewModel::updateCriticality,
                    onExerciseMinutesChange = viewModel::updateExerciseMinutes,
                    onTimePolicyChange = viewModel::updateTimePolicy,
                    onGraceMinutesChange = viewModel::updateGraceMinutes,
                    onReRingMinutesChange = viewModel::updateReRingMinutes,
                    onMinGapMinutesChange = viewModel::updateMinGapMinutes,
                    onMaxPerDayChange = viewModel::updateMaxPerDay,
                    onCurrentStockChange = viewModel::updateCurrentStock,
                    onUnitsPerDoseChange = viewModel::updateUnitsPerDose,
                    onRefillThresholdChange = viewModel::updateRefillThreshold,
                    onAddSchedule = viewModel::addSchedule,
                    onRemoveSchedule = viewModel::removeSchedule,
                    onUpdateSchedule = viewModel::updateSchedule,
                    onPhotoSelected = { uri -> viewModel.onPhotoAdded(context, uri) },
                    onRemovePhoto = { photoId -> viewModel.removePhoto(context, photoId) },
                    onSaveClick = viewModel::saveItem,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 4b. Item Edit
            composable(
                route = Screen.ItemEdit.route,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType })
            ) {
                val viewModel: ItemEditorViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                ItemEditorScreen(
                    uiState = uiState,
                    onNameChange = viewModel::updateName,
                    onTypeChange = viewModel::updateType,
                    onProfileChange = viewModel::updateProfile,
                    onDosageAmountChange = viewModel::updateDosageAmount,
                    onDosageUnitChange = viewModel::updateDosageUnit,
                    onFormChange = viewModel::updateForm,
                    onInstructionsChange = viewModel::updateInstructions,
                    onCriticalityChange = viewModel::updateCriticality,
                    onExerciseMinutesChange = viewModel::updateExerciseMinutes,
                    onTimePolicyChange = viewModel::updateTimePolicy,
                    onGraceMinutesChange = viewModel::updateGraceMinutes,
                    onReRingMinutesChange = viewModel::updateReRingMinutes,
                    onMinGapMinutesChange = viewModel::updateMinGapMinutes,
                    onMaxPerDayChange = viewModel::updateMaxPerDay,
                    onCurrentStockChange = viewModel::updateCurrentStock,
                    onUnitsPerDoseChange = viewModel::updateUnitsPerDose,
                    onRefillThresholdChange = viewModel::updateRefillThreshold,
                    onAddSchedule = viewModel::addSchedule,
                    onRemoveSchedule = viewModel::removeSchedule,
                    onUpdateSchedule = viewModel::updateSchedule,
                    onPhotoSelected = { uri -> viewModel.onPhotoAdded(context, uri) },
                    onRemovePhoto = { photoId -> viewModel.removePhoto(context, photoId) },
                    onSaveClick = viewModel::saveItem,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 5. Item Detail
            composable(
                route = Screen.ItemDetail.route,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType })
            ) {
                val viewModel: ItemDetailViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                ItemDetailScreen(
                    uiState = uiState,
                    onEditClick = { itemId -> navController.navigate("item/edit/$itemId") },
                    onDeleteClick = viewModel::deleteItem,
                    onRefillClick = viewModel::addRefill,
                    onAdjustClick = viewModel::adjustStock,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Appointments List
            composable(Screen.Appointments.route) {
                val viewModel: AppointmentsViewModel = hiltViewModel()
                AppointmentsScreen(
                    viewModel = viewModel,
                    onAddClick = { navController.navigate(Screen.AppointmentAdd.route) },
                    onAppointmentClick = { id -> navController.navigate("appointment/detail/$id") }
                )
            }

            // Appointment Add
            composable(Screen.AppointmentAdd.route) {
                val viewModel: AppointmentEditorViewModel = hiltViewModel()
                AppointmentEditorScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            // Appointment Edit
            composable(
                route = Screen.AppointmentEdit.route,
                arguments = listOf(navArgument("appointmentId") { type = NavType.StringType })
            ) {
                val viewModel: AppointmentEditorViewModel = hiltViewModel()
                AppointmentEditorScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            // Appointment Detail
            composable(
                route = Screen.AppointmentDetail.route,
                arguments = listOf(navArgument("appointmentId") { type = NavType.StringType })
            ) {
                val viewModel: AppointmentDetailViewModel = hiltViewModel()
                AppointmentDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate("appointment/edit/$id") }
                )
            }

            // Progress Screen
            composable(Screen.Progress.route) {
                val viewModel: ProgressViewModel = hiltViewModel()
                ProgressScreen(
                    viewModel = viewModel,
                    onNavigateToHistory = { navController.navigate(Screen.History.route) }
                )
            }

            // History Screen
            composable(Screen.History.route) {
                val viewModel: HistoryViewModel = hiltViewModel()
                HistoryScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            // 6. Profiles
            composable(Screen.Profiles.route) {
                val viewModel: ProfilesViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                ProfilesScreen(
                    uiState = uiState,
                    onSaveProfile = viewModel::saveProfile,
                    onDeleteProfile = viewModel::deleteProfile
                )
            }

            // 7. Settings
            composable(Screen.Settings.route) {
                val viewModel: SettingsViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                SettingsScreen(
                    uiState = uiState,
                    onNavigateToPermissions = { navController.navigate(Screen.Permissions.route) },
                    onNavigateToProfiles = { navController.navigate(Screen.Profiles.route) },
                    onUpdateSnooze = viewModel::updateSnoozeMinutes,
                    onUpdateRingDuration = viewModel::updateRingDurationSeconds,
                    onUpdateSpeakReminders = viewModel::updateSpeakReminders,
                    onRefreshHealthStatus = viewModel::refreshHealthStatus,
                    onSetHealthVerification = viewModel::setHealthVerification,
                    onHealthPermissionResult = viewModel::onHealthPermissionResult,
                    onSeedData = {
                        coroutineScope.launch {
                            val msg = sampleDataSeeder.seedSampleData()
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    onCreateBackup = viewModel::createBackup,
                    onInspectRestore = viewModel::inspectRestore,
                    onConfirmRestore = viewModel::confirmRestore,
                    onDismissRestorePreview = viewModel::dismissRestorePreview,
                    onClearMessages = viewModel::clearMessages
                )
            }

            // Permissions
            composable(Screen.Permissions.route) {
                PermissionHealthCheckScreen(
                    onSeedSampleData = {
                        coroutineScope.launch {
                            val msg = sampleDataSeeder.seedSampleData()
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    onSendTestAlarm = {
                        coroutineScope.launch {
                            val msg = testAlarmScheduler.schedule()
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        }
    }
}
