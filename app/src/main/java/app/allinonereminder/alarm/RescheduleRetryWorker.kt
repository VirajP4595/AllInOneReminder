package app.allinonereminder.alarm

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Retries a failed reschedule in the background (review R2-6), so one failure doesn't leave the
 * next reminder unregistered until the user opens the app and taps Retry.
 */
class RescheduleRetryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun alarmCoordinator(): AlarmCoordinator
    }

    override suspend fun doWork(): Result {
        return try {
            val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
            deps.alarmCoordinator().reschedule("Automatic retry after a failed reschedule")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "reschedule_retry"
        private const val MAX_ATTEMPTS = 6

        /** Queues one retry (replacing any queued one): first after 1 minute, then backing off. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<RescheduleRetryWorker>()
                .setInitialDelay(1, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
