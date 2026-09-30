package app.daybreak.data

import app.daybreak.domain.Holiday
import app.daybreak.domain.LongWeekend
import org.json.JSONArray
import java.time.LocalDate

interface HolidayApi {
    suspend fun publicHolidays(year: Int, countryCode: String): List<Holiday>
    suspend fun longWeekends(year: Int, countryCode: String): List<LongWeekend>
}

/**
 * Nager.Date (free, no API key, HTTPS). Requests carry the country code and year (and, like any request, the
 * phone's IP address). Countries Nager doesn't cover answer 204 or 404, which means "no holidays", not an error.
 */
class NagerHolidayApi(private val http: HttpClient) : HolidayApi {
    override suspend fun publicHolidays(year: Int, countryCode: String): List<Holiday> =
        getOrEmpty("$BASE/PublicHolidays/$year/${countryCode.uppercase()}")?.let(::parsePublicHolidays).orEmpty()

    override suspend fun longWeekends(year: Int, countryCode: String): List<LongWeekend> =
        getOrEmpty("$BASE/LongWeekend/$year/${countryCode.uppercase()}")?.let(::parseLongWeekends).orEmpty()

    private suspend fun getOrEmpty(url: String): String? = try {
        http.get(url).ifBlank { null }
    } catch (e: HttpException) {
        if (e.code == 204 || e.code == 404) null else throw e
    }

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
