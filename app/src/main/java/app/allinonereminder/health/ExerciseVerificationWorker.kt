package app.allinonereminder.health

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.allinonereminder.alarm.AlarmCoordinator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class ExerciseVerificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun exerciseVerifier(): ExerciseVerifier
        fun alarmCoordinator(): AlarmCoordinator
    }

    override suspend fun doWork(): Result {
        return try {
            val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
            val completed = deps.exerciseVerifier().completePending()
            if (completed.isNotEmpty()) {
                deps.alarmCoordinator().reschedule("HealthConnect: verified by worker")
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never fail loudly: a revoked permission or missing provider is not an error.
            Result.success()
        }
    }
}

object ExerciseVerificationScheduler {
    private const val UNIQUE_NAME = "exercise_verification"

    /** Runs every 15 minutes only while the verification toggle is on. */
    fun sync(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (enabled) {
            wm.enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ExerciseVerificationWorker>(15, TimeUnit.MINUTES).build()
            )
        } else {
            wm.cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
