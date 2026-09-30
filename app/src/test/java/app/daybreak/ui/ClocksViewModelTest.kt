package app.daybreak.ui

import app.daybreak.TestData
import app.daybreak.data.ClocksRepository
import app.daybreak.data.InMemoryStore
import app.daybreak.data.WeatherApi
import app.daybreak.domain.Clock
import app.daybreak.domain.ClockFormat
import app.daybreak.domain.Forecast
import app.daybreak.domain.Place
import app.daybreak.narration.ModelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val api = object : WeatherApi {
        override suspend fun forecast(latitude: Double, longitude: Double): Forecast = TestData.forecast()
        override suspend fun searchPlaces(query: String): List<Place> {
            searched += query
            return if (query == "Japan") listOf(Place("geo:1861060", "Japan", country = "Japan", latitude = 35.7, longitude = 139.7, zoneId = "Asia/Tokyo")) else emptyList()
        }
    }
    private val store = InMemoryStore()
    private val bucharest = Clock("geo:683506", "Bucharest", "Bucharest, Romania", "Europe/Bucharest")

    private fun vm(installed: Boolean = true) = ClocksViewModel(
        ClocksRepository(store).apply { add(bucharest) },
        gemma = { _, _, _ -> reply },
        modelStatus = MutableStateFlow(if (installed) ModelStatus.Installed(1) else ModelStatus.NotInstalled),
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
                "At 12:00 PM on Monday your time, it's 10:00 PM in Romania.",
                ConverterRequest(12 * 60, 0, null),
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
        assertEquals("At 9:00 PM on Tuesday your time, it's 1:00 PM on Wednesday in Japan.", (vm.ask.value as AskUi.Answer).text)
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
        assertEquals("At 9:00 AM on Tuesday in Bucharest, it's 11:00 PM on Monday for you.", answer.text)
        assertEquals(ConverterRequest(9 * 60, 0, bucharest.id), answer.request)

        reply = """{"tool":"time_in","place":"Atlantis"}"""
        vm.ask("time in Atlantis")
        advanceUntilIdle()
        assertEquals(AskUi.Failed("time in Atlantis", "Couldn't find “Atlantis”. Try a city name."), vm.ask.value)

        reply = "I don't know."
        vm.ask("hello")
        advanceUntilIdle()
        assertEquals(AskUi.Failed("hello", "Gemma couldn't work that one out. Try the converter above."), vm.ask.value)
    }

    @Test fun `without Gemma there's nothing to ask`() = runTest(dispatcher) {
        val vm = vm(installed = false)
        vm.ask("time in Tokyo")
        advanceUntilIdle()
        assertEquals(AskUi.Failed("time in Tokyo", "Gemma isn't set up yet."), vm.ask.value)
    }
}
