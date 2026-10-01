package app.daybreak.data

import app.daybreak.domain.Activity
import app.daybreak.domain.AppSettings
import app.daybreak.domain.Badge
import app.daybreak.domain.Clock
import app.daybreak.domain.HABIT_COUNT_MAX
import app.daybreak.domain.Habit
import app.daybreak.domain.HabitColor
import app.daybreak.domain.HabitDraft
import app.daybreak.domain.HabitGoal
import app.daybreak.domain.HabitKind
import app.daybreak.domain.HabitPeriod
import app.daybreak.domain.HabitsData
import app.daybreak.domain.PersonalDate
import app.daybreak.domain.Place
import app.daybreak.domain.countryCodeOf
import app.daybreak.domain.TempUnit
import androidx.core.text.util.LocalePreferences
import app.daybreak.domain.withGoal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

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
 * deleting one never takes away what it earned, and the first day of the week, set the first time anything is
 * stored.
 *
 * Nothing stored is dropped: a habit, log entry or badge that doesn't parse (or a colour from a later version) is
 * kept as it was and written back unchanged, as are fields this version doesn't know. A store that doesn't parse at
 * all is copied to a backup key before anything is written over it. Writes happen off the main thread
 * ([writeScope], Dispatchers.IO by default), one at a time, the latest winning.
 */
class HabitsRepository(
    private val store: KeyValueStore,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val defaultWeekStart: () -> DayOfWeek = ::localeWeekStart,
    private val writeScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    /** What's kept from the stored JSON beyond [HabitsData], to write back as it was. */
    private data class Extras(
        /** The stored root object, for the fields this version doesn't know. */
        val root: String? = null,
        /** Each habit's stored object, by id, likewise. */
        val habits: Map<String, String> = emptyMap(),
        /** Colours this version doesn't know, by habit id, until the habit is given another. */
        val colors: Map<String, String> = emptyMap(),
        /** Each habit's log entries that don't parse, as a JSON object, by id. */
        val logs: Map<String, String> = emptyMap(),
        /** Habits that don't parse, each as a one-element JSON array (so any JSON value round-trips). */
        val rawHabits: List<String> = emptyList(),
        val rawBadges: List<String> = emptyList(),
    )

    private val pending = AtomicReference<Pair<HabitsData, Extras>?>(null)
    private val writing = Mutex()
    private var extras = Extras()
    private val _data: MutableStateFlow<HabitsData>
    val data: StateFlow<HabitsData>

    init {
        val (loaded, kept, migrate) = load()
        extras = kept
        _data = MutableStateFlow(loaded)
        data = _data.asStateFlow()
        // An older version's data is written back at once, so its first day of the week is frozen now.
        if (migrate) save(loaded)
    }

    /** Adds [habit] at the end, starting no later than today. Returns false if its id is already taken. */
    fun add(habit: Habit): Boolean {
        if (_data.value.habits.any { it.id == habit.id }) return false
        val day = today()
        val h = if (habit.created.isAfter(day)) habit.copy(created = day, goals = habit.goals.map { it.copy(from = minOf(it.from, day)) }) else habit
        save(_data.value.copy(habits = _data.value.habits + h))
        return true
    }

    /** Saves [draft] over habit [id], keeping its log and start day. A new goal applies from today on. */
    fun update(id: String, draft: HabitDraft) {
        val old = _data.value.habits.firstOrNull { it.id == id } ?: return
        if (draft.color != old.color) extras = extras.copy(colors = extras.colors - id)
        edit { list ->
            list.map { h ->
                if (h.id != id) h
                else h.copy(title = draft.title, color = draft.color).withGoal(draft.kind, draft.period, draft.target, today())
            }
        }
    }

    /** Removes a habit, banking the [points] and [badges] it earned. */
    fun remove(id: String, points: Int = 0, badges: Set<Badge> = emptySet()) {
        val d = _data.value
        if (d.habits.none { it.id == id }) return
        extras = extras.copy(habits = extras.habits - id, colors = extras.colors - id, logs = extras.logs - id)
        save(d.copy(habits = d.habits.filterNot { it.id == id }, bankedPoints = d.bankedPoints + points, bankedBadges = d.bankedBadges + badges))
    }

    /** Moves habit [id] [by] places (−1 is up); a move past either end is ignored. */
    fun move(id: String, by: Int) = edit { list ->
        val from = list.indexOfFirst { it.id == id }
        val to = from + by
        if (from < 0 || to !in list.indices || by == 0) list
        else list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * Adds [delta] to [id]'s count on [date], never below zero or above [HABIT_COUNT_MAX] times ten. Something
     * added is filed no later than today; taking one back can reach a day after it (the clock was ahead).
     */
    fun log(id: String, date: LocalDate, delta: Int) = edit { list ->
        val day = if (delta > 0) minOf(date, today()) else date
        list.map { h ->
            if (h.id != id) return@map h
            val n = ((h.log[day] ?: 0) + delta).coerceIn(0, HABIT_COUNT_MAX * 10)
            h.copy(log = if (n == 0) h.log - day else h.log + (day to n))
        }
    }

    /** Sets the first day of the week, which re-buckets every week: only on purpose, from Settings. */
    fun setWeekStart(day: DayOfWeek) {
        if (_data.value.weekStart != day) save(_data.value.copy(weekStart = day))
    }

    fun markUndoHintShown() {
        if (!_data.value.undoHintShown) save(_data.value.copy(undoHintShown = true))
    }

    private fun edit(transform: (List<Habit>) -> List<Habit>) {
        val d = _data.value
        val list = transform(d.habits)
        if (list != d.habits) save(d.copy(habits = list))
    }

    private fun save(d: HabitsData) {
        _data.value = d
        pending.set(d to extras)
        // Each write takes whatever is newest, so a burst of taps writes once or twice, never out of order.
        writeScope.launch { writing.withLock { pending.getAndSet(null)?.let { (data, kept) -> store.putString(KEY, encode(data, kept)) } } }
    }

    private fun encode(d: HabitsData, e: Extras): String {
        val habits = JSONArray()
        d.habits.forEach { h ->
            val log = e.logs[h.id]?.let(::JSONObject) ?: JSONObject()
            h.log.toSortedMap().forEach { (date, n) -> log.put(date.toString(), n) }
            val goals = JSONArray(
                h.goals.map { g ->
                    JSONObject().put("from", g.from.toString()).put("kind", g.kind.name).put("period", g.period.name).put("target", g.target)
                },
            )
            habits.put(
                (e.habits[h.id]?.let(::JSONObject) ?: JSONObject())
                    .put("id", h.id).put("title", h.title).put("color", e.colors[h.id] ?: h.color.name)
                    .put("created", h.created.toString())
                    // The current goal where version 1 kept it, then every goal.
                    .put("kind", h.kind.name).put("period", h.period.name).put("target", h.target)
                    .put("goals", goals)
                    .put("log", log),
            )
        }
        e.rawHabits.forEach { habits.put(JSONArray(it).get(0)) }
        return (e.root?.let(::JSONObject) ?: JSONObject())
            .put("version", VERSION)
            .put("weekStart", d.weekStart.name)
            .put("habits", habits)
            .put("bankedPoints", d.bankedPoints)
            .put("bankedBadges", JSONArray(d.bankedBadges.sortedBy { it.ordinal }.map { it.name } + e.rawBadges))
            .put("undoHintShown", d.undoHintShown)
            .toString()
    }

    private data class Loaded(val data: HabitsData, val extras: Extras, val migrate: Boolean)

    private fun load(): Loaded {
        val fresh = Loaded(HabitsData(weekStart = defaultWeekStart()), Extras(), migrate = false)
        val raw = store.getString(KEY) ?: return fresh
        val root = runCatching { JSONObject(raw) }.getOrNull()?.takeIf { !it.has("habits") || it.optJSONArray("habits") != null }
        if (root == null) {
            backUp(raw)
            return fresh
        }
        val array = root.optJSONArray("habits") ?: JSONArray()
        val habits = ArrayList<Habit>()
        val objects = HashMap<String, String>()
        val colors = HashMap<String, String>()
        val logs = HashMap<String, String>()
        val rawHabits = ArrayList<String>()
        for (i in 0 until array.length()) {
            val element = array.opt(i)
            val parsed = (element as? JSONObject)?.let { runCatching { parseHabit(it) }.getOrNull() }
            if (parsed == null || habits.any { it.id == parsed.habit.id }) {
                rawHabits += JSONArray().put(element).toString()
                continue
            }
            val h = parsed.habit
            habits += h
            objects[h.id] = (element as JSONObject).toString()
            parsed.unknownColor?.let { colors[h.id] = it }
            parsed.rawLog?.let { logs[h.id] = it }
        }
        val badges = mutableSetOf<Badge>()
        val rawBadges = ArrayList<String>()
        root.optJSONArray("bankedBadges")?.let { a ->
            for (i in 0 until a.length()) {
                val name = a.optString(i)
                val badge = Badge.entries.firstOrNull { it.name == name }
                if (badge != null) badges += badge else rawBadges += name
            }
        }
        val weekStart = runCatching { DayOfWeek.valueOf(root.getString("weekStart")) }.getOrNull()
        return Loaded(
            HabitsData(
                habits = habits,
                bankedPoints = root.optInt("bankedPoints", 0).coerceAtLeast(0),
                bankedBadges = badges,
                weekStart = weekStart ?: defaultWeekStart(),
                undoHintShown = root.optBoolean("undoHintShown", false),
            ),
            Extras(raw, objects, colors, logs, rawHabits, rawBadges),
            migrate = root.optInt("version", 1) < VERSION || weekStart == null,
        )
    }

    private class ParsedHabit(val habit: Habit, val unknownColor: String?, val rawLog: String?)

    /** One habit, or an exception if it can't be used (then it's kept raw). */
    private fun parseHabit(o: JSONObject): ParsedHabit {
        val created = LocalDate.parse(o.getString("created"))
        fun goal(g: JSONObject, from: LocalDate): HabitGoal {
            val kind = HabitKind.valueOf(g.getString("kind"))
            val target = g.getInt("target").coerceIn(if (kind == HabitKind.BUILD) 1 else 0, HABIT_COUNT_MAX)
            return HabitGoal(from, kind, HabitPeriod.valueOf(g.getString("period")), target)
        }
        // Version 1 had one goal, kept on the habit itself: it's been in force since the habit was added.
        val goalsJson = o.optJSONArray("goals")
        val goals = if (goalsJson == null || goalsJson.length() == 0) {
            listOf(goal(o, created))
        } else {
            (0 until goalsJson.length()).map { i -> goalsJson.getJSONObject(i).let { goal(it, LocalDate.parse(it.getString("from"))) } }
                .sortedBy { it.from }
        }
        val colorName = if (o.has("color")) o.optString("color") else null
        val color = HabitColor.entries.firstOrNull { it.name == colorName }
        if (o.has("log") && o.optJSONObject("log") == null) throw JSONException("log isn't an object")
        val logJson = o.optJSONObject("log") ?: JSONObject()
        val log = HashMap<LocalDate, Int>()
        val rawLog = JSONObject()
        logJson.keys().forEach { k ->
            val date = runCatching { LocalDate.parse(k) }.getOrNull()
            val n = (logJson.opt(k) as? Number)?.takeIf { it.toDouble() == it.toInt().toDouble() }?.toInt()
            when {
                date != null && n != null && n > 0 -> log[date] = n
                date != null && n == 0 -> Unit // nothing logged that day
                else -> rawLog.put(k, logJson.opt(k))
            }
        }
        return ParsedHabit(
            Habit(o.getString("id"), o.getString("title"), color ?: HabitColor.BLUE, created, goals, log),
            unknownColor = if (color == null) colorName else null,
            rawLog = if (rawLog.length() > 0) rawLog.toString() else null,
        )
    }

    /** Keeps a store that doesn't parse under the first free backup key (once, however often it's loaded). */
    private fun backUp(raw: String) {
        val keys = generateSequence(1) { it + 1 }.map { if (it == 1) BACKUP_KEY else "$BACKUP_KEY.$it" }
        if (keys.takeWhile { store.getString(it) != null }.any { store.getString(it) == raw }) return
        store.putString(keys.first { store.getString(it) == null }, raw)
    }

    companion object {
        const val KEY = "habits"
        const val BACKUP_KEY = "habits.backup"
        /** 2: goals with their start days, the first day of the week, and the undo hint. */
        const val VERSION = 2
    }
}

/**
 * The first day of the week for this phone: its own setting where Android has one (14 and up), otherwise its
 * language and region's.
 */
fun localeWeekStart(): DayOfWeek = runCatching {
    when (LocalePreferences.getFirstDayOfWeek()) {
        LocalePreferences.FirstDayOfWeek.MONDAY -> DayOfWeek.MONDAY
        LocalePreferences.FirstDayOfWeek.TUESDAY -> DayOfWeek.TUESDAY
        LocalePreferences.FirstDayOfWeek.WEDNESDAY -> DayOfWeek.WEDNESDAY
        LocalePreferences.FirstDayOfWeek.THURSDAY -> DayOfWeek.THURSDAY
        LocalePreferences.FirstDayOfWeek.FRIDAY -> DayOfWeek.FRIDAY
        LocalePreferences.FirstDayOfWeek.SATURDAY -> DayOfWeek.SATURDAY
        LocalePreferences.FirstDayOfWeek.SUNDAY -> DayOfWeek.SUNDAY
        else -> null
    }
}.getOrNull() ?: WeekFields.of(Locale.getDefault()).firstDayOfWeek
