package com.mazzucci.weather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTP GET abstraction so the API code can be tested without a network. */
fun interface HttpClient {
    /** Returns the response body; throws [IOException] on network errors or non-200 responses. */
    suspend fun get(url: String): String
}

class UrlConnectionHttpClient(private val timeoutMs: Int = 10_000) : HttpClient {
    override suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            if (conn.responseCode != 200) throw IOException("Server returned HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
