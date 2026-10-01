package app.daybreak.data

import app.daybreak.domain.TempUnit
import app.daybreak.domain.WidgetSnapshot
import org.json.JSONObject
import java.time.LocalDateTime

/**
 * The widget's saved snapshot, as JSON in [store]. Unreadable data reads as "nothing yet". The app keeps it in a
 * preferences file of its own that's excluded from backup: it holds the device's location.
 * Writers that read, work and write back use [update] so they can't overwrite a newer snapshot.
 */
class WidgetStore(private val store: KeyValueStore) {
    fun load(): WidgetSnapshot? = store.getString(KEY)?.let { json ->
        runCatching {
            val o = JSONObject(json)
            WidgetSnapshot(
                placeName = o.getString("place"),
                latitude = o.getDouble("lat"),
                longitude = o.getDouble("lon"),
                unit = TempUnit.valueOf(o.getString("unit")),
                tempC = o.getDouble("temp"),
                highC = o.getDouble("high"),
                lowC = o.getDouble("low"),
                precipChance = o.getInt("precip"),
                snow = o.optBoolean("snow", false),
                code = o.getInt("code"),
                night = o.getBoolean("night"),
                summary = o.getString("summary"),
                updatedAt = LocalDateTime.parse(o.getString("updated")),
                writtenAtMillis = o.getLong("written"),
            )
        }.getOrNull()
    }

    fun clear() = synchronized(LOCK) { store.remove(KEY) }

    /** Atomically replaces the snapshot with [change] of the current one; null from [change] keeps it as is. */
    fun update(change: (WidgetSnapshot?) -> WidgetSnapshot?): WidgetSnapshot? = synchronized(LOCK) {
        val next = change(load()) ?: return@synchronized null
        save(next)
        next
    }

    fun save(s: WidgetSnapshot) = synchronized(LOCK) {
        store.putString(
            KEY,
            JSONObject()
                .put("place", s.placeName).put("lat", s.latitude).put("lon", s.longitude).put("unit", s.unit.name)
                .put("temp", s.tempC).put("high", s.highC).put("low", s.lowC).put("precip", s.precipChance).put("snow", s.snow)
                .put("code", s.code).put("night", s.night).put("summary", s.summary)
                .put("updated", s.updatedAt.toString()).put("written", s.writtenAtMillis)
                .toString(),
        )
    }

    companion object {
        private const val KEY = "widget_snapshot"
        /** The preferences file the app uses for it (see backup_rules.xml). */
        const val PREFS_FILE = "widget"
        private val LOCK = Any()
    }
}
