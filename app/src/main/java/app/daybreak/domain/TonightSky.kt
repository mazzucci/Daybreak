package app.daybreak.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// --- The moon's phases (Meeus, Astronomical Algorithms, ch. 49 and 27) ---------------------------------------

/** Mean length of the lunar cycle, new moon to new moon. */
const val SYNODIC_DAYS = 29.530588861

private const val J2000_NEW_MOON = 2451550.09766
private const val UNIX_EPOCH_JD = 2440587.5

private fun jdOf(t: Instant): Double = t.epochSecond / 86400.0 + UNIX_EPOCH_JD
private fun instantOf(jd: Double): Instant = Instant.ofEpochMilli(((jd - UNIX_EPOCH_JD) * 86_400_000).toLong())
private fun rad(deg: Double) = deg * PI / 180

/**
 * The instant of lunation [k]'s new moon (whole k) or full moon (k + 0.5): the mean phase plus the main periodic
 * terms, good to a few minutes (the mean cycle alone is up to 18 hours out, a wrong date a third of the time).
 */
fun moonPhaseInstant(k: Double): Instant {
    val t = k / 1236.85
    var jde = J2000_NEW_MOON + SYNODIC_DAYS * k + 0.00015437 * t * t - 0.00000015 * t * t * t
    val e = 1 - 0.002516 * t - 0.0000074 * t * t
    val m = rad(2.5534 + 29.1053567 * k - 0.0000014 * t * t)
    val mp = rad(201.5643 + 385.81693528 * k + 0.0107582 * t * t + 0.00001238 * t * t * t)
    val f = rad(160.7108 + 390.67050284 * k - 0.0016118 * t * t - 0.00000227 * t * t * t)
    val om = rad(124.7746 - 1.56375588 * k + 0.0020672 * t * t)
    val full = abs(k - floor(k) - 0.5) < 0.01
    jde += (if (full) -0.40614 else -0.40720) * sin(mp) +
        (if (full) 0.17302 else 0.17241) * e * sin(m) +
        (if (full) 0.01614 else 0.01608) * sin(2 * mp) +
        (if (full) 0.01043 else 0.01039) * sin(2 * f) +
        (if (full) 0.00734 else 0.00739) * e * sin(mp - m) -
        (if (full) 0.00515 else 0.00514) * e * sin(mp + m) +
        (if (full) 0.00209 else 0.00208) * e * e * sin(2 * m) -
        0.00111 * sin(mp - 2 * f) - 0.00057 * sin(mp + 2 * f) + 0.00056 * e * sin(2 * mp + m) -
        0.00042 * sin(3 * mp) + 0.00042 * e * sin(m + 2 * f) + 0.00038 * e * sin(m - 2 * f) -
        0.00024 * e * sin(2 * mp - m) - 0.00017 * sin(om)
    return instantOf(jde)
}

/** The lunation whose new moon is the last one at or before [at]. */
private fun lunationAt(at: Instant): Double {
    var k = floor((jdOf(at) - J2000_NEW_MOON) / SYNODIC_DAYS)
    if (moonPhaseInstant(k) > at) k -= 1
    if (moonPhaseInstant(k + 1) <= at) k += 1
    return k
}

/**
 * The moon at an instant: [fraction] of the way through its cycle (0 new, 0.5 full), measured between the real
 * new and full moons around it, and how much of it is lit.
 */
data class MoonPhase(val fraction: Double, val illumination: Double) {
    /** Whether the lit part is growing. */
    val waxing: Boolean get() = fraction < 0.5

    /** "Waxing crescent", "First quarter", "Waxing gibbous", … by how much is lit (full and new are nights: see [describeSky]). */
    val name: String get() = when {
        illumination < 0.03 -> "New moon"
        illumination > 0.985 -> "Full moon"
        illumination in 0.45..0.55 -> if (waxing) "First quarter" else "Last quarter"
        illumination < 0.5 -> if (waxing) "Waxing crescent" else "Waning crescent"
        else -> if (waxing) "Waxing gibbous" else "Waning gibbous"
    }
}

fun moonPhase(at: Instant): MoonPhase {
    val k = lunationAt(at)
    val new = moonPhaseInstant(k)
    val full = moonPhaseInstant(k + 0.5)
    val next = moonPhaseInstant(k + 1)
    val fraction = if (at < full) 0.5 * ChronoUnit.SECONDS.between(new, at) / ChronoUnit.SECONDS.between(new, full)
    else 0.5 + 0.5 * ChronoUnit.SECONDS.between(full, at) / ChronoUnit.SECONDS.between(full, next)
    return MoonPhase(fraction, (1 - cos(2 * PI * fraction)) / 2)
}

/** The full moons just before (or at) and after [at]. */
fun fullMoonsAround(at: Instant): Pair<Instant, Instant> {
    val k = lunationAt(at)
    val thisFull = moonPhaseInstant(k + 0.5)
    return if (thisFull <= at) thisFull to moonPhaseInstant(k + 1.5) else moonPhaseInstant(k - 0.5) to thisFull
}

/**
 * The night an instant belongs to, where the viewer is: the small hours count as the evening before, so a 3 AM
 * full moon or a Perseid at 1 AM is "tonight" for the night of the 12th, not the 13th.
 */
fun nightOf(local: LocalDateTime): LocalDate = if (local.hour < 6) local.toLocalDate().minusDays(1) else local.toLocalDate()

fun nightOf(at: Instant, zone: ZoneId): LocalDate = nightOf(at.atZone(zone).toLocalDateTime())

/** The September equinox of [year] (Meeus Table 27.C; minutes out, which is plenty here). */
fun septemberEquinox(year: Int): Instant {
    val y = (year - 2000) / 1000.0
    return instantOf(2451810.21715 + 365242.01767 * y - 0.11575 * y * y + 0.00337 * y * y * y + 0.00078 * y * y * y * y)
}

/**
 * The traditional (northern) name of the full moon at [full]: the Harvest Moon is the one nearest the September
 * equinox and the Hunter's Moon the one after it; the rest go by the month of their night. Null south of the
 * equator, where these northern seasons' names would be wrong.
 */
fun fullMoonName(full: Instant, zone: ZoneId, latitude: Double = 45.0): String? {
    if (latitude < 0) return null
    val equinox = septemberEquinox(full.atZone(zone).year)
    val (before, after) = fullMoonsAround(equinox)
    val harvest = if (ChronoUnit.SECONDS.between(before, equinox) < ChronoUnit.SECONDS.between(equinox, after)) before else after
    val hunters = fullMoonsAround(harvest.plusSeconds(3600)).second
    fun same(a: Instant, b: Instant) = abs(ChronoUnit.HOURS.between(a, b)) < 24
    return when {
        same(full, harvest) -> "Harvest Moon"
        same(full, hunters) -> "Hunter's Moon"
        else -> when (nightOf(full, zone).month) {
            Month.JANUARY -> "Wolf Moon"
            Month.FEBRUARY -> "Snow Moon"
            Month.MARCH -> "Worm Moon"
            Month.APRIL -> "Pink Moon"
            Month.MAY -> "Flower Moon"
            Month.JUNE -> "Strawberry Moon"
            Month.JULY -> "Buck Moon"
            Month.AUGUST -> "Sturgeon Moon"
            // Only when the Harvest Moon fell in October.
            Month.SEPTEMBER -> "Corn Moon"
            Month.OCTOBER -> "Hunter's Moon"
            Month.NOVEMBER -> "Beaver Moon"
            Month.DECEMBER -> "Cold Moon"
        }
    }
}

// --- Meteor showers --------------------------------------------------------------------------------------------

/**
 * A yearly meteor shower: the night it peaks (the evening's date), roughly how many meteors an hour under a dark
 * sky, and its radiant's declination, which decides where on Earth it's worth watching.
 */
data class MeteorShower(val name: String, val peak: MonthDay, val perHour: Int, val declination: Int) {
    /** Worth watching where the radiant climbs at least 20° high: within 70° of the latitude. */
    fun visibleFrom(latitude: Double): Boolean = abs(latitude - declination) <= 70
}

/** The major showers, by their usual peak night (they shift by a day or so between years). */
val METEOR_SHOWERS = listOf(
    MeteorShower("Quadrantids", MonthDay.of(1, 3), 110, 49),
    MeteorShower("Lyrids", MonthDay.of(4, 22), 18, 33),
    MeteorShower("Eta Aquariids", MonthDay.of(5, 5), 50, -1),
    MeteorShower("Perseids", MonthDay.of(8, 12), 100, 58),
    MeteorShower("Draconids", MonthDay.of(10, 8), 10, 54),
    MeteorShower("Orionids", MonthDay.of(10, 21), 20, 16),
    MeteorShower("Leonids", MonthDay.of(11, 17), 15, 22),
    MeteorShower("Geminids", MonthDay.of(12, 13), 120, 33),
)

/** The next shower visible from [latitude] peaking within [withinDays] nights of [night] (tonight included). */
fun nextMeteorShower(night: LocalDate, latitude: Double = 45.0, withinDays: Long = 7): Pair<MeteorShower, LocalDate>? =
    METEOR_SHOWERS.filter { it.visibleFrom(latitude) }
        .flatMap { s -> listOf(s.peak.atYear(night.year), s.peak.atYear(night.year + 1)).map { s to it } }
        .filter { (_, d) -> !d.isBefore(night) && ChronoUnit.DAYS.between(night, d) <= withinDays }
        .minByOrNull { it.second }

// --- Tonight's clouds ------------------------------------------------------------------------------------------

/**
 * Tonight's sky from the forecast: [clear] most of the dark hours, [clearFrom] the hour it clears for good after a
 * cloudy start, [cloudy] under a third clear; [noDark] when there's no real night (the midnight sun).
 */
data class NightSky(val clear: Boolean, val clearFrom: LocalDateTime?, val cloudy: Boolean, val noDark: Boolean = false)

/**
 * The dark hours of tonight (see [nightOf]) still ahead of [now], at the forecast's place (its own UTC offset):
 * the hours it calls night (or after sunset) up to 5 AM. Clear or mainly clear (codes 0–1) for most of them is a
 * clear night, for under a third a cloudy one. Null when the forecast doesn't reach tonight.
 */
fun nightSky(forecast: Forecast, now: Instant): NightSky? {
    val local = now.atOffset(ZoneOffset.ofTotalSeconds(forecast.utcOffsetSeconds)).toLocalDateTime()
    val night = nightOf(local)
    val day = forecast.days.firstOrNull { it.date == night }
    if (day?.daylight == Daylight.MIDNIGHT_SUN) return NightSky(clear = false, clearFrom = null, cloudy = false, noDark = true)
    val start = maxOf(local.truncatedTo(ChronoUnit.HOURS), night.atTime(12, 0))
    val end = night.plusDays(1).atTime(5, 0)
    val window = forecast.hours.filter { !it.time.isBefore(start) && it.time.isBefore(end) }
    if (window.isEmpty()) return null
    val dark = window.filter { h -> h.isDay?.not() ?: (day?.sunset?.let { !h.time.isBefore(it) } ?: (h.time.hour >= 19 || h.time.hour < 5)) }
    if (dark.isEmpty()) return null
    fun clear(h: HourForecast) = h.code <= 1
    val share = dark.count(::clear).toDouble() / dark.size
    val first = dark.firstOrNull(::clear)
    // Clearing only if it stays clear once it does.
    val clearing = first?.takeIf { f -> share >= 0.3 && f != dark.first() && dark.dropWhile { it != f }.all(::clear) }
    return NightSky(clear = share >= 0.6, clearFrom = clearing?.time, cloudy = share < 0.3)
}

// --- The card's words ----------------------------------------------------------------------------------------

/**
 * The card's lines, each short enough for one line: whether to look up tonight ([tonight], first, since the card
 * is about tonight; [clearTonight] when it's worth it), the moon ("Full moon Sunday: the Hunter's Moon", "It's
 * the Harvest Moon tonight", "Just past full: the Harvest Moon"), and a meteor shower in the coming week
 * ("Orionids peak Friday · up to 20 an hour", or "moonlight will hide most" when the moon will be up and bright).
 */
data class SkyCopy(
    val phase: String,
    val moon: String,
    val meteors: String?,
    val tonight: String?,
    val clearTonight: Boolean = false,
) {
    /** All of it for screen readers, with commas for the dots. */
    val spoken: String get() = listOfNotNull(phase, tonight, moon, meteors).joinToString(". ") { it.replace(" · ", ", ") } + "."
}

/**
 * Everything for tonight, from the clock, the viewer's [zone] and [latitude], and (for the clouds) the Home
 * place's [forecast]. Full moon is the night it falls on, so the phase line and the moon line always agree.
 */
fun describeSky(
    now: Instant,
    zone: ZoneId,
    forecast: Forecast?,
    latitude: Double = 45.0,
    use24Hour: Boolean = ClockFormat.use24Hour,
): SkyCopy {
    val night = nightOf(now, zone)
    val phase = moonPhase(now)
    val lit = (phase.illumination * 100).roundToInt()
    val (lastFull, nextFull) = fullMoonsAround(now)
    val lastNight = nightOf(lastFull, zone)
    val nextNight = nightOf(nextFull, zone)
    fun weekday(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
    fun named(full: Instant, lead: String): String = fullMoonName(full, zone, latitude)?.let { "$lead: the $it" } ?: lead
    val fullTonight = lastNight == night || nextNight == night
    val sincePast = ChronoUnit.DAYS.between(lastNight, night)
    val moon = when {
        fullTonight -> fullMoonName(if (nextNight == night) nextFull else lastFull, zone, latitude)
            ?.let { "It's the $it tonight" } ?: "Full moon tonight"
        sincePast in 1..3 -> named(lastFull, "Just past full")
        else -> {
            val whenText = when (ChronoUnit.DAYS.between(night, nextNight)) {
                1L -> "tomorrow"
                in 2..6 -> weekday(nextNight)
                else -> nextNight.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
            }
            named(nextFull, "Full moon $whenText")
        }
    }
    val phaseLine = when {
        fullTonight -> "Full moon · $lit% lit"
        phase.illumination < 0.03 -> "New moon · a dark sky"
        // Not the full night, so not called full even at 99% lit.
        phase.name == "Full moon" -> "${if (phase.waxing) "Waxing" else "Waning"} gibbous · $lit% lit"
        else -> "${phase.name} · $lit% lit"
    }
    val meteors = nextMeteorShower(night, latitude)?.let { (s, d) ->
        val peak = when (ChronoUnit.DAYS.between(night, d)) { 0L -> "peak tonight"; 1L -> "peak tomorrow night"; else -> "peak ${weekday(d)}" }
        // A bright moon up in the small hours, when meteors are best, washes out all but the brightest. A waxing
        // moon has set by then, so only a waning or nearly full one counts.
        val moonThen = moonPhase(d.plusDays(1).atTime(2, 0).atZone(zone).toInstant())
        val bright = moonThen.illumination > 0.6 && (!moonThen.waxing || moonThen.illumination > 0.9)
        "${s.name} $peak · ${if (bright) "moonlight will hide most" else "up to ${s.perHour} an hour"}"
    }
    val sky = forecast?.let { nightSky(it, now) }
    val tonight = sky?.let {
        when {
            it.noDark -> "No real darkness tonight"
            it.clear -> "Clear tonight: a good night to look up"
            it.clearFrom != null -> "Clearing from ${formatHour(it.clearFrom, Locale.US, use24Hour)}"
            it.cloudy -> "Cloudy tonight"
            else -> "Some clouds tonight"
        }
    }
    return SkyCopy(phaseLine, moon, meteors, tonight, clearTonight = sky != null && !sky.noDark && (sky.clear || sky.clearFrom != null))
}
