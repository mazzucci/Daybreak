package com.mazzucci.weather.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mazzucci.weather.domain.Activity
import com.mazzucci.weather.domain.ActivityPlan
import com.mazzucci.weather.domain.ActivityScorer
import com.mazzucci.weather.domain.TempUnit
import com.mazzucci.weather.domain.describeBlockers
import com.mazzucci.weather.domain.describeWindow
import com.mazzucci.weather.domain.formatHour
import com.mazzucci.weather.domain.formatWindow
import java.time.LocalDate

/**
 * "Best time to ride": the best window in the next day or so for the chosen activity, what it's like, and a bar per
 * hour showing how good each one is (the best window highlighted), or what's in the way when there's none.
 */
@Composable
fun ActivityCard(plan: ActivityPlan, unit: TempUnit, today: LocalDate, modifier: Modifier = Modifier) {
    val best = plan.best
    val verb = plan.activity.verb
    val headline = best?.let {
        val day = if (it.start.toLocalDate() == today) "" else "Tomorrow "
        "$day${formatWindow(it)}"
    }
    val detail = if (best != null) describeWindow(best, unit) else "${describeBlockers(plan.blockers)} for the next ${plan.hours.size} hours."
    val spoken = if (best != null) "Best time to $verb: $headline. $detail." else "No good time to $verb soon. $detail"
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
                    Text(
                        if (best != null) "Best time to $verb" else "No good time to $verb soon",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (headline != null) Text(headline, style = MaterialTheme.typography.titleLarge)
                    Text(detail, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(14.dp))
            ScoreBars(plan)
        }
    }
}

/** One bar per hour, height and colour by score; the best window's bars are solid, the rest faded. */
@Composable
private fun ScoreBars(plan: ActivityPlan, barArea: Dp = 32.dp) {
    val good = MaterialTheme.weatherColors.success
    val fair = MaterialTheme.weatherColors.sun
    val poor = MaterialTheme.colorScheme.outlineVariant
    val bestHours = plan.best?.hours?.map { it.hour.time }?.toSet().orEmpty()
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
                    .background(if (bestHours.isEmpty() || h.hour.time in bestHours) color else color.copy(alpha = 0.35f)),
            )
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
                modifier = Modifier.weight(chunk.size.toFloat()),
            )
        }
    }
}

/** A small line drawing: a bicycle for cycling, a pair of footprints for running and walking. */
@Composable
private fun ActivityGlyph(activity: Activity, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(22.dp)) {
            val s = size.minDimension
            val stroke = Stroke(width = s * 0.08f, cap = StrokeCap.Round)
            if (activity == Activity.CYCLING) {
                val r = s * 0.2f
                val back = Offset(s * 0.22f, s * 0.68f)
                val front = Offset(s * 0.78f, s * 0.68f)
                val pedal = Offset(s * 0.48f, s * 0.68f)
                val seat = Offset(s * 0.38f, s * 0.36f)
                val bars = Offset(s * 0.68f, s * 0.3f)
                drawCircle(color, r, back, style = stroke)
                drawCircle(color, r, front, style = stroke)
                drawLine(color, back, pedal, stroke.width, StrokeCap.Round)
                drawLine(color, pedal, seat, stroke.width, StrokeCap.Round)
                drawLine(color, seat, back, stroke.width, StrokeCap.Round)
                drawLine(color, seat, Offset(s * 0.64f, s * 0.4f), stroke.width, StrokeCap.Round)
                drawLine(color, pedal, Offset(s * 0.64f, s * 0.4f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.64f, s * 0.4f), front, stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.64f, s * 0.4f), bars, stroke.width, StrokeCap.Round)
                drawLine(color, Offset(s * 0.3f, s * 0.3f), Offset(s * 0.46f, s * 0.3f), stroke.width, StrokeCap.Round)
            } else {
                // Two offset footprints: a sole oval and a heel dot each.
                listOf(Offset(s * 0.34f, s * 0.58f), Offset(s * 0.66f, s * 0.36f)).forEach { c ->
                    drawOval(color, Offset(c.x - s * 0.1f, c.y - s * 0.2f), androidx.compose.ui.geometry.Size(s * 0.2f, s * 0.28f))
                    drawCircle(color, s * 0.07f, Offset(c.x, c.y + s * 0.18f))
                }
            }
        }
    }
}
