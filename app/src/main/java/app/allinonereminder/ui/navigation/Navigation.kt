package app.allinonereminder.ui.navigation

import android.content.Context
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
import app.allinonereminder.ui.items.*
import app.allinonereminder.ui.onboarding.OnboardingScreen
import app.allinonereminder.ui.permissions.PermissionChecker
import app.allinonereminder.ui.permissions.PermissionHealthCheckScreen
import app.allinonereminder.ui.profiles.ProfilesScreen
import app.allinonereminder.ui.profiles.ProfilesViewModel
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
    data object Profiles : Screen("profiles", "Profiles", Icons.Default.People)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    data object ItemAdd : Screen("item/add", "Add Item")
    data object ItemEdit : Screen("item/edit/{itemId}", "Edit Item")
    data object ItemDetail : Screen("item/detail/{itemId}", "Item Detail")
    data object Permissions : Screen("permissions", "Permissions")
}

val BOTTOM_BAR_SCREENS = listOf(
    Screen.Today,
    Screen.Items,
    Screen.Profiles,
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
    sampleDataSeeder: SampleDataSeeder
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
                OnboardingScreen(
                    permissions = permList,
                    onRequestPermission = { item ->
                        item.fixIntent?.let { context.startActivity(it) }
                        permList = PermissionChecker.checkAll(context)
                    },
                    onSendTestAlarm = {
                        val msg = PermissionChecker.scheduleTestAlarm(context, 1)
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    },
                    onCompleteOnboarding = { name, colorArgb, bHour, lHour, dHour ->
                        coroutineScope.launch {
                            val devId = "local-device"
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
                    onDismissDoubleDoseWarning = viewModel::dismissDoubleDoseWarning,
                    onSnooze = { viewModel.onSnoozeClicked(it, 10) },
                    onSkip = viewModel::onSkipClicked,
                    onStartMeal = viewModel::onStartMealNow,
                    onEndMeal = viewModel::onEndMealNow,
                    onOccurrenceClick = { occ ->
                        navController.navigate("item/detail/${occ.item.id}")
                    }
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
                    onNavigateBack = { navController.popBackStack() }
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
                    onUpdateSnooze = viewModel::updateSnoozeMinutes,
                    onUpdateRingDuration = viewModel::updateRingDurationSeconds,
                    onSeedData = {
                        coroutineScope.launch {
                            val msg = sampleDataSeeder.seedSampleData()
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
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
                    onTestAlarmSent = { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
}
