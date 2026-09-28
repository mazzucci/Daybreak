package com.mazzucci.weather.data

import com.mazzucci.weather.narration.Meme
import com.mazzucci.weather.narration.MemeMood
import com.mazzucci.weather.narration.NarrationSource
import org.json.JSONObject
import java.time.LocalDate

/**
 * Remembers each page's meme for the day, so it stays the same across refreshes and restarts and Gemma writes
 * at most one per place per day. One entry per page key, overwritten the next day.
 */
class MemeRepository(private val store: KeyValueStore) {

    /** Today's meme for [key], if one was saved for [date] with the same [mood]. */
    fun get(key: String, date: LocalDate, mood: MemeMood): Meme? {
        val json = store.getString(storeKey(key)) ?: return null
        return runCatching {
            val o = JSONObject(json)
            if (o.getString("date") != date.toString() || o.getString("mood") != mood.name) return null
            Meme(o.getString("top"), o.getString("bottom"), mood, NarrationSource.valueOf(o.getString("source")))
        }.getOrNull()
    }

    fun put(key: String, date: LocalDate, meme: Meme) {
        val o = JSONObject()
            .put("date", date.toString())
            .put("mood", meme.mood.name)
            .put("top", meme.top)
            .put("bottom", meme.bottom)
            .put("source", meme.source.name)
        store.putString(storeKey(key), o.toString())
    }

    private fun storeKey(key: String) = "meme:$key"
}
