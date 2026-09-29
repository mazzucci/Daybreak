package com.mazzucci.weather.data

import com.mazzucci.weather.domain.Holiday
import com.mazzucci.weather.domain.LongWeekend
import org.json.JSONArray
import java.time.LocalDate

interface HolidayApi {
    suspend fun publicHolidays(year: Int, countryCode: String): List<Holiday>
    suspend fun longWeekends(year: Int, countryCode: String): List<LongWeekend>
}

/** Nager.Date (free, no API key, HTTPS). Only the country code and year are sent. */
class NagerHolidayApi(private val http: HttpClient) : HolidayApi {
    override suspend fun publicHolidays(year: Int, countryCode: String): List<Holiday> =
        parsePublicHolidays(http.get("$BASE/PublicHolidays/$year/${countryCode.uppercase()}"))

    override suspend fun longWeekends(year: Int, countryCode: String): List<LongWeekend> =
        parseLongWeekends(http.get("$BASE/LongWeekend/$year/${countryCode.uppercase()}"))

    private companion object {
        const val BASE = "https://date.nager.at/api/v3"
    }
}

/** Nationwide ("global") public holidays only; regional ones would need the place's state or province code. */
fun parsePublicHolidays(json: String): List<Holiday> {
    val array = JSONArray(json)
    return (0 until array.length()).map { array.getJSONObject(it) }
        .filter { it.optBoolean("global", true) && typesInclude(it.optJSONArray("types"), "Public") }
        .map { Holiday(LocalDate.parse(it.getString("date")), it.getString("name")) }
        .distinctBy { it.date to it.name }
}

fun parseLongWeekends(json: String): List<LongWeekend> {
    val array = JSONArray(json)
    return (0 until array.length()).map { array.getJSONObject(it) }.map { o ->
        val bridges = o.optJSONArray("bridgeDays")
        LongWeekend(
            start = LocalDate.parse(o.getString("startDate")),
            end = LocalDate.parse(o.getString("endDate")),
            dayCount = o.getInt("dayCount"),
            bridgeDays = if (bridges == null) emptyList() else (0 until bridges.length()).map { LocalDate.parse(bridges.getString(it)) },
        )
    }
}

private fun typesInclude(types: JSONArray?, type: String): Boolean =
    types == null || (0 until types.length()).any { types.optString(it) == type }
