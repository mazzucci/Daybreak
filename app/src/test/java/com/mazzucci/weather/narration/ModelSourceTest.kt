package com.mazzucci.weather.narration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSourceTest {
    private val sha = "a".repeat(64)
    private var requestedToken: String? = null

    private fun resolve(token: String = "hf_abc", response: HeadResponse): ResolvedDownload =
        resolveModelDownload(token) { url, t ->
            assertEquals(GemmaModelSource.DOWNLOAD_URL, url)
            requestedToken = t
            response
        }

    private fun error(response: HeadResponse, token: String = "hf_abc"): String =
        assertThrows(ModelDownloadException::class.java) { resolve(token, response) }.message!!

    @Test fun `redirect gives the signed URL, size and checksum`() {
        val r = resolve(
            token = "  hf_abc \n",
            response = HeadResponse(
                302,
                mapOf(
                    "location" to "https://cdn.example/xet/abc?sig=1",
                    "x-linked-size" to "554661246",
                    "x-linked-etag" to "\"$sha\"",
                ),
            ),
        )
        assertEquals("hf_abc", requestedToken) // trimmed
        assertEquals(ResolvedDownload("https://cdn.example/xet/abc?sig=1", 554661246, sha), r)
    }

    @Test fun `relative redirects resolve against huggingface`() {
        val r = resolve(response = HeadResponse(307, mapOf("Location" to "/api/resolve-cache/x.task")))
        assertEquals("https://huggingface.co/api/resolve-cache/x.task", r.url)
        assertNull(r.sizeBytes)
        assertNull(r.sha256)
    }

    @Test fun `ignores an etag that isn't a sha256`() {
        val r = resolve(response = HeadResponse(302, mapOf("Location" to "https://cdn/x", "X-Linked-ETag" to "\"abc\"")))
        assertNull(r.sha256)
    }

    @Test fun `explains token and license problems`() {
        assertTrue(error(HeadResponse(401, emptyMap())).contains("didn't accept that token"))
        assertTrue(error(HeadResponse(403, emptyMap())).contains("accept the Gemma license"))
        assertTrue(error(HeadResponse(404, emptyMap())).contains("wasn't found"))
        assertTrue(error(HeadResponse(500, emptyMap())).contains("HTTP 500"))
        assertTrue(error(HeadResponse(302, emptyMap())).contains("download link"))
    }

    @Test fun `blank token fails before any request`() {
        val e = assertThrows(ModelDownloadException::class.java) {
            resolveModelDownload("  ") { _, _ -> error("should not be called") }
        }
        assertTrue(e.message!!.contains("token"))
    }
}
