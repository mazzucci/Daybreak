package com.mazzucci.weather.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazzucci.weather.data.RideWeatherApi
import com.mazzucci.weather.data.parseGpx
import com.mazzucci.weather.domain.RideReplay
import com.mazzucci.weather.domain.replay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Open-Meteo's Historical Forecast API starts in 2022. */
private val HISTORY_START: Instant = Instant.parse("2022-01-01T00:00:00Z")

sealed interface RideUi {
    data object Idle : RideUi
    data object Loading : RideUi
    data class Loaded(val replay: RideReplay) : RideUi
    data class Failed(val message: String) : RideUi
}

/** Reads a GPX file, fetches the weather for when and where it was ridden, and works out what the wind did. */
class RideViewModel(
    private val weather: RideWeatherApi,
    /** Opens a picked document; the UI passes the content URI. */
    private val openStream: suspend (uri: String) -> InputStream,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {
    private val _state = MutableStateFlow<RideUi>(RideUi.Idle)
    val state: StateFlow<RideUi> = _state.asStateFlow()
    private var job: Job? = null

    fun open(uri: String) {
        job?.cancel()
        _state.value = RideUi.Loading
        job = viewModelScope.launch {
            _state.value = try {
                // Parsing a big file and walking thousands of points is real work: off the main thread.
                val track = withContext(Dispatchers.IO) { openStream(uri).use { parseGpx(it) } }
                val start = track.start ?: throw IOException("This file has no times, so there's no ride to replay (it may be a planned route)")
                val end = track.end ?: start
                if (start.isAfter(now())) throw IOException("That ride is in the future, so there's no weather for it yet")
                if (start.isBefore(HISTORY_START)) throw IOException("Weather history for rides only goes back to 2022")
                val (lat, lon) = track.centre
                // Local dates at the place aren't known before asking, so ask a day either side of the UTC dates;
                // the end gets an extra hour for the rain that falls in the ride's last hour.
                val from = start.atOffset(ZoneOffset.UTC).toLocalDate().minusDays(1)
                val to = end.plusSeconds(3600).atOffset(ZoneOffset.UTC).toLocalDate().plusDays(1)
                val w = try {
                    weather.weather(lat, lon, from, to)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    // A service reply we understood keeps its message; a network failure gets a plain one.
                    if (e.message?.startsWith("Weather history") == true || e.message?.startsWith("The weather history") == true) throw e
                    throw IOException("Couldn't reach the weather service. Check your connection and try again.")
                }
                RideUi.Loaded(withContext(Dispatchers.Default) { replay(track, w.hours, w.utcOffsetSeconds) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RideUi.Failed(e.message ?: "Couldn't replay that ride")
            }
        }
    }

    fun clear() {
        job?.cancel()
        _state.value = RideUi.Idle
    }
}
