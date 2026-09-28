package com.mazzucci.weather.ui

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.mazzucci.weather.TestData
import com.mazzucci.weather.TestData.london
import com.mazzucci.weather.TestData.sanFrancisco
import com.mazzucci.weather.TestData.tokyo
import com.mazzucci.weather.domain.AppSettings
import com.mazzucci.weather.domain.Place
import com.mazzucci.weather.domain.TempUnit
import com.mazzucci.weather.narration.ModelStatus
import com.mazzucci.weather.narration.Narration
import com.mazzucci.weather.narration.NarrationInput
import com.mazzucci.weather.narration.NarrationSource
import com.mazzucci.weather.narration.TemplateNarrator
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/**
 * Renders the app's screens with sample data. Regenerate the README images with:
 *   ./gradlew recordPaparazziDebug
 */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, showSystemUi = false)

    private val forecast = TestData.forecast()
    private val templateSummary = Narration(
        TemplateNarrator(Locale.US).describe(NarrationInput(sanFrancisco.name, forecast, TempUnit.F)),
        NarrationSource.TEMPLATE,
    )
    private val gemmaSummary = Narration(
        "A mild, partly cloudy afternoon at 71°, but grab an umbrella: rain moves in around 6 PM.",
        NarrationSource.GEMMA,
    )

    private fun weatherState(first: PageContent, place: Place? = sanFrancisco, key: String = sanFrancisco.id) =
        WeatherUiState(
            pages = listOf(
                PageUi(key, place, first),
                PageUi(london.id, london, PageContent.Loading),
                PageUi(tokyo.id, tokyo, PageContent.Loading),
            ),
            savedPlaces = listOf(sanFrancisco, london, tokyo),
        )

    private fun snap(name: String, night: Boolean = false, content: @Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(
            DeviceConfig.PIXEL_5.copy(nightMode = if (night) NightMode.NIGHT else NightMode.NOTNIGHT)
        )
        paparazzi.snapshot(name) { WeatherTheme(content) }
    }

    @Composable
    private fun Weather(state: WeatherUiState) {
        WeatherPagerScreen(
            state = state,
            pagerState = rememberPagerState { state.pages.size },
            onRefresh = {}, onRequestPermission = {}, onOpenSearch = {}, onOpenPlaces = {}, onOpenSettings = {},
        )
    }

    @Test fun weatherLight() = snap("weather_light") {
        Weather(weatherState(PageContent.Loaded(forecast, templateSummary)))
    }

    @Test fun weatherDark() = snap("weather_dark", night = true) {
        Weather(weatherState(PageContent.Loaded(forecast, templateSummary)))
    }

    @Test fun weatherGemma() = snap("weather_gemma") {
        Weather(weatherState(PageContent.Loaded(forecast, gemmaSummary)))
    }

    @Test fun permission() = snap("permission") {
        Weather(weatherState(PageContent.NeedsPermission, place = null, key = Place.CURRENT_LOCATION_ID))
    }

    @Test fun error() = snap("error") {
        Weather(weatherState(PageContent.Failed("Couldn't get your location. Is location turned on?"), place = null, key = Place.CURRENT_LOCATION_ID))
    }

    @Test fun search() = snap("search") {
        SearchScreen(
            search = SearchUi(
                query = "Springfield",
                results = listOf(
                    Place("4409896", "Springfield", "Missouri", "United States", 37.2, -93.3),
                    Place("4250542", "Springfield", "Illinois", "United States", 39.8, -89.6),
                    Place("4951788", "Springfield", "Massachusetts", "United States", 42.1, -72.6),
                ),
            ),
            savedIds = setOf("4250542"),
            onQueryChange = {}, onPick = {}, onBack = {},
            autoFocus = false,
        )
    }

    @Test fun places() = snap("places") {
        PlacesScreen(
            places = listOf(sanFrancisco, london, tokyo),
            useCurrentLocation = true,
            onUseCurrentLocationChange = {}, onMove = { _, _ -> }, onRemove = {}, onAdd = {}, onBack = {},
        )
    }

    @Test fun settings() = snap("settings") {
        SettingsScreen(
            settings = AppSettings(),
            modelStatus = ModelStatus.Installed(529L shl 20),
            onUnitChange = {}, onGemmaEnabledChange = {}, onDownloadModel = {}, onCancelDownload = {}, onImportModel = {}, onRemoveModel = {}, onBack = {},
        )
    }
}
