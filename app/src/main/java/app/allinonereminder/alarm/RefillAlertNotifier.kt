package app.allinonereminder.alarm

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.allinonereminder.R
import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.ScheduleRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.data.repository.StockEventRepository
import app.allinonereminder.schedule.SchedulingEngine
import app.allinonereminder.schedule.StockCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RefillAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val itemRepository: ItemRepository,
    private val scheduleRepository: ScheduleRepository,
    private val stockEventRepository: StockEventRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend fun checkAndNotify(itemId: String) {
        // Notification only fires for items with stock tracking turned on (stock events exist)
        if (!stockEventRepository.hasStockEvents(itemId)) return

        val item = itemRepository.getItemSync(itemId) ?: return
        val stock = stockEventRepository.getCurrentStockSync(itemId)
        val schedules = scheduleRepository.getActiveSchedulesSync(itemId)

        val now = Instant.now()
        val plannedCounts = StockCalculator.plan14DaysCounts(listOf(item), schedules, emptyList(), now)
        val plannedSize = plannedCounts[item.id] ?: 0
        val daysOfSupply = StockCalculator.calculateDaysOfSupply(
            currentStock = stock,
            unitsPerDose = item.unitsPerDose,
            plannedOccurrencesCountIn14Days = plannedSize
        )

        val isLow = StockCalculator.isLowStock(
            currentStock = stock,
            refillThreshold = item.refillThreshold,
            daysOfSupply = daysOfSupply
        )
        if (!isLow) return

        // At most one notification per item per day
        val todayStr = LocalDate.now(ZoneId.systemDefault()).toString()
        val lastNotified = settingsRepository.getLastRefillAlertDate(itemId)
        if (lastNotified == todayStr) return

        // Build and display notification
        AlarmNotificationHelper.createNotificationChannels(context)
        val statusText = StockCalculator.formatStockStatus(stock, item.unitsPerDose, plannedSize)
        val notification = NotificationCompat.Builder(context, AlarmNotificationHelper.CHANNEL_REFILL)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Refill soon: ${item.name}")
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            // The medicine name stays off a secure lock screen (channel settings are fixed once
            // created, so this is set per notification).
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, AlarmNotificationHelper.CHANNEL_REFILL)
                    .setSmallIcon(R.drawable.ic_stat_alarm)
                    .setContentTitle("Refill reminder")
                    .setContentText("A medicine is running low")
                    .build()
            )
            .build()

        try {
            val nm = NotificationManagerCompat.from(context)
            nm.notify(itemId.hashCode(), notification)
            settingsRepository.setLastRefillAlertDate(itemId, todayStr)
        } catch (_: SecurityException) {
            // In case notification permission was revoked
        }
    }
}
