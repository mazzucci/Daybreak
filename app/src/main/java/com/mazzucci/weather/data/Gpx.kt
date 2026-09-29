package com.mazzucci.weather.data

import com.mazzucci.weather.domain.Track
import com.mazzucci.weather.domain.TrackPoint
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Largest GPX file accepted: a day-long ride recorded every second, with extensions, is well under this. */
const val MAX_GPX_BYTES = 30L * 1024 * 1024

/**
 * Reads a GPX 1.1 file (Garmin Connect, Strava, Komoot…) as a stream, keeping only positions, times and
 * elevations, so memory stays small whatever the file size. Track segments stay separate (a GPS dropout or a
 * ferry isn't drawn as a straight line); route points are the fallback for planned routes.
 *
 * The file comes from outside the app, so any DOCTYPE is refused outright: no entity can be declared, let alone
 * expanded (no XXE, no billion laughs), whatever the platform parser would otherwise do.
 */
fun parseGpx(input: InputStream, maxBytes: Long = MAX_GPX_BYTES): Track {
    val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
    runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
    parser.setInput(CappedStream(input, maxBytes), null)

    val segments = mutableListOf<MutableList<TrackPoint>>()
    val route = mutableListOf<TrackPoint>()
    var trackName: String? = null
    var metaName: String? = null
    val path = ArrayDeque<String>()
    var lat = 0.0
    var lon = 0.0
    var inPoint = false
    var time: Instant? = null
    var ele: Double? = null
    var text = StringBuilder()
    var sawGpx = false
    try {
        while (true) {
            when (parser.nextToken()) {
                XmlPullParser.DOCDECL -> throw IOException("That doesn't look like a GPX file")
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    if (tag == "gpx") sawGpx = true
                    when (tag) {
                        "trkseg" -> segments += mutableListOf<TrackPoint>()
                        "trkpt", "rtept" -> {
                            val la = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            val lo = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            inPoint = la != null && lo != null
                            lat = la ?: 0.0
                            lon = lo ?: 0.0
                            time = null
                            ele = null
                        }
                    }
                    path.addLast(tag)
                    text = StringBuilder()
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val tag = path.removeLastOrNull()
                    val parent = path.lastOrNull()
                    val value = text.toString().trim()
                    when {
                        tag == "time" && (parent == "trkpt" || parent == "rtept") -> time = parseGpxTime(value)
                        tag == "ele" && (parent == "trkpt" || parent == "rtept") -> ele = value.toDoubleOrNull()
                        tag == "name" && (parent == "trk" || parent == "rte") && trackName == null -> trackName = value.ifBlank { null }
                        tag == "name" && parent == "metadata" -> metaName = value.ifBlank { null }
                        tag == "trkpt" && inPoint -> {
                            if (segments.isEmpty()) segments += mutableListOf<TrackPoint>()
                            segments.last() += TrackPoint(lat, lon, time, ele)
                        }
                        tag == "rtept" && inPoint -> route += TrackPoint(lat, lon, time, ele)
                    }
                    if (tag == "trkpt" || tag == "rtept") inPoint = false
                    text = StringBuilder()
                }
                XmlPullParser.END_DOCUMENT -> break
            }
        }
    } catch (e: XmlPullParserException) {
        throw IOException("That doesn't look like a GPX file")
    }
    if (!sawGpx) throw IOException("That doesn't look like a GPX file")
    val parts = segments.filter { it.isNotEmpty() }.ifEmpty { listOf(route) }.filter { it.isNotEmpty() }
    if (parts.sumOf { it.size } < 2) throw IOException("The GPX file has no track to replay")
    return Track(trackName ?: metaName, parts)
}

fun parseGpx(xml: String): Track = parseGpx(xml.byteInputStream())

/**
 * GPX times are ISO 8601, normally UTC ("Z") but sometimes with an offset. Parsed with the offset-aware formatter,
 * which Android's java.time accepts on every version (Instant.parse only takes offsets from Java 12). A time
 * without any zone is taken as UTC, as the GPX spec says it should be.
 */
fun parseGpxTime(value: String): Instant? =
    runCatching { OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }.getOrNull()

/** Fails as soon as more than [limit] bytes have been read, instead of loading an absurd file. */
private class CappedStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int = super.read().also { if (it >= 0) add(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) add(it.toLong()) }

    private fun add(n: Long) {
        count += n
        if (count > limit) throw IOException("That file is too big to be a single ride")
    }
}
