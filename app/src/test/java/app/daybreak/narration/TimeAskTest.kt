package app.daybreak.narration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class TimeAskTest {
    @Test fun `tool calls parse, with "my time" as me and chatter around the JSON ignored`() {
        assertEquals(TimeCall.TimeIn("Tokyo"), TimeAskPrompt.parse("""{"tool":"time_in","place":"Tokyo"}"""))
        assertEquals(
            TimeCall.Convert(LocalTime.NOON, 0, TimeCall.ME, "Romania"),
            TimeAskPrompt.parse("""Sure! {"tool":"convert_time","time":"12:00","day":"today","from":"my time","to":"Romania"} Hope that helps."""),
        )
        assertEquals(
            TimeCall.Convert(LocalTime.of(9, 5), 1, "London", TimeCall.ME),
            TimeAskPrompt.parse("""{"tool":"convert_time","time":"9:05","day":"Tomorrow","from":"London","to":"here"}"""),
        )
    }

    @Test fun `anything else is no call`() {
        listOf(
            "It's 10 PM in Bucharest.",
            """{"tool":"weather","place":"Tokyo"}""",
            """{"tool":"time_in","place":""}""",
            """{"tool":"convert_time","time":"25:00","day":"today","from":"me","to":"Paris"}""",
            """{"tool":"convert_time","time":"noon","day":"today","from":"me","to":"Paris"}""",
            """{"tool":"convert_time","time":"12:00","day":"next week","from":"me","to":"Paris"}""",
            """{"tool":"convert_time","time":"12:00","day":"today","from":"me"}""",
            """{"tool": broken""",
        ).forEach { assertNull(it, TimeAskPrompt.parse(it)) }
    }

    @Test fun `the prompt names the user's place and clocks, and caps the question`() {
        val p = TimeAskPrompt.build("x".repeat(500), "Los Angeles", listOf("Bucharest", "Tokyo"))
        assertTrue(p.contains("The user is in Los Angeles. Their clocks: Bucharest, Tokyo."))
        assertTrue(p.endsWith("Q: ${"x".repeat(TimeAskPrompt.MAX_QUESTION)}\nA:"))
    }

    @Test fun `nested and named shapes parse, and JSON null is missing`() {
        assertEquals(TimeCall.TimeIn("Tokyo"), TimeAskPrompt.parse("""{"name":"time_in","parameters":{"place":"Tokyo"}}"""))
        assertEquals(TimeCall.TimeIn("Tokyo"), TimeAskPrompt.parse("""{"tool":"time_in","args":{"place":"Tokyo"}}"""))
        assertEquals(TimeCall.TimeIn("Tokyo {HQ}"), TimeAskPrompt.parse("""{"tool":"time_in","place":"Tokyo {HQ}"}"""))
        assertNull(TimeAskPrompt.parse("""{"tool":"time_in","place":null}"""))
    }
}
