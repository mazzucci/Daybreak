package app.daybreak.ui

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
import app.daybreak.R
import app.daybreak.TestData
import app.daybreak.TestData.london
import app.daybreak.TestData.sanFrancisco
import app.daybreak.TestData.tokyo
import app.daybreak.domain.AppSettings
import app.daybreak.domain.ClockFormat
import app.daybreak.domain.Place
import app.daybreak.domain.TempUnit
import app.daybreak.narration.ModelStatus
import app.daybreak.narration.NarrationInput
import app.daybreak.narration.NarrationSource
import app.daybreak.domain.Term
import app.daybreak.domain.explain
import app.daybreak.domain.weekOutlook
import androidx.compose.runtime.CompositionLocalProvider
import app.daybreak.domain.Clock
import app.daybreak.domain.describeSky
import app.daybreak.domain.moonPhase
import app.daybreak.domain.upcomingPersonalDates
import app.daybreak.domain.PersonalDate
import app.daybreak.data.parseLongWeekends
import app.daybreak.data.parsePublicHolidays
import app.daybreak.domain.Holiday
import app.daybreak.domain.LongWeekend
import app.daybreak.domain.comingUp
import app.daybreak.narration.Meme
import app.daybreak.narration.MemeMood
import app.daybreak.narration.TemplateMemes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import app.daybreak.domain.Explanation
import app.daybreak.narration.TemplateNarrator
import app.daybreak.domain.Badge
import app.daybreak.domain.Habit
import app.daybreak.domain.HabitColor
import app.daybreak.domain.HabitDraft
import app.daybreak.domain.HabitKind
import app.daybreak.domain.HabitPeriod
import app.daybreak.domain.HabitsData
import app.daybreak.domain.summarize
import org.junit.Rule
import org.junit.Test

/**
 * Renders the app's screens with sample data. Regenerate the README images with:
 *   ./gradlew recordPaparazziDebug
 */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, showSystemUi = false)

    private val forecast = TestData.forecast()
    private val templateSummary = TemplateNarrator().describe(NarrationInput(sanFrancisco.name, forecast, TempUnit.F))
    private val rainyNight = TestData.rainyNight()
    private val rainyNightSummary = TemplateNarrator().describe(NarrationInput(london.name, rainyNight, TempUnit.C))

    /**
     * The 2026 US calendar from the fixtures plus a made-up holiday two days out, so the card shows all three
     * rows: a holiday with its forecast, the Thanksgiving weekend with its day of leave, and the next season.
     */
    private val sanFranciscoComingUp = run {
        val today = forecast.current.time.toLocalDate()
        comingUp(
            today,
            parsePublicHolidays(TestData.fixture("holidays_us_2026.json")) + Holiday(today.plusDays(2), "Founders Day"),
            parseLongWeekends(TestData.fixture("long_weekends_us_2026.json")),
            latitude = sanFrancisco.latitude,
        )
    }

    /** A long weekend a week out (the last day of the forecast) and the next season, for the dark page. */
    private val londonComingUp = run {
        val today = rainyNight.current.time.toLocalDate()
        comingUp(
            today,
            listOf(Holiday(today.plusDays(7), "Autumn bank holiday")),
            listOf(LongWeekend(today.plusDays(5), today.plusDays(7), 3, emptyList())),
            latitude = london.latitude,
        )
    }

    private fun weatherState(
        first: PageContent,
        place: Place? = sanFrancisco,
        key: String = sanFrancisco.id,
        settings: AppSettings = AppSettings(),
    ) = WeatherUiState(
        // Page keys are unique in the app (the pager relies on it), so the other places skip the first one.
        pages = listOf(PageUi(key, place, first)) +
            listOf(london, tokyo).filter { it.id != key }.map { PageUi(it.id, it, PageContent.Loading) },
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

    /** The moment the sample pages are viewed: 2:30 PM in San Francisco, 8 minutes after [fetched]. */
    private val viewedAt = java.time.Instant.parse("2026-09-28T21:30:00Z")
    private val fetched = viewedAt.minusSeconds(8 * 60)

    @Composable
    private fun Weather(state: WeatherUiState, now: java.time.Instant = viewedAt) {
        WeatherPagerScreen(
            state = state,
            pagerState = rememberPagerState { state.pages.size },
            onRefresh = {}, onRequestPermission = {}, onUseCurrentLocation = {},
            onOpenSearch = {}, onOpenPlaces = {},
            now = now,
        )
    }

    /** The Jungfraujoch fixture (3,200 m): evening showers today, a wet night, then snow on the 8th and 9th. */
    private val alps = TestData.alps()

    @Composable
    private fun Day(date: java.time.LocalDate, unit: TempUnit, forecast: app.daybreak.domain.Forecast = alps) {
        DayScreen("Jungfraujoch", forecast, date, unit, onBack = {})
    }

    @Composable
    private fun Settings(status: ModelStatus, settings: AppSettings = AppSettings(), weekStart: java.time.DayOfWeek? = null) {
        SettingsScreen(
            today = forecast.current.time.toLocalDate(),
            settings = settings,
            habitsWeekStart = weekStart,
            modelStatus = status,
            onUnitChange = {}, onGemmaEnabledChange = {}, onMemesEnabledChange = {}, onComingUpEnabledChange = {}, onDownloadModel = {}, onCancelDownload = {},
            onImportModel = {}, onRemoveModel = {}, onBack = null,
        )
    }

    @Composable
    private fun Home(
        state: WeatherUiState,
        now: java.time.LocalDateTime = forecast.current.time,
        habits: HabitsData? = null,
        celebration: Celebration? = null,
    ) {
        HomeScreen(
            state, onOpenWeather = {}, onRefresh = {}, onRequestPermission = {}, onOpenSearch = {}, onOpenSettings = {},
            now = now, zone = java.time.ZoneId.of("America/Los_Angeles"),
            habits = habits?.let { summarize(it, now.toLocalDate()) }, celebration = celebration,
        )
    }

    // --- Home -----------------------------------------------------------------------------------

    /**
     * Everything on: greeting on the place's sky, the glance, today's habits, what's coming up (with your dates),
     * tonight's sky and the meme.
     */
    @Test fun homeFull() = snap("home_full", tall = true) {
        val today = forecast.current.time.toLocalDate()
        Home(
            weatherState(
                PageContent.Loaded(forecast, templateSummary, TemplateMemes.pick(MemeMood.RAIN, 0), sanFranciscoComingUp),
                settings = AppSettings(
                    personalDates = listOf(PersonalDate(today.plusDays(1), name = "Board presentation")),
                ),
            ),
            habits = habitsData,
        )
    }

    @Test fun homeDark() = snap("home_dark", night = true, tall = true) {
        Home(
            weatherState(
                PageContent.Loaded(
                    rainyNight, rainyNightSummary,
                    Meme("Me: I'll just run to the car", "London: bold of you", MemeMood.RAIN, NarrationSource.GEMMA),
                    londonComingUp,
                ),
                place = london, key = london.id,
                settings = AppSettings(primaryUnit = TempUnit.C),
            ),
            now = rainyNight.current.time,
        )
    }

    /** The current-location page waiting for permission: the glance asks, the rest of Home waits. */
    @Test fun homePermission() = snap("home_permission") {
        Home(
            weatherState(PageContent.NeedsPermission, place = null, key = Place.CURRENT_LOCATION_ID, settings = AppSettings(comingUpEnabled = false))
                .let { it.copy(pages = it.pages.take(1)) },
        )
    }

    @Test fun homeLoading() = snap("home_loading") {
        Home(weatherState(PageContent.Loading, settings = AppSettings(comingUpEnabled = false)).let { it.copy(pages = it.pages.take(1)) })
    }

    /** No places yet, and every Home card off: the glance offers a place, and a line points to Settings. */
    @Test fun homeEmpty() = snap("home_empty") {
        Home(WeatherUiState(settings = AppSettings(comingUpEnabled = false, memesEnabled = false, skyEnabled = false)))
    }

    /** The moon through its cycle, each with its card's words, in both themes' card colours. */
    @Test fun skyCards() = snap("sky_cards", tall = true) {
        val zone = java.time.ZoneId.of("America/Los_Angeles")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("2026-10-14", "2026-10-18", "2026-10-22", "2026-10-26", "2026-10-28", "2026-11-02", "2026-11-06", "2026-11-09").forEach { d ->
                val at = java.time.Instant.parse("${d}T20:00:00Z")
                SkyCard(moonPhase(at), describeSky(at, zone, null, use24Hour = false))
            }
        }
    }

    @Test fun skyCardDark() = snap("sky_card_dark", night = true) {
        val at = java.time.Instant.parse("2026-10-18T20:00:00Z")
        Column(Modifier.padding(16.dp)) { SkyCard(moonPhase(at), describeSky(at, java.time.ZoneId.of("America/Los_Angeles"), forecast, use24Hour = false)) }
    }

    // --- Clocks ---------------------------------------------------------------------------------

    private val clocks = listOf(
        Clock("geo:683506", "Bucharest", "Bucharest, Romania", "Europe/Bucharest"),
        Clock("geo:2643743", "London", "England, United Kingdom", "Europe/London"),
        Clock("geo:1850147", "Tokyo", "Tokyo, Japan", "Asia/Tokyo"),
        Clock("geo:1275339", "Mumbai", "Maharashtra, India", "Asia/Kolkata"),
    )

    /** 2:42 PM on a Monday in Los Angeles: Bucharest is past midnight, Tokyo is tomorrow morning, Mumbai is +12½ h. */
    private val clocksNow = java.time.Instant.parse("2026-09-28T21:42:00Z")

    @Composable
    private fun Clocks(list: List<Clock>, editing: Boolean = false) {
        ClocksScreen(
            list, onAdd = {}, onRemove = {}, onMove = { _, _ -> },
            here = java.time.ZoneId.of("America/Los_Angeles"), now = clocksNow, initiallyEditing = editing,
        )
    }

    /** Edit mode, with a clock whose zone this phone doesn't know (it can only be removed). */
    @Test fun clocksEdit() = snap("clocks_edit") { Clocks(clocks.take(3) + Clock("x", "Atlantis", zoneId = "Atlantis/Lost_City"), editing = true) }

    @Test fun clocksEditLargeFont() = snap("clocks_edit_large_font", narrow = true, fontScale = 1.5f) { Clocks(clocks.take(3), editing = true) }

    @Test fun clocksList() = snap("clocks", tall = true) { Clocks(clocks) }

    @Test fun clocksDark() = snap("clocks_dark", night = true, tall = true) { Clocks(clocks) }

    @Test fun clocksEmpty() = snap("clocks_empty") { Clocks(emptyList()) }

    @Test fun clocksLargeFont() = snap("clocks_large_font", narrow = true, fontScale = 1.5f, tall = true) { Clocks(clocks.take(2)) }

    @Test fun bottomBar() = snap("bottom_bar") {
        Column(Modifier.padding(vertical = 16.dp)) {
            DaybreakNavigationBar(Tab.Home) {}
            Spacer(Modifier.height(16.dp))
            DaybreakNavigationBar(Tab.Weather) {}
            Spacer(Modifier.height(16.dp))
            DaybreakNavigationBar(Tab.Habits) {}
            Spacer(Modifier.height(16.dp))
            DaybreakNavigationBar(Tab.Clocks) {}
        }
    }

    /** Five labels on the narrowest phone at the largest font: they stop growing at 1.3x and stay on one line. */
    @Test fun bottomBarLargeFont() = snap("bottom_bar_large_font", narrow = true, fontScale = 2f) {
        Column(Modifier.padding(vertical = 16.dp)) {
            DaybreakNavigationBar(Tab.Habits) {}
            Spacer(Modifier.height(16.dp))
            DaybreakNavigationBar(Tab.Settings) {}
        }
    }

    @Test fun bottomBarDark() = snap("bottom_bar_dark", night = true) {
        Column(Modifier.padding(vertical = 16.dp)) {
            DaybreakNavigationBar(Tab.Settings) {}
            Spacer(Modifier.height(16.dp))
            DaybreakNavigationBar(Tab.Habits) {}
        }
    }

    // --- Habits ---------------------------------------------------------------------------------

    private val habitsToday = forecast.current.time.toLocalDate() // Monday, September 28

    /** [pattern] gives the count [i] days ago, from [days] days back to today. */
    private fun history(days: Int, pattern: (Int) -> Int): Map<java.time.LocalDate, Int> =
        (0..days).associate { habitsToday.minusDays(it.toLong()) to pattern(it) }.filterValues { it > 0 }

    /**
     * Five habits: water half done today on a 12-day streak, exercise once this week, reading just done (a
     * 7-day milestone), no takeout for 12 days, and coffee within its daily allowance, with Sunday-first weeks as
     * on a US phone. With the points and a badge banked from a deleted habit, that's level 6.
     */
    private val habitsData = HabitsData(
        habits = listOf(
            Habit(
                "water", "Drink water", HabitColor.BLUE, HabitKind.BUILD, HabitPeriod.DAY, 8, habitsToday.minusDays(83),
                history(83) { i -> if (i == 0) 5 else if (i <= 12) 8 + i % 2 else if (i % 9 == 4) 0 else (i * 7) % 6 + 3 },
            ),
            Habit(
                "exercise", "Exercise", HabitColor.GREEN, HabitKind.BUILD, HabitPeriod.WEEK, 3, habitsToday.minusDays(60),
                history(60) { i -> if (i == 0) 1 else if (i in 1..6) 0 else if (i % 7 in setOf(1, 3, 5) && i / 7 != 4) 1 else 0 },
            ),
            Habit(
                "read", "Read 20 pages", HabitColor.PURPLE, HabitKind.BUILD, HabitPeriod.DAY, 1, habitsToday.minusDays(20),
                history(20) { i -> if (i <= 6 || i in 9..14) 1 else 0 },
            ),
            Habit(
                "takeout", "No takeout", HabitColor.CORAL, HabitKind.AVOID, HabitPeriod.WEEK, 0, habitsToday.minusDays(70),
                history(70) { i -> if (i == 12 || i == 30 || i == 31 || i == 55) 1 else 0 },
            ),
            Habit(
                "coffee", "Coffee", HabitColor.AMBER, HabitKind.AVOID, HabitPeriod.DAY, 2, habitsToday.minusDays(40),
                history(40) { i -> if (i == 0) 1 else if (i % 6 == 2) 3 else 2 - i % 3 % 2 },
            ),
        ),
        bankedPoints = 120,
        bankedBadges = setOf(Badge.WEEKS_4),
        weekStart = java.time.DayOfWeek.SUNDAY,
    )

    @Composable
    private fun Habits(data: HabitsData, celebration: Celebration? = null, editing: Boolean = false) {
        HabitsScreen(
            summarize(data, habitsToday), celebration, onLog = {}, onUndo = {}, onAdd = {}, onUpdate = { _, _ -> }, onRemove = {},
            onMove = { _, _ -> }, onCelebrationShown = {}, initiallyEditing = editing,
        )
    }

    private val readCheer = Celebration("read", "7-day streak! +61", 1)

    @Test fun habitsEmpty() = snap("habits_empty") { Habits(HabitsData()) }

    @Test fun habitsList() = snap("habits", tall = true) { Habits(habitsData, readCheer) }

    @Test fun habitsDark() = snap("habits_dark", night = true, tall = true) { Habits(habitsData, readCheer) }

    @Test fun habitsLargeFont() = snap("habits_large_font", narrow = true, fontScale = 1.5f, tall = true) { Habits(habitsData) }

    @Test fun habitsEdit() = snap("habits_edit") { Habits(habitsData, editing = true) }

    /** The add/edit dialog over a dimmed tab, as it opens: new, and editing an avoid habit (dark, large font). */
    @Composable
    private fun EditorOver(initial: HabitDraft?) {
        Box(Modifier.fillMaxSize()) {
            Habits(habitsData)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)), contentAlignment = Alignment.Center) {
                HabitEditor(initial, onDismiss = {}, onSave = {}, modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }

    @Test fun habitEditorNew() = snap("habit_editor_new") { EditorOver(null) }

    @Test fun habitEditorEdit() = snap("habit_editor_edit_dark_large_font", night = true, narrow = true, fontScale = 1.5f) {
        EditorOver(HabitDraft("No takeout", HabitColor.CORAL, HabitKind.AVOID, HabitPeriod.WEEK, 0))
    }

    /** Home's card on its own, in both themes. */
    @Composable
    private fun HomeCards(night: Boolean) {
        val summary = summarize(habitsData, habitsToday)
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(20.dp)) {
            // Light: a cheer after the goal was met. Dark: the first log here, with the hint on how to take it back.
            HabitsHomeCard(summary, if (night) null else Celebration("read", "Done for today · +11", 1), onLog = {}, onUndo = {}, undoHint = night)
        }
    }

    @Test fun habitsHomeCard() = snap("habits_home_card") {
        Column {
            HomeCards(night = false)
            WeatherTheme(darkTheme = true) { HomeCards(night = true) }
        }
    }

    @Test fun habitsHomeCardLargeFont() = snap("habits_home_card_large_font", narrow = true, fontScale = 1.5f) { HomeCards(night = false) }

    // --- Weather page ---------------------------------------------------------------------------

    @Test fun weatherLight() = snap("weather_light") {
        Weather(weatherState(PageContent.Loaded(forecast, templateSummary)))
    }

    @Test fun weatherDark() = snap("weather_dark", night = true) {
        Weather(weatherState(PageContent.Loaded(forecast, templateSummary)))
    }

    @Test fun weatherFullPage() = snap("weather_full_page", tall = true) {
        Weather(weatherState(PageContent.Loaded(forecast, templateSummary, TemplateMemes.pick(MemeMood.RAIN, 0), sanFranciscoComingUp, fetchedAt = fetched)))
    }

    /**
     * Rain and snow amounts on a real forecast: the hourly strip with chances and amounts from 5 PM, and the 10-day
     * list with each day's total (snow days in cm of snow), the last three days lighter under "less certain".
     */
    @Test fun weatherAmounts() = snap("weather_amounts", tall = true) {
        val afternoon = alps.copy(current = alps.current.copy(time = java.time.LocalDateTime.of(2026, 10, 1, 15, 15), isDay = true, windDirectionDeg = 178.0))
        val place = Place("geo:2659811", "Jungfraujoch", "Bern", "Switzerland", 46.55, 7.98)
        val summary = TemplateNarrator().describe(NarrationInput(place.name, afternoon, TempUnit.C))
        Weather(
            weatherState(PageContent.Loaded(afternoon, summary, fetchedAt = fetched), place = place, key = place.id, settings = AppSettings(primaryUnit = TempUnit.C)),
        )
    }

    @Test fun weatherFullPageDark() = snap("weather_full_page_dark", night = true, tall = true) {
        Weather(
            weatherState(
                PageContent.Loaded(
                    rainyNight, rainyNightSummary,
                    Meme("Me: I'll just run to the car", "London: bold of you", MemeMood.RAIN, NarrationSource.GEMMA),
                    londonComingUp,
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
        val lows = listOf(-12.0, -8.5, 3.0, 12.0, 20.0, 24.0, 30.0, 18.0, 9.0, -3.0)
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

    @Test fun weatherRainyNight() = snap("weather_rainy_night") {
        Weather(
            weatherState(
                PageContent.Loaded(rainyNight, rainyNightSummary, fetchedAt = fetched),
                place = london, key = london.id,
                settings = AppSettings(primaryUnit = TempUnit.C),
            )
        )
    }

    /** A refresh that failed keeps the forecast and says so, in amber; under 2 km/h the wind is calm, with no arrow. */
    @Test fun weatherRefreshFailed() = snap("weather_refresh_failed") {
        val calm = rainyNight.copy(current = rainyNight.current.copy(windKmh = 1.2))
        Weather(
            now = fetched.plusSeconds(2 * 3600),
            state = weatherState(
                PageContent.Loaded(calm, rainyNightSummary, fetchedAt = fetched, refreshFailed = true),
                place = london, key = london.id,
                settings = AppSettings(primaryUnit = TempUnit.C),
            )
        )
    }

    /** Fetched two hours ago: the "updated" line turns amber and suggests pulling to refresh. */
    @Test fun weatherRainyNightDark() = snap("weather_rainy_night_dark", night = true) {
        Weather(
            now = fetched.plusSeconds(2 * 3600),
            state = weatherState(
                PageContent.Loaded(rainyNight, rainyNightSummary, fetchedAt = fetched),
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

    // --- Day details ----------------------------------------------------------------------------

    /** Today, with showers this evening and a wet night: the chart, the timing and both halves of the day. */
    @Test fun dayWet() = snap("day_wet", tall = true) { Day(java.time.LocalDate.of(2026, 10, 1), TempUnit.C) }

    /** A dry day five days out: "Rain unlikely" alone, no chart, no hedge. */
    @Test fun dayDry() = snap("day_dry", tall = true) { Day(java.time.LocalDate.of(2026, 10, 6), TempUnit.F) }

    /** A snow day a week out, in °F: the Snow card in inches, the hedge, and feels-like well below the air. */
    @Test fun daySnowy() = snap("day_snowy", tall = true) { Day(java.time.LocalDate.of(2026, 10, 8), TempUnit.F) }

    /** Tomorrow in the dark theme: rain overnight, clearing by morning. */
    @Test fun dayDark() = snap("day_dark", night = true, tall = true) { Day(java.time.LocalDate.of(2026, 10, 2), TempUnit.C) }

    /** The narrow screen at 1.5x: pills wrap, the chart drops its % signs, the rows and tiles grow instead of clipping. */
    @Test fun dayLargeFont() = snap("day_large_font", tall = true, narrow = true, fontScale = 1.5f) {
        Day(java.time.LocalDate.of(2026, 10, 9), TempUnit.F)
    }

    /** A real amount at a low chance: "A small chance of rain · up to 2 mm", and the one row it falls in. */
    @Test fun daySmallChance() = snap("day_small_chance", tall = true) { Day(java.time.LocalDate.of(2026, 10, 4), TempUnit.C) }

    /** Polar night: the rows read Early, Midday (7 AM to 7 PM) and Evening, and the Daylight tile replaces sun times. */
    @Test fun dayPolar() = snap("day_polar", tall = true) {
        val polar = alps.copy(days = alps.days.map { it.copy(sunrise = it.date.atStartOfDay(), sunset = it.date.atStartOfDay()) })
        Day(java.time.LocalDate.of(2026, 10, 1), TempUnit.C, polar)
    }

    /** Back on a day page after the app was stopped, while its forecast is fetched again. */
    @Test fun dayLoading() = snap("day_loading") { DayLoading(onBack = {}) }

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

    /** The whole page with the model installed, down to the Gemma section, with the habits' week start. */
    @Test fun settingsFull() = snap("settings_full", tall = true) {
        Settings(ModelStatus.Installed(529L shl 20), weekStart = java.time.DayOfWeek.SUNDAY)
    }

    /** "Weeks start on" on the narrowest phone at a large font: the three days stay on one line. */
    @Test fun settingsHabitsLargeFont() = snap("settings_habits_large_font", narrow = true, fontScale = 1.5f) {
        Settings(ModelStatus.NotInstalled, weekStart = java.time.DayOfWeek.MONDAY)
    }

    /**
     * The phone on the 24-hour clock: "15:00" in the 52dp hourly cards, "07:02" in the sun tiles, and the copy that
     * names an hour (the summary, "Now–18:00").
     */
    @Test fun weather24Hour() {
        ClockFormat.use24Hour = true
        try {
            snap("weather_24_hour", tall = true) {
                val summary = TemplateNarrator().describe(NarrationInput(sanFrancisco.name, forecast, TempUnit.F))
                Weather(
                    weatherState(
                        PageContent.Loaded(forecast, summary, comingUp = sanFranciscoComingUp),
                    )
                )
            }
        } finally {
            ClockFormat.use24Hour = false
        }
    }

    @Test fun comingUpCard() = snap("coming_up") {
        Column(Modifier.padding(vertical = 16.dp)) {
            ComingUpCard(sanFranciscoComingUp, forecast, TempUnit.F, Modifier.padding(horizontal = 16.dp))
        }
    }

    /** Narrow screen at 1.5x: the detail line wraps without stranding a separator, the countdown stays on one line. */
    /** A presentation and a week off (joined to the weekends either side) among the place's holidays. */
    @Test fun comingUpPersonalDates() = snap("coming_up_personal_dates") {
        val today = forecast.current.time.toLocalDate()
        val mine = listOf(
            PersonalDate(today.plusDays(1), name = "Board presentation"),
            PersonalDate(today.plusDays(5), today.plusDays(9), "Lisbon trip", dayOff = true),
        )
        Column(Modifier.padding(vertical = 16.dp)) {
            ComingUpCard(
                (sanFranciscoComingUp + upcomingPersonalDates(today, mine)).sortedBy { it.date },
                forecast, TempUnit.F, Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    /** A birthday, a presentation and a week off, listed under the holidays switch. */
    @Test fun settingsPersonalDates() = snap("settings_personal_dates", tall = true) {
        val today = forecast.current.time.toLocalDate()
        Settings(
            ModelStatus.Installed(529L shl 20),
            AppSettings(
                personalDates = listOf(
                    PersonalDate(today.plusDays(1), name = "Board presentation"),
                    PersonalDate(today.plusDays(12), today.plusDays(16), "Lisbon trip", dayOff = true),
                    PersonalDate(today.minusDays(40), name = "Mum's birthday", yearly = true),
                ),
            ),
        )
    }

    @Test fun comingUpCardLargeFont() = snap("coming_up_large_font", narrow = true, fontScale = 1.5f) {
        Column(Modifier.padding(vertical = 16.dp)) {
            ComingUpCard(sanFranciscoComingUp, forecast, TempUnit.F, Modifier.padding(horizontal = 16.dp))
        }
    }

    // --- This week ------------------------------------------------------------------------------

    /** The "This week" section as it sits on the Weather page, with "How it works" (explanations available). */
    @Composable
    private fun ThisWeek(f: app.daybreak.domain.Forecast, unit: TempUnit = TempUnit.C) {
        CompositionLocalProvider(LocalExplain provides {}) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                WeekOutlookSection(weekOutlook(f, unit), f.today.date, onOpenDay = {})
            }
        }
    }

    private val sanFranciscoWeek by lazy { app.daybreak.data.parseForecast(TestData.fixture("forecast_sf_week.json")) }

    /** A rainy spell midweek and a sunny weekend: the spell, the best day outlined, rain glyphs under the wet days. */
    @Test fun weekOutlookMixed() = snap("week_outlook_mixed") { ThisWeek(TestData.mixedWeek()) }

    /** San Francisco's dry, warm week (°F): all great, "Dry all week", no best day to single out. */
    @Test fun weekOutlookDry() = snap("week_outlook_dry") { ThisWeek(sanFranciscoWeek, TempUnit.F) }

    /** Ten wet days: every bar short and blue-grey, every day with a glyph. */
    @Test fun weekOutlookStayIn() = snap("week_outlook_stay_in") { ThisWeek(TestData.wetWeek()) }

    /** 7:20 PM: today's daylight is over, so the line is about tomorrow and today's bar is empty. */
    @Test fun weekOutlookEvening() = snap("week_outlook_evening") { ThisWeek(TestData.mixedWeek(TestData.monday.withHour(19).withMinute(20))) }

    @Test fun weekOutlookDark() = snap("week_outlook_dark", night = true) { ThisWeek(TestData.mixedWeek()) }

    /** The narrowest phone at 1.5x: "Today" gives way to three-letter names. */
    @Test fun weekOutlookLargeFont() = snap("week_outlook_large_font", narrow = true, fontScale = 1.5f) { ThisWeek(TestData.mixedWeek()) }

    /** And at 2x: initials, and a star for "Best". */
    @Test fun weekOutlookHugeFont() = snap("week_outlook_huge_font", narrow = true, fontScale = 2f) { ThisWeek(TestData.mixedWeek()) }

    /** The Weather page with the mixed week: "This week" between the hourly strip and the sun tiles. */
    @Test fun weatherThisWeek() = snap("weather_this_week", tall = true) {
        val f = TestData.mixedWeek()
        val place = Place("geo:2950159", "Berlin", "Land Berlin", "Germany", 52.52, 13.41, countryCode = "DE")
        val summary = TemplateNarrator().describe(NarrationInput(place.name, f, TempUnit.C))
        Weather(weatherState(PageContent.Loaded(f, summary, fetchedAt = fetched), place = place, key = place.id, settings = AppSettings(primaryUnit = TempUnit.C)))
    }

    /** "How This week works" for the mixed week's breezy Monday, and in the evening for tomorrow. */
    @Test fun explainWeek() = snap("explain_week", tall = true) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Sheet(explain(Term.WEEK, TestData.mixedWeek(), TempUnit.C))
            Sheet(explain(Term.WEEK, TestData.mixedWeek(TestData.monday.withHour(19).withMinute(20)), TempUnit.F))
        }
    }

    /** The sheet's content on the sheet's own colour and shape, with its handle, as ModalBottomSheet shows it. */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Sheet(explanation: Explanation) {
        Surface(
            color = MaterialTheme.explainSheetColor,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BottomSheetDefaults.DragHandle()
                ExplainContent(explanation)
            }
        }
    }

    /** One sheet without a gauge, the two scale bars (moisture and intensity) and the daylight arc mid-afternoon. */
    @Test fun explainSheets() = snap("explain_sheets", tall = true) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Sheet(explain(Term.FEELS_LIKE, forecast, TempUnit.F))
            Sheet(explain(Term.HUMIDITY, forecast, TempUnit.F))
            Sheet(explain(Term.UV, forecast, TempUnit.F))
            Sheet(explain(Term.SUN, forecast, TempUnit.F))
        }
    }

    /** Dark, narrow and 1.5x: the value stays on one line, the scale labels fit, and the arc is empty at night. */
    @Test fun explainSheetsDarkLargeFont() = snap("explain_sheets_dark_large_font", night = true, tall = true, narrow = true, fontScale = 1.5f) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Sheet(explain(Term.WIND, rainyNight, TempUnit.C))
            Sheet(explain(Term.RAIN_CHANCE, rainyNight, TempUnit.C))
            Sheet(explain(Term.SUN, rainyNight, TempUnit.C))
        }
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
