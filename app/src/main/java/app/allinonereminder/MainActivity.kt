package app.allinonereminder

import android.os.Bundle
import android.widget.Toast
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
import androidx.lifecycle.lifecycleScope
import app.allinonereminder.data.SampleDataSeeder
import app.allinonereminder.data.security.DatabaseKeyManager
import app.allinonereminder.ui.permissions.PermissionHealthCheckScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sampleDataSeeder: SampleDataSeeder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val keyManager = DatabaseKeyManager(this)
        val isKeyAccessible = keyManager.canAccessPassphrase()

        setContent {
            MaterialTheme {
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
                        PermissionHealthCheckScreen(
                            onSeedSampleData = {
                                lifecycleScope.launch {
                                    val msg = sampleDataSeeder.seedSampleData()
                                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                                }
                            },
                            onTestAlarmSent = { msg ->
                                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                            }
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

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Data can't be unlocked",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "The database encryption key cannot be recovered by the device keystore (e.g. after a restore or system key reset). You can restore from an encrypted backup or reset the database.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRestoreFromBackup,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restore from backup")
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
            title = { Text("Reset App Database?") },
            text = {
                Text("This will permanently delete the locked database and encryption key. All local medicines, schedules, and history will be erased. Are you sure you want to proceed?")
            },
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
