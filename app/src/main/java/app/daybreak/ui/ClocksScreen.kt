package app.daybreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.daybreak.domain.Clock
import app.daybreak.domain.ClockFormat
import app.daybreak.domain.cityOf
import app.daybreak.domain.convertTime
import app.daybreak.domain.formatClock
import app.daybreak.domain.formatUtc
import app.daybreak.domain.isNightHour
import app.daybreak.domain.readClock
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Clocks: your phone's time as the page's headline, your saved clocks measured against it, and a converter that
 * shows one moment in every clock at once ("At 12:00 PM today in Los Angeles it's…"), which answers "when can I
 * call" without picking a second place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClocksScreen(
    clocks: List<Clock>,
    onAdd: () -> Unit,
    onRemove: (Clock) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    /** The phone's zone and the current moment; fixed in screenshot tests, otherwise the clock, ticking each minute. */
    here: ZoneId = ZoneId.systemDefault(),
    now: Instant? = null,
) {
    val ticking by produceState(Instant.now()) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = Instant.now()
        }
    }
    val moment = now ?: ticking
    var editing by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Clocks") },
                actions = {
                    if (clocks.isNotEmpty()) TextButton({ editing = !editing }) { Text(if (editing) "Done" else "Edit") }
                    IconButton(onAdd) { Icon(Icons.Default.Add, contentDescription = "Add a clock") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = PageMargin),
        ) {
            val mine = moment.atZone(here)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(formatClock(mine.toLocalDateTime()), style = MaterialTheme.typography.displayMedium)
                Text(
                    "${cityOf(here)} · your phone · ${formatUtc(mine.offset.totalSeconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            ClockList(clocks, moment, here, editing, onAdd, onRemove, onMove)
            Spacer(Modifier.height(24.dp))
            Text("Convert", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(12.dp))
            Converter(clocks, moment, here)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ClockList(
    clocks: List<Clock>,
    now: Instant,
    here: ZoneId,
    editing: Boolean,
    onAdd: () -> Unit,
    onRemove: (Clock) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        if (clocks.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No clocks yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Add the places you call, work with or miss.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onAdd) { Text("Add a clock") }
            }
            return@Card
        }
        Column(Modifier.padding(vertical = 4.dp)) {
            clocks.forEachIndexed { i, clock ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
                val zone = clock.zone ?: return@forEachIndexed
                val r = readClock(now, here, zone)
                val time = formatClock(r.time.toLocalDateTime())
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f).clearAndSetSemantics {
                            contentDescription = "${clock.name}, $time, ${r.day.lowercase()}, ${spokenOffset(r.offset)}, ${if (r.night) "night" else "day"}"
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DayNightDisc(r.night)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(clock.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${r.day} · ${r.offset}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!editing) {
                            Spacer(Modifier.width(12.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(time, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                                Text(r.utc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (editing) {
                        IconButton({ onMove(i, i - 1) }, enabled = i > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move ${clock.name} up")
                        }
                        IconButton({ onMove(i, i + 1) }, enabled = i < clocks.lastIndex) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move ${clock.name} down")
                        }
                        IconButton({ onRemove(clock) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove ${clock.name}", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

/** "+10 h" → "10 hours ahead", "−3½ h" → "3 and a half hours behind". */
private fun spokenOffset(offset: String): String {
    if (!offset.startsWith("+") && !offset.startsWith("−")) return offset
    val ahead = offset.startsWith("+")
    val amount = offset.drop(1).removeSuffix(" h")
        .replace("¼", " and a quarter").replace("½", " and a half").replace("¾", " and three quarters")
    return "$amount hours ${if (ahead) "ahead" else "behind"}"
}

/** A sun on amber for day, a moon on blue for night, like the other cards' glyph discs. */
@Composable
private fun DayNightDisc(night: Boolean) {
    val color = if (night) MaterialTheme.colorScheme.primary else MaterialTheme.weatherColors.sun
    Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        WeatherIcon(code = 0, night = night, palette = monoPalette(color), size = 22.dp, contentDescription = null)
    }
}

/**
 * The converter: pick a time, a day and where that time is (your phone or any clock), and every other clock
 * shows the same moment. Defaults to now, today, your phone.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun Converter(clocks: List<Clock>, now: Instant, here: ZoneId) {
    // Minutes past midnight, or null for "now"; 0 today, 1 tomorrow; the clock the time is in, null for the phone.
    var minutes by rememberSaveable { mutableStateOf<Int?>(null) }
    var dayOffset by rememberSaveable { mutableStateOf(0) }
    var fromId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var dayMenu by rememberSaveable { mutableStateOf(false) }
    var fromMenu by rememberSaveable { mutableStateOf(false) }

    val from = clocks.firstOrNull { it.id == fromId }
    val fromZone = from?.zone ?: here
    val fromName = from?.name ?: cityOf(here)
    val nowThere = now.atZone(fromZone)
    val time = minutes?.let { LocalTime.of(it / 60, it % 60) } ?: nowThere.toLocalTime().withSecond(0).withNano(0)
    val day = nowThere.toLocalDate().plusDays(dayOffset.toLong())
    val moment = convertTime(time, day, fromZone, fromZone)
    val timeLabel = formatClock(moment.toLocalDateTime())
    val dayLabel = if (dayOffset == 0) "Today" else "Tomorrow"

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { pickingTime = true }, label = { Text(timeLabel) }, trailingIcon = { DropIcon() })
                Box {
                    AssistChip(onClick = { dayMenu = true }, label = { Text(dayLabel) }, trailingIcon = { DropIcon() })
                    DropdownMenu(dayMenu, onDismissRequest = { dayMenu = false }) {
                        listOf("Today", "Tomorrow").forEachIndexed { i, label ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { dayOffset = i; dayMenu = false })
                        }
                    }
                }
                Box {
                    AssistChip(onClick = { fromMenu = true }, label = { Text("in $fromName") }, trailingIcon = { DropIcon() })
                    DropdownMenu(fromMenu, onDismissRequest = { fromMenu = false }) {
                        DropdownMenuItem(text = { Text("${cityOf(here)} (your phone)") }, onClick = { fromId = null; fromMenu = false })
                        clocks.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = { fromId = c.id; fromMenu = false })
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val weekday = moment.format(DateTimeFormatter.ofPattern("EEEE", Locale.US))
            Text(
                "At $timeLabel on $weekday in $fromName it's…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Every other clock, and your phone when the time is in a clock.
            val targets = buildList {
                if (from != null) add(Triple("phone", "${cityOf(here)} (you)", here))
                clocks.filter { it.id != fromId }.forEach { c -> c.zone?.let { add(Triple(c.id, c.name, it)) } }
            }
            if (targets.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Add a clock to convert to it.", style = MaterialTheme.typography.bodyMedium)
            }
            targets.forEach { (_, name, zone) ->
                val there = moment.withZoneSameInstant(zone)
                ConvertedRow(name, there, moment)
            }
        }
    }

    if (pickingTime) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = ClockFormat.use24Hour)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = {
                TextButton({ minutes = state.hour * 60 + state.minute; pickingTime = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton({ minutes = null; pickingTime = false }) { Text("Now") }
            },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun DropIcon() = Icon(Icons.Default.ArrowDropDown, contentDescription = null, Modifier.size(18.dp))

/** One clock at the converted moment: disc, name, time, and the weekday when it's another day there. */
@Composable
private fun ConvertedRow(name: String, there: ZonedDateTime, from: ZonedDateTime) {
    val time = formatClock(there.toLocalDateTime())
    val otherDay = there.toLocalDate() != from.toLocalDate()
    val dayNote = if (!otherDay) null else {
        val weekday = there.format(DateTimeFormatter.ofPattern("EEE", Locale.US))
        "$weekday · ${if (there.toLocalDate().isAfter(from.toLocalDate())) "next day" else "day before"}"
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).clearAndSetSemantics {
            contentDescription = listOfNotNull(name, time, dayNote).joinToString(", ")
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayNightDisc(isNightHour(there.hour))
        Spacer(Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(horizontalAlignment = Alignment.End) {
            Text(time, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (dayNote != null) Text(dayNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
