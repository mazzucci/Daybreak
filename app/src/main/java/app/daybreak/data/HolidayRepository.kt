package app.daybreak.data

import app.daybreak.domain.Holiday
import app.daybreak.domain.LongWeekend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Holidays and long weekends per country and year, kept in [store] and refreshed monthly (governments add
 * one-off holidays). A country Nager doesn't cover is cached as empty like any other answer. A failed fetch is
 * remembered for a few minutes so a flaky connection isn't retried on every page. Each country and year has its
 * own lock, so one slow fetch never holds up another country, or a page that can be answered from the cache.
 */
class HolidayRepository(
    private val api: HolidayApi,
    private val store: KeyValueStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    data class Year(val holidays: List<Holiday>, val longWeekends: List<LongWeekend>)

    private val locks = mutableMapOf<String, Mutex>()
    private val failedAt = mutableMapOf<String, Long>()

    /** This year's and next year's, so late-December countdowns can see January. Empty on failure. */
    suspend fun around(today: LocalDate, countryCode: String): Year {
        store.remove(key(countryCode, today.year - 1)) // last year's is no longer needed
        val years = listOf(today.year, today.year + 1).mapNotNull { year(it, countryCode) }
        return Year(years.flatMap { it.holidays }, years.flatMap { it.longWeekends })
    }

    private suspend fun year(year: Int, cc: String): Year? {
        val key = key(cc, year)
        val lock = synchronized(locks) { locks.getOrPut(key) { Mutex() } }
        return lock.withLock {
            val cached = store.getString(key)?.let(::decode)
            if (cached != null && now() - cached.second < MAX_AGE_MS) return@withLock cached.first
            if (failedAt[key]?.let { now() - it < RETRY_AFTER_MS } == true) return@withLock cached?.first
            try {
                Year(api.publicHolidays(year, cc), api.longWeekends(year, cc)).also {
                    store.putString(key, encode(it))
                    failedAt.remove(key)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedAt[key] = now()
                cached?.first // an old copy beats nothing
            }
        }
    }

    private fun key(cc: String, year: Int) = "holidays:$cc:$year"

    private fun encode(y: Year): String = JSONObject()
        .put("fetched_at", now())
        .put("holidays", JSONArray(y.holidays.map { JSONObject().put("date", it.date.toString()).put("name", it.name) }))
        .put("weekends", JSONArray(y.longWeekends.map { w ->
            JSONObject().put("start", w.start.toString()).put("end", w.end.toString()).put("days", w.dayCount)
                .put("bridges", JSONArray(w.bridgeDays.map { it.toString() }))
        }))
        .toString()

    /** The cached year and when it was fetched; null for anything unreadable. */
    private fun decode(json: String): Pair<Year, Long>? = runCatching {
        val o = JSONObject(json)
        val h = o.getJSONArray("holidays")
        val w = o.getJSONArray("weekends")
        Year(
            (0 until h.length()).map { h.getJSONObject(it) }.map { Holiday(LocalDate.parse(it.getString("date")), it.getString("name")) },
            (0 until w.length()).map { w.getJSONObject(it) }.map { j ->
                val b = j.getJSONArray("bridges")
                LongWeekend(
                    LocalDate.parse(j.getString("start")), LocalDate.parse(j.getString("end")), j.getInt("days"),
                    (0 until b.length()).map { LocalDate.parse(b.getString(it)) },
                )
            },
        ) to o.optLong("fetched_at", 0L)
    }.getOrNull()

    private companion object {
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        const val RETRY_AFTER_MS = 10L * 60 * 1000
    }
}
