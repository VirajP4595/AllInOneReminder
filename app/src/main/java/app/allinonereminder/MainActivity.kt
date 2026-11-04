package app.allinonereminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.data.SampleDataSeeder
import app.allinonereminder.data.repository.MealAnchorRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.ui.navigation.MainAppNavigation
import app.allinonereminder.ui.theme.AllInOneReminderTheme
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AllInOneReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppNavigation(
                        settingsRepository = settingsRepository,
                        profileRepository = profileRepository,
                        mealAnchorRepository = mealAnchorRepository,
                        alarmCoordinator = alarmCoordinator,
                        sampleDataSeeder = sampleDataSeeder
                    )
                }
            }
        }
    }
}
