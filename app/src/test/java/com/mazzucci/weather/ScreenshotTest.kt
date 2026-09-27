package com.mazzucci.weather

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import org.junit.Rule
import org.junit.Test

/**
 * Renders the app's screens with sample data. Regenerate the README images with:
 *   ./gradlew recordPaparazziDebug
 */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, showSystemUi = false)

    private val sample = UiState.Loaded(
        Weather(tempC = 21.4, feelsLikeC = 20.1, humidity = 58, windKmh = 14.2, code = 2),
        place = "San Francisco, California",
    )

    private fun snap(state: UiState, name: String, night: Boolean = false) {
        paparazzi.unsafeUpdateConfig(
            DeviceConfig.PIXEL_5.copy(nightMode = if (night) NightMode.NIGHT else NightMode.NOTNIGHT)
        )
        paparazzi.snapshot(name) {
            WeatherTheme { WeatherLayout(state, onRefresh = {}, onRequestPermission = {}) }
        }
    }

    @Test fun weatherLight() = snap(sample, "weather_light")
    @Test fun weatherDark() = snap(sample, "weather_dark", night = true)
    @Test fun permission() = snap(UiState.NeedsPermission, "permission")
    @Test fun error() = snap(UiState.Failed("Couldn't get your location. Is location turned on?"), "error")
}
