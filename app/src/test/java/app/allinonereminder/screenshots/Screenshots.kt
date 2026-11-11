package app.allinonereminder.screenshots

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.allinonereminder.alarm.AlarmItemUiModel
import app.allinonereminder.alarm.AlarmScreenContent
import app.allinonereminder.alarm.AlarmUiState
import app.allinonereminder.model.Criticality
import app.allinonereminder.model.Item
import app.allinonereminder.model.ItemType
import app.allinonereminder.model.Occurrence
import app.allinonereminder.model.OccurrenceStatus
import app.allinonereminder.model.Profile
import app.allinonereminder.ui.items.ItemDetailScreenPreview
import app.allinonereminder.ui.items.ItemEditorScreenPreview
import app.allinonereminder.ui.items.ItemsScreenPreview
import app.allinonereminder.ui.onboarding.OnboardingScreenPreview
import app.allinonereminder.ui.permissions.PermissionHealthCheckScreenPreview
import app.allinonereminder.ui.profiles.ProfilesScreenPreview
import app.allinonereminder.ui.settings.SettingsScreenPreview
import app.allinonereminder.ui.theme.AllInOneReminderTheme
import app.allinonereminder.ui.today.TodayScreenPreview
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders each screen to a PNG so the design can be reviewed without a phone. Skipped unless the
 * build runs with -Pscreenshots=true. Output: app/build/screenshots/<name>[_dark].png
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotBase(private val suffix: String) {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun onlyWhenAsked() {
        Assume.assumeTrue(System.getProperty("screenshots") == "true")
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { content() }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun today() = shot("01_today") { TodayScreenPreview() }
    @Test fun items() = shot("02_items") { ItemsScreenPreview() }
    @Test fun itemDetail() = shot("03_item_detail") { ItemDetailScreenPreview() }
    @Test fun itemEditor() = shot("04_item_editor") { ItemEditorScreenPreview() }
    @Test fun settings() = shot("05_settings") { SettingsScreenPreview() }
    @Test fun profiles() = shot("06_profiles") { ProfilesScreenPreview() }
    @Test fun onboarding() = shot("07_onboarding") { OnboardingScreenPreview() }
    @Test fun permissions() = shot("08_permissions") { AllInOneReminderTheme { PermissionHealthCheckScreenPreview() } }
    @Test fun alarmGroup() = shot("09_alarm_group") { AllInOneReminderTheme { SampleAlarm() } }
}

@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class LightScreenshots : ScreenshotBase("")

@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi-night")
class DarkScreenshots : ScreenshotBase("_dark")

@Composable
private fun SampleAlarm() {
    val now = System.currentTimeMillis()
    val mom = Profile(id = "p1", name = "Mom", colorArgb = 0xFFFB8C00.toInt(), createdAt = 0L, updatedAt = 0L)
    fun item(id: String, name: String, amount: String, unit: String) = Item(
        id = id, profileId = "p1", type = ItemType.MEDICINE, name = name, dosageAmount = amount,
        dosageUnit = unit, form = "Tablet", criticality = Criticality.NORMAL, homeZoneId = "UTC",
        startDate = "2026-01-01", createdAt = 0L, updatedAt = 0L
    )
    fun occ(id: String, itemId: String) = Occurrence(
        id = id, itemId = itemId, scheduleId = "s-$id", profileId = "p1", plannedAt = now,
        dueAt = now, status = OccurrenceStatus.RINGING, deviceId = "d", updatedAt = now
    )
    AlarmScreenContent(
        state = AlarmUiState(
            items = listOf(
                AlarmItemUiModel(occ("o1", "i1"), item("i1", "Metformin", "500", "mg"), null, mom),
                AlarmItemUiModel(occ("o2", "i2"), item("i2", "Vitamin D3", "1", "capsule"), null, mom),
                AlarmItemUiModel(occ("o3", "i3"), item("i3", "Amlodipine", "5", "mg"), null, mom)
            )
        ),
        onTaken = {}, onAllTaken = {}, onSnooze = {}, onSnoozeAll = {}, onSkip = {},
        onNotEatenYet = { _, _, _ -> }, onStartingMealNow = {}
    )
}
