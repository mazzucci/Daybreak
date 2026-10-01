package app.daybreak.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A picture for a slot on screen: [url], or [fallbackUrl] when that can't be had, decoded for a slot of
 * [width] x [height] pixels, either covering it (to be cropped) or, with [fit], inside it whole.
 */
data class ImageRequest(val url: String, val fallbackUrl: String?, val width: Int, val height: Int, val fit: Boolean)

/** Pictures from the web for the cards (so far the "On this day" one). */
interface ImageLoader {
    /** From memory only, at once, so a picture that's already been loaded shows without a placeholder or a fade. */
    fun cached(request: ImageRequest): Bitmap?

    /** From memory, the disk cache or the network, off the main thread; null when it can't be had. */
    suspend fun load(request: ImageRequest): Bitmap?
}

/**
 * A tiny loader instead of an image library: a few pictures a day don't justify Coil and its dependencies. The
 * download goes through HttpURLConnection with Wikimedia's User-Agent, is decoded straight to the slot's size,
 * and only then lands in [disk] (so bytes that aren't a picture are never kept) and in a memory cache of
 * [memoryBytes]. See [ImageCache] for the rest.
 */
class WebImageLoader(
    disk: ImageDiskCache,
    memoryBytes: Int = MEMORY_BYTES,
) : ImageLoader {
    private val cache = ImageCache(disk, ::download, ::decodeBitmap, sizeOf = { it.allocationByteCount }, maxBytes = memoryBytes)

    override fun cached(request: ImageRequest): Bitmap? = cache.cached(request)

    override suspend fun load(request: ImageRequest): Bitmap? = cache.load(request)

    private companion object {
        /**
         * The card's pictures are at most 960 px wide (see OnThisDay.heroUrl) and a cropped one is wider than tall,
         * so one is at most about 960 x 835 px, 3.2 MB: this holds the one showing and the next, plus a fallback.
         */
        const val MEMORY_BYTES = 10 * 1024 * 1024
    }
}

/**
 * The loader's workings, apart from Android's bitmaps so they can be tested on the JVM:
 * - each URL is loaded once at a time (a second ask waits for the first and gets its result);
 * - bytes are [decode]d first, and kept on [disk] only when that works; bytes on disk that no longer decode are
 *   deleted and fetched again;
 * - a URL that fails is not asked for again for 10 minutes, so a dead link isn't retried on every look at Home;
 * - when the request's URL fails and its fallback works, the fallback is used for that URL from then on;
 * - decoded pictures are kept in memory by URL and size, the least recently used dropped past [maxBytes].
 */
class ImageCache<T : Any>(
    private val disk: ImageDiskCache?,
    private val fetch: suspend (String) -> ByteArray,
    private val decode: (ByteArray, ImageRequest) -> T?,
    private val sizeOf: (T) -> Int,
    private val maxBytes: Int,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val memory = LinkedHashMap<String, T>(16, 0.75f, true) // in order of use
    private var memoryBytes = 0
    private val locks = mutableMapOf<String, Lock>()
    private val failures = mutableMapOf<String, Long>()
    /** A request's URL that failed, to the fallback that worked instead. */
    private val worked = mutableMapOf<String, String>()

    private class Lock {
        val mutex = Mutex()
        var users = 0
    }

    fun cached(request: ImageRequest): T? = synchronized(this) {
        val url = worked[request.url] ?: request.url
        memory[keyOf(url, request)] ?: request.fallbackUrl?.let { memory[keyOf(it, request)] }
    }

    suspend fun load(request: ImageRequest): T? {
        cached(request)?.let { return it }
        val primary = synchronized(this) { worked[request.url] } ?: request.url
        load(primary, request)?.let { return it }
        val fallback = request.fallbackUrl?.takeIf { it != primary } ?: return null
        return load(fallback, request)?.also { synchronized(this) { worked[request.url] = fallback } }
    }

    private suspend fun load(url: String, request: ImageRequest): T? {
        val key = keyOf(url, request)
        val lock = synchronized(this) { locks.getOrPut(url) { Lock() }.also { it.users++ } }
        try {
            return lock.mutex.withLock {
                synchronized(this) { memory[key] } ?: if (recentlyFailed(url)) null else withContext(io) { get(url, key, request) }
            }
        } finally {
            synchronized(this) { if (--lock.users == 0) locks.remove(url) }
        }
    }

    private suspend fun get(url: String, key: String, request: ImageRequest): T? = try {
        val onDisk = disk?.get(url)
        val kept = onDisk?.let { decodeOrNull(it, request) }
        if (onDisk != null && kept == null) disk?.remove(url) // spoilt: fetched again below
        val value = kept ?: fetch(url).let { bytes ->
            (decodeOrNull(bytes, request) ?: throw IOException("Not a picture")).also { disk?.put(url, bytes) }
        }
        remember(key, value)
        value
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        synchronized(this) { failures[url] = now() }
        null
    }

    private fun decodeOrNull(bytes: ByteArray, request: ImageRequest): T? = runCatching { decode(bytes, request) }.getOrNull()

    private fun recentlyFailed(url: String): Boolean = synchronized(this) {
        val t = now()
        failures.values.removeAll { t - it >= FAILURE_TTL_MS }
        url in failures
    }

    private fun remember(key: String, value: T) = synchronized(this) {
        val size = sizeOf(value)
        if (size > maxBytes) return@synchronized
        memory.put(key, value)?.let { memoryBytes -= sizeOf(it) }
        memoryBytes += size
        val oldest = memory.entries.iterator()
        while (memoryBytes > maxBytes && oldest.hasNext()) {
            val e = oldest.next()
            memoryBytes -= sizeOf(e.value)
            oldest.remove()
        }
    }

    private fun keyOf(url: String, r: ImageRequest) = "${r.width}x${r.height}${if (r.fit) "f" else "c"} $url"

    /** How many URLs have a lock (for tests: none once nothing is loading). */
    internal val lockCount: Int get() = synchronized(this) { locks.size }

    /** The bytes of the pictures in memory (for tests). */
    internal val bytesInMemory: Int get() = synchronized(this) { memoryBytes }

    private companion object {
        const val FAILURE_TTL_MS = 10L * 60 * 1000
    }
}

/**
 * The size to decode a [width] x [height] picture to for [request]'s slot: just covering the slot (to be cropped)
 * or, with `fit`, just inside it; never larger than the picture itself.
 */
fun targetSize(width: Int, height: Int, request: ImageRequest): Pair<Int, Int> {
    val sx = request.width.toDouble() / width
    val sy = request.height.toDouble() / height
    val scale = min(1.0, if (request.fit) min(sx, sy) else max(sx, sy))
    return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
}

/**
 * BitmapFactory's inSampleSize: the largest power of two that still leaves the picture at least [targetWidth] x
 * [targetHeight], so a big picture isn't decoded at full size before it's scaled to the slot.
 */
fun sampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
    var sample = 1
    while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight) sample *= 2
    return sample
}

/**
 * Decodes [bytes] straight to the slot's size: with ImageDecoder's target size on Android 9 and up (in software
 * memory, so it can be scaled again for a backdrop), else sampled with BitmapFactory and scaled to the exact size.
 * Null if it isn't a picture.
 */
private fun decodeBitmap(bytes: ByteArray, request: ImageRequest): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                val (w, h) = targetSize(info.size.width, info.size.height, request)
                decoder.setTargetSize(w, h)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: IOException) {
            null
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val (w, h) = targetSize(bounds.outWidth, bounds.outHeight, request)
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, w, h) }
    val sampled = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    if (sampled.width == w && sampled.height == h) return sampled
    return Bitmap.createScaledBitmap(sampled, w, h, true).also { if (it !== sampled) sampled.recycle() }
}

/** No card picture is anywhere near this; anything bigger isn't what was asked for. */
private const val MAX_DOWNLOAD_BYTES = 4 * 1024 * 1024

/**
 * GETs [url] with Wikimedia's User-Agent; throws [IOException] for anything but a 200 with a picture's
 * Content-Type and a sensible size.
 */
private fun download(url: String): ByteArray {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", WIKIMEDIA_USER_AGENT)
        if (conn.responseCode != 200) throw HttpException(conn.responseCode)
        if (conn.contentType?.startsWith("image/") != true) throw IOException("Not a picture: ${conn.contentType}")
        if (conn.contentLengthLong > MAX_DOWNLOAD_BYTES) throw IOException("Picture too big")
        return conn.inputStream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_DOWNLOAD_BYTES) throw IOException("Picture too big")
            }
            out.toByteArray()
        }
    } finally {
        conn.disconnect()
    }
}

/**
 * Downloaded pictures in the app's cache directory (which Android may clear, and which isn't backed up), one file
 * per URL named by its hash. Only the [maxFiles] most recently used are kept: the card needs today's few. A file
 * is written aside as ".tmp" and renamed, so a half-written one is never read; one left behind by a crash or a
 * kill mid-write is deleted once it's [staleTmpMs] old.
 */
class ImageDiskCache(
    private val dir: File,
    private val maxFiles: Int = 12,
    private val staleTmpMs: Long = 60_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun get(url: String): ByteArray? = runCatching {
        fileOf(url).takeIf { it.isFile }?.let { file -> file.readBytes().also { file.setLastModified(now()) } }
    }.getOrNull()

    fun put(url: String, bytes: ByteArray) {
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "${fileOf(url).name}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(fileOf(url))) tmp.delete()
            prune()
        }
    }

    fun remove(url: String) {
        runCatching { fileOf(url).delete() }
    }

    private fun prune() {
        val all = dir.listFiles { f -> f.isFile } ?: return
        val (tmp, files) = all.partition { it.name.endsWith(".tmp") }
        tmp.filter { now() - it.lastModified() > staleTmpMs }.forEach { it.delete() }
        files.sortedByDescending { it.lastModified() }.drop(maxFiles).forEach { it.delete() }
    }

    private fun fileOf(url: String): File = File(dir, hash(url))

    private fun hash(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(Locale.US, it) }
}
