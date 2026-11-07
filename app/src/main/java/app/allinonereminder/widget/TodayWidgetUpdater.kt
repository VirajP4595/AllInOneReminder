package app.allinonereminder.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object TodayWidgetUpdater {
    suspend fun update(context: Context) {
        try {
            TodayGlanceWidget().updateAll(context)
        } catch (_: Exception) {
        }
    }
}

