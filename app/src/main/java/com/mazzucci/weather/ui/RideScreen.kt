package com.mazzucci.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mazzucci.weather.domain.Activity
import com.mazzucci.weather.domain.RideReplay
import com.mazzucci.weather.domain.TempUnit
import com.mazzucci.weather.domain.WindSide
import com.mazzucci.weather.domain.compassPoint
import com.mazzucci.weather.domain.describeReplay
import com.mazzucci.weather.domain.formatRain
import com.mazzucci.weather.domain.kmhToMph
import java.time.Duration
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Replay a recorded ride: pick a GPX file, see the route coloured by the wind it met, and a summary. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideScreen(state: RideUi, unit: TempUnit, onPickFile: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Ride replay") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            when (state) {
                RideUi.Idle -> Intro(onPickFile)
                is RideUi.Failed -> Failed(state.message, onPickFile)
                RideUi.Loading -> Loading()
                is RideUi.Loaded -> {
                    ReplayContent(state.replay, unit)
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(onPickFile, Modifier.fillMaxWidth().height(52.dp)) { Text("Replay another ride") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Laid out like the first-run page: a glyph, a headline, what happens, one action, and the privacy note last. */
@Composable
private fun Intro(onPickFile: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ActivityGlyph(Activity.CYCLING, MaterialTheme.colorScheme.primary, Modifier.size(88.dp), glyph = 44.dp)
        Spacer(Modifier.height(24.dp))
        Text("What was the weather on your ride?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick a GPX file of a ride or run (Garmin Connect, Strava and most apps can export one) to see where " +
                "you had a headwind, a crosswind or a tailwind, and how warm and wet it was.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onPickFile, Modifier.fillMaxWidth().height(52.dp)) { Text("Choose a GPX file") }
        Spacer(Modifier.height(16.dp))
        Text(
            "The file stays on this phone. Only the route's rough centre and its dates go to Open-Meteo, to look up the weather.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The same card the forecast page uses when it can't load: icon, title, the reason, one way forward. */
@Composable
private fun Failed(message: String, onPickFile: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Warning, contentDescription = null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            Text("Couldn't replay that ride", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onPickFile) { Text("Choose another file") }
        }
    }
}

@Composable
private fun Loading() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 64.dp).semantics(mergeDescendants = true) {
            contentDescription = "Looking up the weather on your ride"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            "Looking up the weather on your ride…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReplayContent(r: RideReplay, unit: TempUnit) {
    val track = r.track
    // Shown in the ride place's own time, not the phone's: a Tokyo ride reads the same wherever it's viewed.
    val start = track.start?.atOffset(ZoneOffset.ofTotalSeconds(r.utcOffsetSeconds))
    val title = track.name ?: "Your ride"
    val date = start?.format(DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.US))
    val km = track.distanceKm
    val distance = if (unit == TempUnit.F) String.format(Locale.US, "%.1f", kmhToMph(km)) else String.format(Locale.US, "%.1f", km)
    val time = formatRideTime(track.duration)
    val rain = if (r.rainMm >= 0.2) String.format(Locale.US, "%.1f", r.rainMm) else null
    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    if (date != null) Text(date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            RouteMap(r)
            Spacer(Modifier.height(12.dp))
            Legend(r)
        }
    }
    Spacer(Modifier.height(12.dp))
    // Equal heights, so a wrapped label on one tile doesn't leave it taller than its neighbours.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat(
            "Distance",
            if (unit == TempUnit.F) "$distance mi" else "$distance km",
            spoken = if (unit == TempUnit.F) "$distance miles" else "$distance kilometres",
            Modifier.weight(1f),
        )
        Stat("Time", time?.first ?: "—", spoken = time?.second ?: "unknown", Modifier.weight(1f))
        Stat(
            "Rain", rain?.let { formatRain(r.rainMm, unit) } ?: "None",
            spoken = rain?.let { formatRain(r.rainMm, unit).replace(" mm", " millimetres").replace(" in", " inches") } ?: "none",
            Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(describeReplay(r, unit), style = MaterialTheme.typography.bodyLarge)
}

/** "42 min" under an hour, "1h 05m" from then on; the second value is the same spelled out for TalkBack. */
private fun formatRideTime(d: Duration?): Pair<String, String>? {
    val minutes = d?.toMinutes() ?: return null
    val h = minutes / 60
    val m = minutes % 60
    val shown = if (h == 0L) "$m min" else "${h}h ${String.format(Locale.US, "%02d", m)}m"
    val spoken = listOfNotNull(
        h.takeIf { it > 0 }?.let { "$it hour${if (it == 1L) "" else "s"}" },
        "$m minute${if (m == 1L) "" else "s"}",
    ).joinToString(" ")
    return shown to spoken
}

/** Colours for the three wind sides; calm or unknown stretches are drawn in the outline colour. */
@Composable
private fun sideColor(side: WindSide?): Color {
    // Orange against blue, with neutral grey between: tells apart with red-green colour blindness too.
    val dark = MaterialTheme.isDark
    return when (side) {
        WindSide.HEAD -> if (dark) Color(0xFFFF922B) else Color(0xFFE8590C)
        WindSide.TAIL -> if (dark) Color(0xFF4DABF7) else Color(0xFF1C7ED6)
        WindSide.CROSS -> if (dark) Color(0xFFADB5BD) else Color(0xFF868E96)
        null -> MaterialTheme.colorScheme.outlineVariant
    }
}

/**
 * The route sketch under a strip with north on the left and the prevailing wind on the right, kept clear of
 * the route. One description for the lot: the shares are what the picture says, and the legend below repeats
 * the numbers on screen.
 */
@Composable
private fun RouteMap(r: RideReplay) {
    val head = (r.headShare * 100).roundToInt()
    val cross = (r.crossShare * 100).roundToInt()
    val tail = (r.tailShare * 100).roundToInt()
    val from = r.windFromDeg
    val spoken = "Route map: headwind $head%, crosswind $cross%, tailwind $tail%" +
        (from?.let { ". Wind from the ${compassName(it)}" } ?: "")
    Box(Modifier.fillMaxWidth().aspectRatio(1.3f).clearAndSetSemantics { contentDescription = spoken }) {
        RouteSketch(r, Modifier.fillMaxSize().padding(top = 24.dp))
        CompassMark("N", 0.0, Modifier.align(Alignment.TopStart))
        if (from != null) CompassMark("Wind from ${compassPoint(from)}", from + 180, Modifier.align(Alignment.TopEnd))
    }
}

/** A small arrow turned [degrees] clockwise from up, with a label: "N" pointing up, or the wind flying with it. */
@Composable
private fun CompassMark(label: String, degrees: Double, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(14.dp)) {
            val width = 1.6.dp.toPx()
            rotate(degrees.toFloat()) {
                val tip = Offset(center.x, 0f)
                drawLine(color, Offset(center.x, size.height), tip, width, StrokeCap.Round)
                drawLine(color, Offset(center.x - size.width * 0.32f, size.height * 0.34f), tip, width, StrokeCap.Round)
                drawLine(color, Offset(center.x + size.width * 0.32f, size.height * 0.34f), tip, width, StrokeCap.Round)
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/**
 * The route drawn to scale (equirectangular, fine for a ride's extent), north up, each stretch coloured by its
 * wind. Chevrons cut out of the line show the direction of travel, so a loop's head and tail sides make sense
 * against the wind badge; a blue ring marks the start and a dark square the finish. A ride that ends where
 * it began gets the start ring only.
 */
@Composable
private fun RouteSketch(r: RideReplay, modifier: Modifier) {
    val colors = r.segments.map { sideColor(it.side) }
    val startColor = MaterialTheme.colorScheme.primary
    val finishColor = MaterialTheme.colorScheme.onSurface
    val halo = MaterialTheme.colorScheme.surfaceContainer
    val points = r.track.points
    val midLat = Math.toRadians(points.map { it.latitude }.average())
    val xs = points.map { it.longitude * cos(midLat) }
    val ys = points.map { it.latitude }
    val minX = xs.min()
    val maxY = ys.max()
    val w = (xs.max() - minX).takeIf { it > 0 } ?: 1e-6
    val h = (maxY - ys.min()).takeIf { it > 0 } ?: 1e-6
    val loop = hypot(xs.last() - xs.first(), ys.last() - ys.first()) < 0.03 * hypot(w, h)
    Canvas(modifier) {
        val pad = 14.dp.toPx()
        val scale = minOf((size.width - 2 * pad) / w, (size.height - 2 * pad) / h)
        val ox = (size.width - w * scale) / 2
        val oy = (size.height - h * scale) / 2
        val at = points.indices.map { i -> Offset((ox + (xs[i] - minX) * scale).toFloat(), (oy + (maxY - ys[i]) * scale).toFloat()) }
        val stroke = 5.dp.toPx()
        r.segments.indices.forEach { i -> drawLine(colors[i], at[i], at[i + 1], stroke, StrokeCap.Round) }
        drawChevrons(at, halo)
        if (!loop) {
            val side = 10.dp.toPx()
            drawCircle(halo, 8.dp.toPx(), at.last())
            drawRect(finishColor, topLeft = at.last() - Offset(side / 2, side / 2), size = Size(side, side))
        }
        drawCircle(halo, 8.dp.toPx(), at.first())
        drawCircle(startColor, 6.dp.toPx(), at.first())
        drawCircle(halo, 2.5.dp.toPx(), at.first())
    }
}

/** Small arrowheads every 70dp or so along the route, in the card's colour so they read as cut out of the line. */
private fun DrawScope.drawChevrons(at: List<Offset>, color: Color) {
    val lengths = at.zipWithNext { a, b -> (b - a).getDistance() }
    val total = lengths.sum()
    if (total <= 0f) return
    val count = (total / 70.dp.toPx()).toInt().coerceIn(2, 10)
    val arm = 2.6.dp.toPx()
    val width = 1.6.dp.toPx()
    for (k in 1..count) {
        val target = total * k / (count + 1)
        var i = 0
        var before = 0f
        while (i < lengths.size - 1 && before + lengths[i] < target) before += lengths[i++]
        val length = lengths[i]
        if (length <= 0f) continue
        val dir = (at[i + 1] - at[i]) / length
        val tip = at[i] + dir * ((target - before).coerceIn(0f, length)) + dir * arm
        val perp = Offset(-dir.y, dir.x)
        drawLine(color, tip - dir * arm * 2f + perp * arm, tip, width, StrokeCap.Round)
        drawLine(color, tip - dir * arm * 2f - perp * arm, tip, width, StrokeCap.Round)
    }
}

private fun compassName(deg: Double): String = when (compassPoint(deg)) {
    "N" -> "north"
    "NE" -> "north-east"
    "E" -> "east"
    "SE" -> "south-east"
    "S" -> "south"
    "SW" -> "south-west"
    "W" -> "west"
    else -> "north-west"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(r: RideReplay) {
    // Spread across one line when the three fit, as on most phones; wrapped at large font sizes.
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendItem("Headwind", r.headShare, sideColor(WindSide.HEAD))
        LegendItem("Crosswind", r.crossShare, sideColor(WindSide.CROSS))
        LegendItem("Tailwind", r.tailShare, sideColor(WindSide.TAIL))
    }
}

@Composable
private fun LegendItem(label: String, share: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text("$label ${(share * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
    }
}

/** The forecast page's stat tile: muted label over a titleLarge value, one announcement for both. */
@Composable
private fun Stat(label: String, value: String, spoken: String, modifier: Modifier) {
    Card(
        modifier.fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = "$label $spoken" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        }
    }
}
