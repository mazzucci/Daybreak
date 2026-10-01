package app.daybreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.daybreak.domain.Clock
import app.daybreak.domain.ClockFormat
import app.daybreak.domain.cityOf
import app.daybreak.domain.convertTime
import app.daybreak.domain.dayNote
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
 * The current moment, ticking on each minute while the screen is started: it catches up at once when the app
 * comes back (a delay pauses while the phone sleeps), and doesn't wake the phone while it's away.
 */
@Composable
fun rememberMinuteClock(): Instant {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(Instant.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = Instant.now()
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    }
    return now
}

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
    initiallyEditing: Boolean = false,
) {
    val moment = now ?: rememberMinuteClock()
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    // Removing the last clock ends editing; the Done button goes with the list.
    LaunchedEffect(clocks.isEmpty()) { if (clocks.isEmpty()) editing = false }
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
            val time = formatClock(mine.toLocalDateTime())
            Spacer(Modifier.height(8.dp))
            Column(Modifier.clearAndSetSemantics { contentDescription = "$time in ${cityOf(here)}, your phone, ${spokenUtc(mine.offset.totalSeconds)}" }) {
                Text(time, style = MaterialTheme.typography.displayMedium)
                // Non-breaking spaces around the dots, so a wrapped line never ends on one.
                Text(
                    "${cityOf(here)} · your phone · ${formatUtc(mine.offset.totalSeconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            ClockList(clocks, moment, here, editing, onAdd, onRemove, onMove)
            // Nothing to convert to until there's a clock; the empty card already says how to add one.
            if (clocks.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("Convert", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(12.dp))
                Converter(clocks, moment, here)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** "UTC minus 7", "UTC plus 5:30", for screen readers (the minus sign isn't read). */
private fun spokenUtc(seconds: Int): String = formatUtc(seconds).replace("+", " plus ").replace("−", " minus ")

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
                ClockRow(
                    clock, now, here, editing,
                    onUp = if (i > 0) ({ onMove(i, i - 1) }) else null,
                    onDown = if (i < clocks.lastIndex) ({ onMove(i, i + 1) }) else null,
                    onRemove = { onRemove(clock) },
                )
            }
        }
    }
}

/**
 * One clock: disc, name over "Tomorrow · +10 h", and the time over its UTC offset. When the time would squeeze
 * the name (large fonts, narrow phones) it moves to its own line under the name. While editing, the time gives
 * way to move and remove buttons, which TalkBack also offers as actions on the row. A clock whose zone this
 * phone doesn't know still shows, so it can be removed.
 */
@Composable
private fun ClockRow(
    clock: Clock,
    now: Instant,
    here: ZoneId,
    editing: Boolean,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    val zone = clock.zone
    val r = zone?.let { readClock(now, here, it) }
    val time = r?.let { formatClock(it.time.toLocalDateTime()) }
    val detail = r?.let { "${it.day} · ${it.offset.replace(" h", " h").replace(" min", " min")}" }
        ?: "This phone doesn't know its time zone"
    val spoken = if (r == null) "${clock.name}, time zone unknown" else
        "${clock.name}, $time, ${r.day.lowercase()}, ${r.spoken}, ${spokenUtc(r.time.offset.totalSeconds)}, ${if (r.night) "night" else "day"}"
    val timeStyle = MaterialTheme.typography.headlineSmall
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        val measurer = rememberTextMeasurer()
        val timeWidth = with(LocalDensity.current) {
            time?.let { measurer.measure(it, timeStyle, maxLines = 1, softWrap = false).size.width.toDp() } ?: 0.dp
        }
        val stacked = !editing && maxWidth - 36.dp - 24.dp - timeWidth < 120.dp
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clearAndSetSemantics {
                    contentDescription = spoken
                    if (editing) {
                        customActions = listOfNotNull(
                            onUp?.let { f -> CustomAccessibilityAction("Move up") { f(); true } },
                            onDown?.let { f -> CustomAccessibilityAction("Move down") { f(); true } },
                            CustomAccessibilityAction("Remove") { onRemove(); true },
                        )
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (r != null) DayNightDisc(r.night) else UnknownDisc()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(clock.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (stacked && r != null && time != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(time, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                            Spacer(Modifier.width(8.dp))
                            Text(r.utc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (!editing && !stacked && r != null && time != null) {
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(time, style = timeStyle, maxLines = 1)
                        Text(r.utc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (editing) {
                IconButton({ onUp?.invoke() }, enabled = onUp != null) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move ${clock.name} up")
                }
                IconButton({ onDown?.invoke() }, enabled = onDown != null) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move ${clock.name} down")
                }
            }
            // An unknown zone can only be removed, so its button stays out of edit mode too.
            if (editing || r == null) {
                IconButton(onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove ${clock.name}", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** A sun on amber for day, a moon on blue for night, like the other cards' glyph discs. */
@Composable
private fun DayNightDisc(night: Boolean) {
    val color = if (night) MaterialTheme.colorScheme.primary else MaterialTheme.weatherColors.sun
    Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        WeatherIcon(code = 0, night = night, palette = monoPalette(color), size = 22.dp, contentDescription = null)
    }
}

@Composable
private fun UnknownDisc() {
    val color = MaterialTheme.colorScheme.error
    Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Warning, contentDescription = null, Modifier.size(20.dp), tint = color)
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

    // A removed clock (or one whose zone is unknown) can't be where the time is: back to your phone, and to now,
    // since the picked time was that clock's.
    val from = clocks.firstOrNull { it.id == fromId && it.zone != null }
    LaunchedEffect(from == null, fromId) {
        if (fromId != null && from == null) {
            fromId = null
            minutes = null
        }
    }
    val fromZone = from?.zone ?: here
    val phoneName = "${cityOf(here)} (your phone)"
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
                AssistChip(
                    onClick = { pickingTime = true },
                    label = { Text(timeLabel) },
                    trailingIcon = { DropIcon() },
                    modifier = Modifier.semantics { contentDescription = "Time, $timeLabel. Opens a time picker" },
                )
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
                        DropdownMenuItem(text = { Text(phoneName) }, onClick = { fromId = null; fromMenu = false })
                        clocks.filter { it.zone != null }.forEach { c ->
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
            if (from != null) ConvertedRow(phoneName, moment.withZoneSameInstant(here), moment)
            clocks.filter { it.id != from?.id }.forEach { c ->
                c.zone?.let { ConvertedRow(c.name, moment.withZoneSameInstant(it), moment) }
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
                Row {
                    // "Now" goes back to the ticking time; only offered once a time has been picked.
                    if (minutes != null) TextButton({ minutes = null; pickingTime = false }) { Text("Now") }
                    TextButton({ pickingTime = false }) { Text("Cancel") }
                }
            },
            // The app's display style is sized for the big temperature (104sp); the picker's digits need the standard one.
            text = {
                val digits = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                )
                MaterialTheme(typography = MaterialTheme.typography.copy(displayLarge = digits)) {
                    TimePicker(state)
                }
            },
        )
    }
}

@Composable
private fun DropIcon() = Icon(Icons.Default.ArrowDropDown, contentDescription = null, Modifier.size(18.dp))

/** One clock at the converted moment: disc, name, time, and "Tue · next day" when it's another day there. */
@Composable
private fun ConvertedRow(name: String, there: ZonedDateTime, from: ZonedDateTime) {
    val time = formatClock(there.toLocalDateTime())
    val note = dayNote(from.toLocalDate(), there.toLocalDate())?.let { n ->
        if (n.endsWith("day") && n.contains(' ')) "${there.format(DateTimeFormatter.ofPattern("EEE", Locale.US))} · $n" else n
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).clearAndSetSemantics {
            contentDescription = listOfNotNull(name, time, note?.replace(" ·", ",")).joinToString(", ")
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayNightDisc(isNightHour(there.hour))
        Spacer(Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(horizontalAlignment = Alignment.End) {
            Text(time, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
