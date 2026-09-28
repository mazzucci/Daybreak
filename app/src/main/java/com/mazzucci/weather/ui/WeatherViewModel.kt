package com.mazzucci.weather.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.mazzucci.weather.narration.Narration
import com.mazzucci.weather.narration.NarrationInput
import com.mazzucci.weather.narration.NarrationSource
import com.mazzucci.weather.narration.TemplateNarrator
import com.mazzucci.weather.narration.ValidatingNarrator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PageContent {
    data object Loading : PageContent
    data object NeedsPermission : PageContent
    data class Failed(val message: String) : PageContent
    data class Loaded(val forecast: Forecast, val summary: Narration) : PageContent
}

/** One swipeable page. [place] is null for the current-location page until the location is known. */
data class PageUi(val key: String, val place: Place?, val content: PageContent)

data class SearchUi(
    val query: String = "",
    val results: List<Place> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class WeatherUiState(
    val pages: List<PageUi> = emptyList(),
    val savedPlaces: List<Place> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val search: SearchUi = SearchUi(),
    val modelStatus: ModelStatus = ModelStatus.NotInstalled,
)

class WeatherViewModel(
    private val api: WeatherApi,
    private val places: SavedPlacesRepository,
    private val settingsRepo: SettingsRepository,
    private val location: LocationProvider,
    private val model: LocalModelManager,
    /** Null when there's no LLM available at all (e.g. in tests or previews). */
    private val llm: ValidatingNarrator?,
    private val template: TemplateNarrator = TemplateNarrator(),
    private val searchDebounceMs: Long = 350,
) : ViewModel() {

    private val contents = MutableStateFlow<Map<String, PageContent>>(emptyMap())
    private val currentPlace = MutableStateFlow<Place?>(null)
    private val search = MutableStateFlow(SearchUi())
    private val jobs = mutableMapOf<String, Job>()
    private var searchJob: Job? = null

    val uiState: StateFlow<WeatherUiState> = combine(
        combine(places.places, settingsRepo.settings, ::Pair),
        contents,
        currentPlace,
        search,
        model.status,
    ) { (saved, settings), contents, current, search, modelStatus ->
        val pages = buildList {
            if (settings.useCurrentLocation) {
                add(PageUi(CURRENT, current, contents[CURRENT] ?: PageContent.Loading))
            }
            saved.forEach { add(PageUi(it.id, it, contents[it.id] ?: PageContent.Loading)) }
        }
        WeatherUiState(pages, saved, settings, search, modelStatus)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WeatherUiState())

    init {
        viewModelScope.launch {
            places.places.collect { list ->
                list.filter { it.id !in contents.value && it.id !in jobs }.forEach { load(it.id, it) }
            }
        }
        if (settingsRepo.settings.value.useCurrentLocation) refreshCurrentLocation()
        // A download can finish while the user is anywhere in the app; re-narrate as soon as the model lands.
        viewModelScope.launch {
            var wasInstalled = model.status.value is ModelStatus.Installed
            model.status.collect { status ->
                val installed = status is ModelStatus.Installed
                if (installed && !wasInstalled) refreshAll()
                wasInstalled = installed
            }
        }
    }

    // --- Pages ---------------------------------------------------------------------------------

    fun refresh(key: String) {
        if (key == CURRENT) refreshCurrentLocation()
        else places.places.value.firstOrNull { it.id == key }?.let { load(key, it) }
    }

    fun refreshAll() = uiState.value.pages.forEach { refresh(it.key) }

    /** Called with the result of the permission prompt (or the initial check). */
    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) refreshCurrentLocation() else setContent(CURRENT, PageContent.NeedsPermission)
    }

    private fun refreshCurrentLocation() {
        if (!location.hasPermission()) {
            setContent(CURRENT, PageContent.NeedsPermission)
            return
        }
        launchFor(CURRENT) {
            showLoadingIfEmpty(CURRENT)
            val place = location.currentPlace()
            if (place == null) {
                setContent(CURRENT, PageContent.Failed("Couldn't get your location. Is location turned on?"))
            } else {
                currentPlace.value = place
                fetchAndShow(CURRENT, place)
            }
        }
    }

    private fun load(key: String, place: Place) = launchFor(key) {
        showLoadingIfEmpty(key)
        fetchAndShow(key, place)
    }

    private suspend fun fetchAndShow(key: String, place: Place) {
        val forecast = try {
            api.forecast(place.latitude, place.longitude)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setContent(key, PageContent.Failed(e.message ?: "Couldn't load the weather"))
            return
        }
        val settings = settingsRepo.settings.value
        val input = NarrationInput(place.name, forecast, settings.primaryUnit)
        // Show the instant template summary first; swap in Gemma's if it produces a valid one.
        setContent(key, PageContent.Loaded(forecast, Narration(template.describe(input), NarrationSource.TEMPLATE)))
        if (llm != null && settings.gemmaEnabled && model.status.value is ModelStatus.Installed) {
            val narration = llm.narrate(input)
            if (narration.source == NarrationSource.GEMMA) {
                contents.update { map ->
                    val loaded = map[key] as? PageContent.Loaded
                    if (loaded?.forecast == forecast) map + (key to loaded.copy(summary = narration)) else map
                }
            }
        }
    }

    private fun launchFor(key: String, block: suspend () -> Unit) {
        jobs.remove(key)?.cancel()
        val job = viewModelScope.launch { block() }
        jobs[key] = job
        job.invokeOnCompletion { if (jobs[key] === job) jobs.remove(key) }
    }

    private fun showLoadingIfEmpty(key: String) {
        val existing = contents.value[key]
        if (existing !is PageContent.Loaded) setContent(key, PageContent.Loading)
    }

    private fun setContent(key: String, content: PageContent) = contents.update { it + (key to content) }

    // --- Search & saved places ------------------------------------------------------------------

    fun onSearchQueryChange(query: String) {
        search.update { it.copy(query = query, error = null) }
        searchJob?.cancel()
        if (query.isBlank() || query.trim().length < 2) {
            search.update { it.copy(results = emptyList(), loading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(searchDebounceMs)
            search.update { it.copy(loading = true) }
            try {
                val results = api.searchPlaces(query)
                search.update {
                    it.copy(results = results, loading = false, error = if (results.isEmpty()) "No places found" else null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                search.update { it.copy(loading = false, error = e.message ?: "Search failed") }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        search.value = SearchUi()
    }

    /** Saves [place] and returns its page index, so the UI can scroll to it. */
    fun addPlace(place: Place): Int {
        places.add(place)
        clearSearch()
        // Computed from the repository rather than uiState, which updates asynchronously.
        val offset = if (settingsRepo.settings.value.useCurrentLocation) 1 else 0
        return places.places.value.indexOfFirst { it.id == place.id } + offset
    }

    fun removePlace(id: String) {
        places.remove(id)
        jobs.remove(id)?.cancel()
        contents.update { it - id }
    }

    fun movePlace(from: Int, to: Int) = places.move(from, to)

    // --- Settings -------------------------------------------------------------------------------

    fun setPrimaryUnit(unit: TempUnit) {
        settingsRepo.update { it.copy(primaryUnit = unit) }
        refreshAll() // Summaries are written in the primary unit.
    }

    fun setUseCurrentLocation(enabled: Boolean) {
        settingsRepo.update { it.copy(useCurrentLocation = enabled) }
        if (enabled) refreshCurrentLocation()
    }

    fun setGemmaEnabled(enabled: Boolean) {
        settingsRepo.update { it.copy(gemmaEnabled = enabled) }
        refreshAll()
    }

    /** Starts downloading Gemma from Hugging Face; summaries switch over automatically once it's installed. */
    fun downloadModel(hfToken: String) {
        viewModelScope.launch { model.download(hfToken) }
    }

    fun cancelModelDownload() = model.cancelDownload()

    fun importModel(uri: String) {
        viewModelScope.launch { model.import(uri) }
    }

    fun removeModel() {
        model.remove()
        refreshAll()
    }

    companion object {
        const val CURRENT = Place.CURRENT_LOCATION_ID
    }
}
