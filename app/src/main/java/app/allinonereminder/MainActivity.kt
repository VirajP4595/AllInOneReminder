package app.allinonereminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.data.SampleDataSeeder
import app.allinonereminder.data.repository.MealAnchorRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.data.security.DatabaseKeyManager
import app.allinonereminder.ui.navigation.MainAppNavigation
import app.allinonereminder.ui.theme.AppTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var profileRepository: ProfileRepository

    @Inject
    lateinit var mealAnchorRepository: MealAnchorRepository

    @Inject
    lateinit var alarmCoordinator: AlarmCoordinator

    @Inject
    lateinit var sampleDataSeeder: SampleDataSeeder

    @Inject
    lateinit var testAlarmScheduler: app.allinonereminder.alarm.TestAlarmScheduler

    override fun onResume() {
        super.onResume()
        // The user may have switched the phone between 12- and 24-hour time.
        app.allinonereminder.ui.components.AppTime.refresh(this)
    }

    /** Set from launcher shortcuts (#84); read once by the navigation. */
    private var shortcutAction by androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        shortcutAction = intent.action
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) shortcutAction = intent?.action
        app.allinonereminder.ui.components.AppTime.refresh(this)

        val keyManager = DatabaseKeyManager(this)
        val isKeyAccessible = keyManager.canAccessPassphrase()

        // Read the appearance setting once before the first frame so a "Dark" choice doesn't
        // flash light on start (a single small DataStore read).
        val initialThemeMode = runBlocking { settingsRepository.themeModeFlow.first() }

        setContent {
            val themeMode by settingsRepository.themeModeFlow.collectAsState(initial = initialThemeMode)
            AppTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!isKeyAccessible) {
                        DataLockedRecoveryScreen(
                            onResetConfirmed = {
                                deleteDatabase("allinonereminder.db")
                                keyManager.resetDatabaseKey()
                                recreate()
                            },
                            onRestoreFromBackup = {
                                // Handled in M6 backup flow
                            }
                        )
                    } else {
                        MainAppNavigation(
                            settingsRepository = settingsRepository,
                            profileRepository = profileRepository,
                            mealAnchorRepository = mealAnchorRepository,
                            alarmCoordinator = alarmCoordinator,
                            sampleDataSeeder = sampleDataSeeder,
                            testAlarmScheduler = testAlarmScheduler,
                            shortcutAction = shortcutAction
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DataLockedRecoveryScreen(
    onResetConfirmed: () -> Unit,
    onRestoreFromBackup: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Data Locked",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Data can't be unlocked. The database key could not be recovered.\n\nYou can restore your data from an existing offline backup file, or reset the app database to start fresh.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRestoreFromBackup,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restore from Backup")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Reset App Database")
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Reset Database?") },
            text = { Text("All local health reminders, history, and photos will be permanently deleted and a new encryption key will be generated.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onResetConfirmed()
                    }
                ) {
                    Text("Delete & Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
