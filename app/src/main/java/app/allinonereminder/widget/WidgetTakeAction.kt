package app.allinonereminder.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.alarm.AlarmRingService
import app.allinonereminder.alarm.RefillAlertNotifier
import app.allinonereminder.data.repository.OccurrenceRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.model.OccurrenceStatus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * "Taken" from the home-screen widget (#83). Runs the same path as Today: re-checks that the dose
 * is still open and outside the minimum gap, marks it, stops any ringing, and reschedules through
 * AlarmCoordinator (which also refreshes the widget).
 */
class WidgetTakeAction : ActionCallback {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun occurrenceRepository(): OccurrenceRepository
        fun alarmCoordinator(): AlarmCoordinator
        fun refillAlertNotifier(): RefillAlertNotifier
        fun settingsRepository(): SettingsRepository
        fun itemRepository(): app.allinonereminder.data.repository.ItemRepository
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[OCCURRENCE_ID] ?: return
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val repo = deps.occurrenceRepository()
        val occ = repo.getByIdSync(id) ?: return
        val open = occ.status in listOf(
            OccurrenceStatus.PENDING, OccurrenceStatus.RINGING, OccurrenceStatus.SNOOZED, OccurrenceStatus.DEFERRED_MEAL
        )
        if (open) {
            val now = System.currentTimeMillis()
            val item = deps.itemRepository().getItemSync(occ.itemId)
            val minGap = item?.minGapMinutes ?: 0
            val last = repo.getLastTakenBeforeSync(occ.itemId, now)?.actedAt
            val tooSoon = minGap > 0 && last != null && (now - last) < minGap * 60_000L
            if (!tooSoon) {
                AlarmRingService.stopForOccurrence(context, id)
                if (repo.markOccurrenceTaken(id, now)) {
                    repo.setMarkedBy(id, deps.settingsRepository().caregiverNameFlow.first())
                    deps.refillAlertNotifier().checkAndNotify(occ.itemId)
                }
            }
        }
        deps.alarmCoordinator().reschedule("taken_from_widget")
        TodayWidgetUpdater.update(context)
    }

    companion object {
        val OCCURRENCE_ID = ActionParameters.Key<String>("occurrence_id")
    }
}
