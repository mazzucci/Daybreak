package com.mazzucci.weather.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazzucci.weather.data.ExerciseSource
import com.mazzucci.weather.data.RideWeatherApi
import com.mazzucci.weather.domain.ExerciseWeather
import com.mazzucci.weather.domain.Place
import com.mazzucci.weather.domain.withWeather
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

sealed interface ActivityLogUi {
    data object Idle : ActivityLogUi
    /** Health Connect isn't on this phone: installable (Android 9–13) or not supported at all (Android 8). */
    data class Unavailable(val installable: Boolean) : ActivityLogUi
    /** [denied]: the last request came back empty (Health Connect stops asking after two refusals). */
    data class NeedsPermission(val denied: Boolean = false) : ActivityLogUi
    data object Loading : ActivityLogUi
    /** [weatherMissing]: the weather lookup failed (offline?), so rows have none. */
    data class Loaded(val items: List<ExerciseWeather>, val placeName: String?, val weatherMissing: Boolean = false) : ActivityLogUi
    data class Failed(val message: String) : ActivityLogUi
}

/**
 * The last [DAYS] days of workouts from Health Connect, each with the weather it had. Routes aren't shared by
 * most watch apps, so the weather is for [Place] (the app's first page), fetched in one request.
 */
class ActivityLogViewModel(
    private val source: ExerciseSource,
    private val weather: RideWeatherApi,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {
    private val _state = MutableStateFlow<ActivityLogUi>(ActivityLogUi.Idle)
    val state: StateFlow<ActivityLogUi> = _state.asStateFlow()
    private var job: Job? = null
    private var place: Place? = null
    private var placeOffset: Int? = null

    /** What to ask Health Connect for. */
    val permissions: Set<String> get() = source.permissions

    /**
     * Opens the log for [place] (null when there's no place yet: the workouts show without weather).
     * [placeOffsetSeconds] is its current UTC offset, if known, to spot workouts recorded on a trip.
     */
    fun open(place: Place?, placeOffsetSeconds: Int? = null) {
        this.place = place
        this.placeOffset = placeOffsetSeconds
        job?.cancel()
        job = viewModelScope.launch {
            try {
                when (source.availability()) {
                    ExerciseSource.Availability.NOT_SUPPORTED -> _state.value = ActivityLogUi.Unavailable(installable = false)
                    ExerciseSource.Availability.NOT_INSTALLED -> _state.value = ActivityLogUi.Unavailable(installable = true)
                    ExerciseSource.Availability.AVAILABLE ->
                        if (!source.hasPermission()) _state.value = ActivityLogUi.NeedsPermission() else load()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Health Connect updating or its service unreachable: a message, never a crash.
                _state.value = ActivityLogUi.Failed(FRIENDLY_ERROR)
            }
        }
    }

    /** The permission prompt's answer: the granted permissions. */
    fun onPermissionResult(granted: Set<String>) {
        if (granted.containsAll(source.permissions)) {
            job?.cancel()
            job = viewModelScope.launch { load() }
        } else {
            _state.value = ActivityLogUi.NeedsPermission(denied = true)
        }
    }

    private suspend fun load() {
        _state.value = ActivityLogUi.Loading
        _state.value = try {
            val to = now()
            val from = to.minus(Duration.ofDays(DAYS.toLong()))
            val exercises = source.sessions(from, to).sortedByDescending { it.start }
            val p = place
            // UTC hours: a month-long range crosses daylight saving, which one local offset can't describe.
            val result = if (p == null || exercises.all { it.indoor }) null else try {
                weather.weather(
                    p.latitude, p.longitude,
                    from.atOffset(ZoneOffset.UTC).toLocalDate().minusDays(1),
                    to.atOffset(ZoneOffset.UTC).toLocalDate().plusDays(1),
                    localTime = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // the workouts are still worth showing without weather
            }
            ActivityLogUi.Loaded(withWeather(exercises, result?.hours.orEmpty(), placeOffset), p?.name, weatherMissing = p != null && result == null && !exercises.all { it.indoor })
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            ActivityLogUi.NeedsPermission() // revoked in Health Connect since the last check
        } catch (e: Exception) {
            ActivityLogUi.Failed(FRIENDLY_ERROR)
        }
    }

    companion object {
        /** Health Connect lets apps read 30 days back without the extra history permission. */
        const val DAYS = 30
        const val FRIENDLY_ERROR = "Health Connect didn't answer. It may be updating; try again in a moment."
    }
}
