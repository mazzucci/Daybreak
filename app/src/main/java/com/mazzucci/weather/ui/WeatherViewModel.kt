package com.mazzucci.weather.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazzucci.weather.data.LocationProvider
import com.mazzucci.weather.data.SavedPlacesRepository
import com.mazzucci.weather.data.SettingsRepository
import com.mazzucci.weather.data.WeatherApi
import com.mazzucci.weather.domain.Activity
import com.mazzucci.weather.domain.AppSettings
import com.mazzucci.weather.domain.Countdown
import com.mazzucci.weather.domain.comingUp
import com.mazzucci.weather.domain.countryCodeOf
import com.mazzucci.weather.domain.Forecast
import com.mazzucci.weather.domain.Place
import com.mazzucci.weather.domain.capAboutMe
import com.mazzucci.weather.domain.TempUnit
import com.mazzucci.weather.domain.Tone
import com.mazzucci.weather.data.HolidayRepository
import com.mazzucci.weather.data.MemeRepository
import com.mazzucci.weather.data.SavedMeme
import com.mazzucci.weather.narration.LocalModelManager
import com.mazzucci.weather.narration.Meme
import com.mazzucci.weather.narration.MemeWriter
import com.mazzucci.weather.narration.memeMoodOf
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
import java.util.Locale
import kotlin.coroutines.coroutineContext

sealed interface PageContent {
    data object Loading : PageContent
    data object NeedsPermission : PageContent
    data class Failed(val message: String) : PageContent
    /** [meme] is null while memes are off (or until the first one is written). */
    data class Loaded(
        val forecast: Forecast,
        val summary: Narration,
        val meme: Meme? = null,
        /** Upcoming holidays, long weekends and the next season; empty while off or loading. */
        val comingUp: List<Countdown> = emptyList(),
    ) : PageContent
}

/**
 * One swipeable page. [place] is null for the current-location page until the location is known.
 * [refreshing] is true while a fetch is in flight for a page that already shows a forecast.
 */
data class PageUi(val key: String, val place: Place?, val content: PageContent, val refreshing: Boolean = false)

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
    private val memeWriter: MemeWriter = MemeWriter(),
    private val memes: MemeRepository? = null,
    private val holidays: HolidayRepository? = null,
    private val searchDebounceMs: Long = 350,
) : ViewModel() {

    private val contents = MutableStateFlow<Map<String, PageContent>>(emptyMap())
    /** Keys whose forecast is being fetched right now; drives the pull-to-refresh indicator. */
    private val fetching = MutableStateFlow<Set<String>>(emptySet())
    private val currentPlace = MutableStateFlow<Place?>(null)
    private val search = MutableStateFlow(SearchUi())
    private val jobs = mutableMapOf<String, Job>()
    private var searchJob: Job? = null

    val uiState: StateFlow<WeatherUiState> = combine(
        combine(places.places, settingsRepo.settings, ::Pair),
        combine(contents, fetching, ::Pair),
        currentPlace,
        search,
        model.status,
    ) { (saved, settings), (contents, fetching), current, search, modelStatus ->
        val pages = buildList {
            if (settings.useCurrentLocation) {
                add(PageUi(CURRENT, current, contents[CURRENT] ?: PageContent.Loading, CURRENT in fetching))
            }
            saved.forEach { add(PageUi(it.id, it, contents[it.id] ?: PageContent.Loading, it.id in fetching)) }
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
                if (installed && !wasInstalled) renarrateAll()
                wasInstalled = installed
            }
        }
    }

    // --- Pages ---------------------------------------------------------------------------------

    fun refresh(key: String) {
        if (key == CURRENT) refreshCurrentLocation()
        else places.places.value.firstOrNull { it.id == key }?.let { load(key, it) }
    }

    /** Whether the app still needs the location permission (the one place this is checked). */
    fun needsLocationPermission(): Boolean = !location.hasPermission()

    /** Whether to ask for the location permission when the app opens. */
    fun shouldRequestLocationOnStart(): Boolean =
        settingsRepo.settings.value.useCurrentLocation && needsLocationPermission()

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
        } finally {
            // The (possibly slow) Gemma step below isn't part of "refreshing". A cancelled fetch mustn't clear the
            // flag its replacement just set, hence the job check.
            if (jobs[key] === coroutineContext[Job]) fetching.update { it - key }
        }
        showForecast(key, place, forecast)
    }

    /**
     * Shows [forecast] with the instant template summary (and the day's meme), then swaps in Gemma's summary if it
     * produces a valid one, then gives Gemma its one try at the day's meme.
     */
    private suspend fun showForecast(key: String, place: Place, forecast: Forecast) {
        val settings = settingsRepo.settings.value
        val input = narrationInput(place, forecast)
        val quickMeme = if (settings.memesEnabled) savedOrTemplateMeme(key, place, input).meme else null
        setContent(key, PageContent.Loaded(forecast, Narration(template.describe(input), NarrationSource.TEMPLATE), quickMeme))
        // Alongside Gemma rather than before it: a slow holiday lookup must never hold up the summary.
        viewModelScope.launch { showComingUp(key, place, forecast) }
        if (gemmaReady()) {
            val narration = llm!!.narrate(input)
            if (narration.source == NarrationSource.GEMMA) updateLoaded(key, forecast) { it.copy(summary = narration) }
        }
        // Re-reads the setting: memes may have been turned on or off while Gemma was busy.
        showMeme(key, place, input)
    }

    private fun narrationInput(place: Place, forecast: Forecast): NarrationInput {
        val settings = settingsRepo.settings.value
        return NarrationInput(place.name, forecast, settings.primaryUnit, settings.tone, settings.aboutMe)
    }

    /** Adds the countdowns for [place]'s country; seasons still show when the holiday lookup fails. */
    private suspend fun showComingUp(key: String, place: Place, forecast: Forecast) {
        if (!settingsRepo.settings.value.comingUpEnabled) return
        val today = forecast.current.time.toLocalDate()
        val year = countryCodeOf(place)?.let { cc -> holidays?.around(today, cc) }
        if (!settingsRepo.settings.value.comingUpEnabled) return // turned off while fetching
        val items = comingUp(today, year?.holidays.orEmpty(), year?.longWeekends.orEmpty(), place.latitude)
        updateLoaded(key, forecast) { it.copy(comingUp = items) }
    }

    private fun gemmaReady(): Boolean =
        llm != null && settingsRepo.settings.value.gemmaEnabled && model.status.value is ModelStatus.Installed

    private fun memesOn(): Boolean = settingsRepo.settings.value.memesEnabled

    private fun savedOrTemplateMeme(key: String, place: Place, input: NarrationInput): SavedMeme =
        memes?.get(key, input.forecast.today.date, memePlaceTag(place), memeMoodOf(input.forecast))
            ?: SavedMeme(memeWriter.template(input, key), gemmaTried = false)

    /**
     * Puts the day's meme on a loaded page (if memes are on), asks Gemma for one unless it already had its try at
     * this place, day and mood, and remembers the result so refreshes and restarts show the same meme.
     */
    private suspend fun showMeme(key: String, place: Place, input: NarrationInput) {
        if (!memesOn()) return
        val forecast = input.forecast
        val saved = savedOrTemplateMeme(key, place, input)
        updateLoaded(key, forecast) { it.copy(meme = saved.meme) }
        var result = saved
        if (!saved.gemmaTried && saved.meme.source != NarrationSource.GEMMA && memeWriter.canUseModel && gemmaReady()) {
            val gemma = memeWriter.fromModel(input, key)
            if (!memesOn()) return // turned off while Gemma was writing: don't bring the card back
            gemma?.let { m -> updateLoaded(key, forecast) { it.copy(meme = m) } }
            result = SavedMeme(gemma ?: saved.meme, gemmaTried = true)
        }
        memes?.put(key, forecast.today.date, memePlaceTag(place), result)
    }

    /** Identifies the place a meme was written for, so the current-location page doesn't keep another city's. */
    private fun memePlaceTag(place: Place): String =
        String.format(Locale.ROOT, "%s|%.2f|%.2f", place.name, place.latitude, place.longitude)

    private fun updateLoaded(key: String, forecast: Forecast, change: (PageContent.Loaded) -> PageContent.Loaded) =
        contents.update { map ->
            val loaded = map[key] as? PageContent.Loaded
            if (loaded?.forecast == forecast) map + (key to change(loaded)) else map
        }

    /**
     * Rewrites the summary of every loaded page from its cached forecast, without touching the network. Used when
     * something that only affects the wording changes (unit, Gemma on/off, model installed/removed).
     */
    private fun renarrateAll() {
        // Visible pages only, in page order, so the page the user is most likely looking at goes first (Gemma
        // generates one summary at a time). Built from the sources rather than uiState, which updates asynchronously.
        visiblePlaces().forEach { (key, place) ->
            val content = contents.value[key]
            // A page that's still fetching will narrate with the new settings when its forecast arrives.
            if (content is PageContent.Loaded && key !in fetching.value) {
                launchFor(key, fetch = false) { showForecast(key, place, content.forecast) }
            }
        }
    }

    /** Page keys and places currently shown, in page order. */
    private fun visiblePlaces(): List<Pair<String, Place>> = buildList {
        if (settingsRepo.settings.value.useCurrentLocation) currentPlace.value?.let { add(CURRENT to it) }
        places.places.value.forEach { add(it.id to it) }
    }

    /**
     * Adds memes to loaded pages after they're turned on, without re-running the summaries. Pages with a job in
     * flight add theirs when that job reaches its meme step.
     */
    private fun showMemesOnLoadedPages() {
        visiblePlaces().forEach { (key, place) ->
            val content = contents.value[key]
            if (content is PageContent.Loaded && key !in jobs) {
                launchFor(key, fetch = false) { showMeme(key, place, narrationInput(place, content.forecast)) }
            }
        }
    }

    /** Runs [block] as the only job for [key], replacing any running one. [fetch] drives the refresh indicator. */
    private fun launchFor(key: String, fetch: Boolean = true, block: suspend () -> Unit) {
        jobs.remove(key)?.cancel()
        if (fetch) fetching.update { it + key }
        val job = viewModelScope.launch { block() }
        jobs[key] = job
        job.invokeOnCompletion {
            if (jobs[key] === job) {
                jobs.remove(key)
                fetching.update { it - key }
            }
        }
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
        memes?.remove(id)
        jobs.remove(id)?.cancel()
        contents.update { it - id }
        fetching.update { it - id }
    }

    fun movePlace(from: Int, to: Int) = places.move(from, to)

    // --- Settings -------------------------------------------------------------------------------

    fun setPrimaryUnit(unit: TempUnit) {
        settingsRepo.update { it.copy(primaryUnit = unit) }
        renarrateAll() // Summaries are written in the primary unit.
    }

    fun setUseCurrentLocation(enabled: Boolean) {
        settingsRepo.update { it.copy(useCurrentLocation = enabled) }
        if (enabled) {
            refreshCurrentLocation()
        } else {
            // The page is gone; don't keep fetching or narrating for it.
            jobs.remove(CURRENT)?.cancel()
            memes?.remove(CURRENT)
            contents.update { it - CURRENT }
            fetching.update { it - CURRENT }
        }
    }

    fun setTone(tone: Tone) {
        if (tone == settingsRepo.settings.value.tone) return
        settingsRepo.update { it.copy(tone = tone) }
        renarrateAll()
    }

    fun setAboutMe(text: String) {
        val trimmed = capAboutMe(text)
        if (trimmed == settingsRepo.settings.value.aboutMe) return
        settingsRepo.update { it.copy(aboutMe = trimmed) }
        renarrateAll()
    }

    /** Only changes the activity card, which is computed from the cached forecast: nothing to refetch or re-narrate. */
    fun setActivity(activity: Activity?) = settingsRepo.update { it.copy(activity = activity) }

    fun setComingUpEnabled(enabled: Boolean) {
        settingsRepo.update { it.copy(comingUpEnabled = enabled) }
        if (enabled) {
            // Not through launchFor: a page busy with Gemma still gets its card, and nothing gets cancelled. A page
            // that's refetching drops this result (updateLoaded checks the forecast) and adds its own.
            visiblePlaces().forEach { (key, place) ->
                val content = contents.value[key]
                if (content is PageContent.Loaded) viewModelScope.launch { showComingUp(key, place, content.forecast) }
            }
        } else {
            contents.update { map -> map.mapValues { (_, c) -> if (c is PageContent.Loaded) c.copy(comingUp = emptyList()) else c } }
        }
    }

    fun setMemesEnabled(enabled: Boolean) {
        settingsRepo.update { it.copy(memesEnabled = enabled) }
        if (enabled) showMemesOnLoadedPages() else contents.update { map ->
            map.mapValues { (_, c) -> if (c is PageContent.Loaded) c.copy(meme = null) else c }
        }
    }

    fun setGemmaEnabled(enabled: Boolean) {
        settingsRepo.update { it.copy(gemmaEnabled = enabled) }
        renarrateAll()
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
        llm?.releaseResources()
        renarrateAll()
    }

    override fun onCleared() {
        llm?.close()
    }

    companion object {
        const val CURRENT = Place.CURRENT_LOCATION_ID
    }
}
