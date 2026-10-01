package app.daybreak.ui

import app.daybreak.data.InMemoryStore
import app.daybreak.data.OnThisDayApi
import app.daybreak.data.OnThisDayFeed
import app.daybreak.data.OnThisDayRepository
import app.daybreak.domain.OnThisDayEvent
import app.daybreak.domain.WikiPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class OnThisDayViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var offline = false
    private val fetches = mutableListOf<String>()
    /** Per day ("10/1"), a gate the fetch waits on, to hold a load in flight. */
    private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val api = object : OnThisDayApi {
        override suspend fun events(month: Int, day: Int, feed: OnThisDayFeed): List<OnThisDayEvent> {
            val key = "$month/$day"
            fetches += key
            gates[key]?.await()
            if (offline) throw IOException("offline")
            return (1..3).map { OnThisDayEvent(1900 + 10 * it, "A bridge opens on $key, number $it.", listOf(WikiPage("Bridge $it", "", "https://w/$it"))) }
        }
    }
    private var today = LocalDate.of(2026, 10, 1)
    private val store = InMemoryStore()
    private fun viewModel() = OnThisDayViewModel(OnThisDayRepository(api, store, background = dispatcher), today = { today })

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `offline, the card stays hidden`() = runTest(dispatcher) {
        offline = true
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()
        assertNull(vm.day.value)
    }

    @Test fun `loads once a day, and Another goes round the picks, remembered for the day`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load()
        vm.load() // already on its way
        advanceUntilIdle()
        vm.load() // already in hand
        advanceUntilIdle()
        assertEquals(1, fetches.size)
        val first = vm.day.value!!.current
        vm.another()
        vm.another()
        vm.another()
        assertEquals(first, vm.day.value!!.current) // round to the first again
        vm.another()
        advanceUntilIdle()
        // A relaunch the same day shows the same one, without a fetch.
        val again = viewModel().apply { load() }
        advanceUntilIdle()
        assertEquals(vm.day.value!!.current, again.day.value!!.current)
        assertEquals(1, fetches.size)
        // The next day starts afresh.
        today = today.plusDays(1)
        again.load()
        advanceUntilIdle()
        assertEquals(2, fetches.size)
        assertEquals(0, again.day.value!!.index)
    }

    @Test fun `a load still running at midnight gives way to the new day's`() = runTest(dispatcher) {
        gates["10/1"] = CompletableDeferred()
        val vm = viewModel()
        vm.load()
        advanceUntilIdle() // yesterday's fetch is stuck
        today = today.plusDays(1)
        vm.load()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 10, 2), vm.day.value?.date)
        // Yesterday's finishing late changes nothing: it was cancelled.
        gates["10/1"]!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 10, 2), vm.day.value?.date)
        assertEquals(listOf("10/1", "10/2"), fetches)
    }

    @Test fun `switched off, the card goes at once, and switched back on it comes back`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()
        vm.clear()
        assertNull(vm.day.value)
        vm.load()
        advanceUntilIdle()
        assertNotNull(vm.day.value)
        // Switched off mid-load, then on again: the load starts again rather than being skipped.
        val other = viewModel()
        gates["10/1"] = CompletableDeferred()
        store.remove("on_this_day_v2")
        other.load()
        advanceUntilIdle()
        other.clear()
        gates.remove("10/1")!!.complete(Unit)
        other.load()
        advanceUntilIdle()
        assertNotNull(other.day.value)
    }

    @Test fun `pull-to-refresh after a failure tries again at once`() = runTest(dispatcher) {
        offline = true
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()
        offline = false
        vm.load() // a resume within the few minutes' wait: nothing asked
        advanceUntilIdle()
        assertNull(vm.day.value)
        assertEquals(1, fetches.size)
        vm.load(force = true)
        advanceUntilIdle()
        assertNotNull(vm.day.value)
    }
}
