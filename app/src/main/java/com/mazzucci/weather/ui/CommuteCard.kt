package com.mazzucci.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.mazzucci.weather.domain.Activity
import com.mazzucci.weather.domain.CommuteAdvice
import com.mazzucci.weather.domain.TempUnit
import com.mazzucci.weather.domain.describeCommute
import java.time.LocalDate

/**
 * "Office or home?": the verdict on the next weekday commute, in the same three-line shape as the "best time"
 * card so the two read as a pair: which commute this is about, the call, and the one line that justifies it.
 * The glyph carries the verdict too (an office block to go in, a house to stay), so the colour isn't doing the
 * work alone: green for a clear day, amber for a catch, and the calm primary blue for staying home, since
 * working from home is a suggestion, not an alarm.
 */
@Composable
fun CommuteCard(advice: CommuteAdvice, activity: Activity, unit: TempUnit, today: LocalDate, modifier: Modifier = Modifier) {
    val copy = describeCommute(advice, activity, unit, today)
    val accent = when (advice.verdict) {
        CommuteAdvice.Verdict.OFFICE -> MaterialTheme.weatherColors.success
        CommuteAdvice.Verdict.OFFICE_WITH_CAVEAT -> MaterialTheme.weatherColors.sun
        CommuteAdvice.Verdict.WORK_FROM_HOME -> MaterialTheme.colorScheme.primary
    }
    // One announcement for the whole card, like the "best time" card.
    val spoken = "${copy.eyebrow}: ${copy.headline.replaceFirstChar { it.lowercase() }}. ${copy.spokenDetail}."
    Card(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                BuildingGlyph(home = advice.verdict == CommuteAdvice.Verdict.WORK_FROM_HOME, color = accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(copy.eyebrow, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(copy.headline, style = MaterialTheme.typography.titleLarge)
                // Non-breaking spaces around the separator so a wrapped line never starts with a dot, and in "at 5 PM"
                // and "at 18:00" so the hour never splits from its "at" or its AM/PM.
                val shown = copy.detail
                    .replace(" · ", " · ")
                    .replace(Regex("at (\\d[\\d:]*)( [AP]M)?")) { m ->
                        "at\u00A0${m.groupValues[1]}${m.groupValues[2].replace(' ', '\u00A0')}"
                    }
                Text(
                    shown,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A little office block with lit windows and a door, or a house with a roof and a door, for working from home. */
@Composable
private fun BuildingGlyph(home: Boolean, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension
        val width = s * 0.09f
        val stroke = Stroke(width = width, join = StrokeJoin.Round, cap = StrokeCap.Round)
        if (home) {
            // Roof first, then walls hanging from its eaves, so the two meet cleanly at the corners.
            val roof = Path().apply {
                moveTo(s * 0.1f, s * 0.5f); lineTo(s * 0.5f, s * 0.14f); lineTo(s * 0.9f, s * 0.5f)
            }
            drawPath(roof, color, style = stroke)
            drawRect(color, Offset(s * 0.22f, s * 0.5f), Size(s * 0.56f, s * 0.4f), style = stroke)
            drawRect(color, Offset(s * 0.42f, s * 0.64f), Size(s * 0.16f, s * 0.26f))
        } else {
            drawRect(color, Offset(s * 0.2f, s * 0.1f), Size(s * 0.6f, s * 0.8f), style = stroke)
            for (row in 0..2) for (col in 0..1) {
                drawRect(color, Offset(s * (0.31f + col * 0.24f), s * (0.21f + row * 0.17f)), Size(s * 0.14f, s * 0.1f))
            }
            drawRect(color, Offset(s * 0.42f, s * 0.72f), Size(s * 0.16f, s * 0.18f))
        }
    }
}
