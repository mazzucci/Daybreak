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
import app.daybreak.narration.ModelStatus
import app.daybreak.narration.TextGenerator
import app.daybreak.narration.TimeAskPrompt
import app.daybreak.narration.TimeCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the converter should show after an answer: a time (null for now), today or tomorrow, and where it is. */
data class ConverterRequest(val minutes: Int?, val dayOffset: Int, val fromClockId: String?)

/** The Ask box: idle, working on [question], its answer, or why there isn't one. */
sealed interface AskUi {
    data object Idle : AskUi
    data class Working(val question: String) : AskUi
    /** [request] sets the converter to the same moment, when it's in a place the converter has (a clock or you). */
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
    modelStatus: StateFlow<ModelStatus> = MutableStateFlow(ModelStatus.NotInstalled),
    private val places: WeatherApi? = null,
    private val here: () -> ZoneId = ZoneId::systemDefault,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {
    val clocks: StateFlow<List<Clock>> = repo.clocks
    val modelStatus: StateFlow<ModelStatus> = modelStatus
    private val _ask = MutableStateFlow<AskUi>(AskUi.Idle)
    val ask: StateFlow<AskUi> = _ask.asStateFlow()
    private var askJob: Job? = null

    /** Adds [place] as a clock; false when it has no time zone or is already there. */
    fun add(place: Place): Boolean = Clock.of(place)?.let(repo::add) ?: false

    fun remove(clock: Clock) = repo.remove(clock.id)

    fun move(from: Int, to: Int) = repo.move(from, to)

    fun ask(question: String) {
        val q = question.trim().take(TimeAskPrompt.MAX_QUESTION)
        if (q.isEmpty()) return
        askJob?.cancel()
        _ask.value = AskUi.Working(q)
        askJob = viewModelScope.launch { _ask.value = answer(q) }
    }

    private suspend fun answer(q: String): AskUi {
        val gen = gemma?.takeIf { modelStatus.value is ModelStatus.Installed } ?: return AskUi.Failed(q, "Gemma isn't set up yet.")
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
        return when (call) {
            is TimeCall.TimeIn -> {
                val there = resolve(call.place, zone, saved) ?: return notFound(q, call.place)
                val t = now.atZone(there.zone)
                val text = if (there.isMe) "It's ${clockText(t)} here." else "It's ${clockText(t)} on ${weekday(t)} in ${there.label}."
                AskUi.Answer(q, text, ConverterRequest(null, 0, null))
            }
            is TimeCall.Convert -> {
                val from = resolve(call.from, zone, saved) ?: return notFound(q, call.from)
                val to = resolve(call.to, zone, saved) ?: return notFound(q, call.to)
                val day = now.atZone(from.zone).toLocalDate().plusDays(call.day.toLong())
                val at = convertTime(call.time, day, from.zone, from.zone)
                val there = at.withZoneSameInstant(to.zone)
                val fromText = if (from.isMe) "At ${clockText(at)} on ${weekday(at)} your time" else "At ${clockText(at)} on ${weekday(at)} in ${from.label}"
                val toDay = if (there.toLocalDate() != at.toLocalDate()) " on ${weekday(there)}" else ""
                val toText = if (to.isMe) "it's ${clockText(there)}$toDay for you" else "it's ${clockText(there)}$toDay in ${to.label}"
                // The converter can show it when the time is in a place it has: your phone or one of the clocks.
                val request = if (from.isMe || from.clockId != null) {
                    ConverterRequest(call.time.hour * 60 + call.time.minute, call.day, from.clockId)
                } else null
                AskUi.Answer(q, "$fromText, $toText.", request)
            }
        }
    }

    private data class Resolved(val label: String, val zone: ZoneId, val clockId: String? = null, val isMe: Boolean = false)

    /**
     * "me" is the phone; then a clock by its name, or by its region or country ("Romania" is the Bucharest clock);
     * then the first place search result with a time zone. Null when nothing matches (or the search fails).
     */
    private suspend fun resolve(name: String, zone: ZoneId, saved: List<Clock>): Resolved? {
        if (name == TimeCall.ME || name.equals(cityOf(zone), ignoreCase = true)) return Resolved(cityOf(zone), zone, isMe = true)
        saved.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return Resolved(it.name, it.zone!!, it.id) }
        saved.firstOrNull { c -> c.detail?.split(", ")?.any { it.equals(name, ignoreCase = true) } == true }
            ?.let { return Resolved(name.replaceFirstChar { it.uppercase() }, it.zone!!, it.id) }
        val found = try {
            places?.searchPlaces(name).orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        val place = found.firstNotNullOfOrNull { p -> Clock.of(p)?.let { p to it } } ?: return null
        return Resolved(place.first.name, place.second.zone!!)
    }

    private fun notFound(q: String, place: String) = AskUi.Failed(q, "Couldn't find “$place”. Try a city name.")

    private fun clockText(t: ZonedDateTime) = formatClock(t.toLocalDateTime())

    private fun weekday(t: ZonedDateTime) = t.format(DateTimeFormatter.ofPattern("EEEE", Locale.US))

    private companion object {
        const val NOT_UNDERSTOOD = "Gemma couldn't work that one out. Try the converter above."
    }
}
