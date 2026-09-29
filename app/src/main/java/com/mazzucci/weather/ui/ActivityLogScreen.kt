package com.mazzucci.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mazzucci.weather.domain.Activity
import com.mazzucci.weather.domain.ExerciseKind
import com.mazzucci.weather.domain.ExerciseWeather
import com.mazzucci.weather.domain.LogWeek
import com.mazzucci.weather.domain.TempUnit
import com.mazzucci.weather.domain.WET_MM
import com.mazzucci.weather.domain.degrees
import com.mazzucci.weather.domain.describeLog
import com.mazzucci.weather.domain.describeWeatherCode
import com.mazzucci.weather.domain.formatBothUnits
import com.mazzucci.weather.domain.formatDegrees
import com.mazzucci.weather.domain.formatDuration
import com.mazzucci.weather.domain.formatHour
import com.mazzucci.weather.domain.formatRain
import com.mazzucci.weather.domain.groupByWeek
import com.mazzucci.weather.domain.spokenDuration
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The last 30 days of workouts from Health Connect, each with the weather it had. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(
    state: ActivityLogUi,
    unit: TempUnit,
    onConnect: () -> Unit,
    onInstall: () -> Unit,
    onBack: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Activity log") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            when (state) {
                ActivityLogUi.Idle, ActivityLogUi.Loading -> LoadingNote("Reading your workouts…")
                is ActivityLogUi.NeedsPermission -> Message(
                    title = "See the weather on your workouts",
                    text = if (state.denied) "Health Connect didn't give access. Open it to allow Weather to read your " +
                        "exercise sessions, then come back."
                    else "Connect Health Connect, where Garmin Connect and other watch apps save your workouts, to see " +
                        "how warm, wet and windy each one was.",
                    action = (if (state.denied) "Open Health Connect" else "Connect Health Connect") to onConnect,
                    note = "Weather only reads $READS from the last 30 days, never heart rate or routes. They stay on " +
                        "this phone: only $SENT go to Open-Meteo, to look up the weather.",
                )
                is ActivityLogUi.Unavailable -> if (state.installable) Message(
                    title = "Get Health Connect",
                    text = "On this version of Android, Health Connect is a separate app from Google Play, and it needs " +
                        "installing or updating. Then let your watch's app (e.g. Garmin Connect) share workouts with " +
                        "it, and come back.",
                    action = "Get Health Connect" to onInstall,
                ) else Message(
                    title = "Health Connect isn't available",
                    text = "It needs Android 9 or newer, and it isn't available in some work or private profiles.",
                    action = null,
                )
                is ActivityLogUi.Failed -> FailedCard("Couldn't read your workouts", state.message, "Try again", onConnect)
                is ActivityLogUi.Loaded -> if (state.items.isEmpty()) Message(
                    title = describeLog(state.items, ActivityLogViewModel.DAYS, unit),
                    text = "Rides, runs and walks that Garmin Connect or another app saves to Health Connect will show " +
                        "up here, with the weather each one had.",
                    action = null,
                ) else Log(state, unit, zone, today)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Laid out like the ride replay's intro: a glyph, a headline, what happens, one action, and the privacy note last. */
@Composable
private fun Message(title: String, text: String, action: Pair<String, () -> Unit>?, note: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ActivityGlyph(Activity.RUNNING, MaterialTheme.colorScheme.primary, Modifier.size(88.dp), glyph = 44.dp)
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(28.dp))
            Button(action.second, Modifier.fillMaxWidth().height(52.dp)) { Text(action.first) }
        }
        if (note != null) {
            Spacer(Modifier.height(16.dp))
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Laid out like the ride replay: a headline (the count), its subline (where the weather is for), the three
 * tiles, then the workouts a week at a time so a month of them has some shape ("This week" first). The tiles
 * carry their own labels for TalkBack; the rain and coldest ones say "—" when no workout has weather.
 */
@Composable
private fun Log(state: ActivityLogUi.Loaded, unit: TempUnit, zone: ZoneId, today: LocalDate) {
    val items = state.items
    val outdoors = items.count { !it.exercise.indoor }
    val known = items.any { it.minTempC != null }
    val wet = items.count { it.rainMm >= WET_MM }
    val coldest = items.mapNotNull { it.minTempC }.minOrNull()
    val total = items.fold(Duration.ZERO) { sum, it -> sum + it.exercise.duration }
    Text(describeLog(items, ActivityLogViewModel.DAYS, unit), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    if (outdoors > 0) {
        Text(
            when {
                state.placeName == null -> "No place yet, so no weather: add a place or allow your location first."
                state.weatherMissing -> "Couldn't get the weather for ${state.placeName} just now. Try again later."
                else -> "Weather at ${state.placeName}, your first page: workouts don't say where they happened. " +
                    "Workouts recorded in another time zone are marked as elsewhere."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(12.dp))
    // Equal heights, so a wrapped label on one tile doesn't leave it taller than its neighbours.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat("Time", formatDuration(total), spoken = spokenDuration(total), Modifier.weight(1f))
        Stat(
            "In the rain",
            when {
                !known -> "—"
                wet == 0 -> "None"
                else -> "$wet"
            },
            spoken = when {
                !known -> "unknown"
                wet == 0 -> "none"
                else -> "$wet of $outdoors outdoor workouts"
            },
            Modifier.weight(1f),
        )
        Stat(
            "Coldest",
            coldest?.let { formatDegrees(it, unit) } ?: "—",
            spoken = coldest?.let { formatBothUnits(it, unit) } ?: "unknown",
            Modifier.weight(1f),
        )
    }
    groupByWeek(items, today, zone).forEach { week ->
        WeekHeader(week)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(vertical = 4.dp)) {
                week.items.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    WorkoutRow(item, unit, zone)
                }
            }
        }
    }
}

/** The settings page's section title, with the week's total time on the right; one heading for TalkBack. */
@Composable
private fun WeekHeader(week: LogWeek) {
    val count = week.items.size
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp, start = 4.dp, end = 4.dp).semantics(mergeDescendants = true) {
            heading()
            contentDescription = "${week.label}: $count ${if (count == 1) "workout" else "workouts"}, ${spokenDuration(week.duration)}"
        },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(week.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(formatDuration(week.duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Glyph, title over date · start · length, and on the right the weather: the sky icon beside the temperature
 * range, with the rain under it in the rain colour. Indoor workouts and missing hours get a muted word instead.
 */
@Composable
private fun WorkoutRow(item: ExerciseWeather, unit: TempUnit, zone: ZoneId) {
    val e = item.exercise
    // In the workout's own local time when the watch app recorded it (a morning run in Tokyo stays a morning run).
    val start = e.zoneOffsetSeconds?.let { e.start.atOffset(java.time.ZoneOffset.ofTotalSeconds(it)).atZoneSameInstant(java.time.ZoneOffset.ofTotalSeconds(it)) }
        ?: e.start.atZone(zone)
    val date = start.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US))
    val hour = formatHour(start.toLocalDateTime())
    val title = e.title ?: e.kind.label
    val lo = item.minTempC?.let { degrees(it, unit) }
    val hi = (item.maxTempC ?: item.minTempC)?.let { degrees(it, unit) }
    val temps = if (lo == null || hi == null) null else if (lo == hi) "$lo°" else "$lo–$hi°"
    val rain = formatRain(item.rainMm, unit).takeIf { !e.indoor && item.rainMm >= WET_MM }
    val noWeather = when {
        e.indoor -> "Indoors"
        item.elsewhere -> "Elsewhere"
        temps == null -> "No weather data"
        else -> null
    }
    val spoken = listOfNotNull(
        title, date, hour, spokenDuration(e.duration),
        item.code?.takeIf { !e.indoor }?.let { describeWeatherCode(it) },
        temps?.let { if (lo == hi) "$lo°" else "$lo° to $hi°" },
        rain?.let { it.replace(" mm", " millimetres").replace(" in", " inches") + " of rain" },
        noWeather,
    ).joinToString(", ")
    // The weather takes the width it needs, up to two fifths of the row (enough for the icon and a range at 1.5x
    // on a narrow phone), so at a large font size "No weather data" wraps instead of squeezing the title.
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).clearAndSetSemantics { contentDescription = spoken }) {
        val weatherWidth = maxWidth * 0.42f
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ActivityGlyph(
                if (e.kind == ExerciseKind.RIDE) Activity.CYCLING else Activity.RUNNING,
                MaterialTheme.colorScheme.primary, Modifier.size(36.dp), glyph = 20.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    "$date · $hour · ${formatDuration(e.duration)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.widthIn(max = weatherWidth), horizontalAlignment = Alignment.End) {
                if (noWeather != null) {
                    Text(noWeather, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.code != null) {
                            WeatherIcon(item.code, night = false, cardIconPalette(), size = 22.dp, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(temps!!, style = MaterialTheme.typography.titleSmall, softWrap = false)
                    }
                    if (rain != null) {
                        Text(rain, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.weatherColors.rain)
                    }
                }
            }
        }
    }
}

/**
 * What Health Connect shows when the user asks why the app wants their data: the same headline-and-card layout as
 * the rest of the app, with the answer split into the four questions people actually have.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsRationaleScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Privacy") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("How Weather uses your workouts", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(4.dp))
            Text(
                "What the Activity log reads from Health Connect, and where it goes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    RATIONALE.forEachIndexed { i, (question, answer) ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(Modifier.padding(vertical = 12.dp).semantics(mergeDescendants = true) {}) {
                            Text(question, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(2.dp))
                            Text(answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** What's read, in the same words everywhere the app explains it. */
private const val READS = "exercise sessions (the kind of workout, its times and its title, as your watch app saved them)"

/** What's sent, likewise. */
private const val SENT = "the position of your first page's place (your current location, if that's on) and the range of dates"

private val RATIONALE = listOf(
    "What it reads" to "Exercise sessions from the last 30 days: $READS. They're used only to show the weather each " +
        "workout had, in the Activity log.",
    "What it never reads" to "Heart rate, routes, steps or any other health data. Weather never writes anything to " +
        "Health Connect.",
    "Where it goes" to "Nowhere: your workouts stay on this phone. To look up the weather, the app sends Open-Meteo " +
        "$SENT, not the workouts themselves.",
    "Changing your mind" to "You can remove the permission at any time in Health Connect.",
)
