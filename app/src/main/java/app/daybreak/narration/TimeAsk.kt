package app.daybreak.narration

import org.json.JSONException
import org.json.JSONObject
import java.time.LocalTime

/**
 * A question about time in other places, as the one tool call Gemma picks for it. Gemma only turns the words into
 * the call; the app does the time-zone maths and writes the answer, so the answer is right even when a small
 * model's wording wouldn't be. [ME] stands for the user's own place.
 */
sealed interface TimeCall {
    /** time_in(place): what time is it there now. */
    data class TimeIn(val place: String) : TimeCall

    /** convert_time(time, day, from, to): [time] on [day] (0 today, 1 tomorrow) where [from] is, in [to]. */
    data class Convert(val time: LocalTime, val day: Int, val from: String, val to: String) : TimeCall

    companion object {
        const val ME = "me"
    }
}

object TimeAskPrompt {
    /** Longest question passed on; enough for any real one, short enough to keep the prompt small. */
    const val MAX_QUESTION = 160

    /**
     * Instructions, the two tools, the user's place and clocks (so "Bucharest" and "my time" resolve), a few
     * examples, and the question. The examples cover the shapes people ask in: now there, my time to there, and
     * their time to mine.
     */
    fun build(question: String, phoneCity: String, clocks: List<String>): String = buildString {
        appendLine("You turn a question about the time in different places into one tool call, as JSON on one line.")
        appendLine("Tools:")
        appendLine("""- {"tool":"time_in","place":"<place>"}: the time in a place right now.""")
        appendLine("""- {"tool":"convert_time","time":"<HH:MM, 24-hour>","day":"today" or "tomorrow","from":"<place>","to":"<place>"}: what a time in one place is in another.""")
        appendLine("""Use "me" for the user's own place ("my time", "here", "local"). Places are cities or countries, as the user wrote them.""")
        appendLine("The user is in $phoneCity." + if (clocks.isEmpty()) "" else " Their clocks: ${clocks.joinToString(", ")}.")
        appendLine("Examples:")
        appendLine("Q: What time is it in Tokyo?")
        appendLine("""A: {"tool":"time_in","place":"Tokyo"}""")
        appendLine("Q: What time is it in Romania at noon my time?")
        appendLine("""A: {"tool":"convert_time","time":"12:00","day":"today","from":"me","to":"Romania"}""")
        appendLine("Q: If it's 9am tomorrow in London, what time is that here?")
        appendLine("""A: {"tool":"convert_time","time":"09:00","day":"tomorrow","from":"London","to":"me"}""")
        appendLine("Q: 6:30 pm in New York is what in Paris?")
        appendLine("""A: {"tool":"convert_time","time":"18:30","day":"today","from":"New York","to":"Paris"}""")
        appendLine("Reply with the JSON only.")
        appendLine("Q: ${question.trim().take(MAX_QUESTION)}")
        append("A:")
    }

    /**
     * The first JSON object in [reply] as a tool call, or null if there isn't a valid one: an unknown tool, a
     * missing or blank place, a time that isn't HH:MM, or a day other than today or tomorrow.
     */
    fun parse(reply: String): TimeCall? {
        val start = reply.indexOf('{')
        val end = reply.indexOf('}', start)
        if (start < 0 || end < 0) return null
        val o = try {
            JSONObject(reply.substring(start, end + 1))
        } catch (e: JSONException) {
            return null
        }
        fun place(key: String) = o.optString(key).trim().takeIf { it.isNotEmpty() && it.length <= 60 }
            ?.let { if (it.lowercase() in SELF) TimeCall.ME else it }
        return when (o.optString("tool")) {
            "time_in" -> place("place")?.let { TimeCall.TimeIn(it) }
            "convert_time" -> {
                val time = TIME.matchEntire(o.optString("time").trim())?.let { m ->
                    val h = m.groupValues[1].toInt()
                    val min = m.groupValues[2].toInt()
                    if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
                } ?: return null
                val day = when (o.optString("day", "today").trim().lowercase()) {
                    "today", "" -> 0
                    "tomorrow" -> 1
                    else -> return null
                }
                val from = place("from") ?: return null
                val to = place("to") ?: return null
                TimeCall.Convert(time, day, from, to)
            }
            else -> null
        }
    }

    private val TIME = Regex("""(\d{1,2}):(\d{2})""")
    private val SELF = setOf("me", "here", "my time", "local", "my place", "mine")
}
