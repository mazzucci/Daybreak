package app.daybreak.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.daybreak.data.LocationProvider
import app.daybreak.data.SavedPlacesRepository
import app.daybreak.data.SettingsRepository
import app.daybreak.data.WeatherApi
import app.daybreak.domain.AppSettings
import app.daybreak.domain.WidgetSnapshot
import app.daybreak.domain.widgetSnapshotOf
import app.daybreak.widget.WidgetPublisher
import app.daybreak.domain.Countdown
import app.daybreak.domain.PersonalDate
import app.daybreak.domain.dayOffDates
import app.daybreak.domain.comingUp
import app.daybreak.domain.countryCodeOf
import app.daybreak.domain.Forecast
import app.daybreak.domain.Place
import app.daybreak.domain.TempUnit
import app.daybreak.data.HolidayRepository
import app.daybreak.data.MemeRepository
import app.daybreak.data.SavedMeme
import app.daybreak.narration.LocalModelManager
import app.daybreak.narration.Meme
import app.daybreak.narration.MemeWriter
import app.daybreak.narration.memeMoodOf
import app.daybreak.narration.ModelStatus
import app.daybreak.narration.NarrationInput
import app.daybreak.narration.NarrationSource
import app.daybreak.narration.TemplateNarrator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.coroutines.coroutineContext

/** What the widget should do after a UI change. */
internal sealed interface WidgetUpdate {
    data object Clear : WidgetUpdate
    data object Keep : WidgetUpdate
    data class Show(val snapshot: WidgetSnapshot) : WidgetUpdate
}

/** What the widget is drawn from: the first page with a forecast, as its page shows it. */
private sealed interface WidgetSource {
    data object Clear : WidgetSource
    data object Keep : WidgetSource
    /** [pageSummary] is there to notice a rewritten summary (the unit or the 12/24-hour clock changed). */
    data class Show(val place: Place, val forecast: Forecast, val unit: TempUnit, val pageSummary: String) : WidgetSource
}

/**
 * The widget mirrors the first page that has a forecast. With no pages at all it's cleared; while pages are loading or
 * failed it keeps what it had. Only a change to what it shows gets as far as [summary] (the template, which isn't
 * free), not the many unrelated UI changes (a search, a refresh starting, a meme).
 */
internal fun widgetUpdates(states: Flow<WeatherUiState>, summary: (NarrationInput) -> String): Flow<WidgetUpdate> =
    states
        .map { s ->
            // From the settings, not the page list: the very first UI state has no pages yet either.
            if (s.savedPlaces.isEmpty() && !s.settings.useCurrentLocation) return@map WidgetSource.Clear
            s.pages.firstNotNullOfOrNull { page ->
                val loaded = page.content as? PageContent.Loaded ?: return@firstNotNullOfOrNull null
                val place = page.place ?: return@firstNotNullOfOrNull null
                WidgetSource.Show(place, loaded.forecast, s.settings.primaryUnit, loaded.summary)
            } ?: WidgetSource.Keep
        }
        .distinctUntilChanged()
        .map { source ->
            when (source) {
                WidgetSource.Clear -> WidgetUpdate.Clear
                WidgetSource.Keep -> WidgetUpdate.Keep
                // The page's summary without the amount still to come, which the widget has no room for.
                is WidgetSource.Show -> WidgetUpdate.Show(
                    widgetSnapshotOf(
                        source.place, source.forecast, source.unit,
                        summary(NarrationInput(source.place.name, source.forecast, source.unit)), nowMillis = 0,
                    ),
                )
            }
        }
        .distinctUntilChanged()

sealed interface PageContent {
    data object Loading : PageContent
    data object NeedsPermission : PageContent
    data class Failed(val message: String) : PageContent
    /** [meme] is null while memes are off (or until the first one is written). */
    data class Loaded(
        val forecast: Forecast,
        /** The template summary line. */
        val summary: String,
        val meme: Meme? = null,
        /** Upcoming holidays, long weekends and the next season; empty while off or loading. */
        val comingUp: List<Countdown> = emptyList(),
        /** Public holiday dates for the place's country (this year and next), for the breaks your days off make. */
        val holidays: Set<LocalDate> = emptySet(),
        /**
         * When the app fetched [forecast], for the "Updated 8 min ago" line. Our own clock, not the forecast's
         * current.time (that's the model's time step). Null where it isn't known (previews).
         */
        val fetchedAt: Instant? = null,
        /** The last refresh failed, so [forecast] is the one from [fetchedAt]: the "updated" line says so. */
        val refreshFailed: Boolean = false,
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
    /**
     * False only for the placeholder before the ViewModel's first state, when [pages] is empty because nothing has been
     * read yet rather than because there are no places (so an open day page waits instead of closing).
     */
    val ready: Boolean = false,
)

class WeatherViewModel(
    private val api: WeatherApi,
    private val places: SavedPlacesRepository,
    private val settingsRepo: SettingsRepository,
    private val location: LocationProvider,
    private val model: LocalModelManager,
    private val template: TemplateNarrator = TemplateNarrator(),
    /** Writes the daily meme; without a generator (e.g. in tests or previews) only the hand-written ones. */
    private val memeWriter: MemeWriter = MemeWriter(),
    private val memes: MemeRepository? = null,
    private val holidays: HolidayRepository? = null,
    /** Receives what the first loaded page shows, for the home-screen widget. */
    private val widget: WidgetPublisher? = null,
    private val clock: () -> Long = System::currentTimeMillis,
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
        WeatherUiState(pages, saved, settings, search, modelStatus, ready = true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WeatherUiState())

    init {
        if (widget != null) {
            viewModelScope.launch {
                widgetUpdates(uiState) { template.describe(it, withTotal = false) }.collect { update ->
                    when (update) {
                        WidgetUpdate.Clear -> widget.clear()
                        WidgetUpdate.Keep -> Unit
                        is WidgetUpdate.Show -> widget.publish(update.snapshot.copy(writtenAtMillis = clock()))
                    }
                }
            }
        }
        viewModelScope.launch {
            places.places.collect { list ->
                list.filter { it.id !in contents.value && it.id !in jobs }.forEach { load(it.id, it) }
            }
        }
        if (settingsRepo.settings.value.useCurrentLocation) refreshCurrentLocation()
        // A download can finish while the user is anywhere in the app; give Gemma its try at the meme once it lands.
        viewModelScope.launch {
            var wasInstalled = model.status.value is ModelStatus.Installed
            model.status.collect { status ->
                val installed = status is ModelStatus.Installed
                if (installed && !wasInstalled) showMemesOnLoadedPages()
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
                showFailure(CURRENT, "Couldn't get your location. Is location turned on?")
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
            showFailure(key, e.message ?: "Couldn't load the weather")
            return
        } finally {
            // The (possibly slow) Gemma meme below isn't part of "refreshing". A cancelled fetch mustn't clear the
            // flag its replacement just set, hence the job check.
            if (jobs[key] === coroutineContext[Job]) fetching.update { it - key }
        }
        showForecast(key, place, forecast)
    }

    /** Shows [forecast] with its template summary and the day's meme, then gives Gemma its one try at the meme. */
    private suspend fun showForecast(key: String, place: Place, forecast: Forecast) {
        val input = narrationInput(place, forecast)
        val quickMeme = if (memesOn() && key == homePageKey()) savedOrTemplateMeme(key, place, input).meme else null
        setContent(key, PageContent.Loaded(forecast, template.describe(input), quickMeme, fetchedAt = Instant.ofEpochMilli(clock())))
        // Alongside the meme rather than before it: a slow holiday lookup must never hold up the page.
        viewModelScope.launch { showComingUp(key, place, forecast) }
        showMeme(key, place, input)
    }

    private fun narrationInput(place: Place, forecast: Forecast): NarrationInput =
        NarrationInput(place.name, forecast, settingsRepo.settings.value.primaryUnit)

    /**
     * Adds the countdowns for [place]'s country (seasons still show when the holiday lookup fails) and its holiday
     * dates, for the breaks your days off make.
     */
    private suspend fun showComingUp(key: String, place: Place, forecast: Forecast) {
        fun wanted() = settingsRepo.settings.value.comingUpEnabled
        if (!wanted()) return
        val today = forecast.current.time.toLocalDate()
        val year = countryCodeOf(place)?.let { cc -> holidays?.around(today, cc) }
        if (!wanted()) return // turned off while fetching
        val offDates = dayOffDates(settingsRepo.settings.value.personalDates, today)
        val items = comingUp(today, year?.holidays.orEmpty(), year?.longWeekends.orEmpty(), place.latitude, offDates = offDates)
        val dates = year?.holidays.orEmpty().map { it.date }.toSet()
        updateLoaded(key, forecast) { it.copy(comingUp = items, holidays = dates) }
    }

    /** Re-runs the holiday lookup for every loaded page (after Coming up is switched on or your dates change). */
    private fun refreshHolidays() {
        // Not through launchFor: a page busy with Gemma's meme still gets its card, and nothing gets cancelled. A page
        // that's refetching drops this result (updateLoaded checks the forecast) and adds its own.
        visiblePlaces().forEach { (key, place) ->
            val content = contents.value[key]
            if (content is PageContent.Loaded) viewModelScope.launch { showComingUp(key, place, content.forecast) }
        }
    }

    private fun gemmaReady(): Boolean =
        settingsRepo.settings.value.gemmaEnabled && model.status.value is ModelStatus.Installed

    private fun memesOn(): Boolean = settingsRepo.settings.value.memesEnabled

    private fun savedOrTemplateMeme(key: String, place: Place, input: NarrationInput): SavedMeme =
        memes?.get(key, input.forecast.today.date, memePlaceTag(place), memeMoodOf(input.forecast))
            ?: SavedMeme(memeWriter.template(input, key), gemmaTried = false)

    /**
     * Puts the day's meme on a loaded page (if memes are on), asks Gemma for one unless it already had its try at
     * this place, day and mood, and remembers the result so refreshes and restarts show the same meme.
     */
    private suspend fun showMeme(key: String, place: Place, input: NarrationInput) {
        // Only Home shows a meme, for the page it follows: don't spend Gemma on places nobody sees one for.
        if (!memesOn() || key != homePageKey()) return
        val forecast = input.forecast
        val saved = savedOrTemplateMeme(key, place, input)
        updateLoaded(key, forecast) { it.copy(meme = saved.meme) }
        var result = saved
        if (!saved.gemmaTried && saved.meme.source != NarrationSource.GEMMA && memeWriter.canUseModel && gemmaReady()) {
            val gemma = memeWriter.fromModel(input, key)
            if (!memesOn()) return // turned off while Gemma was writing: don't bring the card back
            // Gemma switched off or its model removed meanwhile: keep the hand-written one, and let Gemma try again later.
            if (!gemmaReady()) return
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
     * Rewrites the summary of every loaded page from the forecast it already has, without touching the network. Used when
     * something the template's wording depends on changes (the unit, the phone's 12- or 24-hour clock).
     */
    private fun renarrateAll() {
        // Built from the sources rather than uiState, which updates asynchronously.
        visiblePlaces().forEach { (key, place) ->
            val content = contents.value[key] as? PageContent.Loaded ?: return@forEach
            updateLoaded(key, content.forecast) { it.copy(summary = template.describe(narrationInput(place, it.forecast))) }
        }
    }

    /** The page Home follows (see glancePageIndex): the first, unless it's waiting for location permission. */
    private fun homePageKey(): String? {
        val keys = visiblePlaces().map { it.first }
        return keys.firstOrNull { contents.value[it] !is PageContent.NeedsPermission } ?: keys.firstOrNull()
    }

    /** Page keys and places currently shown, in page order. */
    private fun visiblePlaces(): List<Pair<String, Place>> = buildList {
        if (settingsRepo.settings.value.useCurrentLocation) currentPlace.value?.let { add(CURRENT to it) }
        places.places.value.forEach { add(it.id to it) }
    }

    /**
     * Adds memes to loaded pages after they're turned on (or Gemma becomes available for them), without refetching.
     * Pages with a job in flight add theirs when that job reaches its meme step.
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

    /**
     * A failed fetch: a page that already shows a forecast keeps it and says the refresh failed (in its "updated"
     * line); only a page with nothing to show gets the error card.
     */
    private fun showFailure(key: String, message: String) = contents.update { map ->
        val loaded = map[key] as? PageContent.Loaded
        map + (key to (loaded?.copy(refreshFailed = true) ?: PageContent.Failed(message)))
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

    /** The phone switched between 12- and 24-hour time: summaries mention times, so rewrite them. */
    fun onClockFormatChanged() = renarrateAll()

    /**
     * Adds [date], dropping one-off dates that are over. The dates' card is computed from settings; the holiday
     * rows are refreshed so a booked day of leave stops being suggested.
     */
    fun addPersonalDate(date: PersonalDate) = updatePersonalDates { it + date }

    fun removePersonalDate(date: PersonalDate) = updatePersonalDates { it - date }

    private fun updatePersonalDates(change: (List<PersonalDate>) -> List<PersonalDate>) {
        val today = LocalDate.now()
        settingsRepo.update { s -> s.copy(personalDates = change(s.personalDates).filter { it.next(today) != null }.distinct().sortedBy { it.start }) }
        if (settingsRepo.settings.value.comingUpEnabled) refreshHolidays()
    }

    fun setComingUpEnabled(enabled: Boolean) {
        settingsRepo.update { it.copy(comingUpEnabled = enabled) }
        if (enabled) {
            refreshHolidays()
        } else {
            contents.update { map -> map.mapValues { (_, c) -> if (c is PageContent.Loaded) c.copy(comingUp = emptyList()) else c } }
        }
    }

    /** Tonight's sky is computed from the clock and the forecast the page already has: nothing to fetch. */
    fun setSkyEnabled(enabled: Boolean) = settingsRepo.update { it.copy(skyEnabled = enabled) }

    fun setHabitsOnHome(enabled: Boolean) = settingsRepo.update { it.copy(habitsOnHome = enabled) }

    fun setMemesEnabled(enabled: Boolean) {
        settingsRepo.update { it.copy(memesEnabled = enabled) }
        if (enabled) showMemesOnLoadedPages() else contents.update { map ->
            map.mapValues { (_, c) -> if (c is PageContent.Loaded) c.copy(meme = null) else c }
        }
    }

    /** Switching Gemma on gives it its try at today's meme; switching it off keeps the meme already shown. */
    fun setGemmaEnabled(enabled: Boolean) {
        settingsRepo.update { it.copy(gemmaEnabled = enabled) }
        if (enabled) showMemesOnLoadedPages()
    }

    /** Starts downloading Gemma from Hugging Face; the meme switches over automatically once it's installed. */
    fun downloadModel(hfToken: String) {
        viewModelScope.launch { model.download(hfToken) }
    }

    fun cancelModelDownload() = model.cancelDownload()

    fun importModel(uri: String) {
        viewModelScope.launch { model.import(uri) }
    }

    fun removeModel() {
        model.remove()
        memeWriter.releaseResources()
    }

    override fun onCleared() {
        memeWriter.close()
    }

    companion object {
        const val CURRENT = Place.CURRENT_LOCATION_ID
    }
}
