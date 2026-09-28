package com.mazzucci.weather.narration

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Where the Gemma model comes from. The repo is license-gated, so downloads need a Hugging Face token. */
object GemmaModelSource {
    const val FILE_NAME = "gemma3-1b-it-int4.task"
    const val MODEL_PAGE = "https://huggingface.co/litert-community/Gemma3-1B-IT"
    const val TOKENS_PAGE = "https://huggingface.co/settings/tokens"
    const val DOWNLOAD_URL = "$MODEL_PAGE/resolve/main/$FILE_NAME"
}

/** A signed, token-free CDN URL for the model plus what we expect to receive. */
data class ResolvedDownload(val url: String, val sizeBytes: Long?, val sha256: String?)

/** Minimal response info needed to resolve a Hugging Face download without following redirects. */
data class HeadResponse(val code: Int, val headers: Map<String, String>) {
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

class ModelDownloadException(message: String) : IOException(message)

/**
 * Asks Hugging Face for the model with the user's token, without letting the HTTP client follow redirects, and
 * returns the signed CDN URL. Redirects that stay on huggingface.co (e.g. to a cache path) are followed here with
 * the token; the first off-host redirect is the CDN link, which is handed to DownloadManager without the token.
 * Pure apart from [head], so the error mapping is unit-tested.
 */
fun resolveModelDownload(token: String, head: (url: String, token: String) -> HeadResponse): ResolvedDownload {
    val trimmed = token.trim()
    if (trimmed.isEmpty()) throw ModelDownloadException("Paste a Hugging Face access token first")
    var url = URL(GemmaModelSource.DOWNLOAD_URL)
    var size: Long? = null
    var sha: String? = null
    repeat(MAX_HOPS) {
        val response = head(url.toString(), trimmed)
        // Size and checksum can come on any hop; keep the first ones seen.
        size = size ?: response.header("X-Linked-Size")?.toLongOrNull()
        sha = sha ?: response.header("X-Linked-ETag")?.trim('"', ' ')?.takeIf { SHA256.matches(it) }
        when (response.code) {
            in 300..399 -> {
                val location = response.header("Location")
                    ?: throw ModelDownloadException("Hugging Face didn't return a download link")
                val next = URL(url, location)
                if (!next.host.equals(url.host, ignoreCase = true)) return ResolvedDownload(next.toString(), size, sha)
                url = next
            }
            // Served directly by huggingface.co, which would need the token on the download itself. LFS files
            // always redirect to the CDN, so treat this as unexpected rather than hand the token to DownloadManager.
            200 -> throw ModelDownloadException("Hugging Face didn't return a download link")
            401 -> throw ModelDownloadException(
                "Hugging Face didn't accept that token. Create a read token at huggingface.co/settings/tokens."
            )
            403 -> throw ModelDownloadException(
                "Your Hugging Face account doesn't have access yet. Open the model page, accept the Gemma license, then try again."
            )
            404 -> throw ModelDownloadException("The model file wasn't found on Hugging Face")
            else -> throw ModelDownloadException("Hugging Face returned HTTP ${response.code}")
        }
    }
    throw ModelDownloadException("Too many redirects from Hugging Face")
}

private const val MAX_HOPS = 5

/** Real HEAD request with redirects disabled. Call off the main thread. */
fun httpHead(url: String, token: String): HeadResponse {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "HEAD"
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Authorization", "Bearer $token")
        val code = conn.responseCode
        val headers = conn.headerFields.filterKeys { it != null }.mapValues { it.value.firstOrNull().orEmpty() }
        return HeadResponse(code, headers)
    } finally {
        conn.disconnect()
    }
}

private val SHA256 = Regex("[0-9a-fA-F]{64}")
