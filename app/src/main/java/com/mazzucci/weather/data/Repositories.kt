package com.mazzucci.weather.data

import com.mazzucci.weather.domain.AppSettings
import com.mazzucci.weather.domain.Place
import com.mazzucci.weather.domain.TempUnit
import com.mazzucci.weather.domain.Tone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

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
            val places = stored.map { it.toPlace() }
            // Places saved before ids were prefixed ("4409896" → "geo:4409896"): persist the migration once.
            if (stored.zip(places).any { (json, place) -> json.getString("id") != place.id }) {
                store.putString(KEY, JSONArray(places.map { it.toJson() }).toString())
            }
            places
        } catch (e: JSONException) {
            emptyList() // Corrupt data shouldn't brick the app; the user can re-add places.
        }
    }

    private fun Place.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .putOpt("region", region)
        .putOpt("country", country)
        .put("lat", latitude)
        .put("lon", longitude)

    private fun JSONObject.toPlace() = Place(
        id = getString("id").let { if (it.startsWith(Place.GEOCODING_PREFIX)) it else Place.GEOCODING_PREFIX + it },
        name = getString("name"),
        region = optString("region").ifBlank { null },
        country = optString("country").ifBlank { null },
        latitude = getDouble("lat"),
        longitude = getDouble("lon"),
    )

    private companion object {
        const val KEY = "saved_places"
    }
}

class SettingsRepository(private val store: KeyValueStore, defaults: AppSettings = AppSettings()) {
    private val _settings = MutableStateFlow(load(defaults))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        val s = _settings.value
        store.putString(KEY_UNIT, s.primaryUnit.name)
        store.putString(KEY_CURRENT, s.useCurrentLocation.toString())
        store.putString(KEY_GEMMA, s.gemmaEnabled.toString())
        store.putString(KEY_MEMES, s.memesEnabled.toString())
        store.putString(KEY_TONE, s.tone.name)
        store.putString(KEY_ABOUT_ME, s.aboutMe)
    }

    private fun load(defaults: AppSettings) = AppSettings(
        primaryUnit = store.getString(KEY_UNIT)?.let { runCatching { TempUnit.valueOf(it) }.getOrNull() }
            ?: defaults.primaryUnit,
        useCurrentLocation = store.getString(KEY_CURRENT)?.toBooleanStrictOrNull() ?: defaults.useCurrentLocation,
        gemmaEnabled = store.getString(KEY_GEMMA)?.toBooleanStrictOrNull() ?: defaults.gemmaEnabled,
        memesEnabled = store.getString(KEY_MEMES)?.toBooleanStrictOrNull() ?: defaults.memesEnabled,
        tone = store.getString(KEY_TONE)?.let { runCatching { Tone.valueOf(it) }.getOrNull() } ?: defaults.tone,
        aboutMe = store.getString(KEY_ABOUT_ME) ?: defaults.aboutMe,
    )

    private companion object {
        const val KEY_UNIT = "primary_unit"
        const val KEY_CURRENT = "use_current_location"
        const val KEY_GEMMA = "gemma_enabled"
        const val KEY_MEMES = "memes_enabled"
        const val KEY_TONE = "tone"
        const val KEY_ABOUT_ME = "about_me"
    }
}
