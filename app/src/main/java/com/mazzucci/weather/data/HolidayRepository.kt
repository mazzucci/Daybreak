package com.mazzucci.weather.data

import com.mazzucci.weather.domain.Holiday
import com.mazzucci.weather.domain.LongWeekend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Holidays and long weekends per country and year. They don't change, so each is fetched once and kept in
 * [store]; a failed fetch just means no holidays on the card this time (it's retried on the next load).
 */
class HolidayRepository(private val api: HolidayApi, private val store: KeyValueStore) {
    private val mutex = Mutex()

    data class Year(val holidays: List<Holiday>, val longWeekends: List<LongWeekend>)

    /** This year's and next year's, so late-December countdowns can see January. Empty on failure. */
    suspend fun around(today: LocalDate, countryCode: String): Year {
        val years = listOf(today.year, today.year + 1).mapNotNull { year(it, countryCode) }
        return Year(years.flatMap { it.holidays }, years.flatMap { it.longWeekends })
    }

    private suspend fun year(year: Int, cc: String): Year? = mutex.withLock {
        val key = "holidays:$cc:$year"
        store.getString(key)?.let { cached -> decode(cached)?.let { return@withLock it } }
        val fetched = try {
            Year(api.publicHolidays(year, cc), api.longWeekends(year, cc))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withLock null
        }
        store.putString(key, encode(fetched))
        fetched
    }

    private fun encode(y: Year): String = JSONObject()
        .put("holidays", JSONArray(y.holidays.map { JSONObject().put("date", it.date.toString()).put("name", it.name) }))
        .put("weekends", JSONArray(y.longWeekends.map { w ->
            JSONObject().put("start", w.start.toString()).put("end", w.end.toString()).put("days", w.dayCount)
                .put("bridges", JSONArray(w.bridgeDays.map { it.toString() }))
        }))
        .toString()

    private fun decode(json: String): Year? = runCatching {
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
        )
    }.getOrNull()
}
