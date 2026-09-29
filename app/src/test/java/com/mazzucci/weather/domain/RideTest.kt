package com.mazzucci.weather.domain

import com.mazzucci.weather.TestData.fixture
import com.mazzucci.weather.data.OpenMeteoRideWeather
import com.mazzucci.weather.data.parseGpx
import com.mazzucci.weather.data.parseGpxTime
import com.mazzucci.weather.data.parseRideHours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

class RideTest {
    private val track = parseGpx(fixture("ride_out_and_back.gpx"))
    private val hours = parseRideHours(fixture("ride_weather_sf.json"))

    @Test fun `parses a GPX track with times, elevation and name`() {
        assertEquals("Ocean Beach loop", track.name)
        assertEquals(87, track.points.size)
        assertEquals(Instant.parse("2026-09-20T15:00:00Z"), track.start)
        assertEquals(Instant.parse("2026-09-20T16:30:00Z"), track.end)
        assertEquals(90L, track.duration!!.toMinutes())
        assertEquals(16.0, track.distanceKm, 0.2)
        assertEquals(10.0, track.points.first().elevationM!!, 0.001)
    }

    @Test fun `route points work when there's no track, and junk is refused`() {
        val route = parseGpx(
            """<gpx xmlns="http://www.topografix.com/GPX/1/1"><rte><name>Plan</name>
               <rtept lat="1.0" lon="2.0"/><rtept lat="1.1" lon="2.1"/></rte></gpx>""",
        )
        assertEquals("Plan", route.name)
        assertNull(route.start) // a planned route has no times
        assertThrows(IOException::class.java) { parseGpx("not xml") }
        assertThrows(IOException::class.java) { parseGpx("<gpx><trk><trkseg><trkpt lat=\"1\" lon=\"2\"/></trkseg></trk></gpx>") }
    }

    @Test fun `any DOCTYPE is refused, so no entity can be declared or expanded`() {
        val xxe = """<?xml version="1.0"?><!DOCTYPE gpx [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <gpx><trk><name>&x;</name><trkseg><trkpt lat="1" lon="2"/><trkpt lat="1.1" lon="2"/></trkseg></trk></gpx>"""
        assertEquals("That doesn't look like a GPX file", assertThrows(IOException::class.java) { parseGpx(xxe) }.message)
        val laughs = """<?xml version="1.0"?><!DOCTYPE lolz [<!ENTITY lol "lol"><!ENTITY lol2 "&lol;&lol;&lol;&lol;">]><gpx>&lol2;</gpx>"""
        assertThrows(IOException::class.java) { parseGpx(laughs) }
    }

    @Test fun `times with offsets, fractions or no zone all parse`() {
        assertEquals(Instant.parse("2026-09-20T15:00:00Z"), parseGpxTime("2026-09-20T17:00:00+02:00"))
        assertEquals(Instant.parse("2026-09-20T15:00:00.250Z"), parseGpxTime("2026-09-20T15:00:00.250Z"))
        assertEquals(Instant.parse("2026-09-20T15:00:00Z"), parseGpxTime("2026-09-20T15:00:00"))
        assertNull(parseGpxTime("yesterday"))
    }

    @Test fun `segments and long pauses are never joined by a straight line`() {
        val gpx = """<gpx><trk>
            <trkseg><trkpt lat="37.0" lon="-122.0"><time>2026-09-20T15:00:00Z</time></trkpt><trkpt lat="37.001" lon="-122.0"><time>2026-09-20T15:01:00Z</time></trkpt></trkseg>
            <trkseg><trkpt lat="38.0" lon="-122.0"><time>2026-09-20T16:00:00Z</time></trkpt><trkpt lat="38.001" lon="-122.0"><time>2026-09-20T16:01:00Z</time></trkpt>
              <trkpt lat="38.002" lon="-122.0"><time>2026-09-20T17:00:00Z</time></trkpt><trkpt lat="38.003" lon="-122.0"><time>2026-09-20T17:01:00Z</time></trkpt></trkseg>
            </trk></gpx>"""
        val t = parseGpx(gpx)
        assertEquals(2, t.segments.size)
        assertEquals(3, t.stretches().size) // the hour-long pause splits the second segment
        assertEquals(0.33, t.distanceKm, 0.02) // three 111 m hops, not 111 km across the gap
    }

    @Test fun `a dense one-second recording is still classified`() {
        // 5 m/s due west for 10 minutes, a point every second (5 m apart), into a westerly.
        val start = Instant.parse("2026-09-20T15:00:00Z")
        val dLon = 0.005 / (111.32 * kotlin.math.cos(Math.toRadians(37.77)))
        val points = (0..600).map { i -> TrackPoint(37.77, -122.42 - i * dLon, start.plusSeconds(i.toLong())) }
        val r = replay(Track("dense", listOf(points)), hours)
        assertEquals(1.0, r.headShare, 0.01)
        assertTrue(r.segments.size.toString(), r.segments.size in 70..110) // ~30 m steps, not 600 jittery ones
    }

    @Test fun `stops don't count as any wind side`() {
        val start = Instant.parse("2026-09-20T15:00:00Z")
        // 40 m in 10 minutes: standing around at a café.
        val points = listOf(TrackPoint(37.77, -122.42, start), TrackPoint(37.77036, -122.42, start.plusSeconds(600)))
        val r = replay(Track("café", listOf(points)), hours)
        assertTrue(r.segments.all { it.side == null })
    }

    @Test fun `rain counts through the hour the ride ends in`() {
        // 16:05–16:25: the rain for 16–17 is stamped 17:00, after the ride.
        val start = Instant.parse("2026-09-20T16:05:00Z")
        val dLon = 0.2 / (111.32 * kotlin.math.cos(Math.toRadians(37.77)))
        val points = (0..20).map { i -> TrackPoint(37.77, -122.42 - i * dLon, start.plusSeconds(i * 60L)) }
        val wet = hours.map { if (it.time == Instant.parse("2026-09-20T17:00:00Z")) it.copy(precipitationMm = 1.4) else it }
        assertEquals(1.4, replay(Track("short", listOf(points)), wet).rainMm, 0.001)
    }

    @Test fun `files over the cap are refused while reading`() {
        val big = fixture("ride_out_and_back.gpx")
        assertEquals("That file is too big to be a single ride", assertThrows(IOException::class.java) { parseGpx(big.byteInputStream(), maxBytes = 1000) }.message)
    }

    @Test fun `parses the ride's weather in UTC`() {
        assertEquals(24, hours.size)
        val h = hours[15]
        assertEquals(Instant.parse("2026-09-20T15:00:00Z"), h.time)
        assertEquals(13.6, h.tempC, 0.001)
        assertEquals(277.0, h.windFromDeg, 0.001)
    }

    @Test fun `head, cross and tail follow the angle to where the wind comes from`() {
        assertEquals(WindSide.HEAD, windSide(bearing = 270.0, windFrom = 270.0))
        assertEquals(WindSide.HEAD, windSide(bearing = 10.0, windFrom = 330.0)) // across north
        assertEquals(WindSide.CROSS, windSide(bearing = 0.0, windFrom = 90.0))
        assertEquals(WindSide.TAIL, windSide(bearing = 90.0, windFrom = 270.0))
        assertEquals(270.0, bearingDeg(TrackPoint(37.77, -122.40, null), TrackPoint(37.77, -122.50, null)), 0.1)
    }

    @Test fun `an out-and-back ride into a westerly splits head and tail`() {
        val r = replay(track, hours)
        assertEquals(0.5, r.headShare, 0.05)
        assertEquals(0.5, r.tailShare, 0.05)
        assertEquals(0.0, r.rainMm, 0.001)
        // 15:00–17:00 UTC: wind from 261–277°, up to 12.7 km/h (8 mph); 13.6–14.0 °C.
        assertEquals(
            "Headwind for 50% and tailwind for 50%, wind up to 8 mph: it evened out. 56° to 57°F. Dry.",
            describeReplay(r, TempUnit.F),
        )
        assertEquals(270.0, r.windFromDeg!!, 10.0) // a westerly, for the sketch's wind arrow
        assertEquals("W", compassPoint(r.windFromDeg!!))
    }

    @Test fun `compass points round to the nearest of eight, across north`() {
        assertEquals("N", compassPoint(350.0))
        assertEquals("N", compassPoint(0.0))
        assertEquals("NE", compassPoint(30.0))
        assertEquals("SW", compassPoint(225.0))
        assertNull(replay(track, emptyList()).windFromDeg)
    }

    @Test fun `a square loop in a westerly has headwind west, tailwind east, crosswind north and south`() {
        val loop = parseGpx(fixture("ride_loop.gpx"))
        assertEquals("Park loop", loop.name) // from <metadata> when the track has no name
        val r = replay(loop, hours)
        assertEquals(4.0 / 14, r.headShare, 0.08)
        assertEquals(4.0 / 14, r.tailShare, 0.08)
        assertEquals(6.0 / 14, r.crossShare, 0.08)
    }

    @Test fun `the summary follows the largest share`() {
        fun seg(side: WindSide?, km: Double) = RideSegment(TrackPoint(0.0, 0.0, null), TrackPoint(0.0, 0.0, null), km, side, 20.0)
        val base = replay(track, hours)
        val lopsided = base.copy(
            segments = listOf(seg(WindSide.HEAD, 5.5), seg(WindSide.CROSS, 3.5), seg(WindSide.TAIL, 1.0)),
            headShare = 0.55, crossShare = 0.35, tailShare = 0.10,
        )
        assertTrue(describeReplay(lopsided, TempUnit.C).startsWith("Headwind for 55% of the way"))
        val crossy = lopsided.copy(headShare = 0.2, crossShare = 0.7, tailShare = 0.1)
        assertTrue(describeReplay(crossy, TempUnit.C).startsWith("Mostly crosswind (70%)"))
        val noMovement = base.copy(segments = base.segments.map { it.copy(side = null) })
        assertTrue(describeReplay(noMovement, TempUnit.C).startsWith("Not enough movement"))
    }

    @Test fun `rain is in inches for Fahrenheit users`() {
        assertEquals("0.06 in", formatRain(1.4, TempUnit.F))
        assertEquals("1.4 mm", formatRain(1.4, TempUnit.C))
    }

    @Test fun `no weather data says so, calm says so`() {
        val none = replay(track, emptyList())
        assertEquals("No weather data for this ride.", describeReplay(none, TempUnit.C))
        val calm = replay(track, hours.map { it.copy(windKmh = 2.0) })
        assertTrue(describeReplay(calm, TempUnit.C).startsWith("Barely any wind."))
    }

    @Test fun `local hours convert to UTC with the response's offset`() {
        val local = fixture("ride_weather_sf.json").replace("\"utc_offset_seconds\":0", "\"utc_offset_seconds\":-25200")
        assertEquals(Instant.parse("2026-09-20T22:00:00Z"), parseRideHours(local)[15].time)
    }

    @Test fun `history URL asks in the place's time zone over the ride's dates`() {
        val url = OpenMeteoRideWeather.url(37.77, -122.47, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 20))
        assertTrue(url.startsWith("https://historical-forecast-api.open-meteo.com/v1/forecast?latitude=37.77&longitude=-122.47"))
        assertTrue("start_date=2026-09-20&end_date=2026-09-20" in url && "timezone=auto" in url && "wind_direction_10m" in url)
    }
}
