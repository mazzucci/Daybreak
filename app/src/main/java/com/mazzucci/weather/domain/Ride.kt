package com.mazzucci.weather.domain

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** One recorded position. [time] is null in routes planned rather than ridden. */
data class TrackPoint(val latitude: Double, val longitude: Double, val time: Instant?, val elevationM: Double? = null)

/**
 * A recorded ride or run, as read from a GPX file: one or more segments (a pause or GPS dropout starts a new
 * one), never joined by a straight line.
 */
data class Track(val name: String?, val segments: List<List<TrackPoint>>) {
    val points: List<TrackPoint> get() = segments.flatten()
    val start: Instant? get() = points.firstNotNullOfOrNull { it.time }
    val end: Instant? get() = points.lastOrNull { it.time != null }?.time
    val duration: Duration? get() = start?.let { s -> end?.let { e -> Duration.between(s, e) } }
    val distanceKm: Double get() = stretches().sumOf { part -> part.zipWithNext { a, b -> distanceKm(a, b) }.sum() }

    /** The segments, further split wherever the recording has a gap of more than five minutes. */
    fun stretches(): List<List<TrackPoint>> = segments.flatMap { seg ->
        val parts = mutableListOf(mutableListOf<TrackPoint>())
        seg.forEach { p ->
            val last = parts.last().lastOrNull()
            val gap = last?.time?.let { t -> p.time?.let { Duration.between(t, it) } }
            if (gap != null && gap > MAX_GAP) parts += mutableListOf<TrackPoint>()
            parts.last() += p
        }
        parts.filter { it.size >= 2 }
    }

    /** The middle of the route's bounding box: where to ask for the weather (rides are local enough). */
    val centre: Pair<Double, Double>
        get() = ((points.minOf { it.latitude } + points.maxOf { it.latitude }) / 2) to
            ((points.minOf { it.longitude } + points.maxOf { it.longitude }) / 2)
}

private val MAX_GAP: Duration = Duration.ofMinutes(5)

/** Hourly weather during the ride, UTC. Wind direction is where the wind blows from, in degrees. */
data class RideHour(
    val time: Instant,
    val tempC: Double,
    val precipitationMm: Double,
    val windKmh: Double,
    val windFromDeg: Double,
    val code: Int,
)

enum class WindSide { HEAD, CROSS, TAIL }

/** One stretch of the route with the wind it met. */
data class RideSegment(val from: TrackPoint, val to: TrackPoint, val km: Double, val side: WindSide?, val windKmh: Double?)

/** What the weather did to a ride. Shares are fractions of the distance with wind data, 0–1. */
data class RideReplay(
    /** The ride place's offset from UTC (from the weather service), for showing its local times. */
    val utcOffsetSeconds: Int = 0,
    val track: Track,
    val segments: List<RideSegment>,
    val headShare: Double,
    val crossShare: Double,
    val tailShare: Double,
    val minTempC: Double?,
    val maxTempC: Double?,
    val rainMm: Double,
    val maxWindKmh: Double?,
    /** Where the wind mostly came from during the ride, degrees clockwise from north; null with no data or in calm. */
    val windFromDeg: Double? = null,
)

/**
 * Pairs each stretch of [track] with the weather hour it was ridden in, and classifies the wind as head, cross
 * or tail by the angle between the direction of travel and where the wind came from (within 60° is a
 * headwind, beyond 120° a tailwind). Calm hours (under 5 km/h) don't count as any side.
 *
 * Directions come from steps of at least [STEP_KM] (30 m), not from consecutive points: a watch recording every
 * second moves a few metres between points, where GPS jitter would decide the bearing. Steps slower than walking
 * pace are stops, and don't count either.
 */
fun replay(track: Track, hours: List<RideHour>, utcOffsetSeconds: Int = 0): RideReplay {
    fun hourAt(t: Instant?): RideHour? = t?.let { time -> hours.minByOrNull { abs(Duration.between(it.time, time).seconds) } }
        ?.takeIf { abs(Duration.between(it.time, t).toMinutes()) <= 90 }
    val segments = track.stretches().flatMap { part ->
        val steps = mutableListOf<RideSegment>()
        var anchor = part.first()
        for (p in part.drop(1)) {
            val km = distanceKm(anchor, p)
            if (km < STEP_KM && p !== part.last()) continue
            val seconds = anchor.time?.let { a -> p.time?.let { Duration.between(a, it).seconds } }
            val stopped = seconds != null && seconds > 0 && km * 1000 / seconds < MIN_SPEED_MS
            val hour = hourAt(p.time ?: anchor.time)
            val side = if (hour == null || hour.windKmh < CALM_KMH || stopped || km < STEP_KM / 2) null
            else windSide(bearingDeg(anchor, p), hour.windFromDeg)
            steps += RideSegment(anchor, p, km, side, hour?.windKmh)
            anchor = p
        }
        steps
    }
    val windy = segments.filter { it.side != null }
    val windyKm = windy.sumOf { it.km }
    fun share(side: WindSide) = if (windyKm == 0.0) 0.0 else windy.filter { it.side == side }.sumOf { it.km } / windyKm
    val start = track.start
    val end = track.end
    // The hourly values nearest the ride: within half an hour of its start and end.
    val during = hours.filter { h ->
        start != null && end != null && !h.time.isBefore(start.minusSeconds(1800)) && !h.time.isAfter(end.plusSeconds(1800))
    }
    // Precipitation is stamped at the end of its hour, so the hours that overlap the ride run to end + 1 h.
    val rainHours = hours.filter { h ->
        start != null && end != null && h.time.isAfter(start) && h.time.isBefore(end.plusSeconds(3600))
    }
    return RideReplay(
        utcOffsetSeconds = utcOffsetSeconds,
        track = track,
        segments = segments,
        headShare = share(WindSide.HEAD),
        crossShare = share(WindSide.CROSS),
        tailShare = share(WindSide.TAIL),
        minTempC = during.minOfOrNull { it.tempC },
        maxTempC = during.maxOfOrNull { it.tempC },
        rainMm = rainHours.sumOf { it.precipitationMm },
        maxWindKmh = during.maxOfOrNull { it.windKmh },
        windFromDeg = prevailingWindFrom(during),
    )
}

/** The speed-weighted mean of where the wind came from, so a gusty hour counts for more than a lull. */
private fun prevailingWindFrom(hours: List<RideHour>): Double? {
    val windy = hours.filter { it.windKmh >= CALM_KMH }
    if (windy.isEmpty()) return null
    val x = windy.sumOf { sin(Math.toRadians(it.windFromDeg)) * it.windKmh }
    val y = windy.sumOf { cos(Math.toRadians(it.windFromDeg)) * it.windKmh }
    return (Math.toDegrees(atan2(x, y)) + 360) % 360
}

/** The nearest of the eight compass points for [deg] clockwise from north: "W", "NE". */
fun compassPoint(deg: Double): String {
    val points = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return points[((deg % 360 + 360) % 360 / 45).roundToInt() % 8]
}

/** Head, cross or tail for travel on [bearing] with wind from [windFrom] (both degrees clockwise from north). */
fun windSide(bearing: Double, windFrom: Double): WindSide {
    val diff = abs(((bearing - windFrom) % 360 + 540) % 360 - 180) // 0 = riding straight into it
    return when {
        diff <= 60 -> WindSide.HEAD
        diff >= 120 -> WindSide.TAIL
        else -> WindSide.CROSS
    }
}

/** A plain-language line about the ride's weather. Every number comes from the replay. */
fun describeReplay(r: RideReplay, unit: TempUnit): String {
    val parts = mutableListOf<String>()
    val head = (r.headShare * 100).roundToInt()
    val tail = (r.tailShare * 100).roundToInt()
    val wind = r.maxWindKmh
    val cross = (r.crossShare * 100).roundToInt()
    val windy = r.segments.any { it.side != null }
    when {
        wind == null -> parts += "No weather data for this ride."
        wind < CALM_KMH -> parts += "Barely any wind."
        !windy -> parts += "Not enough movement to tell the wind's direction."
        // The largest share leads, so the sentence can't contradict the legend.
        head >= 25 && tail >= 25 && abs(head - tail) <= 15 ->
            parts += "Headwind for $head% and tailwind for $tail%, wind up to ${formatWind(wind, unit)}: it evened out."
        head >= tail && head >= cross -> parts += "Headwind for $head% of the way, wind up to ${formatWind(wind, unit)}." +
            if (head >= 60) " Tough going." else ""
        tail >= head && tail >= cross -> parts += "Tailwind for $tail% of the way: the wind was on your side."
        else -> parts += "Mostly crosswind ($cross%), wind up to ${formatWind(wind, unit)}."
    }
    val lo = r.minTempC
    val hi = r.maxTempC
    if (lo != null && hi != null) {
        parts += if (degrees(lo, unit) == degrees(hi, unit)) "${formatTemp(lo, unit)} throughout." else "${formatDegrees(lo, unit)} to ${formatTemp(hi, unit)}."
    }
    if (r.rainMm >= 0.2) parts += "About ${formatRain(r.rainMm, unit)} of rain fell."
    else if (wind != null) parts += "Dry."
    return parts.joinToString(" ")
}

private const val CALM_KMH = 5.0

/** Shortest step whose direction is trusted, km. */
private const val STEP_KM = 0.03

/** Slower than this (m/s) between steps is a stop, not riding or running. */
private const val MIN_SPEED_MS = 0.7

/** "1.4 mm", or "0.06 in" for °F users. */
fun formatRain(mm: Double, unit: TempUnit): String =
    if (unit == TempUnit.F) String.format(java.util.Locale.US, "%.2f in", mm / 25.4)
    else String.format(java.util.Locale.US, "%.1f mm", mm)

/** Great-circle distance, km. */
fun distanceKm(a: TrackPoint, b: TrackPoint): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

/** Initial bearing from [a] to [b], degrees clockwise from north. */
fun bearingDeg(a: TrackPoint, b: TrackPoint): Double {
    val la = Math.toRadians(a.latitude)
    val lb = Math.toRadians(b.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val y = sin(dLon) * cos(lb)
    val x = cos(la) * sin(lb) - sin(la) * cos(lb) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}
