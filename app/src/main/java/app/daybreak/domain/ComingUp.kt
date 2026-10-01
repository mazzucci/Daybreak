package app.daybreak.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
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
    /** When it starts on [date], for your own dates that have a time ("Thu, Oct 1 · 2:00 PM"). */
    val time: LocalTime? = null,
    /** For your own dates, the [PersonalDate.id] it counts down to, so tapping it can edit it. */
    val dateId: String? = null,
) {
    enum class Kind { HOLIDAY, LONG_WEEKEND, SEASON, DAY_OFF, PERSONAL }

    fun daysFrom(today: LocalDate): Long = ChronoUnit.DAYS.between(today, date)
}

/**
 * A date of the user's own: a birthday, a big presentation, leave. One day or a run of them ([end] inclusive), with
 * their [name] for it. A [dayOff] is counted down with the break it makes; a
 * [yearly] one comes round every year on the same dates.
 *
 * It can start at a [time] (wall-clock, in whatever zone the phone is in, like a calendar's local event; for a run of
 * days, the time on the first) and have up to [REMINDERS_MAX] [reminders], which a yearly date repeats every year.
 * [id] keeps a date's reminders and notifications its own while it's edited; dates saved before reminders have none
 * ("") until they're next edited.
 */
data class PersonalDate(
    val start: LocalDate,
    val end: LocalDate = start,
    val name: String = "",
    val dayOff: Boolean = false,
    val yearly: Boolean = false,
    val time: LocalTime? = null,
    val reminders: List<Reminder> = emptyList(),
    val id: String = "",
) {
    init {
        require(!end.isBefore(start)) { "A date can't end before it starts" }
    }

    /** The name, or what it is without one. */
    val title: String get() = name.ifBlank { if (!dayOff) "Your date" else if (start == end) "Day off" else "Days off" }

    val dayCount: Long get() = ChronoUnit.DAYS.between(start, end) + 1

    fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)

    /**
     * The occurrence that's still ahead of (or under way on) [today]: this one, or for a yearly date the first
     * that hasn't ended, from last year's on (one over New Year can still be running in January). Each end moves
     * with its own year, so a 29 February falls on the 28th without stretching the range. Null once a one-off
     * date is over.
     */
    fun next(today: LocalDate): PersonalDate? {
        if (!yearly) return takeIf { !end.isBefore(today) }
        return (today.year - 1..today.year + 1).firstNotNullOfOrNull { y -> inYear(y).takeIf { !it.end.isBefore(today) } }
    }

    /** A yearly date's time round in [year] (the year it starts). */
    fun inYear(year: Int): PersonalDate {
        val s = start.withYear(year)
        return copy(start = s, end = maxOf(s, end.withYear(year + (end.year - start.year))))
    }
}

/** Longest name a date keeps; enough for "Lisbon with the kids". */
const val PERSONAL_DATE_NAME_MAX = 40

/**
 * Every day off in [dates] from yesterday on (so a break that began yesterday still counts towards the one it
 * joins), with a yearly one's next two times round.
 */
fun dayOffDates(dates: List<PersonalDate>, today: LocalDate): Set<LocalDate> {
    val from = today.minusDays(1)
    return dates.filter { it.dayOff }
        .flatMap { d -> listOfNotNull(d.next(from), d.next(from)?.takeIf { d.yearly }?.let { n -> d.next(n.end.plusDays(1)) }) }
        .flatMap { d -> generateSequence(d.start) { it.plusDays(1) }.take(d.dayCount.toInt()).toList() }
        .toSet()
}

/**
 * The user's next dates (up to [max]) within [horizonDays], soonest first: counted down to their first day, or to
 * today once under way. A day off's note says how long a break it makes with the [weekend], [holidays] and other
 * days off around it ("Makes a 4-day weekend", "9 days in a row"); once started, any multi-day date says when it
 * ends. A day off that only covers days that were off anyway (a Saturday, a holiday) gets no note.
 */
fun upcomingPersonalDates(
    today: LocalDate,
    dates: List<PersonalDate>,
    holidays: Set<LocalDate> = emptySet(),
    weekend: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
    horizonDays: Long = 120,
    max: Int = 3,
    locale: Locale = Locale.US,
): List<Countdown> {
    val offDates = dayOffDates(dates, today)
    fun dayName(d: LocalDate) = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)
    return dates.mapNotNull { it.next(today) }
        .filter { !it.start.isAfter(today.plusDays(horizonDays)) }
        .sortedBy { it.start }
        .take(max)
        .map { d ->
            val kind = if (d.dayOff) Countdown.Kind.DAY_OFF else Countdown.Kind.PERSONAL
            if (d.start.isBefore(today)) {
                return@map Countdown(kind, d.title, today, d.end, if (d.end == today) "Last day" else "Until ${dayName(d.end)}", dateId = d.id.ifBlank { null })
            }
            val end = d.end.takeIf { it != d.start }
            if (!d.dayOff) return@map Countdown(kind, d.title, d.start, end, if (d.dayCount > 1) "${d.dayCount} days" else null, d.time, d.id.ifBlank { null })
            // The whole break: out past the weekend, holidays and other days off on either side.
            fun off(x: LocalDate) = x.dayOfWeek in weekend || x in holidays || x in offDates
            var first = d.start
            while (off(first.minusDays(1)) && ChronoUnit.DAYS.between(first, d.start) < 30) first = first.minusDays(1)
            var last = d.end
            while (off(last.plusDays(1)) && ChronoUnit.DAYS.between(d.end, last) < 30) last = last.plusDays(1)
            val total = ChronoUnit.DAYS.between(first, last) + 1
            val run = generateSequence(first) { it.plusDays(1) }.take(total.toInt())
            val freesWorkdays = generateSequence(d.start) { it.plusDays(1) }.take(d.dayCount.toInt())
                .any { it.dayOfWeek !in weekend && it !in holidays }
            val note = when {
                !freesWorkdays -> null
                total == d.dayCount -> if (total > 1) "$total days" else null
                total <= 4 && run.any { it.dayOfWeek in weekend } -> "Makes a $total-day weekend"
                else -> "$total days in a row"
            }
            Countdown(kind, d.title, d.start, end, note, d.time, d.id.ifBlank { null })
        }
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
    /** The user's days off: a day of leave that's already booked isn't suggested again. */
    offDates: Set<LocalDate> = emptySet(),
): List<Countdown> {
    val limit = today.plusDays(horizonDays)
    val holiday = holidays.filter { !it.date.isBefore(today) && !it.date.isAfter(limit) }.minByOrNull { it.date }
    val weekend = longWeekends.filter { !it.end.isBefore(today) && !it.start.isAfter(limit) }.minByOrNull { it.start }
    val items = mutableListOf<Countdown>()
    if (holiday != null) {
        val around = longWeekends.firstOrNull { !holiday.date.isBefore(it.start) && !holiday.date.isAfter(it.end) }
        items += Countdown(
            Countdown.Kind.HOLIDAY, holiday.name, holiday.date,
            note = around?.let { weekendNote(it, null, today, locale, offDates) },
        )
    }
    if (weekend != null && (holiday == null || holiday.date.isBefore(weekend.start) || holiday.date.isAfter(weekend.end))) {
        items += Countdown(
            Countdown.Kind.LONG_WEEKEND, "${weekend.dayCount}-day weekend", maxOf(weekend.start, today), weekend.end,
            note = weekendNote(weekend, holidays.firstOrNull { !it.date.isBefore(weekend.start) && !it.date.isAfter(weekend.end) }?.name, today, locale, offDates),
        )
    }
    nextSeason(today, latitude)?.takeIf { !it.date.isAfter(limit) }?.let { items += it }
    return items.sortedBy { it.date }
}

/**
 * On a holiday row, the weekend it makes: "4-day weekend", or "Take Friday off for a 4-day weekend" when it
 * needs a day of leave. On a weekend row, the holiday behind it and any leave ("Thanksgiving Day · take Friday off").
 */
private fun weekendNote(w: LongWeekend, holidayName: String?, today: LocalDate, locale: Locale, offDates: Set<LocalDate>): String {
    fun dayName(d: LocalDate) = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)
    // Already under way: advice about days off is too late; say when it ends instead.
    if (w.start.isBefore(today)) return listOfNotNull(holidayName, "ends ${dayName(w.end)}").joinToString(" · ").replaceFirstChar { it.uppercase() }
    val leave = w.bridgeDays.filter { !it.isBefore(today) && it !in offDates }.takeIf { it.isNotEmpty() }?.joinToString(" and ") { dayName(it) }
    val weekend = "${w.dayCount}-day weekend"
    if (holidayName == null) return leave?.let { "Take $it off for a $weekend" } ?: weekend
    return listOfNotNull(holidayName, leave?.let { "take $it off" }).joinToString(" · ")
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
    val (date, season) = starts.first { !it.first.isBefore(today) } // on the day itself: "Today"
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
    COUNTRY_ALIASES[name.lowercase()] ?: COUNTRY_CODES_BY_NAME[name.lowercase()]
}

/** English country name → ISO code, built once (it walks every locale). */
private val COUNTRY_CODES_BY_NAME: Map<String, String> by lazy {
    Locale.getISOCountries().associateBy { Locale("", it).getDisplayCountry(Locale.ENGLISH).lowercase() }
}

/** Geocoder names that differ from the platform's locale names ("&" vs "and", common short forms). */
private val COUNTRY_ALIASES = mapOf(
    "bosnia and herzegovina" to "BA",
    "ivory coast" to "CI",
    "côte d'ivoire" to "CI",
    "dr congo" to "CD",
    "democratic republic of the congo" to "CD",
    "republic of the congo" to "CG",
    "hong kong" to "HK",
    "macao" to "MO",
    "myanmar" to "MM",
    "türkiye" to "TR",
    "turkey" to "TR",
    "czech republic" to "CZ",
    "czechia" to "CZ",
    "south korea" to "KR",
    "north korea" to "KP",
    "united states" to "US",
    "united kingdom" to "GB",
    "russia" to "RU",
    "vietnam" to "VN",
    "palestine" to "PS",
    "eswatini" to "SZ",
    "cape verde" to "CV",
)

private val SAT_SUN = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
private val FRI_SAT = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)

/** Countries whose weekend isn't Saturday and Sunday (ISO codes). */
private val WEEKENDS: Map<String, Set<DayOfWeek>> =
    listOf("BH", "BD", "DZ", "EG", "IL", "IQ", "JO", "KW", "LY", "MV", "OM", "QA", "SA", "SD", "SY", "YE").associateWith { FRI_SAT } +
        mapOf(
            "AF" to setOf(DayOfWeek.FRIDAY),
            "IR" to setOf(DayOfWeek.FRIDAY),
            "SO" to setOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            "BN" to setOf(DayOfWeek.FRIDAY, DayOfWeek.SUNDAY),
            "NP" to setOf(DayOfWeek.SATURDAY),
        )

/** The usual weekend days in a country; Saturday and Sunday when unknown. */
fun weekendDays(countryCode: String?): Set<DayOfWeek> = countryCode?.uppercase()?.let { WEEKENDS[it] } ?: SAT_SUN
