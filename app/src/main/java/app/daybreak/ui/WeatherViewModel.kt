package app.daybreak.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.daybreak.data.LocationProvider
import app.daybreak.data.SavedPlacesRepository
import app.daybreak.data.SettingsRepository
import app.daybreak.data.WeatherApi
import app.daybreak.domain.Activity
import app.daybreak.domain.AppSettings
import app.daybreak.domain.WidgetSnapshot
import app.daybreak.domain.widgetSnapshotOf
import app.daybreak.widget.WidgetPublisher
import app.daybreak.domain.CommuteEnd
import app.daybreak.domain.CommuteSettings
import app.daybreak.domain.weekendDays
import app.daybreak.domain.with
import app.daybreak.domain.Countdown
import app.daybreak.domain.PersonalDate
import app.daybreak.domain.dayOffDates
import app.daybreak.domain.comingUp
import app.daybreak.domain.countryCodeOf
import app.daybreak.domain.Forecast
import app.daybreak.domain.Place
import app.daybreak.domain.capAboutMe
import app.daybreak.domain.TempUnit
import app.daybreak.domain.Tone
import app.daybreak.data.HolidayRepository
import app.daybreak.data.MemeRepository
import app.daybreak.data.SavedMeme
import app.daybreak.narration.LocalModelManager
import app.daybreak.narration.Meme
import app.daybreak.narration.MemeWriter
import app.daybreak.narration.memeMoodOf
import app.daybreak.narration.ModelStatus
import app.daybreak.narration.Narration
import app.daybreak.narration.NarrationInput
import app.daybreak.narration.NarrationSource
import app.daybreak.narration.TemplateNarrator
import app.daybreak.narration.ValidatingNarrator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlin.coroutines.coroutineContext

/** What the widget should do after a UI change. */
private sealed interface WidgetUpdate {
    data object Clear : WidgetUpdate
    data object Keep : WidgetUpdate
    data class Show(val snapshot: WidgetSnapshot) : WidgetUpdate
}

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
        /** Public holiday dates for the place's country (this year and next), for the commute check. */
        val holidays: Set<LocalDate> = emptySet(),
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

/**
 * The commute's own forecasts, once home is set: [home]'s and, if there is one, [office]'s ([officeForecast] is
 * null when it couldn't be fetched), with the public holidays and weekend of home's country.
 */
data class CommuteForecasts(
    val home: Place,
    val homeForecast: Forecast,
    val office: Place? = null,
    val officeForecast: Forecast? = null,
    val holidays: Set<LocalDate> = emptySet(),
    val weekend: Set<DayOfWeek> = weekendDays(null),
)

/** "Use where I am now" for a commute end: finding the location, or why it couldn't ([error]). */
data class CommuteLocating(val end: CommuteEnd, val error: String? = null)

data class WeatherUiState(
    val pages: List<PageUi> = emptyList(),
    val savedPlaces: List<Place> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val search: SearchUi = SearchUi(),
    val modelStatus: ModelStatus = ModelStatus.NotInstalled,
    /** Null until home is set and its forecast has loaded, or while the check is off. */
    val commute: CommuteForecasts? = null,
    /** Home's forecast couldn't be loaded (and there's none from before): the card falls back to the first page. */
    val commuteUnavailable: Boolean = false,
    val commuteLocating: CommuteLocating? = null,
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
    private val commute = MutableStateFlow<CommuteForecasts?>(null)
    private val commuteUnavailable = MutableStateFlow(false)
    private val commuteLocating = MutableStateFlow<CommuteLocating?>(null)
    private val jobs = mutableMapOf<String, Job>()
    private var searchJob: Job? = null
    private var commuteJob: Job? = null
    private var locateJob: Job? = null

    val uiState: StateFlow<WeatherUiState> = combine(
        combine(places.places, settingsRepo.settings, ::Pair),
        combine(contents, fetching, ::Pair),
        combine(currentPlace, combine(commute, commuteUnavailable, ::Pair), commuteLocating, ::Triple),
        search,
        model.status,
    ) { (saved, settings), (contents, fetching), (current, commuteState, locating), search, modelStatus ->
        val (commute, commuteUnavailable) = commuteState
        val pages = buildList {
            if (settings.useCurrentLocation) {
                add(PageUi(CURRENT, current, contents[CURRENT] ?: PageContent.Loading, CURRENT in fetching))
            }
            saved.forEach { add(PageUi(it.id, it, contents[it.id] ?: PageContent.Loading, it.id in fetching)) }
        }
        WeatherUiState(pages, saved, settings, search, modelStatus, commute, commuteUnavailable, locating)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WeatherUiState())

    init {
        if (widget != null) {
            // The widget mirrors the first page that has a forecast, including Gemma's summary once it lands. With
            // no pages at all it's cleared; while pages are loading or failed it keeps what it had.
            viewModelScope.launch {
                uiState
                    .map { s ->
                        // From the settings, not the page list: the very first UI state has no pages yet either.
                        if (s.savedPlaces.isEmpty() && !s.settings.useCurrentLocation) return@map WidgetUpdate.Clear
                        s.pages.firstNotNullOfOrNull { page ->
                            val loaded = page.content as? PageContent.Loaded ?: return@firstNotNullOfOrNull null
                            val place = page.place ?: return@firstNotNullOfOrNull null
                            WidgetUpdate.Show(
                                widgetSnapshotOf(
                                    place, loaded.forecast, s.settings.primaryUnit, loaded.summary.text,
                                    summaryByGemma = loaded.summary.source == NarrationSource.GEMMA, nowMillis = 0,
                                ),
                            )
                        } ?: WidgetUpdate.Keep
                    }
                    .distinctUntilChanged()
                    .collect { update ->
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
        refreshCommute()
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
        // The commute card is on Home, but pulling any page is a request for fresh weather.
        refreshCommute()
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
        val quickMeme = if (settings.memesEnabled && key == homePageKey()) savedOrTemplateMeme(key, place, input).meme else null
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
    /**
     * Adds the countdowns for [place]'s country (seasons still show when the holiday lookup fails) and the holiday
     * dates the commute check skips. Holidays are fetched when either feature is on.
     */
    private suspend fun showComingUp(key: String, place: Place, forecast: Forecast) {
        fun wanted() = settingsRepo.settings.value.let { it.comingUpEnabled || it.commute.enabled }
        if (!wanted()) return
        val today = forecast.current.time.toLocalDate()
        val year = countryCodeOf(place)?.let { cc -> holidays?.around(today, cc) }
        if (!wanted()) return // turned off while fetching
        val show = settingsRepo.settings.value.comingUpEnabled
        val offDates = dayOffDates(settingsRepo.settings.value.personalDates, today)
        val items = if (show) comingUp(today, year?.holidays.orEmpty(), year?.longWeekends.orEmpty(), place.latitude, offDates = offDates) else emptyList()
        val dates = year?.holidays.orEmpty().map { it.date }.toSet()
        updateLoaded(key, forecast) { it.copy(comingUp = items, holidays = dates) }
    }

    /** Re-runs the holiday lookup for every loaded page (after a setting that needs it is switched on). */
    private fun refreshHolidays() {
        // Not through launchFor: a page busy with Gemma still gets its card, and nothing gets cancelled. A page
        // that's refetching drops this result (updateLoaded checks the forecast) and adds its own.
        visiblePlaces().forEach { (key, place) ->
            val content = contents.value[key]
            if (content is PageContent.Loaded) viewModelScope.launch { showComingUp(key, place, content.forecast) }
        }
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
        // Only Home shows a meme, for the page it follows: don't spend Gemma on places nobody sees one for.
        if (!memesOn() || key != homePageKey()) return
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

    /** The phone switched between 12- and 24-hour time: summaries mention times, so rewrite them. */
    fun onClockFormatChanged() = renarrateAll()

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

    /**
     * Without a home, the commute card is computed from the first page's cached forecast. With one, home's and the
     * office's forecasts are fetched when the check is switched on or either place changes (new times need nothing).
     */
    fun setCommute(commute: CommuteSettings) {
        val old = settingsRepo.settings.value.commute
        settingsRepo.update { it.copy(commute = commute) }
        // The commute skips public holidays, which come with the countdowns: fetch them if nothing has yet.
        if (commute.enabled && !old.enabled) refreshHolidays()
        val placesChanged = commute.home != old.home || commute.office != old.office
        if (placesChanged) {
            this.commute.value = null // never judge the new places with the old ones' weather
            // A place set any other way wins over a location still being looked up.
            locateJob?.cancel()
            commuteLocating.value = null
        } else if (commuteLocating.value?.error != null) {
            commuteLocating.value = null // any other change moves on from the error
        }
        if (placesChanged || commute.enabled != old.enabled) refreshCommute()
    }

    /** Sets (or with null, clears) one end of the commute. */
    fun setCommutePlace(end: CommuteEnd, place: Place?) {
        locateJob?.cancel()
        commuteLocating.value = null
        setCommute(settingsRepo.settings.value.commute.with(end, place))
    }

    /** "Use where I am now": the device's location becomes [end]. The caller has asked for the permission. */
    fun setCommutePlaceHere(end: CommuteEnd) {
        locateJob?.cancel()
        if (!location.hasPermission()) {
            commuteLocating.value = CommuteLocating(end, "Location access is off for this app.")
            return
        }
        commuteLocating.value = CommuteLocating(end)
        locateJob = viewModelScope.launch {
            val place = location.currentPlace()
            if (place == null) {
                commuteLocating.value = CommuteLocating(end, "Couldn't get your location. Is location turned on?")
            } else {
                commuteLocating.value = null
                setCommute(settingsRepo.settings.value.commute.with(end, place))
            }
        }
    }

    /**
     * Fetches home's and the office's forecasts for the commute card, with home's holidays. A failed refresh keeps
     * what's shown (the office's too); with nothing to keep, a failed home forecast lets the card fall back to the
     * first page, and a failed office forecast leaves the trips judged at home only.
     */
    private fun refreshCommute() {
        commuteJob?.cancel()
        commuteUnavailable.value = false
        val settings = settingsRepo.settings.value.commute
        val home = settings.home
        if (!settings.enabled || home == null) {
            commute.value = null
            return
        }
        val office = settings.office
        commuteJob = viewModelScope.launch {
            val homeForecast = try {
                api.forecast(home.latitude, home.longitude)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (commute.value == null) commuteUnavailable.value = true
                return@launch
            }
            val officeForecast = office?.let {
                try {
                    api.forecast(it.latitude, it.longitude)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    commute.value?.takeIf { c -> c.office == office }?.officeForecast
                }
            }
            val cc = countryCodeOf(home)
            val dates = cc?.let { holidays?.around(homeForecast.current.time.toLocalDate(), it) }?.holidays.orEmpty()
            commute.value = CommuteForecasts(home, homeForecast, office, officeForecast, dates.map { it.date }.toSet(), weekendDays(cc))
        }
    }

    /** Tonight's sky is computed from the clock and the cached forecast: nothing to fetch. */
    fun setSkyEnabled(enabled: Boolean) = settingsRepo.update { it.copy(skyEnabled = enabled) }

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
