package com.mazzucci.weather.ui

import com.mazzucci.weather.TestData
import com.mazzucci.weather.TestData.fixture
import com.mazzucci.weather.data.ExerciseSource
import com.mazzucci.weather.data.RideWeather
import com.mazzucci.weather.data.parseRideHours
import com.mazzucci.weather.domain.Exercise
import com.mazzucci.weather.domain.ExerciseKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityLogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = Instant.parse("2026-09-29T00:00:00Z")

    private class FakeSource : ExerciseSource {
        var available = ExerciseSource.Availability.AVAILABLE
        var granted = false
        var failWith: Exception? = null
        val sessions = mutableListOf<Exercise>()
        var asked: Pair<Instant, Instant>? = null
        override fun availability() = available
        override val permissions = setOf("android.permission.health.READ_EXERCISE")
        override suspend fun hasPermission() = granted
        override suspend fun sessions(from: Instant, to: Instant): List<Exercise> {
            asked = from to to
            failWith?.let { throw it }
            return sessions
        }
    }

    private val source = FakeSource()
    private var weatherCalls = 0
    private var weatherFails = false
    private fun vm() = ActivityLogViewModel(
        source,
        weather = { _, _, _, _, _ ->
            weatherCalls++
            if (weatherFails) throw IOException("offline")
            RideWeather(parseRideHours(fixture("ride_weather_sf.json")), 0)
        },
        now = { now },
    )

    private fun ride(start: String, indoor: Boolean = false) =
        Instant.parse(start).let { Exercise(start, ExerciseKind.RIDE, "Morning ride", it, it.plusSeconds(3600), "garmin", indoor) }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `without Health Connect it says whether it can be installed`() = runTest(dispatcher) {
        val vm = vm()
        source.available = ExerciseSource.Availability.NOT_INSTALLED
        vm.open(TestData.sanFrancisco); advanceUntilIdle()
        assertEquals(ActivityLogUi.Unavailable(installable = true), vm.state.value)
        source.available = ExerciseSource.Availability.NOT_SUPPORTED
        vm.open(TestData.sanFrancisco); advanceUntilIdle()
        assertEquals(ActivityLogUi.Unavailable(installable = false), vm.state.value)
    }

    @Test fun `asks for permission, then loads the last 30 days with weather`() = runTest(dispatcher) {
        source.sessions += ride("2026-09-20T15:00:00Z")
        val vm = vm()
        vm.open(TestData.sanFrancisco); advanceUntilIdle()
        assertEquals(ActivityLogUi.NeedsPermission(), vm.state.value)
        vm.onPermissionResult(emptySet()); advanceUntilIdle()
        assertEquals(ActivityLogUi.NeedsPermission(denied = true), vm.state.value)
        vm.onPermissionResult(source.permissions); advanceUntilIdle()
        val loaded = vm.state.value as ActivityLogUi.Loaded
        assertEquals("San Francisco", loaded.placeName)
        assertEquals(13.6, loaded.items.single().minTempC!!, 0.001)
        assertEquals(now.minusSeconds(30L * 24 * 3600) to now, source.asked)
        assertEquals(1, weatherCalls)
    }

    @Test fun `weather failures still show the workouts, and no place or only indoor means no weather request`() = runTest(dispatcher) {
        source.granted = true
        source.sessions += ride("2026-09-20T15:00:00Z")
        weatherFails = true
        val vm = vm()
        vm.open(TestData.sanFrancisco); advanceUntilIdle()
        assertNull((vm.state.value as ActivityLogUi.Loaded).items.single().minTempC)
        weatherFails = false
        vm.open(null); advanceUntilIdle()
        assertEquals(1, weatherCalls) // only the failed one
        source.sessions.clear(); source.sessions += ride("2026-09-20T15:00:00Z", indoor = true)
        vm.open(TestData.sanFrancisco); advanceUntilIdle()
        assertEquals(1, weatherCalls)
    }

    @Test fun `a revoked permission goes back to asking, other errors show a message`() = runTest(dispatcher) {
        source.granted = true
        source.failWith = SecurityException("revoked")
        val vm = vm()
        vm.open(TestData.sanFrancisco); advanceUntilIdle()
        assertEquals(ActivityLogUi.NeedsPermission(), vm.state.value)
        source.failWith = IllegalStateException("internal error 42")
        vm.open(TestData.sanFrancisco); advanceUntilIdle()
        assertEquals(ActivityLogUi.Failed(ActivityLogViewModel.FRIENDLY_ERROR), vm.state.value)
    }

    @Test fun `Health Connect failing while checking the permission is a message, not a crash`() = runTest(dispatcher) {
        val vm = ActivityLogViewModel(
            object : ExerciseSource by source {
                override suspend fun hasPermission(): Boolean = throw IllegalStateException("service unavailable")
            },
            weather = { _, _, _, _, _ -> RideWeather(emptyList(), 0) },
            now = { now },
        )
        vm.open(TestData.sanFrancisco); advanceUntilIdle()
        assertEquals(ActivityLogUi.Failed(ActivityLogViewModel.FRIENDLY_ERROR), vm.state.value)
    }

    @Test fun `the log asks for UTC hours, and a failed lookup is flagged`() = runTest(dispatcher) {
        source.granted = true
        source.sessions += ride("2026-09-20T15:00:00Z")
        var localTime: Boolean? = null
        val vm = ActivityLogViewModel(source, weather = { _, _, _, _, local -> localTime = local; throw IOException("offline") }, now = { now })
        vm.open(TestData.sanFrancisco); advanceUntilIdle()
        assertEquals(false, localTime)
        assertTrue((vm.state.value as ActivityLogUi.Loaded).weatherMissing)
    }

    @Test fun `newest workouts come first`() = runTest(dispatcher) {
        source.granted = true
        source.sessions += listOf(ride("2026-09-10T08:00:00Z"), ride("2026-09-20T15:00:00Z"))
        val vm = vm()
        vm.open(TestData.sanFrancisco); advanceUntilIdle()
        val items = (vm.state.value as ActivityLogUi.Loaded).items
        assertTrue(items.first().exercise.start.isAfter(items.last().exercise.start))
    }
}
