package com.mazzucci.weather.ui

import com.mazzucci.weather.TestData.fixture
import com.mazzucci.weather.data.parseRideHours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import com.mazzucci.weather.data.RideWeather

@OptIn(ExperimentalCoroutinesApi::class)
class RideViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val requests = mutableListOf<String>()
    private var failWeather = false

    private fun vm(files: Map<String, String>, now: Instant = Instant.parse("2026-09-29T00:00:00Z")) = RideViewModel(
        weather = { lat, lon, from, to ->
            requests += "$lat,$lon,$from,$to"
            if (failWeather) throw IOException("Weather history returned HTTP 500")
            RideWeather(parseRideHours(fixture("ride_weather_sf.json")), 0)
        },
        openStream = { uri -> files[uri]?.byteInputStream() ?: throw IOException("Couldn't open that file") },
        now = { now },
    )

    /** open() hops to IO and Default threads; let them finish. */
    private fun kotlinx.coroutines.test.TestScope.settle(vm: RideViewModel) {
        advanceUntilIdle()
        repeat(300) { if (vm.state.value == RideUi.Loading) { Thread.sleep(10); advanceUntilIdle() } }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `a GPX ride loads with its weather, asked for the ride's centre and dates`() = runTest(dispatcher) {
        val vm = vm(mapOf("content://ride" to fixture("ride_out_and_back.gpx")))
        vm.open("content://ride")
        assertEquals(RideUi.Loading, vm.state.value)
        advanceUntilIdle()
        // The IO/Default hops finish on real threads; wait for them.
        settle(vm)
        val loaded = vm.state.value as RideUi.Loaded
        assertEquals("Ocean Beach loop", loaded.replay.track.name)
        val (lat, lon, from, to) = requests.single().split(",")
        assertEquals(37.7705, lat.toDouble(), 0.001)
        assertEquals(-122.4655, lon.toDouble(), 0.01)
        // A day either side of the UTC dates, since the place's local dates aren't known yet.
        assertEquals(LocalDate.of(2026, 9, 19).toString(), from)
        assertEquals(LocalDate.of(2026, 9, 21).toString(), to)
    }

    @Test fun `a planned route without times says why it can't be replayed`() = runTest(dispatcher) {
        val route = """<gpx><rte><rtept lat="1" lon="2"/><rtept lat="1.1" lon="2"/></rte></gpx>"""
        val vm = vm(mapOf("content://plan" to route))
        vm.open("content://plan")
        settle(vm)
        val failed = vm.state.value as RideUi.Failed
        assertTrue(failed.message.contains("no times"))
        assertTrue(requests.isEmpty())
    }

    @Test fun `unreadable files and weather errors become messages`() = runTest(dispatcher) {
        val vm = vm(mapOf("content://junk" to "hello", "content://ride" to fixture("ride_out_and_back.gpx")))
        vm.open("content://missing")
        settle(vm)
        assertEquals(RideUi.Failed("Couldn't open that file"), vm.state.value)
        vm.open("content://junk")
        settle(vm)
        assertEquals(RideUi.Failed("That doesn't look like a GPX file"), vm.state.value)
        failWeather = true
        vm.open("content://ride")
        settle(vm)
        assertEquals(RideUi.Failed("Weather history returned HTTP 500"), vm.state.value)
        vm.clear()
        assertEquals(RideUi.Idle, vm.state.value)
    }

    @Test fun `rides in the future or before 2022 get a clear message`() = runTest(dispatcher) {
        val early = vm(mapOf("r" to fixture("ride_out_and_back.gpx")), now = Instant.parse("2026-09-01T00:00:00Z"))
        early.open("r")
        settle(early)
        assertEquals(RideUi.Failed("That ride is in the future, so there's no weather for it yet"), early.state.value)
        val old = fixture("ride_out_and_back.gpx").replace("2026-09-20", "2019-09-20")
        val vm = vm(mapOf("r" to old))
        vm.open("r")
        settle(vm)
        assertEquals(RideUi.Failed("Weather history for rides only goes back to 2022"), vm.state.value)
        assertTrue(requests.isEmpty())
    }
}
