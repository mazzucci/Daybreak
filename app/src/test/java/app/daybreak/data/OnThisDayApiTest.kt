package app.daybreak.data

import app.daybreak.TestData
import app.daybreak.domain.OnThisDay
import app.daybreak.domain.OnThisDayEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.time.LocalDate
import kotlin.concurrent.thread

class OnThisDayApiTest {
    private val selected = TestData.fixture("onthisday_selected_10_01.json")
    private val events = TestData.fixture("onthisday_events_10_01.json")

    private fun feed(date: String, feed: OnThisDayFeed): List<OnThisDayEvent> =
        parseOnThisDay(TestData.fixture("onthisday_${feed.path}_$date.json"), feed)

    @Test fun `parses the real feed for October 1st`() {
        val items = parseOnThisDay(selected, OnThisDayFeed.SELECTED)
        assertEquals(25, items.size)
        val tuvalu = items.first { it.year == 1978 }
        assertEquals("Tuvalu adopted its national flag (pictured) on the day that the country gained its independence.", tuvalu.text)
        val flag = tuvalu.pages.single()
        assertEquals("Flag of Tuvalu", flag.title)
        assertEquals("https://en.wikipedia.org/wiki/Flag_of_Tuvalu", flag.url)
        assertEquals(330, flag.thumbnail?.width)
        assertEquals(165, flag.thumbnail?.height)
        assertTrue(flag.thumbnail!!.url.contains("/330px-Flag_of_Tuvalu.svg.png"))
        assertTrue(flag.extract.startsWith("The national flag of Tuvalu"))
        // An article without a picture parses without one.
        assertNull(items.first { it.year == 1989 }.pages.first().thumbnail)
        assertEquals(25, parseOnThisDay(events, OnThisDayFeed.EVENTS).size)
        // Asked for the other list, there's nothing.
        assertTrue(parseOnThisDay(selected, OnThisDayFeed.EVENTS).isEmpty())
    }

    /**
     * Real items from the feed that an earlier version of the filter got wrong: cheerful ones it dropped because
     * an article's life story mentioned a war or a shooting star, and grim ones its word list missed.
     */
    @Test fun `the filter gets real items right that it used to get wrong`() {
        data class Row(val date: String, val feed: OnThisDayFeed, val year: Int, val snippet: String, val grim: Boolean)
        val table = listOf(
            // Cheerful: must survive.
            Row("11_09", OnThisDayFeed.EVENTS, 1967, "Apollo 4", grim = false),
            Row("07_16", OnThisDayFeed.SELECTED, 1951, "The Catcher in the Rye", grim = false),
            Row("07_16", OnThisDayFeed.EVENTS, 1951, "The Catcher in the Rye", grim = false),
            Row("02_29", OnThisDayFeed.SELECTED, 1940, "Hattie McDaniel", grim = false),
            Row("02_29", OnThisDayFeed.EVENTS, 1940, "Hattie McDaniel", grim = false),
            Row("10_01", OnThisDayFeed.EVENTS, 1947, "F-86 Sabre", grim = false),
            Row("08_06", OnThisDayFeed.SELECTED, 1996, "ALH84001", grim = false),
            // Grim: must be caught.
            Row("08_06", OnThisDayFeed.EVENTS, 258, "beheaded", grim = true),
            Row("07_16", OnThisDayFeed.EVENTS, 2009, "found dead", grim = true),
            Row("11_09", OnThisDayFeed.EVENTS, 1872, "Great Boston Fire", grim = true),
            Row("11_09", OnThisDayFeed.EVENTS, 1307, "persecuted", grim = true),
            Row("07_16", OnThisDayFeed.SELECTED, 1945, "Trinity test", grim = true),
            Row("07_16", OnThisDayFeed.EVENTS, 1945, "nuclear weapon", grim = true),
        )
        val wrong = table.filter { row ->
            val item = feed(row.date, row.feed).single { it.year == row.year && row.snippet in it.text }
            OnThisDay.isGrim(item) != row.grim
        }
        assertTrue("Wrong: $wrong", wrong.isEmpty())
    }

    @Test fun `December 7th's picks leave out Pearl Harbor`() {
        val picks = OnThisDay.choose(feed("12_07", OnThisDayFeed.SELECTED), LocalDate.of(2026, 12, 7))
        assertTrue(picks.isNotEmpty())
        assertTrue(picks.none { "Pearl Harbor" in it.text })
    }

    @Test fun `October 1st's picks leave out the stampede, the shooting, the wars and the murders`() {
        val items = parseOnThisDay(selected, OnThisDayFeed.SELECTED)
        val picks = OnThisDay.choose(items, LocalDate.of(2026, 10, 1))
        assertEquals(5, picks.size)
        val years = picks.map { it.year }
        listOf(2022, 2017, 2012, 1991, 1965, 1918).forEach { assertFalse("$it", it in years) }
        assertEquals(picks, OnThisDay.choose(items, LocalDate.of(2026, 10, 1)))
        // No two from the same decade, and every picture a free one from Commons.
        assertEquals(picks.size, picks.map { it.year / 10 }.toSet().size)
        assertTrue(picks.mapNotNull { it.picture }.all { OnThisDay.isCommons(it.url) && it.fallbackUrl?.let(OnThisDay::isCommons) != false })
    }

    @Test fun `the Thrilla in Manila never shows its non-free poster`() {
        val ali = parseOnThisDay(selected, OnThisDayFeed.SELECTED).single { it.year == 1975 }
        assertTrue(ali.pages.any { p -> p.thumbnail?.url?.contains("/wikipedia/en/") == true })
        val picture = OnThisDay.pictureOf(ali)
        assertTrue(picture == null || OnThisDay.isCommons(picture.url))
        // The 1975 fight in the full list links only the poster's article: words only.
        val fight = parseOnThisDay(events, OnThisDayFeed.EVENTS).single { it.year == 1975 && "Frazier" in it.text }
        assertNull(OnThisDay.pictureOf(fight))
    }

    @Test fun `asks for the date's list with Wikimedia's User-Agent`() = runTest {
        val urls = mutableListOf<String>()
        val sent = mutableListOf<Map<String, String>>()
        val http = object : HttpClient {
            override suspend fun get(url: String): String = error("expected the call with headers")
            override suspend fun get(url: String, headers: Map<String, String>): String {
                urls += url
                sent += headers
                return selected
            }
        }
        val items = WikipediaOnThisDayApi(http).events(3, 5, OnThisDayFeed.SELECTED)
        assertEquals(25, items.size)
        assertEquals(listOf("https://en.wikipedia.org/api/rest_v1/feed/onthisday/selected/03/05"), urls)
        assertEquals("Daybreak/1.0 (https://github.com/mazzucci/Daybreak)", sent.single()["User-Agent"])
    }

    /** A local HTTP server for [requests] requests: notes each one's path and User-Agent, and answers [body]. */
    private class Server(requests: Int, body: String) : AutoCloseable {
        private val socket = ServerSocket(0, 0, InetAddress.getLoopbackAddress())
        val paths = mutableListOf<String>()
        val agents = mutableListOf<String?>()
        val base = "http://127.0.0.1:${socket.localPort}"
        private val worker = thread {
            val bytes = body.toByteArray()
            repeat(requests) {
                socket.accept().use { s ->
                    val reader = s.getInputStream().bufferedReader()
                    val head = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                    paths += head.first().split(' ')[1]
                    agents += head.firstOrNull { it.startsWith("User-Agent:", ignoreCase = true) }?.substringAfter(':')?.trim()
                    s.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes)
                        flush()
                    }
                }
            }
        }

        fun join() = worker.join(5_000)
        override fun close() = socket.close()
    }

    @Test fun `the API's User-Agent goes out through the real client`() = runTest {
        Server(1, selected).use { server ->
            val items = WikipediaOnThisDayApi(UrlConnectionHttpClient(), base = "${server.base}/feed").events(10, 1, OnThisDayFeed.SELECTED)
            server.join()
            assertEquals(25, items.size)
            assertEquals(listOf("/feed/selected/10/01"), server.paths)
            assertEquals(WIKIMEDIA_USER_AGENT, server.agents.single())
        }
    }

    @Test fun `the URL connection client sends the headers it's given, and none with the one-argument call`() = runTest {
        Server(2, "{}").use { server ->
            val url = "${server.base}/feed"
            assertEquals("{}", UrlConnectionHttpClient().get(url, mapOf("User-Agent" to WIKIMEDIA_USER_AGENT)))
            assertEquals("{}", UrlConnectionHttpClient().get(url))
            server.join()
            assertEquals(WIKIMEDIA_USER_AGENT, server.agents[0])
            assertFalse(server.agents[1].orEmpty().startsWith("Daybreak"))
        }
    }

    @Test fun `a lambda client still works, without headers`() = runTest {
        val http = HttpClient { "{\"selected\":[]}" }
        assertTrue(WikipediaOnThisDayApi(http).events(10, 1, OnThisDayFeed.SELECTED).isEmpty())
    }

    @Test fun `parses every fixture date, each item with at least its year and text`() {
        for (date in listOf("02_29", "07_16", "08_06", "11_09", "12_07")) {
            val items = feed(date, OnThisDayFeed.SELECTED)
            assertTrue(date, items.size > 10)
            assertNotNull(items.first().text)
        }
    }
}
