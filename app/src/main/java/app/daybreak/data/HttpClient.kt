package app.daybreak.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A non-200 response; callers word the message for the user ("Weather service returned HTTP 503"). */
class HttpException(val code: Int) : IOException("HTTP $code")

/** Minimal HTTP GET abstraction so the API code can be tested without a network. */
fun interface HttpClient {
    /** Returns the response body; throws [HttpException] for non-200 responses, [IOException] for network errors. */
    suspend fun get(url: String): String

    /**
     * [get] with extra request [headers] (Wikimedia asks for a User-Agent that says who's calling).
     *
     * Implementers that talk to a real server must override this: the default just calls [get] and **drops the
     * headers**, which only suits test fakes that have no use for them. [UrlConnectionHttpClient] overrides it.
     */
    suspend fun get(url: String, headers: Map<String, String>): String = get(url)
}

class UrlConnectionHttpClient(private val timeoutMs: Int = 10_000) : HttpClient {
    override suspend fun get(url: String): String = get(url, emptyMap())

    override suspend fun get(url: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            headers.forEach { (name, value) -> conn.setRequestProperty(name, value) }
            if (conn.responseCode != 200) throw HttpException(conn.responseCode)
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
