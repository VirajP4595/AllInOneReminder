package app.allinonereminder.alarm

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager

/**
 * Opens [AlarmActivity] straight away, over whatever app is in use.
 *
 * A full-screen intent on a notification is only honoured while the phone is locked or the screen
 * is off; while the phone is in use Android shows a small heads-up banner instead. An app that the
 * user has allowed to "Display over other apps" may start an activity from the background, so the
 * ring service calls this as well as posting the notification (which stays as the fallback and as
 * the lock-screen path).
 */
object AlarmLauncher {
    private const val OVERLAY_LIFETIME_MS = 3_000L

    /** Returns false when the permission is missing and only the notification path is available. */
    fun launch(context: Context, occurrenceIds: Array<String>): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            val overlay = addInvisibleOverlayIfNeeded(appContext)
            try {
                appContext.startActivity(
                    Intent(appContext, AlarmActivity::class.java).apply {
                        putExtra(AlarmCoordinator.EXTRA_OCCURRENCE_IDS, occurrenceIds)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                )
            } catch (_: Exception) {
                // The notification's full-screen intent remains as the fallback.
            }
            if (overlay != null) {
                Handler(Looper.getMainLooper()).postDelayed({ removeOverlay(appContext, overlay) }, OVERLAY_LIFETIME_MS)
            }
        }
        return true
    }

    /**
     * From Android 15 the background-start exemption requires a visible overlay window, not only the
     * permission, so a 1x1 transparent window is shown while the activity starts.
     */
    private fun addInvisibleOverlayIfNeeded(context: Context): View? {
        if (Build.VERSION.SDK_INT < 35) return null
        return try {
            val view = View(context)
            val params = WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(view, params)
            view
        } catch (_: Exception) {
            null
        }
    }

    private fun removeOverlay(context: Context, view: View) {
        try {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (_: Exception) {
        }
    }
}
