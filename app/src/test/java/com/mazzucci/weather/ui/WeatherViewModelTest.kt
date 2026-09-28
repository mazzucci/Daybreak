package com.mazzucci.weather.ui

import com.mazzucci.weather.TestData
import com.mazzucci.weather.TestData.london
import com.mazzucci.weather.TestData.sanFrancisco
import com.mazzucci.weather.TestData.tokyo
import com.mazzucci.weather.data.InMemoryStore
import com.mazzucci.weather.data.LocationProvider
import com.mazzucci.weather.data.SavedPlacesRepository
import com.mazzucci.weather.data.SettingsRepository
import com.mazzucci.weather.data.WeatherApi
import com.mazzucci.weather.domain.AppSettings
import com.mazzucci.weather.domain.Forecast
import com.mazzucci.weather.domain.Place
import com.mazzucci.weather.domain.TempUnit
import com.mazzucci.weather.narration.LocalModelManager
import com.mazzucci.weather.narration.ModelStatus
import com.mazzucci.weather.narration.NarrationSource
import com.mazzucci.weather.narration.TemplateNarrator
import com.mazzucci.weather.narration.ValidatingNarrator
import com.mazzucci.weather.narration.MemeWriter
import com.mazzucci.weather.data.MemeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
        val searches = mutableListOf<String>()
        var searchResults: List<Place> = emptyList()
        var forecastCalls = 0

        override suspend fun forecast(latitude: Double, longitude: Double): Forecast {
            forecastCalls++
            if (failing) throw IOException("Weather service returned HTTP 503")
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
    private val llm = ValidatingNarrator({ input -> gemmaPlaces += input.placeName; gemmaReply }, TemplateNarrator(Locale.US))

    private var memeReply = "TOP: Fog rolls in\nBOTTOM: Bridge has left"
    private var memeCalls = 0
    private val memeWriter = MemeWriter({ _, _, _ -> memeCalls++; memeReply })

    private fun TestScope.viewModel(
        settings: AppSettings = AppSettings(),
        modelInstalled: Boolean = false,
        model: FakeModel = FakeModel(modelInstalled),
    ): WeatherViewModel {
        val repo = SettingsRepository(store, settings)
        return WeatherViewModel(
            api, places, repo, location, model, llm, TemplateNarrator(Locale.US),
            memeWriter = memeWriter, memes = MemeRepository(store),
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
