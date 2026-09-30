package app.daybreak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.daybreak.domain.Activity
import app.daybreak.domain.ActivityPlan
import app.daybreak.domain.ActivityScorer
import app.daybreak.domain.TempUnit
import app.daybreak.domain.describeBlockers
import app.daybreak.domain.Limit
import app.daybreak.domain.describeWindow
import app.daybreak.domain.describeWindowSpoken
import app.daybreak.domain.formatHour
import app.daybreak.domain.formatWindow
import java.time.LocalDate

/**
 * "Best time to ride": the best window in the next day or so for the chosen activity, what it's like, and a bar per
 * hour showing how good each one is, with the chosen window marked underneath. The card keeps the same three-line
 * shape when there's no window ("Not in the next 24 hours", then what's in the way), so it's recognisable at a glance
 * in either state.
 */
@Composable
fun ActivityCard(plan: ActivityPlan, unit: TempUnit, today: LocalDate, modifier: Modifier = Modifier) {
    val best = plan.best
    val verb = plan.activity.verb
    val eyebrow = "Best time to $verb"
    val headline = if (best != null) {
        val startsNow = best.start == plan.hours.first().hour.time
        val day = if (startsNow || best.start.toLocalDate() == today) "" else "Tomorrow "
        "$day${formatWindow(best, startsNow)}"
    } else {
        val n = plan.hours.size
        if (n == 1) "Not in the next hour" else "Not in the next $n hours"
    }
    // A fragment like the window's "Dry · light wind · 17–21°": "Because of rain", "Because it's dark, with rain".
    val detail = when {
        best != null -> describeWindow(best, unit)
        plan.blockers.isEmpty() -> "Not great conditions"
        plan.blockers.first() == Limit.DARK -> "Because it's dark" +
            plan.blockers.drop(1).takeIf { it.isNotEmpty() }?.let { ", with ${describeBlockers(it).replaceFirstChar { c -> c.lowercase() }}" }.orEmpty()
        else -> "Because of ${describeBlockers(plan.blockers).replaceFirstChar { it.lowercase() }}"
    }
    // One announcement for the whole card: pauses instead of separators, "to" for ranges, both temperature units.
    val spokenDetail = if (best != null) describeWindowSpoken(best, unit) else detail
    val spoken = "$eyebrow: ${headline.replaceFirstChar { it.lowercase() }.replace("–", " to ")}. $spokenDetail."
    Card(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActivityGlyph(
                    plan.activity,
                    if (best != null) MaterialTheme.weatherColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    Modifier.size(36.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(headline, style = MaterialTheme.typography.titleLarge)
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            ScoreBars(plan)
        }
    }
}

/**
 * One bar per hour, height and colour by score (green good, amber fair, grey poor), so a second decent spell
 * shows as well as the chosen one. The chosen window gets a line under its bars: an explicit marker rather than
 * dimming everything else, which would make the other hours look worse than they are.
 */
@Composable
private fun ScoreBars(plan: ActivityPlan, barArea: Dp = 32.dp) {
    val good = MaterialTheme.weatherColors.success
    val fair = MaterialTheme.weatherColors.sun
    val poor = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier.fillMaxWidth().height(barArea),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        plan.hours.forEach { h ->
            val color = when {
                h.score >= ActivityScorer.GOOD -> good
                h.score >= 40 -> fair
                else -> poor
            }
            val fraction = 0.18f + 0.82f * h.score / 100f
            Box(
                Modifier
                    .weight(1f)
                    .height(barArea * fraction)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(color),
            )
        }
    }
    val best = plan.best
    if (best != null) {
        // Windows are runs of consecutive hours, so the marker is one span. Weights ignore the 2dp gaps between
        // bars, which puts its ends within a couple of dp of the bars' edges.
        val first = plan.hours.indexOf(best.hours.first())
        val count = best.hours.size
        val after = plan.hours.size - first - count
        Spacer(Modifier.height(3.dp))
        Row(Modifier.fillMaxWidth()) {
            if (first > 0) Spacer(Modifier.weight(first.toFloat()))
            Box(Modifier.weight(count.toFloat()).height(3.dp).clip(CircleShape).background(good))
            if (after > 0) Spacer(Modifier.weight(after.toFloat()))
        }
    }
    Spacer(Modifier.height(4.dp))
    // Labels every six hours under the matching bar.
    Row(Modifier.fillMaxWidth()) {
        plan.hours.chunked(6).forEachIndexed { i, chunk ->
            Text(
                if (i == 0) "Now" else formatHour(chunk.first().hour.time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(chunk.size.toFloat()),
            )
        }
    }
}

/** A small line drawing in the style of the weather icons: a bicycle for cycling, footprints for running and walking. */
@Composable
private fun ActivityGlyph(activity: Activity, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            if (activity == Activity.CYCLING) bicycle(color) else footprints(color)
        }
    }
}

/** Two wheels and a diamond frame, with a saddle on the left and an angled handlebar on the right. */
private fun DrawScope.bicycle(color: Color) {
    val s = size.minDimension
    val width = s * 0.075f
    fun p(x: Float, y: Float) = Offset(s * x, s * y)
    fun line(a: Offset, b: Offset) = drawLine(color, a, b, width, StrokeCap.Round)
    val back = p(0.23f, 0.71f)
    val front = p(0.77f, 0.71f)
    val crank = p(0.50f, 0.74f)
    val seat = p(0.36f, 0.36f)
    val head = p(0.66f, 0.36f)
    drawCircle(color, s * 0.21f, back, style = Stroke(width))
    drawCircle(color, s * 0.21f, front, style = Stroke(width))
    line(back, seat); line(seat, crank); line(back, crank) // rear triangle
    line(seat, head); line(head, crank); line(head, front) // top tube, down tube, fork
    line(seat, p(0.34f, 0.26f)); line(p(0.26f, 0.26f), p(0.42f, 0.26f)) // seat post and saddle
    line(head, p(0.70f, 0.26f)); line(p(0.64f, 0.29f), p(0.80f, 0.24f)) // stem and handlebar
}

/** Two footprints mid-stride, each a sole and a heel, toes turned slightly outward. */
private fun DrawScope.footprints(color: Color) {
    val s = size.minDimension
    listOf(Offset(s * 0.32f, s * 0.62f) to -14f, Offset(s * 0.68f, s * 0.38f) to 14f).forEach { (c, angle) ->
        rotate(angle, pivot = c) {
            drawOval(color, Offset(c.x - s * 0.12f, c.y - s * 0.28f), Size(s * 0.24f, s * 0.32f))
            drawCircle(color, s * 0.08f, Offset(c.x, c.y + s * 0.17f))
        }
    }
}
