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
}

class UrlConnectionHttpClient(private val timeoutMs: Int = 10_000) : HttpClient {
    override suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            if (conn.responseCode != 200) throw HttpException(conn.responseCode)
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
