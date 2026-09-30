package app.daybreak.ui

import app.daybreak.TestData
import app.daybreak.TestData.london
import app.daybreak.TestData.sanFrancisco
import app.daybreak.TestData.tokyo
import app.daybreak.data.InMemoryStore
import app.daybreak.data.LocationProvider
import app.daybreak.data.SavedPlacesRepository
import app.daybreak.data.SettingsRepository
import app.daybreak.data.WeatherApi
import app.daybreak.domain.AppSettings
import app.daybreak.domain.CommuteEnd
import app.daybreak.domain.CommuteSettings
import app.daybreak.domain.PersonalDate
import app.daybreak.narration.NarrationInput
import app.daybreak.domain.ABOUT_ME_MAX_CHARS
import app.daybreak.domain.Tone
import app.daybreak.domain.Forecast
import app.daybreak.domain.Place
import app.daybreak.domain.TempUnit
import app.daybreak.narration.LocalModelManager
import app.daybreak.narration.ModelStatus
import app.daybreak.narration.NarrationSource
import app.daybreak.narration.TemplateNarrator
import app.daybreak.narration.ValidatingNarrator
import app.daybreak.narration.MemeWriter
import app.daybreak.data.MemeRepository
import app.daybreak.domain.WidgetSnapshot
import app.daybreak.widget.WidgetPublisher
import java.time.LocalDate
import app.daybreak.domain.LongWeekend
import app.daybreak.domain.Holiday
import app.daybreak.data.HolidayRepository
import app.daybreak.data.HolidayApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeApi : WeatherApi {
        val forecasts = mutableMapOf<Double, Forecast>()
        var failing = false
        /** Latitudes whose forecast fails, for one place failing while others load. */
        val failingAt = mutableSetOf<Double>()
        val searches = mutableListOf<String>()
        var searchResults: List<Place> = emptyList()
        var forecastCalls = 0

        override suspend fun forecast(latitude: Double, longitude: Double): Forecast {
            forecastCalls++
            if (failing || latitude in failingAt) throw IOException("Weather service returned HTTP 503")
            return forecasts[latitude] ?: TestData.forecast()
        }

        override suspend fun searchPlaces(query: String): List<Place> {
            searches += query
            return searchResults
        }
    }

    private class FakeLocation(var granted: Boolean = true, var place: Place? = here) : LocationProvider {
        override fun hasPermission() = granted
        override suspend fun currentPlace() = place
    }

    private class FakeModel(installed: Boolean) : LocalModelManager {
        override val status = MutableStateFlow(if (installed) ModelStatus.Installed(500L shl 20) else ModelStatus.NotInstalled)
        var lastToken: String? = null
        override suspend fun download(hfToken: String) {
            lastToken = hfToken
            status.value = ModelStatus.Downloading(0, 500L shl 20)
        }
        override fun cancelDownload() { status.value = ModelStatus.NotInstalled }
        override suspend fun import(uri: String) { status.value = ModelStatus.Installed(1) }
        override fun remove() { status.value = ModelStatus.NotInstalled }
    }

    private val api = FakeApi()
    private val location = FakeLocation()
    private val store = InMemoryStore()
    private val places = SavedPlacesRepository(store)
    private var gemmaReply = "71° and cloudy, with rain by 6 PM."
    private val gemmaPlaces = mutableListOf<String>()
    private var lastGemmaInput: NarrationInput? = null
    private val llm = ValidatingNarrator(
        { input -> gemmaPlaces += input.placeName; lastGemmaInput = input; gemmaReply },
        TemplateNarrator(Locale.US),
    )

    private val fakeHolidays = object : HolidayApi {
        override suspend fun publicHolidays(year: Int, countryCode: String) =
            if (countryCode == "US") listOf(Holiday(LocalDate.of(year, 11, 11), "Veterans Day")) else emptyList()
        override suspend fun longWeekends(year: Int, countryCode: String) = emptyList<LongWeekend>()
    }

    private val published = mutableListOf<WidgetSnapshot>()
    private var widgetCleared = 0

    private var memeReply = "TOP: Fog rolls in\nBOTTOM: Bridge has left"
    private var memeCalls = 0
    /** When set, the fake Gemma waits on it before answering, to test what happens meanwhile. */
    private var memeGate: CompletableDeferred<Unit>? = null
    private val memeWriter = MemeWriter({ _, _, _ -> memeCalls++; memeGate?.await(); memeReply })

    private fun TestScope.viewModel(
        settings: AppSettings = AppSettings(),
        modelInstalled: Boolean = false,
        model: FakeModel = FakeModel(modelInstalled),
    ): WeatherViewModel {
        val repo = SettingsRepository(store, settings)
        return WeatherViewModel(
            api, places, repo, location, model, llm, TemplateNarrator(Locale.US),
            memeWriter = memeWriter, memes = MemeRepository(store),
            holidays = HolidayRepository(fakeHolidays, store),
            widget = object : WidgetPublisher {
                override fun publish(snapshot: WidgetSnapshot) { published += snapshot }
                override fun clear() { widgetCleared++ }
            },
            clock = { 42L },
        ).also { advanceUntilIdle() }
    }

    private fun WeatherViewModel.meme(key: String) = (content(key) as PageContent.Loaded).meme

    private val WeatherViewModel.pages get() = uiState.value.pages
    private fun WeatherViewModel.content(key: String) = pages.first { it.key == key }.content

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `shows current location first, then saved places, all loaded`() = runTest(dispatcher) {
        places.add(sanFrancisco); places.add(london)
        val vm = viewModel()
        assertEquals(listOf(Place.CURRENT_LOCATION_ID, sanFrancisco.id, london.id), vm.pages.map { it.key })
        assertEquals(here, vm.pages.first().place)
        assertTrue(vm.pages.all { it.content is PageContent.Loaded })
    }

    @Test fun `current location page asks for permission, then loads once granted`() = runTest(dispatcher) {
        location.granted = false
        val vm = viewModel()
        assertEquals(PageContent.NeedsPermission, vm.content(WeatherViewModel.CURRENT))

        location.granted = true
        vm.onLocationPermissionResult(true)
        advanceUntilIdle()
        assertTrue(vm.content(WeatherViewModel.CURRENT) is PageContent.Loaded)
    }

    @Test fun `denied permission keeps the permission page`() = runTest(dispatcher) {
        location.granted = false
        val vm = viewModel()
        vm.onLocationPermissionResult(false)
        advanceUntilIdle()
        assertEquals(PageContent.NeedsPermission, vm.content(WeatherViewModel.CURRENT))
    }

    @Test fun `unknown location shows an error`() = runTest(dispatcher) {
        location.place = null
        val vm = viewModel()
        assertEquals(
            PageContent.Failed("Couldn't get your location. Is location turned on?"),
            vm.content(WeatherViewModel.CURRENT),
        )
    }

    @Test fun `current location can be turned off`() = runTest(dispatcher) {
        places.add(tokyo)
        val vm = viewModel(AppSettings(useCurrentLocation = false))
        assertEquals(listOf(tokyo.id), vm.pages.map { it.key })

        vm.setUseCurrentLocation(true)
        advanceUntilIdle()
        assertEquals(listOf(Place.CURRENT_LOCATION_ID, tokyo.id), vm.pages.map { it.key })
    }

    @Test fun `network errors show a message and refresh recovers`() = runTest(dispatcher) {
        places.add(london)
        api.failing = true
        val vm = viewModel(AppSettings(useCurrentLocation = false))
        assertEquals(PageContent.Failed("Weather service returned HTTP 503"), vm.content(london.id))

        api.failing = false
        vm.refresh(london.id)
        advanceUntilIdle()
        assertTrue(vm.content(london.id) is PageContent.Loaded)
    }

    @Test fun `summary uses the template when no model is installed`() = runTest(dispatcher) {
        places.add(london)
        val vm = viewModel(AppSettings(useCurrentLocation = false), modelInstalled = false)
        val summary = (vm.content(london.id) as PageContent.Loaded).summary
        assertEquals(NarrationSource.TEMPLATE, summary.source)
        assertTrue(summary.text.startsWith("71° and partly cloudy now"))
    }

    @Test fun `summary uses Gemma when installed, enabled and valid`() = runTest(dispatcher) {
        places.add(london)
        val vm = viewModel(AppSettings(useCurrentLocation = false), modelInstalled = true)
        val summary = (vm.content(london.id) as PageContent.Loaded).summary
        assertEquals(NarrationSource.GEMMA, summary.source)
        assertEquals("71° and cloudy, with rain by 6 PM.", summary.text)
    }

    @Test fun `summary keeps the template when Gemma is disabled or invents numbers`() = runTest(dispatcher) {
        places.add(london)
        val disabled = viewModel(AppSettings(useCurrentLocation = false, gemmaEnabled = false), modelInstalled = true)
        assertEquals(NarrationSource.TEMPLATE, (disabled.content(london.id) as PageContent.Loaded).summary.source)

        gemmaReply = "A balmy 88° all afternoon."
        val invented = viewModel(AppSettings(useCurrentLocation = false), modelInstalled = true)
        assertEquals(NarrationSource.TEMPLATE, (invented.content(london.id) as PageContent.Loaded).summary.source)
    }

    @Test fun `changing the unit rewrites the summary`() = runTest(dispatcher) {
        places.add(london)
        val vm = viewModel(AppSettings(primaryUnit = TempUnit.F, useCurrentLocation = false))
        val calls = api.forecastCalls
        vm.setPrimaryUnit(TempUnit.C)
        advanceUntilIdle()
        assertEquals("no network needed to rewrite summaries", calls, api.forecastCalls)
        val summary = (vm.content(london.id) as PageContent.Loaded).summary
        assertTrue(summary.text, summary.text.startsWith("21° and partly cloudy now"))
        assertEquals(TempUnit.C, vm.uiState.value.settings.primaryUnit)
    }

    @Test fun `turning Gemma off rewrites summaries from the cached forecast`() = runTest(dispatcher) {
        places.add(london)
        val vm = viewModel(AppSettings(useCurrentLocation = false), modelInstalled = true)
        assertEquals(NarrationSource.GEMMA, (vm.content(london.id) as PageContent.Loaded).summary.source)
        val calls = api.forecastCalls

        vm.setGemmaEnabled(false)
        advanceUntilIdle()
        assertEquals(NarrationSource.TEMPLATE, (vm.content(london.id) as PageContent.Loaded).summary.source)
        assertEquals(calls, api.forecastCalls)
    }

    @Test fun `hidden current-location page is dropped and not re-narrated`() = runTest(dispatcher) {
        places.add(london)
        val vm = viewModel(modelInstalled = true)
        assertTrue(here.name in gemmaPlaces)

        vm.setUseCurrentLocation(false)
        advanceUntilIdle()
        assertEquals(listOf(london.id), vm.pages.map { it.key })
        gemmaPlaces.clear()

        vm.setPrimaryUnit(TempUnit.C)
        advanceUntilIdle()
        assertEquals(listOf("London"), gemmaPlaces)
    }

    @Test fun `location permission is answered by the provider`() = runTest(dispatcher) {
        location.granted = false
        val vm = viewModel()
        assertTrue(vm.needsLocationPermission())
        assertTrue(vm.shouldRequestLocationOnStart())
        location.granted = true
        assertFalse(vm.needsLocationPermission())

        location.granted = false
        val noCurrent = viewModel(AppSettings(useCurrentLocation = false))
        assertFalse(noCurrent.shouldRequestLocationOnStart())
    }

    @Test fun `search is debounced and uses the latest query`() = runTest(dispatcher) {
        api.searchResults = listOf(london)
        val vm = viewModel()
        vm.onSearchQueryChange("Lo")
        advanceTimeBy(100)
        vm.onSearchQueryChange("Lon")
        advanceTimeBy(100)
        vm.onSearchQueryChange("London")
        advanceUntilIdle()
        assertEquals(listOf("London"), api.searches)
        assertEquals(listOf(london), vm.uiState.value.search.results)
        assertNull(vm.uiState.value.search.error)
    }

    @Test fun `short queries don't search and empty results say so`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onSearchQueryChange("L")
        advanceUntilIdle()
        assertTrue(api.searches.isEmpty())

        vm.onSearchQueryChange("Nowhereville")
        advanceUntilIdle()
        assertEquals("No places found", vm.uiState.value.search.error)
    }

    @Test fun `adding a place saves it, loads it and returns its page index`() = runTest(dispatcher) {
        places.add(london)
        val vm = viewModel()
        vm.onSearchQueryChange("Tokyo")
        val index = vm.addPlace(tokyo)
        advanceUntilIdle()
        assertEquals(2, index)
        assertEquals(tokyo.id, vm.pages[index].key)
        assertTrue(vm.content(tokyo.id) is PageContent.Loaded)
        assertEquals("", vm.uiState.value.search.query)
        assertEquals(listOf(london, tokyo), SavedPlacesRepository(store).places.value) // persisted
    }

    @Test fun `removing and reordering places updates the pages`() = runTest(dispatcher) {
        places.add(sanFrancisco); places.add(london); places.add(tokyo)
        val vm = viewModel(AppSettings(useCurrentLocation = false))
        vm.movePlace(2, 0)
        advanceUntilIdle()
        assertEquals(listOf(tokyo.id, sanFrancisco.id, london.id), vm.pages.map { it.key })

        vm.removePlace(sanFrancisco.id)
        advanceUntilIdle()
        assertEquals(listOf(tokyo.id, london.id), vm.pages.map { it.key })
    }

    @Test fun `importing a model switches summaries to Gemma`() = runTest(dispatcher) {
        places.add(london)
        val vm = viewModel(AppSettings(useCurrentLocation = false), modelInstalled = false)
        val calls = api.forecastCalls
        vm.importModel("content://picked/gemma3-1b-it-int4.task")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.modelStatus is ModelStatus.Installed)
        assertEquals(NarrationSource.GEMMA, (vm.content(london.id) as PageContent.Loaded).summary.source)

        vm.removeModel()
        advanceUntilIdle()
        assertEquals(NarrationSource.TEMPLATE, (vm.content(london.id) as PageContent.Loaded).summary.source)
        assertEquals(calls, api.forecastCalls)
    }

    @Test fun `a finished download switches summaries to Gemma without a manual refresh`() = runTest(dispatcher) {
        places.add(london)
        val model = FakeModel(installed = false)
        val vm = viewModel(AppSettings(useCurrentLocation = false), model = model)

        vm.downloadModel("hf_token")
        advanceUntilIdle()
        assertEquals("hf_token", model.lastToken)
        assertTrue(vm.uiState.value.modelStatus is ModelStatus.Downloading)
        assertEquals(NarrationSource.TEMPLATE, (vm.content(london.id) as PageContent.Loaded).summary.source)

        model.status.value = ModelStatus.Installed(500L shl 20) // DownloadManager reports completion later
        advanceUntilIdle()
        assertEquals(NarrationSource.GEMMA, (vm.content(london.id) as PageContent.Loaded).summary.source)
    }

    @Test fun `changing the voice rewrites summaries without refetching`() = runTest(dispatcher) {
        places.add(sanFrancisco)
        val vm = viewModel(AppSettings(useCurrentLocation = false))
        val fetches = api.forecastCalls
        vm.setTone(Tone.PIRATE)
        advanceUntilIdle()
        val summary = (vm.content(sanFrancisco.id) as PageContent.Loaded).summary.text
        assertTrue(summary, summary.startsWith("Ahoy"))
        assertEquals(fetches, api.forecastCalls)
    }

    @Test fun `the note about me reaches Gemma, trimmed and capped`() = runTest(dispatcher) {
        places.add(sanFrancisco)
        val vm = viewModel(AppSettings(useCurrentLocation = false), modelInstalled = true)
        val calls = gemmaPlaces.size
        vm.setAboutMe("  I cycle to work  ")
        advanceUntilIdle()
        assertEquals("I cycle to work", lastGemmaInput?.aboutMe)
        assertEquals(calls + 1, gemmaPlaces.size)
        vm.setAboutMe("I cycle to work") // unchanged: nothing to redo
        advanceUntilIdle()
        assertEquals(calls + 1, gemmaPlaces.size)
        vm.setAboutMe("x".repeat(400))
        advanceUntilIdle()
        assertEquals(ABOUT_ME_MAX_CHARS, vm.uiState.value.settings.aboutMe.length)
    }

    @Test fun `pages count down to the country's next holiday and season, and the switch hides them`() = runTest(dispatcher) {
        places.add(sanFrancisco)
        val vm = viewModel(AppSettings(useCurrentLocation = false))
        val items = (vm.content(sanFrancisco.id) as PageContent.Loaded).comingUp
        assertEquals(listOf("Veterans Day", "First day of winter"), items.map { it.title })
        vm.setComingUpEnabled(false)
        advanceUntilIdle()
        assertTrue((vm.content(sanFrancisco.id) as PageContent.Loaded).comingUp.isEmpty())
        vm.setComingUpEnabled(true)
        advanceUntilIdle()
        assertEquals(2, (vm.content(sanFrancisco.id) as PageContent.Loaded).comingUp.size)
    }

    @Test fun `with a home set, the commute gets home's and the office's forecasts and home's holidays`() = runTest(dispatcher) {
        val homeForecast = TestData.forecast().let { it.copy(current = it.current.copy(tempC = 1.0)) }
        val officeForecast = TestData.forecast().let { it.copy(current = it.current.copy(tempC = 2.0)) }
        api.forecasts[sanFrancisco.latitude] = homeForecast
        api.forecasts[london.latitude] = officeForecast
        val vm = viewModel(AppSettings(commute = CommuteSettings(enabled = true, home = sanFrancisco)))
        assertEquals(homeForecast, vm.uiState.value.commute?.homeForecast)
        assertNull(vm.uiState.value.commute?.officeForecast)
        assertEquals(setOf(LocalDate.of(2026, 11, 11), LocalDate.of(2027, 11, 11)), vm.uiState.value.commute?.holidays)

        vm.setCommutePlace(CommuteEnd.OFFICE, london)
        advanceUntilIdle()
        val commute = vm.uiState.value.commute!!
        assertEquals(Place.COMMUTE_OFFICE_ID, commute.office?.id)
        assertEquals(officeForecast, commute.officeForecast)
        assertEquals(london.copy(id = Place.COMMUTE_OFFICE_ID), vm.uiState.value.settings.commute.office)

        // New times need nothing new; switching off drops the forecasts.
        val calls = api.forecastCalls
        vm.setCommute(vm.uiState.value.settings.commute.copy(leaveHour = 7))
        advanceUntilIdle()
        assertEquals(calls, api.forecastCalls)
        vm.setCommute(vm.uiState.value.settings.commute.copy(enabled = false))
        advanceUntilIdle()
        assertNull(vm.uiState.value.commute)
    }

    @Test fun `a failed refresh keeps the commute's forecasts`() = runTest(dispatcher) {
        val vm = viewModel(AppSettings(useCurrentLocation = false, commute = CommuteSettings(enabled = true, home = sanFrancisco)))
        val before = vm.uiState.value.commute
        assertTrue(before != null)
        api.failing = true
        vm.refresh("anything")
        advanceUntilIdle()
        assertEquals(before, vm.uiState.value.commute)
    }

    @Test fun `home's forecast failing lets the card fall back, and a failed office refresh keeps the office's`() = runTest(dispatcher) {
        api.failingAt += sanFrancisco.latitude
        val vm = viewModel(AppSettings(commute = CommuteSettings(enabled = true, home = sanFrancisco)))
        assertNull(vm.uiState.value.commute)
        assertTrue(vm.uiState.value.commuteUnavailable)

        api.failingAt.clear()
        vm.setCommutePlace(CommuteEnd.OFFICE, london)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.commuteUnavailable)
        val officeForecast = vm.uiState.value.commute?.officeForecast
        assertTrue(officeForecast != null)
        api.failingAt += london.latitude
        vm.refresh(WeatherViewModel.CURRENT)
        advanceUntilIdle()
        assertEquals(officeForecast, vm.uiState.value.commute?.officeForecast)
    }

    @Test fun `a lookup error goes once anything else changes`() = runTest(dispatcher) {
        location.place = null
        val vm = viewModel(AppSettings(commute = CommuteSettings(enabled = true)))
        vm.setCommutePlaceHere(CommuteEnd.HOME)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.commuteLocating?.error != null)
        vm.setCommute(vm.uiState.value.settings.commute.copy(leaveHour = 7))
        advanceUntilIdle()
        assertNull(vm.uiState.value.commuteLocating)
    }

    @Test fun `use where I am now sets the end from the device location`() = runTest(dispatcher) {
        val vm = viewModel(AppSettings(commute = CommuteSettings(enabled = true)))
        assertNull(vm.uiState.value.commute) // no home yet: the card uses the first page
        vm.setCommutePlaceHere(CommuteEnd.HOME)
        advanceUntilIdle()
        assertNull(vm.uiState.value.commuteLocating)
        assertEquals(here.copy(id = Place.COMMUTE_HOME_ID), vm.uiState.value.settings.commute.home)
        assertTrue(vm.uiState.value.commute != null)

        location.place = null
        vm.setCommutePlaceHere(CommuteEnd.OFFICE)
        advanceUntilIdle()
        assertEquals(CommuteLocating(CommuteEnd.OFFICE, "Couldn't get your location. Is location turned on?"), vm.uiState.value.commuteLocating)
        assertNull(vm.uiState.value.settings.commute.office)

        location.granted = false
        vm.setCommutePlaceHere(CommuteEnd.OFFICE)
        runCurrent()
        assertEquals(CommuteLocating(CommuteEnd.OFFICE, "Location access is off for this app."), vm.uiState.value.commuteLocating)
    }

    @Test fun `adding a date keeps them sorted and drops one-off dates that are over`() = runTest(dispatcher) {
        val today = LocalDate.now()
        val past = PersonalDate(today.minusDays(10), name = "Old talk")
        val birthday = PersonalDate(today.minusDays(40), name = "Birthday", yearly = true)
        val vm = viewModel(AppSettings(personalDates = listOf(past, birthday)))
        val trip = PersonalDate(today.plusDays(3), today.plusDays(5), "Trip", dayOff = true)
        vm.addPersonalDate(trip)
        runCurrent()
        assertEquals(listOf(birthday, trip), vm.uiState.value.settings.personalDates)
        vm.removePersonalDate(birthday)
        runCurrent()
        assertEquals(listOf(trip), vm.uiState.value.settings.personalDates)
    }

    @Test fun `only Home's place gets a meme`() = runTest(dispatcher) {
        places.add(sanFrancisco); places.add(london)
        val vm = viewModel(AppSettings(useCurrentLocation = false))
        assertTrue(vm.meme(sanFrancisco.id) != null)
        assertNull(vm.meme(london.id))
    }

    @Test fun `the widget mirrors the first loaded page, and Gemma's summary when it lands`() = runTest(dispatcher) {
        places.add(sanFrancisco); places.add(london)
        viewModel(AppSettings(useCurrentLocation = false), modelInstalled = true)
        val last = published.last()
        assertEquals("San Francisco", last.placeName)
        assertEquals(gemmaReply, last.summary)
        assertTrue(last.summaryByGemma)
        assertEquals(42L, last.writtenAtMillis)
        assertTrue(published.none { it.placeName == "London" })
    }

    @Test fun `removing the last place clears the widget, a failed load keeps it`() = runTest(dispatcher) {
        places.add(sanFrancisco)
        val vm = viewModel(AppSettings(useCurrentLocation = false))
        assertEquals("San Francisco", published.last().placeName)
        api.failing = true
        vm.refresh(sanFrancisco.id)
        advanceUntilIdle()
        assertEquals(0, widgetCleared) // still a page, just failed: keep the last good snapshot
        vm.removePlace(sanFrancisco.id)
        advanceUntilIdle()
        assertEquals(1, widgetCleared)
    }

    @Test fun `the widget falls back to the next page when location isn't available`() = runTest(dispatcher) {
        location.granted = false
        places.add(london)
        viewModel()
        assertEquals("London", published.last().placeName)
    }

    @Test fun `each page gets a template meme without the model`() = runTest(dispatcher) {
        places.add(sanFrancisco)
        val vm = viewModel(AppSettings(useCurrentLocation = false))
        assertEquals(NarrationSource.TEMPLATE, vm.meme(sanFrancisco.id)?.source)
        assertEquals(0, memeCalls)
    }

    @Test fun `Gemma writes the meme once a day and it survives a refresh and a restart`() = runTest(dispatcher) {
        places.add(sanFrancisco)
        val vm = viewModel(AppSettings(useCurrentLocation = false), modelInstalled = true)
        assertEquals("Fog rolls in", vm.meme(sanFrancisco.id)?.top)
        assertEquals(NarrationSource.GEMMA, vm.meme(sanFrancisco.id)?.source)
        assertEquals(1, memeCalls)

        vm.refresh(sanFrancisco.id)
        advanceUntilIdle()
        val again = viewModel(AppSettings(useCurrentLocation = false), modelInstalled = true)
        assertEquals("Fog rolls in", again.meme(sanFrancisco.id)?.top)
        assertEquals(1, memeCalls)
    }

    @Test fun `a rejected Gemma meme keeps the template one`() = runTest(dispatcher) {
        memeReply = "TOP: It is 71 degrees\nBOTTOM: wow"
        places.add(sanFrancisco)
        val vm = viewModel(AppSettings(useCurrentLocation = false), modelInstalled = true)
        assertEquals(NarrationSource.TEMPLATE, vm.meme(sanFrancisco.id)?.source)
        assertEquals(1, memeCalls)
    }

    @Test fun `a rejected Gemma meme isn't retried on refresh the same day`() = runTest(dispatcher) {
        memeReply = "TOP: 42\nBOTTOM: nope"
        places.add(sanFrancisco)
        val vm = viewModel(AppSettings(useCurrentLocation = false), modelInstalled = true)
        vm.refresh(sanFrancisco.id)
        advanceUntilIdle()
        assertEquals(1, memeCalls)
        assertEquals(NarrationSource.TEMPLATE, vm.meme(sanFrancisco.id)?.source)
    }

    @Test fun `turning memes off while Gemma writes one keeps them off`() = runTest(dispatcher) {
        memeGate = CompletableDeferred()
        places.add(sanFrancisco)
        val vm = viewModel(AppSettings(useCurrentLocation = false), modelInstalled = true)
        assertEquals(1, memeCalls) // waiting on the gate
        vm.setMemesEnabled(false)
        memeGate!!.complete(Unit)
        advanceUntilIdle()
        assertNull(vm.meme(sanFrancisco.id))
        assertNull(store.getString("meme:${sanFrancisco.id}"))
    }

    @Test fun `the current-location meme follows the place, not just the page`() = runTest(dispatcher) {
        val vm = viewModel(modelInstalled = true)
        assertEquals("Fog rolls in", vm.meme(WeatherViewModel.CURRENT)?.top)
        memeReply = "TOP: New town\nBOTTOM: Same rain"
        location.place = london.copy(id = Place.CURRENT_LOCATION_ID)
        vm.refresh(WeatherViewModel.CURRENT)
        advanceUntilIdle()
        assertEquals("New town", vm.meme(WeatherViewModel.CURRENT)?.top)
        assertEquals(2, memeCalls)
    }

    @Test fun `turning memes on keeps Gemma's summary and doesn't ask for it again`() = runTest(dispatcher) {
        places.add(sanFrancisco)
        val vm = viewModel(AppSettings(useCurrentLocation = false, memesEnabled = false), modelInstalled = true)
        val summaries = gemmaPlaces.size
        assertNull(vm.meme(sanFrancisco.id))
        vm.setMemesEnabled(true)
        advanceUntilIdle()
        assertEquals(NarrationSource.GEMMA, vm.meme(sanFrancisco.id)?.source)
        assertEquals(NarrationSource.GEMMA, (vm.content(sanFrancisco.id) as PageContent.Loaded).summary.source)
        assertEquals(summaries, gemmaPlaces.size)
    }

    @Test fun `removing a place forgets its meme`() = runTest(dispatcher) {
        places.add(sanFrancisco)
        val vm = viewModel(AppSettings(useCurrentLocation = false))
        assertTrue(store.getString("meme:${sanFrancisco.id}") != null)
        vm.removePlace(sanFrancisco.id)
        assertNull(store.getString("meme:${sanFrancisco.id}"))
    }

    @Test fun `turning memes off hides them without a refetch, and back on restores them`() = runTest(dispatcher) {
        places.add(sanFrancisco)
        val vm = viewModel(AppSettings(useCurrentLocation = false))
        val fetches = api.forecastCalls
        vm.setMemesEnabled(false)
        advanceUntilIdle()
        assertNull(vm.meme(sanFrancisco.id))
        vm.setMemesEnabled(true)
        advanceUntilIdle()
        assertEquals(NarrationSource.TEMPLATE, vm.meme(sanFrancisco.id)?.source)
        assertEquals(fetches, api.forecastCalls)
    }

    @Test fun `cancelling a download returns to not installed`() = runTest(dispatcher) {
        val model = FakeModel(installed = false)
        val vm = viewModel(model = model)
        vm.downloadModel("hf_token")
        advanceUntilIdle()
        vm.cancelModelDownload()
        advanceUntilIdle()
        assertEquals(ModelStatus.NotInstalled, vm.uiState.value.modelStatus)
    }

    private companion object {
        val here = Place(Place.CURRENT_LOCATION_ID, "Oakland", latitude = 37.8, longitude = -122.27)
    }
}
