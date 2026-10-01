package app.daybreak.data

import app.daybreak.BuildConfig
import app.daybreak.domain.OnThisDayEvent
import app.daybreak.domain.WikiImage
import app.daybreak.domain.WikiPage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

/** Wikipedia's lists of what happened on a date: the curated few, or every event it knows. */
enum class OnThisDayFeed(val path: String) { SELECTED("selected"), EVENTS("events") }

interface OnThisDayApi {
    suspend fun events(month: Int, day: Int, feed: OnThisDayFeed): List<OnThisDayEvent>
}

/** Who's calling, as Wikimedia asks every client to say, with a way to reach the project. */
val WIKIMEDIA_USER_AGENT = "Daybreak/${BuildConfig.VERSION_NAME} (https://github.com/mazzucci/Daybreak)"

/**
 * English Wikipedia's "On this day" feed (free, no API key, CC BY-SA). The request carries only the month and day
 * (and, like any request, the phone's IP address). The REST endpoint on en.wikipedia.org is used rather than
 * api.wikimedia.org's copy of it, which serves the same response. The answer (half a megabyte for the full list)
 * is parsed on [parseOn], off the main thread.
 */
class WikipediaOnThisDayApi(
    private val http: HttpClient,
    private val base: String = BASE,
    private val parseOn: CoroutineDispatcher = Dispatchers.Default,
) : OnThisDayApi {
    override suspend fun events(month: Int, day: Int, feed: OnThisDayFeed): List<OnThisDayEvent> {
        val url = String.format(Locale.US, "%s/%s/%02d/%02d", base, feed.path, month, day)
        val json = http.get(url, mapOf("User-Agent" to WIKIMEDIA_USER_AGENT))
        return withContext(parseOn) { parseOnThisDay(json, feed) }
    }

    private companion object {
        const val BASE = "https://en.wikipedia.org/api/rest_v1/feed/onthisday"
    }
}

/**
 * The [feed]'s items: each one's year, text and the ordinary articles it links (a disambiguation page or one
 * without a link is left out). An item that doesn't parse is skipped rather than losing the rest.
 */
fun parseOnThisDay(json: String, feed: OnThisDayFeed): List<OnThisDayEvent> {
    val items = JSONObject(json).optJSONArray(feed.path) ?: return emptyList()
    return (0 until items.length()).mapNotNull { i ->
        runCatching {
            val o = items.getJSONObject(i)
            val pages = o.optJSONArray("pages")
            OnThisDayEvent(
                year = o.getInt("year"),
                text = o.getString("text"),
                pages = if (pages == null) emptyList() else (0 until pages.length()).mapNotNull { parsePage(pages.getJSONObject(it)) },
            )
        }.getOrNull()
    }
}

private fun parsePage(o: JSONObject): WikiPage? {
    if (o.optString("type", "standard") != "standard") return null
    val urls = o.optJSONObject("content_urls")
    val url = urls?.optJSONObject("mobile")?.optString("page").orEmpty()
        .ifBlank { urls?.optJSONObject("desktop")?.optString("page").orEmpty() }
    if (url.isBlank()) return null
    val title = o.optJSONObject("titles")?.optString("normalized").orEmpty().ifBlank { o.optString("title").replace('_', ' ') }
    if (title.isBlank()) return null
    return WikiPage(
        title = title,
        extract = o.optString("extract"),
        url = url,
        thumbnail = parseImage(o.optJSONObject("thumbnail")),
        original = parseImage(o.optJSONObject("originalimage")),
    )
}

private fun parseImage(o: JSONObject?): WikiImage? {
    val source = o?.optString("source").orEmpty()
    if (source.isBlank()) return null
    return WikiImage(source, o!!.optInt("width"), o.optInt("height"))
}
