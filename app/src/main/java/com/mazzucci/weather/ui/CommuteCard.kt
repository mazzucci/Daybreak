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

/** "Office day" or "Maybe work from home", with the reason, for the next weekday commute. */
@Composable
fun CommuteCard(advice: CommuteAdvice, activity: Activity, unit: TempUnit, today: LocalDate, modifier: Modifier = Modifier) {
    val (headline, detail) = describeCommute(advice, activity, unit, today)
    val accent = when (advice.verdict) {
        CommuteAdvice.Verdict.OFFICE -> MaterialTheme.weatherColors.success
        CommuteAdvice.Verdict.OFFICE_WITH_CAVEAT -> MaterialTheme.weatherColors.sun
        CommuteAdvice.Verdict.WORK_FROM_HOME -> MaterialTheme.colorScheme.primary
    }
    Card(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Commute. $headline. $detail" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                BuildingGlyph(home = advice.verdict == CommuteAdvice.Verdict.WORK_FROM_HOME, color = accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A little office block, or a house for working from home. */
@Composable
private fun BuildingGlyph(home: Boolean, color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.09f, join = StrokeJoin.Round)
        if (home) {
            val roof = Path().apply {
                moveTo(s * 0.1f, s * 0.48f); lineTo(s * 0.5f, s * 0.12f); lineTo(s * 0.9f, s * 0.48f)
            }
            drawPath(roof, color, style = stroke)
            drawRect(color, Offset(s * 0.22f, s * 0.44f), Size(s * 0.56f, s * 0.46f), style = stroke)
            drawRect(color, Offset(s * 0.42f, s * 0.62f), Size(s * 0.16f, s * 0.28f))
        } else {
            drawRect(color, Offset(s * 0.2f, s * 0.1f), Size(s * 0.6f, s * 0.8f), style = stroke)
            for (row in 0..2) for (col in 0..1) {
                drawRect(color, Offset(s * (0.32f + col * 0.22f), s * (0.22f + row * 0.18f)), Size(s * 0.12f, s * 0.1f))
            }
        }
    }
}
