package com.mazzucci.weather.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.mazzucci.weather.domain.Exercise
import com.mazzucci.weather.domain.ExerciseKind
import java.time.Instant

/** Where workouts come from. Behind an interface so the log is tested without Health Connect. */
interface ExerciseSource {
    enum class Availability { AVAILABLE, NOT_INSTALLED, NOT_SUPPORTED }

    fun availability(): Availability

    /** The one permission the log needs: reading exercise sessions (no heart rate, no routes). */
    val permissions: Set<String>

    suspend fun hasPermission(): Boolean

    suspend fun sessions(from: Instant, to: Instant): List<Exercise>
}

/**
 * Health Connect: built into Android 14+, an app from Google Play on Android 9–13, and unavailable on Android 8.
 * Only exercise sessions are read, and only as far back as Health Connect allows without the history permission
 * (30 days before the permission was granted).
 */
class HealthConnectExerciseSource(private val context: Context) : ExerciseSource {
    override val permissions = setOf(HealthPermission.getReadPermission(ExerciseSessionRecord::class))

    override fun availability(): ExerciseSource.Availability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> ExerciseSource.Availability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> ExerciseSource.Availability.NOT_INSTALLED
        else -> ExerciseSource.Availability.NOT_SUPPORTED
    }

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    override suspend fun hasPermission(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(permissions)

    override suspend fun sessions(from: Instant, to: Instant): List<Exercise> {
        val records = mutableListOf<ExerciseSessionRecord>()
        var page: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    ascendingOrder = false,
                    pageSize = 500,
                    pageToken = page,
                ),
            )
            records += response.records
            page = response.pageToken
        } while (page != null && records.size < 500)
        return records.take(500).map { r ->
            Exercise(
                id = r.metadata.id,
                kind = kindOf(r.exerciseType),
                title = r.title?.ifBlank { null },
                start = r.startTime,
                end = r.endTime,
                source = r.metadata.dataOrigin.packageName,
                indoor = r.exerciseType in INDOOR,
                zoneOffsetSeconds = r.startZoneOffset?.totalSeconds,
            )
        }
    }

    /** Workouts that happen indoors (the weather outside doesn't apply). */
    private val INDOOR = setOf(
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL,
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
    )

    private fun kindOf(type: Int): ExerciseKind = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> ExerciseKind.RIDE
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> ExerciseKind.RUN
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> ExerciseKind.WALK
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> ExerciseKind.HIKE
        else -> ExerciseKind.OTHER
    }
}
