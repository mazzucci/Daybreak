package com.mazzucci.weather.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.mazzucci.weather.R
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
import com.mazzucci.weather.domain.Tone
import com.mazzucci.weather.domain.Activity
import com.mazzucci.weather.domain.ActivityScorer
import com.mazzucci.weather.narration.Meme
import com.mazzucci.weather.narration.MemeMood
import com.mazzucci.weather.narration.TemplateMemes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
    private val rainyNight = TestData.rainyNight()
    private val rainyNightSummary = Narration(
        TemplateNarrator(Locale.US).describe(NarrationInput(london.name, rainyNight, TempUnit.C)),
        NarrationSource.TEMPLATE,
    )

    private fun weatherState(
        first: PageContent,
        place: Place? = sanFrancisco,
        key: String = sanFrancisco.id,
        settings: AppSettings = AppSettings(),
    ) = WeatherUiState(
        pages = listOf(
            PageUi(key, place, first),
            PageUi(london.id, london, PageContent.Loading),
            PageUi(tokyo.id, tokyo, PageContent.Loading),
        ),
        savedPlaces = listOf(sanFrancisco, london, tokyo),
        settings = settings,
    )

    /**
     * [tall] renders on a very tall screen so the whole scrolling page (down to the 7-day list) is visible.
     * [narrow] uses the 320dp width of the smallest phones; [fontScale] is the system "font size" setting.
     */
    private fun snap(
        name: String,
        night: Boolean = false,
        tall: Boolean = false,
        narrow: Boolean = false,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        val device = DeviceConfig.PIXEL_5
        paparazzi.unsafeUpdateConfig(
            device.copy(
                nightMode = if (night) NightMode.NIGHT else NightMode.NOTNIGHT,
                screenWidth = if (narrow) 320 * device.density.dpiValue / 160 else device.screenWidth,
                screenHeight = if (tall) device.screenHeight * 2 else device.screenHeight,
                fontScale = fontScale,
            )
        )
        paparazzi.snapshot(name) { WeatherTheme(darkTheme = night, content = content) }
    }

    @Composable
    private fun Weather(state: WeatherUiState) {
        WeatherPagerScreen(
            state = state,
            pagerState = rememberPagerState { state.pages.size },
            onRefresh = {}, onRequestPermission = {}, onUseCurrentLocation = {},
            onOpenSearch = {}, onOpenPlaces = {}, onOpenSettings = {},
        )
    }

    @Composable
    private fun Settings(status: ModelStatus, settings: AppSettings = AppSettings()) {
        SettingsScreen(
            settings = settings,
            modelStatus = status,
            onUnitChange = {}, onGemmaEnabledChange = {}, onMemesEnabledChange = {}, onToneChange = {}, onAboutMeChange = {}, onActivityChange = {}, onDownloadModel = {}, onCancelDownload = {},
            onImportModel = {}, onRemoveModel = {}, onBack = {},
        )
    }

    // --- Weather page ---------------------------------------------------------------------------

    @Test fun weatherLight() = snap("weather_light") {
        Weather(weatherState(PageContent.Loaded(forecast, templateSummary)))
    }

    @Test fun weatherDark() = snap("weather_dark", night = true) {
        Weather(weatherState(PageContent.Loaded(forecast, templateSummary)))
    }

    @Test fun weatherFullPage() = snap("weather_full_page", tall = true) {
        Weather(weatherState(PageContent.Loaded(forecast, templateSummary, TemplateMemes.pick(MemeMood.RAIN, 0))))
    }

    @Test fun weatherFullPageDark() = snap("weather_full_page_dark", night = true, tall = true) {
        Weather(
            weatherState(
                PageContent.Loaded(
                    rainyNight, rainyNightSummary,
                    Meme("Me: I'll just run to the car", "London: bold of you", MemeMood.RAIN, NarrationSource.GEMMA),
                ),
                place = london, key = london.id,
                settings = AppSettings(primaryUnit = TempUnit.C),
            )
        )
    }

    @Test fun weatherPolarNight() = snap("weather_polar_night", night = true, tall = true) {
        // Polar night: one Daylight tile instead of sunrise and sunset.
        val polar = rainyNight.copy(days = rainyNight.days.map { it.copy(sunrise = it.date.atStartOfDay(), sunset = it.date.atStartOfDay()) })
        Weather(
            weatherState(
                PageContent.Loaded(polar, rainyNightSummary),
                place = london, key = london.id,
                settings = AppSettings(primaryUnit = TempUnit.C),
            )
        )
    }

    /** Smallest supported width: the 7-day rows must still fit both units, the bar and the rain chance. */
    @Test fun weatherNarrow() = snap("weather_narrow", tall = true, narrow = true) {
        Weather(weatherState(PageContent.Loaded(forecast, templateSummary)))
    }

    /** Largest common font size, on the narrow screen: cells grow instead of clipping. */
    @Test fun weatherLargeFont() = snap("weather_large_font", tall = true, narrow = true, fontScale = 1.5f) {
        Weather(weatherState(PageContent.Loaded(forecast, templateSummary)))
    }

    /** Extremes on the narrow screen at 1.5x: negative and three-digit values must keep the 7-day columns aligned. */
    @Test fun weatherExtremesLargeFont() = snap("weather_extremes_large_font", tall = true, narrow = true, fontScale = 1.5f) {
        val lows = listOf(-12.0, -8.5, 3.0, 12.0, 20.0, 24.0, 30.0, 18.0)
        val extremes = forecast.copy(days = forecast.days.mapIndexed { i, d -> d.copy(lowC = lows[i], highC = lows[i] + 8.5) })
        Weather(
            weatherState(
                PageContent.Loaded(extremes, templateSummary),
                settings = AppSettings(primaryUnit = TempUnit.C),
            )
        )
    }

    @Test fun memeMoods() = snap("meme_moods", tall = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(MemeMood.HEAT, MemeMood.SNOW, MemeMood.STORM, MemeMood.FOG).forEach { mood ->
                MemeCard(TemplateMemes.pick(mood, 1))
            }
        }
    }

    /** Dark theme: the heat and cold backdrops must dim like the hero, and the Gemma tag must read on the surface. */
    @Test fun memeMoodsDark() = snap("meme_moods_dark", night = true, tall = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(MemeMood.HEAT, MemeMood.COLD, MemeMood.WIND, MemeMood.SUN).forEach { mood ->
                MemeCard(TemplateMemes.pick(mood, 0).copy(source = NarrationSource.GEMMA))
            }
        }
    }

    @Test fun weatherGemma() = snap("weather_gemma") {
        Weather(weatherState(PageContent.Loaded(forecast, gemmaSummary)))
    }

    @Test fun weatherRainyNight() = snap("weather_rainy_night") {
        Weather(
            weatherState(
                PageContent.Loaded(rainyNight, rainyNightSummary),
                place = london, key = london.id,
                settings = AppSettings(primaryUnit = TempUnit.C),
            )
        )
    }

    @Test fun weatherRainyNightDark() = snap("weather_rainy_night_dark", night = true) {
        Weather(
            weatherState(
                PageContent.Loaded(rainyNight, rainyNightSummary),
                place = london, key = london.id,
                settings = AppSettings(primaryUnit = TempUnit.C),
            )
        )
    }

    /** Ten pages: the indicator collapses to a "1 / 10" label so the four action buttons always fit. */
    @Test fun manyPages() = snap("many_pages") {
        val extra = (1..7).map { i -> Place("extra$i", "Place $i", null, null, 0.0, 0.0) }
        val base = weatherState(PageContent.Loaded(forecast, templateSummary))
        Weather(
            base.copy(
                pages = base.pages + extra.map { PageUi(it.id, it, PageContent.Loading) },
                savedPlaces = base.savedPlaces + extra,
            )
        )
    }

    @Test fun loading() = snap("loading") {
        Weather(weatherState(PageContent.Loading))
    }

    @Test fun permission() = snap("permission") {
        Weather(weatherState(PageContent.NeedsPermission, place = null, key = Place.CURRENT_LOCATION_ID))
    }

    @Test fun error() = snap("error") {
        Weather(
            weatherState(
                PageContent.Failed("Couldn't get your location. Is location turned on?"),
                place = null, key = Place.CURRENT_LOCATION_ID,
            )
        )
    }

    @Test fun empty() = snap("empty") {
        Weather(WeatherUiState(settings = AppSettings(useCurrentLocation = false)))
    }

    // --- Other screens --------------------------------------------------------------------------

    @Test fun search() = snap("search") {
        SearchScreen(
            search = SearchUi(
                query = "Springfield",
                results = listOf(
                    Place("geo:4409896", "Springfield", "Missouri", "United States", 37.2, -93.3),
                    Place("geo:4250542", "Springfield", "Illinois", "United States", 39.8, -89.6),
                    Place("geo:4951788", "Springfield", "Massachusetts", "United States", 42.1, -72.6),
                ),
            ),
            savedIds = setOf("geo:4250542"),
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

    @Test fun settingsNotInstalled() = snap("settings_not_installed") {
        Settings(ModelStatus.NotInstalled)
    }

    @Test fun settingsDownloading() = snap("settings_downloading") {
        Settings(ModelStatus.Downloading(downloadedBytes = 212L shl 20, totalBytes = 529L shl 20))
    }

    @Test fun settingsPaused() = snap("settings_paused") {
        Settings(
            ModelStatus.Downloading(
                downloadedBytes = 212L shl 20, totalBytes = 529L shl 20,
                pausedReason = "Waiting for a network connection",
            )
        )
    }

    @Test fun settingsFailed() = snap("settings_failed", night = true) {
        Settings(ModelStatus.Failed("Your Hugging Face account doesn't have access yet. Open the model page, accept the Gemma license, then try again."))
    }

    @Test fun settingsInstalled() = snap("settings_installed") {
        Settings(ModelStatus.Installed(529L shl 20))
    }

    @Test fun settingsVoice() = snap("settings_voice", tall = true) {
        Settings(
            ModelStatus.Installed(529L shl 20),
            AppSettings(tone = Tone.PIRATE, aboutMe = "I cycle to work and hate getting rained on"),
        )
    }

    @Test fun activityCards() = snap("activity_cards", tall = true) {
        val wet = rainyNight.copy(hours = rainyNight.hours.map { it.copy(precipChance = 90, code = 63) })
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ActivityCard(ActivityScorer.plan(forecast, Activity.CYCLING), TempUnit.F, forecast.current.time.toLocalDate())
            ActivityCard(ActivityScorer.plan(forecast, Activity.RUNNING), TempUnit.C, forecast.current.time.toLocalDate())
            ActivityCard(ActivityScorer.plan(wet, Activity.WALKING), TempUnit.C, wet.current.time.toLocalDate())
        }
    }

    @Test fun weatherPirate() = snap("weather_pirate") {
        val input = NarrationInput(sanFrancisco.name, forecast, TempUnit.F, Tone.PIRATE)
        Weather(weatherState(PageContent.Loaded(forecast, Narration(TemplateNarrator(Locale.US).describe(input), NarrationSource.TEMPLATE))))
    }

    /** The adaptive launcher icon, composited the way a circular launcher mask would show it. */
    @Test fun appIcon() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(screenWidth = 432, screenHeight = 432))
        paparazzi.snapshot("app_icon") {
            Box(Modifier.fillMaxSize().background(Color(0xFFF2F5F9)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(108.dp).clip(CircleShape)) {
                    Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, Modifier.fillMaxSize())
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = "Weather", Modifier.fillMaxSize())
                }
            }
        }
    }
}
