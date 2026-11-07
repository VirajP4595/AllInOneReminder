package app.allinonereminder.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectActivitySource @Inject constructor(
    @ApplicationContext private val context: Context
) : ActivitySource {

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

        val EXERCISE_PERMISSION: String = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        val STEPS_PERMISSION: String = HealthPermission.getReadPermission(StepsRecord::class)
        val PERMISSIONS: Set<String> = setOf(EXERCISE_PERMISSION, STEPS_PERMISSION)
    }

    override fun availability(): HealthAvailability = try {
        when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE -> HealthAvailability.NOT_INSTALLED
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
            else -> HealthAvailability.UNSUPPORTED
        }
    } catch (e: Exception) {
        HealthAvailability.UNSUPPORTED
    }

    private fun client(): HealthConnectClient? =
        if (availability() == HealthAvailability.AVAILABLE) {
            try {
                HealthConnectClient.getOrCreate(context)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

    override suspend fun hasExercisePermission(): Boolean {
        val c = client() ?: return false
        return try {
            EXERCISE_PERMISSION in c.permissionController.getGrantedPermissions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun exerciseSpans(from: Instant, to: Instant): List<ExerciseSpan> {
        val c = client() ?: return emptyList()
        return try {
            val spans = mutableListOf<ExerciseSpan>()
            var token: String? = null
            do {
                val response = c.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(from, to),
                        pageToken = token
                    )
                )
                response.records.forEach { spans.add(ExerciseSpan(it.startTime, it.endTime)) }
                token = response.pageToken
            } while (!token.isNullOrEmpty())
            spans
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Permission revoked, provider crashed, etc.: behave as "no data", never crash.
            emptyList()
        }
    }

    override suspend fun todaySteps(): Long {
        val c = client() ?: return 0L
        return try {
            val startOfDay = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
                .atStartOfDay(ZoneId.systemDefault()).toInstant()
            val result = c.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, Instant.now())
                )
            )
            result[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            0L
        }
    }
}
