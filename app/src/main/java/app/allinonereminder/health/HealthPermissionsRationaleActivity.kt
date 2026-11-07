package app.allinonereminder.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.allinonereminder.ui.theme.AllInOneReminderTheme

/** Shown by Health Connect ("Why does this app want my data?"). Plain-language privacy note. */
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AllInOneReminderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("Health data and this app", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "\nAll-in-One Reminder reads your exercise sessions and step count from " +
                                "Health Connect for one reason: to tick off an exercise reminder " +
                                "(like \"15 min walk\") when you have already done it.\n\n" +
                                "• Nothing is ever written to Health Connect.\n" +
                                "• Your health data stays on this phone. The app has no internet access " +
                                "and sends nothing anywhere.\n" +
                                "• You can turn this off in the app's Settings, or remove the permission " +
                                "in Health Connect at any time. Reminders keep working either way."
                        )
                    }
                }
            }
        }
    }
}
