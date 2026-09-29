package com.mazzucci.weather.domain

import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** A public holiday in a place's country (nationwide ones only). */
data class Holiday(val date: LocalDate, val name: String)

/** A run of days off (weekend plus holiday), as Nager.Date reports it. [bridgeDays] need a day of leave. */
data class LongWeekend(val start: LocalDate, val end: LocalDate, val dayCount: Int, val bridgeDays: List<LocalDate>)

/** Something worth counting down to. [endDate] is set for multi-day events (a long weekend). */
data class Countdown(
    val kind: Kind,
    val title: String,
    val date: LocalDate,
    val endDate: LocalDate? = null,
    /** Extra context: "4-day weekend", "Take Friday off for a 4-day weekend". */
    val note: String? = null,
) {
    enum class Kind { HOLIDAY, LONG_WEEKEND, SEASON }

    fun daysFrom(today: LocalDate): Long = ChronoUnit.DAYS.between(today, date)
}

/**
 * The next holiday, the next long weekend (if it isn't just that holiday's weekend) and the next season, soonest
 * first. Only things within [horizonDays] count, so the card doesn't announce Christmas in February.
 */
fun comingUp(
    today: LocalDate,
    holidays: List<Holiday>,
    longWeekends: List<LongWeekend>,
    latitude: Double,
    horizonDays: Long = 120,
    locale: Locale = Locale.US,
): List<Countdown> {
    val limit = today.plusDays(horizonDays)
    val holiday = holidays.filter { !it.date.isBefore(today) && !it.date.isAfter(limit) }.minByOrNull { it.date }
    val weekend = longWeekends.filter { !it.end.isBefore(today) && !it.start.isAfter(limit) }.minByOrNull { it.start }
    val items = mutableListOf<Countdown>()
    if (holiday != null) {
        val around = longWeekends.firstOrNull { !holiday.date.isBefore(it.start) && !holiday.date.isAfter(it.end) }
        items += Countdown(
            Countdown.Kind.HOLIDAY, holiday.name, holiday.date,
            note = around?.let { weekendNote(it, null, locale) },
        )
    }
    if (weekend != null && (holiday == null || holiday.date.isBefore(weekend.start) || holiday.date.isAfter(weekend.end))) {
        items += Countdown(
            Countdown.Kind.LONG_WEEKEND, "${weekend.dayCount}-day weekend", maxOf(weekend.start, today), weekend.end,
            note = weekendNote(weekend, holidays.firstOrNull { !it.date.isBefore(weekend.start) && !it.date.isAfter(weekend.end) }?.name, locale),
        )
    }
    nextSeason(today, latitude)?.takeIf { !it.date.isAfter(limit) }?.let { items += it }
    return items.sortedBy { it.date }
}

/**
 * "4-day weekend" on a holiday row; on a weekend row, the holiday behind it and any day of leave it needs
 * ("Thanksgiving Day · take Friday off").
 */
private fun weekendNote(w: LongWeekend, holidayName: String?, locale: Locale): String {
    val leave = w.bridgeDays.takeIf { it.isNotEmpty() }
        ?.joinToString(" and ") { it.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale) }
    if (holidayName == null && leave == null) return "${w.dayCount}-day weekend"
    return listOfNotNull(holidayName, leave?.let { "take $it off" }).joinToString(" · ").replaceFirstChar { it.uppercase() }
}

/**
 * The next astronomical season start at [latitude]: equinoxes and solstices, using their usual dates (they drift
 * by a day between years, which is fine for a countdown). Near the equator seasons don't mean much, so none.
 */
fun nextSeason(today: LocalDate, latitude: Double): Countdown? {
    if (abs(latitude) < 15) return null
    val north = latitude >= 0
    val starts = (today.year..today.year + 1).flatMap { y ->
        listOf(
            LocalDate.of(y, Month.MARCH, 20) to if (north) "spring" else "autumn",
            LocalDate.of(y, Month.JUNE, 21) to if (north) "summer" else "winter",
            LocalDate.of(y, Month.SEPTEMBER, 22) to if (north) "autumn" else "spring",
            LocalDate.of(y, Month.DECEMBER, 21) to if (north) "winter" else "summer",
        )
    }
    val (date, season) = starts.first { it.first.isAfter(today) }
    return Countdown(Countdown.Kind.SEASON, "First day of $season", date)
}

/** "Today", "Tomorrow", "In 5 days", "In 3 weeks". */
fun formatCountdown(days: Long): String = when {
    days <= 0 -> "Today"
    days == 1L -> "Tomorrow"
    days < 14 -> "In $days days"
    days < 60 -> "In ${(days + 3) / 7} weeks"
    else -> "In ${(days + 15) / 30} months"
}

/**
 * The place's country code: the geocoder's when known, else looked up from the English country name (places
 * saved before the code was stored).
 */
fun countryCodeOf(place: Place): String? = place.countryCode ?: place.country?.let { name ->
    Locale.getISOCountries().firstOrNull { Locale("", it).getDisplayCountry(Locale.ENGLISH).equals(name, ignoreCase = true) }
}
