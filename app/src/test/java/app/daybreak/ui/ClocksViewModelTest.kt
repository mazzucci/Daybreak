package app.daybreak.ui

import app.daybreak.TestData
import app.daybreak.data.ClocksRepository
import app.daybreak.data.InMemoryStore
import app.daybreak.data.WeatherApi
import app.daybreak.domain.Clock
import app.daybreak.domain.ClockFormat
import app.daybreak.domain.Forecast
import app.daybreak.domain.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ClocksViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var reply = ""
    private val searched = mutableListOf<String>()
    private var failing = false
    private val api = object : WeatherApi {
        override suspend fun forecast(latitude: Double, longitude: Double): Forecast = TestData.forecast()
        override suspend fun searchPlaces(query: String): List<Place> {
            searched += query
            if (failing) throw java.io.IOException("offline")
            return if (query == "Japan") listOf(Place("geo:1861060", "Japan", country = "Japan", latitude = 35.7, longitude = 139.7, zoneId = "Asia/Tokyo")) else emptyList()
        }
    }
    private val store = InMemoryStore()
    private val bucharest = Clock("geo:683506", "Bucharest", "Bucharest, Romania", "Europe/Bucharest")

    private fun vm(installed: Boolean = true) = ClocksViewModel(
        ClocksRepository(store).apply { add(bucharest) },
        gemma = { _, _, _ -> reply },
        gemmaReady = { installed },
        places = api,
        here = { ZoneId.of("America/Los_Angeles") },
        // Monday 28 September 2026, 2:42 PM in Los Angeles.
        clock = { Instant.parse("2026-09-28T21:42:00Z") },
    )

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        ClockFormat.use24Hour = false
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `a country finds its clock, and the answer sets the converter`() = runTest(dispatcher) {
        reply = """{"tool":"convert_time","time":"12:00","day":"today","from":"me","to":"Romania"}"""
        val vm = vm()
        vm.ask("What time is it in Romania at noon my time?")
        advanceUntilIdle()
        assertEquals(
            AskUi.Answer(
                "What time is it in Romania at noon my time?",
                "At 12:00 PM on Monday in Los Angeles, it's 10:00 PM in Bucharest.",
                ConverterRequest(12 * 60, 0, null, 1),
            ),
            vm.ask.value,
        )
        assertEquals(emptyList<String>(), searched) // found among the clocks, no search
    }

    @Test fun `a place that isn't a clock is searched, and a later day is named`() = runTest(dispatcher) {
        reply = """{"tool":"convert_time","time":"21:00","day":"tomorrow","from":"me","to":"Japan"}"""
        val vm = vm()
        vm.ask("9pm tomorrow here is what in Japan?")
        advanceUntilIdle()
        val japan = vm.ask.value as AskUi.Answer
        assertEquals("At 9:00 PM on Tuesday in Los Angeles, it's 1:00 PM on Wednesday in Japan.", japan.text)
        assertEquals(ConverterRequest(21 * 60, 1, null, 1), japan.request)
        assertEquals(listOf("Japan"), searched)
    }

    @Test fun `time in a clock, from a clock, and the failures`() = runTest(dispatcher) {
        val vm = vm()
        reply = """{"tool":"time_in","place":"Bucharest"}"""
        vm.ask("time in bucharest")
        advanceUntilIdle()
        assertEquals("It's 12:42 AM on Tuesday in Bucharest.", (vm.ask.value as AskUi.Answer).text)

        reply = """{"tool":"convert_time","time":"09:00","day":"today","from":"Bucharest","to":"me"}"""
        vm.ask("9am in Bucharest for me?")
        advanceUntilIdle()
        val answer = vm.ask.value as AskUi.Answer
        // Today in Bucharest is already Tuesday.
        assertEquals("At 9:00 AM on Tuesday in Bucharest, it's 11:00 PM on Monday in Los Angeles.", answer.text)
        assertEquals(ConverterRequest(9 * 60, 0, bucharest.id, 2), answer.request)

        reply = """{"tool":"time_in","place":"Atlantis"}"""
        vm.ask("time in Atlantis")
        advanceUntilIdle()
        assertEquals(AskUi.Failed("time in Atlantis", "Couldn't find “Atlantis”. Try a city name."), vm.ask.value)

        reply = "I don't know."
        vm.ask("hello")
        advanceUntilIdle()
        assertEquals(AskUi.Failed("hello", "Gemma couldn't work that one out. Try it another way, like “What time is it in Tokyo?”"), vm.ask.value)
    }

    @Test fun `without Gemma there's nothing to ask`() = runTest(dispatcher) {
        val vm = vm(installed = false)
        vm.ask("time in Tokyo")
        advanceUntilIdle()
        assertEquals(AskUi.Failed("time in Tokyo", "Gemma isn't set up yet."), vm.ask.value)
    }

    @Test fun `"City, Country" finds the city, a place that isn't a clock still sets the converter, and offline says so`() = runTest(dispatcher) {
        val vm = vm()
        reply = """{"tool":"time_in","place":"Bucharest, Romania"}"""
        vm.ask("time in Bucharest, Romania")
        advanceUntilIdle()
        assertEquals("It's 12:42 AM on Tuesday in Bucharest.", (vm.ask.value as AskUi.Answer).text)

        // 9 AM in Tokyo (not a clock) is 5 PM today in Los Angeles: the converter shows it from the phone.
        reply = """{"tool":"convert_time","time":"09:00","day":"tomorrow","from":"Japan","to":"me"}"""
        vm.ask("9am tomorrow in Japan for me?")
        advanceUntilIdle()
        val japan = vm.ask.value as AskUi.Answer
        assertEquals("At 9:00 AM on Wednesday in Japan, it's 5:00 PM on Tuesday in Los Angeles.", japan.text)
        assertEquals(ConverterRequest(17 * 60, 1, null, 2), japan.request)

        failing = true
        reply = """{"tool":"time_in","place":"Lima"}"""
        vm.ask("time in Lima")
        advanceUntilIdle()
        assertEquals(
            AskUi.Failed("time in Lima", "Couldn't look up “Lima” without a connection. Add it as a clock to ask offline."),
            vm.ask.value,
        )
    }

    @Test fun `a slow Gemma times out, and a question can be cancelled`() = runTest(dispatcher) {
        val slow = ClocksViewModel(
            ClocksRepository(store), gemma = { _, _, _ -> kotlinx.coroutines.delay(60_000); "" },
            gemmaReady = { true }, timeoutMs = 45_000,
        )
        slow.ask("time in Tokyo")
        advanceUntilIdle()
        assertEquals(AskUi.Failed("time in Tokyo", "Gemma is busy with the weather. Try again in a moment."), slow.ask.value)
        slow.ask("time in Tokyo")
        slow.cancelAsk()
        advanceUntilIdle()
        assertEquals(AskUi.Idle, slow.ask.value)
    }
}
