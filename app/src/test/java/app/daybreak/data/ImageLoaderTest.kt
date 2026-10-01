package app.daybreak.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ImageLoaderTest {
    /** A decoded picture, as far as the cache cares: its bytes and size. */
    private data class Pic(val text: String, val bytes: Int)

    private val dir: File = kotlin.io.path.createTempDirectory("images").toFile()

    @After fun cleanUp() {
        dir.deleteRecursively()
    }

    private val served = mutableMapOf<String, String>()
    private val fetched = mutableListOf<String>()
    private var gate: CompletableDeferred<Unit>? = null
    private var clock = 0L

    /** Text that starts with "pic:" decodes; anything else isn't a picture. */
    private fun cache(maxBytes: Int = 1_000, disk: ImageDiskCache? = ImageDiskCache(dir), dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        ImageCache(
            disk = disk,
            fetch = { url ->
                fetched += url
                gate?.await()
                (served[url] ?: throw IOException("404")).toByteArray()
            },
            decode = { bytes, _ -> String(bytes).takeIf { it.startsWith("pic:") }?.let { Pic(it, it.length * 10) } },
            sizeOf = { it.bytes },
            maxBytes = maxBytes,
            now = { clock },
            io = dispatcher,
        )

    private fun request(url: String, fallback: String? = null) = ImageRequest(url, fallback, 100, 50, fit = false)

    @Test fun `asked twice at once, a picture is fetched once`() = runTest {
        val images = cache(dispatcher = StandardTestDispatcher(testScheduler))
        served["a"] = "pic:a"
        gate = CompletableDeferred()
        val first = async { images.load(request("a")) }
        val second = async { images.load(request("a")) }
        advanceUntilIdle()
        gate!!.complete(Unit)
        assertEquals(Pic("pic:a", 50), first.await())
        assertSame(first.await(), second.await())
        assertEquals(listOf("a"), fetched)
        // Once nothing is loading, no lock is left behind.
        assertEquals(0, images.lockCount)
        assertSame(first.await(), images.cached(request("a")))
    }

    @Test fun `bytes that aren't a picture are never kept`() = runTest {
        val disk = ImageDiskCache(dir)
        val images = cache(disk = disk, dispatcher = StandardTestDispatcher(testScheduler))
        served["a"] = "<html>Not found</html>"
        assertNull(images.load(request("a")))
        assertNull(disk.get("a"))
        assertNull(images.cached(request("a")))
    }

    @Test fun `a picture spoilt on disk is deleted and fetched again`() = runTest {
        val disk = ImageDiskCache(dir).apply { put("a", "garbage".toByteArray()) }
        served["a"] = "pic:a"
        val images = cache(disk = disk, dispatcher = StandardTestDispatcher(testScheduler))
        assertEquals("pic:a", images.load(request("a"))?.text)
        assertEquals(listOf("a"), fetched)
        assertEquals("pic:a", disk.get("a")?.let(::String))
        // Kept on disk, a new loader (a relaunch) doesn't fetch it again.
        assertEquals("pic:a", cache(disk = disk, dispatcher = StandardTestDispatcher(testScheduler)).load(request("a"))?.text)
        assertEquals(1, fetched.size)
    }

    @Test fun `a failed picture isn't asked for again for ten minutes`() = runTest {
        val images = cache(dispatcher = StandardTestDispatcher(testScheduler))
        assertNull(images.load(request("a")))
        served["a"] = "pic:a"
        clock += 9 * 60_000
        assertNull(images.load(request("a")))
        assertEquals(1, fetched.size)
        clock += 2 * 60_000
        assertEquals("pic:a", images.load(request("a"))?.text)
        assertEquals(2, fetched.size)
    }

    @Test fun `the fallback is used when the picture fails, and remembered`() = runTest {
        val images = cache(dispatcher = StandardTestDispatcher(testScheduler))
        served["small"] = "pic:small"
        assertEquals("pic:small", images.load(request("big", fallback = "small"))?.text)
        assertEquals(listOf("big", "small"), fetched)
        assertEquals("pic:small", images.cached(request("big", fallback = "small"))?.text)
        // Long after, the one that worked is still used, without trying the other again.
        clock += 60 * 60_000
        assertEquals("pic:small", images.load(request("big", fallback = "small"))?.text)
        assertEquals(2, fetched.size)
    }

    @Test fun `memory holds no more than its size, dropping the least recently used`() = runTest {
        val images = cache(maxBytes = 120, disk = null, dispatcher = StandardTestDispatcher(testScheduler))
        listOf("a", "b", "c").forEach { served[it] = "pic:$it" } // 50 bytes each
        images.load(request("a"))
        images.load(request("b"))
        images.cached(request("a")) // used: now b is the oldest
        images.load(request("c"))
        assertEquals(100, images.bytesInMemory)
        assertNull(images.cached(request("b")))
        assertEquals("pic:a", images.cached(request("a"))?.text)
        assertEquals("pic:c", images.cached(request("c"))?.text)
        // One bigger than the whole cache isn't kept at all.
        served["huge"] = "pic:" + "x".repeat(20)
        assertEquals(240, images.load(request("huge"))?.bytes)
        assertNull(images.cached(request("huge")))
        assertEquals(100, images.bytesInMemory)
    }

    @Test fun `decoded sizes are kept apart`() = runTest {
        val images = cache(dispatcher = StandardTestDispatcher(testScheduler))
        served["a"] = "pic:a"
        images.load(request("a"))
        assertNull(images.cached(ImageRequest("a", null, 300, 300, fit = true)))
    }

    // --- Sizes ------------------------------------------------------------------------------

    @Test fun `pictures are decoded to cover the slot or fit inside it, never larger than they are`() {
        val slot = ImageRequest("a", null, 1000, 500, fit = false)
        assertEquals(1000 to 750, targetSize(3264, 2448, slot)) // covers: as wide as the slot
        assertEquals(1000 to 500, targetSize(2000, 1000, slot))
        assertEquals(960 to 480, targetSize(960, 480, slot)) // never scaled up
        val poster = slot.copy(width = 900, height = 400, fit = true)
        assertEquals(300 to 400, targetSize(600, 800, poster)) // fits: as tall as the frame
        assertEquals(400 to 400, targetSize(500, 500, poster))
        assertEquals(800 to 400, targetSize(1200, 600, poster))
        assertEquals(200 to 100, targetSize(200, 100, poster))
    }

    @Test fun `pictures are sampled down towards the slot's size, never below it`() {
        assertEquals(1, sampleSize(500, 375, 480, 360))
        assertEquals(1, sampleSize(100, 100, 480, 480))
        assertEquals(2, sampleSize(1200, 1000, 480, 400))
        assertEquals(2, sampleSize(3264, 2448, 1000, 750))
        assertEquals(4, sampleSize(3264, 2448, 600, 450))
    }

    // --- The disk cache ---------------------------------------------------------------------

    @Test fun `downloaded pictures are kept on disk by URL, only the newest few`() {
        val cache = ImageDiskCache(dir, maxFiles = 2)
        assertNull(cache.get("https://a"))
        cache.put("https://a", byteArrayOf(1))
        assertEquals(listOf<Byte>(1), cache.get("https://a")?.toList())
        // Each one newer than the last, so "a" is the oldest when the third arrives.
        dir.listFiles()!!.forEach { it.setLastModified(1_000_000) }
        cache.put("https://b", byteArrayOf(2))
        dir.listFiles()!!.filter { it.lastModified() != 1_000_000L }.forEach { it.setLastModified(2_000_000) }
        cache.put("https://c", byteArrayOf(3))
        assertEquals(2, dir.listFiles()!!.size)
        assertNull(cache.get("https://a"))
        assertEquals(listOf<Byte>(3), cache.get("https://c")?.toList())
        cache.remove("https://c")
        assertNull(cache.get("https://c"))
    }

    @Test fun `a half-written file left by a crash is cleaned up, one being written isn't`() {
        val now = System.currentTimeMillis()
        val cache = ImageDiskCache(dir, staleTmpMs = 60_000, now = { now })
        dir.mkdirs()
        val orphan = File(dir, "abc.tmp").apply { writeBytes(byteArrayOf(9)); setLastModified(now - 10 * 60_000) }
        val fresh = File(dir, "def.tmp").apply { writeBytes(byteArrayOf(9)); setLastModified(now - 1_000) }
        cache.put("https://a", byteArrayOf(1))
        assertTrue(!orphan.exists())
        assertTrue(fresh.exists())
        assertEquals(listOf<Byte>(1), cache.get("https://a")?.toList())
    }
}
