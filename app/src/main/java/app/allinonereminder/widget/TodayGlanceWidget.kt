package app.allinonereminder.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.allinonereminder.MainActivity
import app.allinonereminder.data.local.dao.ItemDao
import app.allinonereminder.data.local.dao.OccurrenceDao
import app.allinonereminder.data.mapper.toDomain
import app.allinonereminder.data.repository.ProgressRepository
import app.allinonereminder.model.OccurrenceStatus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun occurrenceDao(): OccurrenceDao
    fun itemDao(): ItemDao
    fun progressRepository(): ProgressRepository
}

class TodayGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = System.currentTimeMillis()
        val endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        var nextItemName: String? = null
        var nextItemTime: String? = null
        var nextItemDose: String? = null

        var medsPercent = 100
        var hydrationPercent = 100
        var movementPercent = 100

        try {
            val entryPoint: WidgetEntryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java
            )

            val upcoming = entryPoint.occurrenceDao().getOccurrencesBetweenSync(now, endOfDay)
                .filter {
                    it.status == OccurrenceStatus.PENDING ||
                    it.status == OccurrenceStatus.RINGING ||
                    it.status == OccurrenceStatus.SNOOZED ||
                    it.status == OccurrenceStatus.DEFERRED_MEAL
                }
                .minByOrNull { it.dueAt }

            if (upcoming != null) {
                val itemEntity = entryPoint.itemDao().getByIdSync(upcoming.itemId)
                if (itemEntity != null) {
                    val item = itemEntity.toDomain()
                    nextItemName = item.name
                    val timeFormatter = app.allinonereminder.ui.components.AppTime.formatter()
                    nextItemTime = Instant.ofEpochMilli(upcoming.dueAt).atZone(zone).format(timeFormatter)
                    nextItemDose = "${item.dosageAmount} ${item.dosageUnit}"
                }
            }

            val progress = entryPoint.progressRepository().getGamificationProgress(daysBack = 1, zoneId = zone)
            val todayRings = progress.rings.find { it.date == today }
            if (todayRings != null) {
                medsPercent = todayRings.meds.percent
                hydrationPercent = todayRings.hydration.percent
                movementPercent = todayRings.movement.percent
            }
        } catch (_: Exception) {
        }

        provideContent {
            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(16.dp)
                        .padding(14.dp)
                        .clickable(actionStartActivity<MainActivity>())
                ) {
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Title
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Reminder & Health",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GlanceTheme.colors.primary
                                )
                            )
                        }

                        Spacer(modifier = GlanceModifier.height(6.dp))

                        // Next Dose Information
                        if (nextItemName != null && nextItemTime != null) {
                            Text(
                                text = "Next: $nextItemName",
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GlanceTheme.colors.onSurface
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = "at $nextItemTime (${nextItemDose.orEmpty()})",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    color = GlanceTheme.colors.onSurfaceVariant
                                )
                            )
                        } else {
                            Text(
                                text = "All caught up for today! 🎉",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ColorProvider(Color(0xFF2E7D32))
                                )
                            )
                            Text(
                                text = "Tap to open Today schedule",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = GlanceTheme.colors.onSurfaceVariant
                                )
                            )
                        }

                        Spacer(modifier = GlanceModifier.height(8.dp))

                        // Health Rings / Status Row
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RingPill(label = "💊 Meds", percent = medsPercent, color = Color(0xFFE53935))
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            RingPill(label = "💧 Water", percent = hydrationPercent, color = Color(0xFF0288D1))
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            RingPill(label = "🏃 Move", percent = movementPercent, color = Color(0xFF388E3C))
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun RingPill(label: String, percent: Int, color: Color) {
        Box(
            modifier = GlanceModifier
                .background(GlanceTheme.colors.surfaceVariant)
                .cornerRadius(8.dp)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                text = "$label $percent%",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(color)
                )
            )
        }
    }
}
