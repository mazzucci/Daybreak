package app.daybreak.data

import app.daybreak.domain.Activity
import app.daybreak.domain.AppSettings
import app.daybreak.domain.Badge
import app.daybreak.domain.Clock
import app.daybreak.domain.HABIT_COUNT_MAX
import app.daybreak.domain.HABIT_TITLE_MAX
import app.daybreak.domain.Habit
import app.daybreak.domain.HabitColor
import app.daybreak.domain.HabitKind
import app.daybreak.domain.HabitPeriod
import app.daybreak.domain.HabitsData
import app.daybreak.domain.PersonalDate
import app.daybreak.domain.Place
import app.daybreak.domain.countryCodeOf
import app.daybreak.domain.TempUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate

/** The user's saved places, in display order, persisted as a JSON array. */
class SavedPlacesRepository(private val store: KeyValueStore) {
    private val _places = MutableStateFlow(load())
    val places: StateFlow<List<Place>> = _places.asStateFlow()

    /** Adds [place] at the end. Returns false if it's already saved. */
    fun add(place: Place): Boolean {
        if (_places.value.any { it.id == place.id }) return false
        save(_places.value + place)
        return true
    }

    fun remove(id: String) = save(_places.value.filterNot { it.id == id })

    /** Moves the place at [from] to index [to]; out-of-range indices are ignored. */
    fun move(from: Int, to: Int) {
        val list = _places.value.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        list.add(to, list.removeAt(from))
        save(list)
    }

    private fun save(list: List<Place>) {
        _places.value = list
        store.putString(KEY, JSONArray(list.map { it.toJson() }).toString())
    }

    private fun load(): List<Place> {
        val raw = store.getString(KEY) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val stored = (0 until array.length()).map { array.getJSONObject(it) }
            // Places saved before country codes were stored get one from their country name, once.
            val places = stored.map { it.toPlace() }.map { p -> if (p.countryCode == null) p.copy(countryCode = countryCodeOf(p)) else p }
            // Places saved before ids were prefixed ("4409896" → "geo:4409896") or before country codes: persist once.
            if (stored.zip(places).any { (json, place) -> json.getString("id") != place.id || (!json.has("cc") && place.countryCode != null) }) {
                store.putString(KEY, JSONArray(places.map { it.toJson() }).toString())
            }
            places
        } catch (e: JSONException) {
            emptyList() // Corrupt data shouldn't brick the app; the user can re-add places.
        }
    }

    private companion object {
        const val KEY = "saved_places"
    }
}

private fun Place.toJson() = JSONObject()
    .put("id", id)
    .put("name", name)
    .putOpt("region", region)
    .putOpt("country", country)
    .putOpt("cc", countryCode)
    .putOpt("tz", zoneId)
    .put("lat", latitude)
    .put("lon", longitude)

private fun JSONObject.toPlace() = Place(
    id = getString("id").let { if (it.startsWith(Place.GEOCODING_PREFIX)) it else Place.GEOCODING_PREFIX + it },
    name = getString("name"),
    region = optString("region").ifBlank { null },
    country = optString("country").ifBlank { null },
    latitude = getDouble("lat"),
    longitude = getDouble("lon"),
    countryCode = optString("cc").ifBlank { null },
    zoneId = optString("tz").ifBlank { null },
)

/**
 * App settings. The user's own dates live in [privateStore], a separate file that's excluded from Android backup and
 * device transfer (see res/xml/backup_rules.xml), so they really stay on this phone.
 */
class SettingsRepository(
    private val store: KeyValueStore,
    defaults: AppSettings = AppSettings(),
    private val privateStore: KeyValueStore = store,
) {
    init {
        // Settings of features that are gone (the summary's voice, "About me", the commute check): clear them once.
        REMOVED_KEYS.forEach { if (store.getString(it) != null) store.remove(it) }
        REMOVED_PRIVATE_KEYS.forEach { if (privateStore.getString(it) != null) privateStore.remove(it) }
    }

    private val _settings = MutableStateFlow(load(defaults))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        val s = _settings.value
        store.putString(KEY_UNIT, s.primaryUnit.name)
        store.putString(KEY_CURRENT, s.useCurrentLocation.toString())
        store.putString(KEY_GEMMA, s.gemmaEnabled.toString())
        store.putString(KEY_MEMES, s.memesEnabled.toString())
        store.putString(KEY_COMING_UP, s.comingUpEnabled.toString())
        store.putString(KEY_SKY, s.skyEnabled.toString())
        store.putString(KEY_HABITS_HOME, s.habitsOnHome.toString())
        store.putString(KEY_ACTIVITY, s.activity?.name ?: ACTIVITY_OFF)
        if (s.personalDates.isEmpty()) {
            privateStore.remove(KEY_DATES)
        } else {
            privateStore.putString(
                KEY_DATES,
                JSONArray(
                    s.personalDates.map {
                        JSONObject().put("start", it.start.toString()).put("end", it.end.toString()).put("name", it.name)
                            .put("dayOff", it.dayOff).put("yearly", it.yearly)
                    },
                ).toString(),
            )
        }
    }

    /** Stored dates; any entry that doesn't parse is dropped rather than losing the rest. */
    private fun loadPersonalDates(): List<PersonalDate>? {
        val raw = privateStore.getString(KEY_DATES) ?: return null
        val array = try {
            JSONArray(raw)
        } catch (e: JSONException) {
            return emptyList()
        }
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                PersonalDate(
                    LocalDate.parse(o.getString("start")), LocalDate.parse(o.getString("end")), o.optString("name"),
                    dayOff = o.optBoolean("dayOff"), yearly = o.optBoolean("yearly"),
                )
            }.getOrNull()
        }.sortedBy { it.start }
    }

    private fun load(defaults: AppSettings) = AppSettings(
        primaryUnit = store.getString(KEY_UNIT)?.let { runCatching { TempUnit.valueOf(it) }.getOrNull() }
            ?: defaults.primaryUnit,
        useCurrentLocation = store.getString(KEY_CURRENT)?.toBooleanStrictOrNull() ?: defaults.useCurrentLocation,
        gemmaEnabled = store.getString(KEY_GEMMA)?.toBooleanStrictOrNull() ?: defaults.gemmaEnabled,
        memesEnabled = store.getString(KEY_MEMES)?.toBooleanStrictOrNull() ?: defaults.memesEnabled,
        comingUpEnabled = store.getString(KEY_COMING_UP)?.toBooleanStrictOrNull() ?: defaults.comingUpEnabled,
        skyEnabled = store.getString(KEY_SKY)?.toBooleanStrictOrNull() ?: defaults.skyEnabled,
        habitsOnHome = store.getString(KEY_HABITS_HOME)?.toBooleanStrictOrNull() ?: defaults.habitsOnHome,
        personalDates = loadPersonalDates() ?: defaults.personalDates,
        activity = when (val v = store.getString(KEY_ACTIVITY)) {
            null -> defaults.activity
            ACTIVITY_OFF -> null
            else -> runCatching { Activity.valueOf(v) }.getOrDefault(defaults.activity)
        },
    )

    private companion object {
        const val KEY_UNIT = "primary_unit"
        const val KEY_CURRENT = "use_current_location"
        const val KEY_GEMMA = "gemma_enabled"
        const val KEY_MEMES = "memes_enabled"
        const val KEY_COMING_UP = "coming_up_enabled"
        const val KEY_SKY = "sky_enabled"
        const val KEY_HABITS_HOME = "habits_on_home"
        const val KEY_ACTIVITY = "activity"
        const val KEY_DATES = "personal_dates"
        const val ACTIVITY_OFF = "OFF"
        val REMOVED_KEYS = listOf("tone", "commute")
        val REMOVED_PRIVATE_KEYS = listOf("about_me", "commute_home", "commute_office")
    }
}

/** The user's clocks, in display order, persisted as a JSON array. */
class ClocksRepository(private val store: KeyValueStore) {
    private val _clocks = MutableStateFlow(load())
    val clocks: StateFlow<List<Clock>> = _clocks.asStateFlow()

    /** Adds [clock] at the end. Returns false if it's already there. */
    fun add(clock: Clock): Boolean {
        if (_clocks.value.any { it.id == clock.id }) return false
        save(_clocks.value + clock)
        return true
    }

    fun remove(id: String) = save(_clocks.value.filterNot { it.id == id })

    /** Moves the clock at [from] to index [to]; out-of-range indices are ignored. */
    fun move(from: Int, to: Int) {
        val list = _clocks.value.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        list.add(to, list.removeAt(from))
        save(list)
    }

    private fun save(list: List<Clock>) {
        _clocks.value = list
        store.putString(KEY, JSONArray(list.map { JSONObject().put("id", it.id).put("name", it.name).putOpt("detail", it.detail).put("tz", it.zoneId) }).toString())
    }

    /** Entries that don't parse are dropped rather than losing the rest. */
    private fun load(): List<Clock> {
        val array = store.getString(KEY)?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                Clock(o.getString("id"), o.getString("name"), o.optString("detail").ifBlank { null }, o.getString("tz"))
            }.getOrNull()
        }
    }

    private companion object {
        const val KEY = "clocks"
    }
}

/**
 * Your habits and their logs, in display order, as JSON in the private store (excluded from backup, like your
 * dates): what you're working on stays on this phone. Also keeps the points and badges of deleted habits, so
 * deleting one never takes away what it earned.
 */
class HabitsRepository(private val store: KeyValueStore) {
    private val _data = MutableStateFlow(load())
    val data: StateFlow<HabitsData> = _data.asStateFlow()

    /** Adds [habit] at the end. Returns false if its id is already taken. */
    fun add(habit: Habit): Boolean {
        if (_data.value.habits.any { it.id == habit.id }) return false
        save(_data.value.copy(habits = _data.value.habits + habit))
        return true
    }

    /** Replaces the habit with [habit]'s id, keeping its log and start day. */
    fun update(habit: Habit) = edit { list ->
        list.map { if (it.id == habit.id) habit.copy(created = it.created, log = it.log) else it }
    }

    /** Removes a habit, banking the [points] and [badges] it earned. */
    fun remove(id: String, points: Int = 0, badges: Set<Badge> = emptySet()) {
        val d = _data.value
        if (d.habits.none { it.id == id }) return
        save(HabitsData(d.habits.filterNot { it.id == id }, d.bankedPoints + points, d.bankedBadges + badges))
    }

    /** Moves the habit at [from] to index [to]; out-of-range indices are ignored. */
    fun move(from: Int, to: Int) = edit { list ->
        if (from !in list.indices || to !in list.indices || from == to) list
        else list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Adds [delta] to [id]'s count on [date], never below zero or above [HABIT_COUNT_MAX] times ten. */
    fun log(id: String, date: LocalDate, delta: Int) = edit { list ->
        list.map { h ->
            if (h.id != id) return@map h
            val n = ((h.log[date] ?: 0) + delta).coerceIn(0, HABIT_COUNT_MAX * 10)
            h.copy(log = if (n == 0) h.log - date else h.log + (date to n))
        }
    }

    private fun edit(transform: (List<Habit>) -> List<Habit>) {
        val d = _data.value
        val list = transform(d.habits)
        if (list != d.habits) save(d.copy(habits = list))
    }

    private fun save(d: HabitsData) {
        _data.value = d
        if (d == HabitsData()) {
            store.remove(KEY)
            return
        }
        val habits = JSONArray(
            d.habits.map { h ->
                JSONObject().put("id", h.id).put("title", h.title).put("color", h.color.name).put("kind", h.kind.name)
                    .put("period", h.period.name).put("target", h.target).put("created", h.created.toString())
                    .put("log", JSONObject().apply { h.log.toSortedMap().forEach { (date, n) -> put(date.toString(), n) } })
            },
        )
        store.putString(
            KEY,
            JSONObject().put("habits", habits).put("bankedPoints", d.bankedPoints)
                .put("bankedBadges", JSONArray(d.bankedBadges.map { it.name })).toString(),
        )
    }

    /** A habit or log entry that doesn't parse is dropped rather than losing the rest. */
    private fun load(): HabitsData {
        val root = store.getString(KEY)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return HabitsData()
        val array = root.optJSONArray("habits") ?: JSONArray()
        val habits = (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                val kind = HabitKind.valueOf(o.getString("kind"))
                val log = o.optJSONObject("log")
                Habit(
                    id = o.getString("id"),
                    title = o.getString("title").take(HABIT_TITLE_MAX),
                    // An unknown colour (from a later version) falls back rather than losing the habit.
                    color = runCatching { HabitColor.valueOf(o.getString("color")) }.getOrDefault(HabitColor.BLUE),
                    kind = kind,
                    period = HabitPeriod.valueOf(o.getString("period")),
                    target = o.getInt("target").coerceIn(if (kind == HabitKind.BUILD) 1 else 0, HABIT_COUNT_MAX),
                    created = LocalDate.parse(o.getString("created")),
                    log = log?.keys()?.asSequence()?.mapNotNull { k ->
                        runCatching { LocalDate.parse(k) to log.getInt(k) }.getOrNull()?.takeIf { it.second > 0 }
                    }?.toMap().orEmpty(),
                )
            }.getOrNull()
        }.distinctBy { it.id }
        val badges = root.optJSONArray("bankedBadges")?.let { a ->
            (0 until a.length()).mapNotNull { runCatching { Badge.valueOf(a.getString(it)) }.getOrNull() }.toSet()
        }.orEmpty()
        return HabitsData(habits, root.optInt("bankedPoints", 0).coerceAtLeast(0), badges)
    }

    private companion object {
        const val KEY = "habits"
    }
}
