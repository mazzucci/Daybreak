package app.daybreak.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.MonthDay
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/** The moon on a given night: how far through its cycle it is (0 new, 0.5 full) and how much of it is lit. */
data class MoonPhase(val age: Double, val illumination: Double) {
    /**
     * "New moon", "Waxing crescent", "First quarter", … by how much is lit, the way people see it: new and full are
     * the nights it's (nearly) all dark or lit, a quarter the nights it's about half lit.
     */
    val name: String get() = when {
        illumination < 0.03 -> "New moon"
        illumination > 0.97 -> "Full moon"
        illumination in 0.45..0.55 -> if (waxing) "First quarter" else "Last quarter"
        illumination < 0.5 -> if (waxing) "Waxing crescent" else "Waning crescent"
        else -> if (waxing) "Waxing gibbous" else "Waning gibbous"
    }

    /** Whether the lit side is growing (the right side, seen from the northern hemisphere). */
    val waxing: Boolean get() = age < SYNODIC_DAYS / 2
}

/** Mean length of the lunar cycle, new moon to new moon. */
const val SYNODIC_DAYS = 29.530588853

/** A known new moon (6 January 2000, 18:14 UTC); the mean cycle from it is within a day of the true phases. */
private val EPOCH = Instant.parse("2000-01-06T18:14:00Z")

fun moonPhase(at: Instant): MoonPhase {
    val days = Duration.between(EPOCH, at).toMinutes() / 1440.0
    val age = ((days % SYNODIC_DAYS) + SYNODIC_DAYS) % SYNODIC_DAYS
    return MoonPhase(age, (1 - cos(2 * PI * age / SYNODIC_DAYS)) / 2)
}

/** The next full moon on or after [at], to the nearest day in [zone]. */
fun nextFullMoon(at: Instant, zone: ZoneId): LocalDate {
    val age = moonPhase(at).age
    val until = (SYNODIC_DAYS / 2 - age).let { if (it < -0.5) it + SYNODIC_DAYS else it.coerceAtLeast(0.0) }
    return at.plusSeconds((until * 86400).toLong()).atZone(zone).toLocalDate()
}

/**
 * The traditional (northern) name of the full moon on [date]: the Harvest Moon is the one nearest the September
 * equinox and the Hunter's Moon the one after it; the rest go by month.
 */
fun fullMoonName(date: LocalDate): String {
    val equinox = LocalDate.of(date.year, Month.SEPTEMBER, 22)
    val fromEquinox = ChronoUnit.DAYS.between(equinox, date)
    return when {
        abs(fromEquinox) <= SYNODIC_DAYS / 2 -> "Harvest Moon"
        fromEquinox in 15..45 -> "Hunter's Moon"
        else -> when (date.month) {
            Month.JANUARY -> "Wolf Moon"
            Month.FEBRUARY -> "Snow Moon"
            Month.MARCH -> "Worm Moon"
            Month.APRIL -> "Pink Moon"
            Month.MAY -> "Flower Moon"
            Month.JUNE -> "Strawberry Moon"
            Month.JULY -> "Buck Moon"
            Month.AUGUST -> "Sturgeon Moon"
            Month.SEPTEMBER -> "Corn Moon"
            Month.OCTOBER -> "Hunter's Moon"
            Month.NOVEMBER -> "Beaver Moon"
            Month.DECEMBER -> "Cold Moon"
        }
    }
}

/** A yearly meteor shower: the night it peaks and roughly how many meteors an hour under a dark sky. */
data class MeteorShower(val name: String, val peak: MonthDay, val perHour: Int)

/** The major showers, by their usual peak night (they shift by a day or so between years). */
val METEOR_SHOWERS = listOf(
    MeteorShower("Quadrantids", MonthDay.of(1, 3), 110),
    MeteorShower("Lyrids", MonthDay.of(4, 22), 18),
    MeteorShower("Eta Aquariids", MonthDay.of(5, 6), 50),
    MeteorShower("Perseids", MonthDay.of(8, 12), 100),
    MeteorShower("Draconids", MonthDay.of(10, 8), 10),
    MeteorShower("Orionids", MonthDay.of(10, 21), 20),
    MeteorShower("Leonids", MonthDay.of(11, 17), 15),
    MeteorShower("Geminids", MonthDay.of(12, 14), 120),
)

/** The next shower peaking within [withinDays] of [today] (tonight included), with its peak date. */
fun nextMeteorShower(today: LocalDate, withinDays: Long = 14): Pair<MeteorShower, LocalDate>? =
    METEOR_SHOWERS.flatMap { s -> listOf(s.peak.atYear(today.year), s.peak.atYear(today.year + 1)).map { s to it } }
        .filter { (_, d) -> !d.isBefore(today) && ChronoUnit.DAYS.between(today, d) <= withinDays }
        .minByOrNull { it.second }

/** Tonight's sky from the forecast: clear, partly cloudy or cloudy, and from when it clears if it does. */
data class NightSky(val clear: Boolean, val clearFrom: LocalDateTime?, val cloudy: Boolean)

/**
 * The hours from dusk (sunset, or now if later) to 2 AM. Clear or mainly clear (codes 0–1) most of that time is a
 * clear night, anything with rain, snow, fog or overcast most of it is a cloudy one; [NightSky.clearFrom] is the
 * first clear hour after a cloudy start. Null when the forecast doesn't reach tonight.
 */
fun nightSky(forecast: Forecast): NightSky? {
    val now = forecast.current.time
    val dusk = maxOf(forecast.today.sunset ?: now.toLocalDate().atTime(19, 0), now).truncatedTo(ChronoUnit.HOURS)
    val end = now.toLocalDate().plusDays(1).atTime(2, 0)
    val hours = forecast.hours.filter { !it.time.isBefore(dusk) && it.time.isBefore(end) }
    if (hours.isEmpty()) return null
    fun clear(h: HourForecast) = h.code <= 1
    val clearShare = hours.count(::clear).toDouble() / hours.size
    val first = hours.firstOrNull(::clear)
    return NightSky(
        clear = clearShare >= 0.6,
        clearFrom = first?.time?.takeIf { clearShare >= 0.3 && it != hours.first().time },
        cloudy = clearShare < 0.3,
    )
}

/**
 * The card's lines: the phase and how lit ("Waxing gibbous · 87% lit"), what's next for the moon ("Full moon on
 * Friday, the Hunter's Moon"), a meteor shower when one's close ("Orionids peak in 3 days · up to 20 an hour"),
 * and whether to look up tonight.
 */
data class SkyCopy(val phase: String, val moon: String, val meteors: String?, val tonight: String?)

fun describeSky(now: Instant, zone: ZoneId, forecast: Forecast?, use24Hour: Boolean = ClockFormat.use24Hour): SkyCopy {
    val phase = moonPhase(now)
    val today = now.atZone(zone).toLocalDate()
    val lit = (phase.illumination * 100).roundToInt()
    // Within a day of full it's tonight's moon, even just after the exact moment.
    val full = if (abs(phase.age - SYNODIC_DAYS / 2) < 1.0) today else nextFullMoon(now, zone)
    val days = ChronoUnit.DAYS.between(today, full)
    val when_ = when (days) {
        0L -> "tonight"
        1L -> "tomorrow"
        in 2..6 -> "on ${full.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US)}"
        else -> "in $days days"
    }
    val moon = if (days == 0L) "It's the ${fullMoonName(full)}" else "Full moon $when_, the ${fullMoonName(full)}"
    val meteors = nextMeteorShower(today)?.let { (s, d) ->
        val inDays = ChronoUnit.DAYS.between(today, d)
        val peak = when (inDays) { 0L -> "peak tonight"; 1L -> "peak tomorrow night"; else -> "peak in $inDays days" }
        // A bright moon washes out all but the brightest meteors.
        val moonNote = if (moonPhase(d.atTime(23, 0).atZone(zone).toInstant()).illumination > 0.6) ", though the moon will be bright" else ""
        "${s.name} $peak · up to ${s.perHour} an hour$moonNote"
    }
    val tonight = forecast?.let(::nightSky)?.let { sky ->
        when {
            sky.clear -> "Clear tonight: a good night to look up"
            sky.clearFrom != null -> "Clearing from ${formatHour(sky.clearFrom, java.util.Locale.US, use24Hour)}"
            sky.cloudy -> "Clouds will hide it tonight"
            else -> "Some clouds tonight"
        }
    }
    val phaseLine = if (phase.name == "New moon") "New moon · a dark sky" else "${phase.name} · $lit% lit"
    return SkyCopy(phaseLine, moon, meteors, tonight)
}
