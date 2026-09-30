package app.daybreak.data

import app.daybreak.narration.Meme
import app.daybreak.narration.MemeMood
import app.daybreak.narration.NarrationSource
import org.json.JSONObject
import java.time.LocalDate

/** A page's meme for one mood of the day, and whether Gemma already had its one try at it. */
data class SavedMeme(val meme: Meme, val gemmaTried: Boolean)

/**
 * Remembers each page's memes for the day, so they stay the same across refreshes and restarts and Gemma is
 * asked at most once per place, day and mood (a mood can flip back and forth as the forecast updates; each keeps
 * its own meme). One entry per page key, holding the date, the place it was written for, and a meme per mood;
 * it's replaced on a new day or a new place (the current-location page moving city).
 */
class MemeRepository(private val store: KeyValueStore) {

    fun get(key: String, date: LocalDate, place: String, mood: MemeMood): SavedMeme? {
        val entry = entry(key, date, place) ?: return null
        return runCatching {
            val o = entry.getJSONObject("moods").optJSONObject(mood.name) ?: return null
            SavedMeme(
                Meme(o.getString("top"), o.getString("bottom"), mood, NarrationSource.valueOf(o.getString("source"))),
                gemmaTried = o.optBoolean("gemma_tried", false),
            )
        }.getOrNull()
    }

    fun put(key: String, date: LocalDate, place: String, saved: SavedMeme) {
        val entry = entry(key, date, place) ?: JSONObject()
            .put("date", date.toString())
            .put("place", place)
            .put("moods", JSONObject())
        entry.getJSONObject("moods").put(
            saved.meme.mood.name,
            JSONObject()
                .put("top", saved.meme.top)
                .put("bottom", saved.meme.bottom)
                .put("source", saved.meme.source.name)
                .put("gemma_tried", saved.gemmaTried),
        )
        store.putString(storeKey(key), entry.toString())
    }

    /** Forgets a page's memes (the place was removed or the current-location page turned off). */
    fun remove(key: String) = store.remove(storeKey(key))

    /** The stored entry if it's for [date] and [place]; null for anything else, including unreadable JSON. */
    private fun entry(key: String, date: LocalDate, place: String): JSONObject? {
        val json = store.getString(storeKey(key)) ?: return null
        return runCatching { JSONObject(json) }.getOrNull()
            ?.takeIf { it.optString("date") == date.toString() && it.optString("place") == place && it.has("moods") }
    }

    private fun storeKey(key: String) = "meme:$key"
}
