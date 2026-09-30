package app.daybreak.data

import app.daybreak.domain.Activity
import app.daybreak.domain.AppSettings
import app.daybreak.domain.Clock
import app.daybreak.domain.CommuteSettings
import app.daybreak.domain.PersonalDate
import app.daybreak.domain.Place
import app.daybreak.domain.countryCodeOf
import app.daybreak.domain.TempUnit
import app.daybreak.domain.Tone
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
 * App settings. The "About me" note, the commute's home and office and the user's own dates live in [privateStore],
 * a separate file that's excluded from Android backup and device transfer (see res/xml/backup_rules.xml), so they
 * really stay on this phone.
 */
class SettingsRepository(
    private val store: KeyValueStore,
    defaults: AppSettings = AppSettings(),
    private val privateStore: KeyValueStore = store,
) {
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
        store.putString(KEY_TONE, s.tone.name)
        privateStore.putString(KEY_ABOUT_ME, s.aboutMe)
        store.putString(KEY_ACTIVITY, s.activity?.name ?: ACTIVITY_OFF)
        // "on-8-17" / "off-8-17": the times survive switching the check off.
        store.putString(KEY_COMMUTE, "${if (s.commute.enabled) "on" else "off"}-${s.commute.leaveHour}-${s.commute.returnHour}")
        putPlace(KEY_HOME, s.commute.home)
        putPlace(KEY_OFFICE, s.commute.office)
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

    private fun putPlace(key: String, place: Place?) {
        if (place == null) privateStore.remove(key) else privateStore.putString(key, place.toJson().toString())
    }

    private fun getPlace(key: String, id: String): Place? = privateStore.getString(key)?.let {
        try {
            JSONObject(it).toPlace().copy(id = id)
        } catch (e: JSONException) {
            null
        }
    }

    private fun load(defaults: AppSettings) = AppSettings(
        primaryUnit = store.getString(KEY_UNIT)?.let { runCatching { TempUnit.valueOf(it) }.getOrNull() }
            ?: defaults.primaryUnit,
        useCurrentLocation = store.getString(KEY_CURRENT)?.toBooleanStrictOrNull() ?: defaults.useCurrentLocation,
        gemmaEnabled = store.getString(KEY_GEMMA)?.toBooleanStrictOrNull() ?: defaults.gemmaEnabled,
        memesEnabled = store.getString(KEY_MEMES)?.toBooleanStrictOrNull() ?: defaults.memesEnabled,
        comingUpEnabled = store.getString(KEY_COMING_UP)?.toBooleanStrictOrNull() ?: defaults.comingUpEnabled,
        tone = store.getString(KEY_TONE)?.let { runCatching { Tone.valueOf(it) }.getOrNull() } ?: defaults.tone,
        aboutMe = privateStore.getString(KEY_ABOUT_ME) ?: defaults.aboutMe,
        commute = store.getString(KEY_COMMUTE)?.split("-")?.let { parts ->
            val hours = parts.drop(1).mapNotNull { it.toIntOrNull() }
            if (parts.size == 3 && parts[0] in setOf("on", "off") && hours.size == 2 && hours.all { it in 0..23 }) {
                CommuteSettings(hours[0], hours[1], enabled = parts[0] == "on")
            } else null
        }.let { c ->
            (c ?: defaults.commute).copy(
                home = getPlace(KEY_HOME, Place.COMMUTE_HOME_ID) ?: defaults.commute.home,
                office = getPlace(KEY_OFFICE, Place.COMMUTE_OFFICE_ID) ?: defaults.commute.office,
            )
        },
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
        const val KEY_TONE = "tone"
        const val KEY_ABOUT_ME = "about_me"
        const val KEY_ACTIVITY = "activity"
        const val KEY_COMMUTE = "commute"
        const val KEY_HOME = "commute_home"
        const val KEY_OFFICE = "commute_office"
        const val KEY_DATES = "personal_dates"
        const val ACTIVITY_OFF = "OFF"
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
