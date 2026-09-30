package app.daybreak.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.daybreak.data.ClocksRepository
import app.daybreak.data.WeatherApi
import app.daybreak.domain.Clock
import app.daybreak.domain.Place
import app.daybreak.domain.cityOf
import app.daybreak.domain.convertTime
import app.daybreak.domain.formatClock
import app.daybreak.narration.TextGenerator
import app.daybreak.narration.TimeAskPrompt
import app.daybreak.narration.TimeCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * What the converter should show after an answer: a time (null for now), today or tomorrow, and where it is.
 * [id] is the answer's, so the converter applies it once and not again over the user's own picks.
 */
data class ConverterRequest(val minutes: Int?, val dayOffset: Int, val fromClockId: String?, val id: Long = 0)

/** The Ask box: idle, working on [question], its answer, or why there isn't one. */
sealed interface AskUi {
    data object Idle : AskUi
    data class Working(val question: String) : AskUi
    /** [request] sets the converter to the same moment, when the converter can show it. */
    data class Answer(val question: String, val text: String, val request: ConverterRequest?) : AskUi
    data class Failed(val question: String, val message: String) : AskUi
}

/**
 * The saved clocks, and the Ask box: Gemma turns the question into a tool call ([TimeAskPrompt]), and this does
 * the rest, finding each place among the clocks (by name or country) or with the place search, and working out
 * the time with java.time, so the answer is right whatever Gemma's wording.
 */
class ClocksViewModel(
    private val repo: ClocksRepository,
    /** Null when there's no model engine at all (tests, previews). */
    private val gemma: TextGenerator? = null,
    /** Whether Gemma can be asked now: installed and switched on. */
    private val gemmaReady: () -> Boolean = { false },
    private val places: WeatherApi? = null,
    private val here: () -> ZoneId = ZoneId::systemDefault,
    private val clock: () -> Instant = Instant::now,
    /** The whole ask, including waiting for Gemma behind the summaries and memes, must finish within this. */
    private val timeoutMs: Long = 45_000,
) : ViewModel() {
    val clocks: StateFlow<List<Clock>> = repo.clocks
    private val _ask = MutableStateFlow<AskUi>(AskUi.Idle)
    val ask: StateFlow<AskUi> = _ask.asStateFlow()
    private var askJob: Job? = null
    private var answers = 0L

    /** Adds [place] as a clock; false when it has no time zone or is already there. */
    fun add(place: Place): Boolean = Clock.of(place)?.let(repo::add) ?: false

    fun remove(clock: Clock) = repo.remove(clock.id)

    fun move(from: Int, to: Int) = repo.move(from, to)

    fun ask(question: String) {
        val q = question.trim().take(TimeAskPrompt.MAX_QUESTION)
        if (q.isEmpty()) return
        askJob?.cancel()
        _ask.value = AskUi.Working(q)
        askJob = viewModelScope.launch {
            _ask.value = withTimeoutOrNull(timeoutMs) { answer(q) }
                ?: AskUi.Failed(q, "Gemma is busy with the weather. Try again in a moment.")
        }
    }

    /** Stops a question that's taking too long. */
    fun cancelAsk() {
        askJob?.cancel()
        _ask.value = AskUi.Idle
    }

    private suspend fun answer(q: String): AskUi {
        val gen = gemma?.takeIf { gemmaReady() } ?: return AskUi.Failed(q, "Gemma isn't set up yet.")
        val zone = here()
        val saved = clocks.value.filter { it.zone != null }
        val reply = try {
            gen.generate(TimeAskPrompt.build(q, cityOf(zone), saved.map { it.name }), temperature = 0f, seed = 1)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AskUi.Failed(q, NOT_UNDERSTOOD)
        }
        val call = TimeAskPrompt.parse(reply) ?: return AskUi.Failed(q, NOT_UNDERSTOOD)
        val now = clock()
        val id = ++answers
        return when (call) {
            is TimeCall.TimeIn -> {
                val there = resolve(call.place, zone, saved).let { it as? Found ?: return (it as Missing).failure(q) }
                val t = now.atZone(there.zone)
                val text = if (there.isMe) "It's ${clockText(t)} in ${there.label}." else "It's ${clockText(t)} on ${weekday(t)} in ${there.label}."
                AskUi.Answer(q, text, ConverterRequest(null, 0, null, id))
            }
            is TimeCall.Convert -> {
                val from = resolve(call.from, zone, saved).let { it as? Found ?: return (it as Missing).failure(q) }
                val to = resolve(call.to, zone, saved).let { it as? Found ?: return (it as Missing).failure(q) }
                val day = now.atZone(from.zone).toLocalDate().plusDays(call.day.toLong())
                val at = convertTime(call.time, day, from.zone, from.zone)
                val there = at.withZoneSameInstant(to.zone)
                // The same voice as the converter's line: every place by name, the phone's too.
                val toDay = if (there.toLocalDate() != at.toLocalDate()) " on ${weekday(there)}" else ""
                val text = "At ${clockText(at)} on ${weekday(at)} in ${from.label}, it's ${clockText(there)}$toDay in ${to.label}."
                AskUi.Answer(q, text, requestFor(at, from, zone, now, id))
            }
        }
    }

    /**
     * The converter shows a time in your phone's place or a clock's, today or tomorrow there: the answer's own
     * time when it's in one of those, else the same moment in your phone's terms (null if that isn't today or
     * tomorrow, when the converter can't show it).
     */
    private fun requestFor(at: ZonedDateTime, from: Found, here: ZoneId, now: Instant, id: Long): ConverterRequest? {
        if (from.isMe || from.clockId != null) {
            val offset = ChronoUnit.DAYS.between(now.atZone(from.zone).toLocalDate(), at.toLocalDate()).toInt()
            return ConverterRequest(at.hour * 60 + at.minute, offset, from.clockId, id).takeIf { offset in 0..1 }
        }
        val mine = at.withZoneSameInstant(here)
        val offset = ChronoUnit.DAYS.between(now.atZone(here).toLocalDate(), mine.toLocalDate()).toInt()
        return ConverterRequest(mine.hour * 60 + mine.minute, offset, null, id).takeIf { offset in 0..1 }
    }

    private sealed interface Resolved
    private data class Found(val label: String, val zone: ZoneId, val clockId: String? = null, val isMe: Boolean = false) : Resolved
    private data class Missing(val place: String, val offline: Boolean) : Resolved {
        fun failure(q: String) = AskUi.Failed(
            q,
            if (offline) "Couldn't look up “$place” without a connection. Add it as a clock to ask offline."
            else "Couldn't find “$place”. Try a city name.",
        )
    }

    /**
     * A clock by its name (the user's own list comes first), then "me" or the phone's city, then a clock by its
     * region or country ("Romania" is the Bucharest clock, and the answer names Bucharest, as the rows do), then
     * the first place search result with a time zone. "Bucharest, Romania" is tried as "Bucharest" too.
     */
    private suspend fun resolve(name: String, zone: ZoneId, saved: List<Clock>): Resolved {
        val names = listOf(name, name.substringBefore(',').trim()).distinct()
        for (n in names) saved.firstOrNull { it.name.equals(n, ignoreCase = true) }?.let { return Found(it.name, it.zone!!, it.id) }
        if (name == TimeCall.ME || names.any { it.equals(cityOf(zone), ignoreCase = true) }) return Found(cityOf(zone), zone, isMe = true)
        for (n in names) {
            saved.firstOrNull { c -> c.detail?.split(", ")?.any { it.equals(n, ignoreCase = true) } == true }
                ?.let { return Found(it.name, it.zone!!, it.id) }
        }
        var offline = false
        for (n in names) {
            val found = try {
                places?.searchPlaces(n).orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                offline = true
                emptyList()
            }
            found.firstNotNullOfOrNull { p -> Clock.of(p)?.let { p to it } }?.let { (p, c) -> return Found(p.name, c.zone!!) }
        }
        return Missing(name, offline)
    }

    private fun clockText(t: ZonedDateTime) = formatClock(t.toLocalDateTime())

    private fun weekday(t: ZonedDateTime) = t.format(DateTimeFormatter.ofPattern("EEEE", Locale.US))

    private companion object {
        const val NOT_UNDERSTOOD = "Gemma couldn't work that one out. Try it another way, like “What time is it in Tokyo?”"
    }
}
