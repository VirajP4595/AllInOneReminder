package app.allinonereminder.ui.components

import android.content.Context
import android.text.format.DateFormat
import java.time.format.DateTimeFormatter

/**
 * One place for clock-time formatting, so every screen follows the phone's 12/24-hour setting.
 * [refresh] runs at app start and whenever an activity resumes (the user may change the setting).
 */
object AppTime {
    @Volatile
    var is24Hour: Boolean = false
        private set

    fun refresh(context: Context) {
        is24Hour = DateFormat.is24HourFormat(context)
    }

    /** "HH:mm" or "h:mm a". */
    fun timePattern(): String = if (is24Hour) "HH:mm" else "h:mm a"

    fun formatter(): DateTimeFormatter = DateTimeFormatter.ofPattern(timePattern())

    /** Formats a minute of the day (0..1439), e.g. "8:15 AM" or "08:15". */
    fun format(minuteOfDay: Int): String {
        val total = ((minuteOfDay % 1440) + 1440) % 1440
        val hour = total / 60
        val min = total % 60
        return if (is24Hour) {
            String.format("%02d:%02d", hour, min)
        } else {
            val ampm = if (hour >= 12) "PM" else "AM"
            val hour12 = if (hour % 12 == 0) 12 else hour % 12
            String.format("%d:%02d %s", hour12, min, ampm)
        }
    }
}
