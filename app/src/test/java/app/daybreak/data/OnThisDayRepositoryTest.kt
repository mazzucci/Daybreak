package app.daybreak.data

import app.daybreak.domain.OnThisDayEvent
import app.daybreak.domain.WikiImage
import app.daybreak.domain.WikiPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class OnThisDayRepositoryTest {
    private class FakeFeed(
        var selected: List<OnThisDayEvent> = cheerful(6, from = 1900),
        var events: List<OnThisDayEvent> = cheerful(6, from = 1800),
    ) : OnThisDayApi {
        val calls = mutableListOf<String>()
        var offline = false
        var eventsOffline = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun events(month: Int, day: Int, feed: OnThisDayFeed): List<OnThisDayEvent> {
            calls += "${feed.path} $month/$day"
            gate?.await()
            if (offline || (feed == OnThisDayFeed.EVENTS && eventsOffline)) throw IOException("offline")
            return if (feed == OnThisDayFeed.SELECTED) selected else events
        }
    }

    private val today = LocalDate.of(2026, 10, 1)

    private fun TestScope.repo(api: OnThisDayApi, store: KeyValueStore = InMemoryStore(), now: () -> Long = { 0L }) =
        OnThisDayRepository(api, store, now, background = StandardTestDispatcher(testScheduler))

    @Test fun `fetches once a day, then serves the day from the store`() = runTest {
        val api = FakeFeed()
        val store = InMemoryStore()
        val first = repo(api, store).today(today)
        assertEquals(5, first?.picks?.size)
        assertEquals(listOf("selected 10/1"), api.calls)
        assertEquals(first, repo(api, store).today(today)) // a new instance (a relaunch): from the store
        assertEquals(1, api.calls.size)
    }

    @Test fun `asked twice at once, it fetches once`() = runTest {
        val api = FakeFeed().apply { gate = CompletableDeferred() }
        val repo = repo(api)
        val a = async { repo.today(today) }
        val b = async { repo.today(today) }
        advanceUntilIdle()
        api.gate!!.complete(Unit)
        assertEquals(a.await(), b.await())
        assertNotNull(a.await())
        assertEquals(1, api.calls.size)
    }

    @Test fun `a new day fetches again and replaces yesterday's`() = runTest {
        val api = FakeFeed()
        val repo = repo(api)
        repo.today(today)
        val tomorrow = repo.today(today.plusDays(1))
        assertEquals(today.plusDays(1), tomorrow?.date)
        assertEquals(listOf("selected 10/1", "selected 10/2"), api.calls)
    }

    @Test fun `offline gives nothing to show, and waits a few minutes before trying again`() = runTest {
        val api = FakeFeed().apply { offline = true }
        var clock = 0L
        val repo = repo(api, now = { clock })
        assertNull(repo.today(today))
        api.offline = false
        assertNull(repo.today(today)) // too soon
        assertEquals(1, api.calls.size)
        clock += 11 * 60_000
        assertNotNull(repo.today(today))
    }

    @Test fun `forced (pull-to-refresh), it doesn't wait`() = runTest {
        val api = FakeFeed().apply { offline = true }
        val repo = repo(api)
        assertNull(repo.today(today))
        api.offline = false
        assertNull(repo.today(today))
        assertNotNull(repo.today(today, force = true))
        assertEquals(2, api.calls.size)
    }

    @Test fun `a failure one day doesn't hold up the next`() = runTest {
        val api = FakeFeed().apply { offline = true }
        val repo = repo(api)
        assertNull(repo.today(today))
        api.offline = false
        assertNotNull(repo.today(today.plusDays(1)))
    }

    @Test fun `too few cheerful items in the curated list are made up from all the events`() = runTest {
        val api = FakeFeed(selected = cheerful(2, from = 1900) + grim(5))
        val day = repo(api).today(today)!!
        assertEquals(listOf("selected 10/1", "events 10/1"), api.calls)
        assertEquals(5, day.picks.size)
        assertTrue(day.complete)
        // The curated ones first.
        assertTrue(day.picks.take(2).all { it.year >= 1900 })
    }

    @Test fun `when the full list can't be had, the curated few show but aren't kept`() = runTest {
        val api = FakeFeed(selected = cheerful(2, from = 1900)).apply { eventsOffline = true }
        val store = InMemoryStore()
        var clock = 0L
        val repo = repo(api, store, now = { clock })
        val few = repo.today(today)!!
        assertEquals(2, few.picks.size)
        assertFalse(few.complete)
        assertNull(store.getString("on_this_day_v2"))
        // Within the wait, the same few without asking again.
        assertEquals(few, repo.today(today))
        assertEquals(2, api.calls.size)
        // Later (a resume), asked again, and with the full list back the day is made up and kept.
        api.eventsOffline = false
        clock += 11 * 60_000
        val whole = repo.today(today)!!
        assertEquals(5, whole.picks.size)
        assertTrue(whole.complete)
        assertNotNull(store.getString("on_this_day_v2"))
    }

    @Test fun `a day with nothing cheerful is an empty day, not a failure`() = runTest {
        val api = FakeFeed(selected = grim(3), events = grim(3))
        val day = repo(api).today(today)
        assertNotNull(day)
        assertTrue(day!!.picks.isEmpty())
        assertNull(day.current)
    }

    @Test fun `the pick showing is kept for the day, with its picture`() = runTest {
        val store = InMemoryStore()
        val repo = repo(FakeFeed(), store)
        val day = repo.today(today)!!
        repo.setIndex(today, 3)
        val again = repo(FakeFeed(), store).today(today)!!
        assertEquals(3, again.index)
        assertEquals(day.picks[3], again.current)
        assertEquals(day.picks, again.picks)
        assertNotNull(again.current?.picture)
    }

    @Test fun `setting the pick for another day does nothing`() = runTest {
        val store = InMemoryStore()
        val repo = repo(FakeFeed(), store)
        repo.today(today)
        val before = store.getString("on_this_day_v2")
        repo.setIndex(today.plusDays(1), 2)
        repo.setIndex(today.minusDays(1), 2)
        assertSame(before, store.getString("on_this_day_v2"))
    }

    @Test fun `an unreadable store is fetched again, and the first version's entry is dropped`() = runTest {
        val api = FakeFeed()
        val store = InMemoryStore(mapOf("on_this_day_v2" to "{not json", "on_this_day" to "{}"))
        assertNotNull(repo(api, store).today(today))
        assertEquals(1, api.calls.size)
        assertNull(store.getString("on_this_day"))
    }

    private companion object {
        fun cheerful(n: Int, from: Int) = (0 until n).map { i ->
            val name = "Museum_$i.jpg"
            OnThisDayEvent(
                from + 10 * i, "A museum opens in town number $i.",
                listOf(
                    WikiPage(
                        "Museum $i", "A museum.", "https://en.wikipedia.org/wiki/Museum_$i",
                        WikiImage("https://thumb.wikimedia.org/wikipedia/commons/thumb/a/ab/$name/330px-$name", 330, 248),
                        WikiImage("https://upload.wikimedia.org/wikipedia/commons/a/ab/$name", 3264, 2448),
                    ),
                ),
            )
        }

        fun grim(n: Int) = (0 until n).map { i ->
            OnThisDayEvent(2000 + i, "Troops invade town number $i.", listOf(WikiPage("Invasion $i", "", "https://en.wikipedia.org/wiki/Invasion_$i")))
        }
    }
}
