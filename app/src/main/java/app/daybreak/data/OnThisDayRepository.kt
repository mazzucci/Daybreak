package app.daybreak.data

import app.daybreak.domain.OnThisDay
import app.daybreak.domain.OnThisDayEvent
import app.daybreak.domain.OnThisDayPick
import app.daybreak.domain.OnThisDayPicture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * The day's picks for the "On this day" card, and which one is showing ("Another" moves it on). [complete] is
 * false for a day made of the curated list alone because the full list that should have made it up couldn't be
 * fetched: shown, but not kept, and asked for again later.
 */
data class OnThisDayToday(
    val date: LocalDate,
    val picks: List<OnThisDayPick>,
    val index: Int = 0,
    val complete: Boolean = true,
) {
    val current: OnThisDayPick? get() = picks.getOrNull(index.mod(picks.size.coerceAtLeast(1)))
}

/**
 * Fetches "On this day" once per local date and keeps the day's picks (and the one showing) in [store], so the
 * card is the same all day, across restarts, and costs one request a day: the curated list, plus the full list of
 * events only when too few of the curated ones are cheerful enough. Nothing is fetched ahead. A failed fetch gives
 * null (Home hides the card), or the curated few when only the full list failed, and is remembered for a few
 * minutes so a flaky connection isn't retried on every resume; pull-to-refresh asks with `force` and skips that
 * wait. Nothing personal is kept: just the date and what Wikipedia said about it. Choosing and the stored JSON
 * run on [background], off the main thread.
 */
class OnThisDayRepository(
    private val api: OnThisDayApi,
    private val store: KeyValueStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val background: CoroutineDispatcher = Dispatchers.Default,
) {
    private val lock = Mutex()

    /** The last failure: its date, when, and the curated few if those were had. */
    private var failed: Failure? = null

    private class Failure(val date: LocalDate, val at: Long, val partial: OnThisDayToday?)

    /**
     * Today's picks (empty when nothing that day was cheerful enough), or null when they couldn't be fetched.
     * Within a few minutes of a failure for [date] it doesn't ask again (giving what it had then) unless [force].
     */
    suspend fun today(date: LocalDate, force: Boolean = false): OnThisDayToday? = lock.withLock {
        cached(date)?.let { return@withLock it }
        failed?.let { if (!force && it.date == date && now() - it.at < RETRY_AFTER_MS) return@withLock it.partial }
        try {
            val selected = api.events(date.monthValue, date.dayOfMonth, OnThisDayFeed.SELECTED)
            val first = withContext(background) { OnThisDay.choose(selected, date) }
            if (first.size >= OnThisDay.MIN_SELECTED) return@withLock done(OnThisDayToday(date, first))
            val more = more(date, selected, first)
            if (more == null) {
                // The few stand for now, unsaved, and the full list is asked for again on a later resume.
                OnThisDayToday(date, first, complete = false).also { failed = Failure(date, now(), it) }
            } else {
                done(OnThisDayToday(date, first + more))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = Failure(date, now(), null)
            null
        }
    }

    private suspend fun done(day: OnThisDayToday): OnThisDayToday {
        save(day)
        failed = null
        return day
    }

    /** Shows the pick at [index] for the rest of [date] (from "Another"); nothing for another day, or an unkept one. */
    suspend fun setIndex(date: LocalDate, index: Int) = lock.withLock {
        val day = cached(date) ?: return@withLock
        save(day.copy(index = index))
    }

    /** From the full list, to make up the curated one's few; null when it can't be had. */
    private suspend fun more(date: LocalDate, selected: List<OnThisDayEvent>, have: List<OnThisDayPick>): List<OnThisDayPick>? = try {
        val events = api.events(date.monthValue, date.dayOfMonth, OnThisDayFeed.EVENTS)
        withContext(background) { OnThisDay.choose(events, date, OnThisDay.MAX_PICKS - have.size, besides = selected, taken = have) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** The stored day if it's [date]'s; null for another day's, or anything unreadable. */
    private suspend fun cached(date: LocalDate): OnThisDayToday? = withContext(background) {
        val json = store.getString(KEY) ?: return@withContext null
        runCatching {
            val o = JSONObject(json)
            if (o.getString("date") != date.toString()) return@withContext null
            val a = o.getJSONArray("picks")
            OnThisDayToday(date, (0 until a.length()).map { pickOf(a.getJSONObject(it)) }, o.optInt("index", 0))
        }.getOrNull()
    }

    private fun pickOf(p: JSONObject) = OnThisDayPick(
        year = p.getInt("year"),
        text = p.getString("text"),
        title = p.getString("title"),
        url = p.getString("url"),
        picture = p.optJSONObject("picture")?.let {
            OnThisDayPicture(
                url = it.getString("url"),
                fallbackUrl = it.optString("fallback").ifBlank { null },
                width = it.getInt("width"),
                height = it.getInt("height"),
                fromSvg = it.optBoolean("svg"),
            )
        },
    )

    private suspend fun save(day: OnThisDayToday) = withContext(background) {
        store.remove(OLD_KEY)
        store.putString(
            KEY,
            JSONObject()
                .put("date", day.date.toString())
                .put("index", day.index)
                .put("picks", JSONArray(day.picks.map { pick ->
                    JSONObject().put("year", pick.year).put("text", pick.text).put("title", pick.title).put("url", pick.url)
                        .putOpt("picture", pick.picture?.let {
                            JSONObject().put("url", it.url).putOpt("fallback", it.fallbackUrl)
                                .put("width", it.width).put("height", it.height).put("svg", it.fromSvg)
                        })
                }))
                .toString(),
        )
    }

    private companion object {
        /** One entry, replaced each day, so yesterday's never lingers. (v2: picks with their picture's size.) */
        const val KEY = "on_this_day_v2"
        /** The first version's entry, from test builds: dropped on the first save. */
        const val OLD_KEY = "on_this_day"
        const val RETRY_AFTER_MS = 10L * 60 * 1000
    }
}
