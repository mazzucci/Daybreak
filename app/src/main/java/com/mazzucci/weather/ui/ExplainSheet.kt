package com.mazzucci.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mazzucci.weather.domain.Explanation
import com.mazzucci.weather.domain.Gauge
import com.mazzucci.weather.domain.Term
import com.mazzucci.weather.domain.formatClock
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Opens the explanation for a tapped term. Null where explanations aren't available (e.g. screenshots of parts). */
val LocalExplain = compositionLocalOf<((Term) -> Unit)?> { null }

/** The sheet's background. The gauges ring their markers in it, so they read as sitting on the sheet. */
internal val MaterialTheme.explainSheetColor: Color
    @Composable get() = colorScheme.surfaceContainerLow

/** The explanation sheet: title, today's value with a gauge, what it means today, and what the term means. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplainSheet(explanation: Explanation, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.explainSheetColor,
    ) {
        ExplainContent(explanation)
    }
}

/** The sheet's content on its own, so it can be rendered in screenshots. */
@Composable
fun ExplainContent(explanation: Explanation) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(explanation.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        // The value and its detail are one thing to a screen reader ("68°F (20°C)", "11 hours 54 minutes of daylight").
        Column(
            Modifier.semantics(mergeDescendants = true) {
                explanation.spoken?.let { contentDescription = it }
            },
        ) {
            Text(explanation.value, style = MaterialTheme.typography.displaySmall)
            if (explanation.detail != null) {
                Text(explanation.detail, style = MaterialTheme.typography.bodyMedium, color = muted)
            }
        }
        when (val gauge = explanation.gauge) {
            is Gauge.Scale -> {
                Spacer(Modifier.height(14.dp))
                ScaleBar(gauge)
            }
            is Gauge.Sun -> {
                Spacer(Modifier.height(8.dp))
                DaylightArc(gauge)
            }
            null -> Unit
        }
        Spacer(Modifier.height(14.dp))
        Text(explanation.now, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(20.dp))
        Text(
            "What it means",
            style = MaterialTheme.typography.labelLarge,
            color = muted,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Text(explanation.meaning, style = MaterialTheme.typography.bodyMedium, color = muted)
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/**
 * A gradient track from the low end to the high end of the scale with a dot where today's value sits. Same
 * track as the 7-day range bars, coloured by what the ends mean: blue-to-amber for sun, heat and wind, and
 * amber-to-blue for water. Hidden from screen readers: the text above already says the value and its category.
 */
@Composable
private fun ScaleBar(scale: Gauge.Scale) {
    val cool = MaterialTheme.weatherColors.rain
    val warm = MaterialTheme.weatherColors.sun
    val colors = when (scale.kind) {
        Gauge.Scale.Kind.INTENSITY -> listOf(cool, warm)
        Gauge.Scale.Kind.MOISTURE -> listOf(warm, cool)
    }
    val marker = MaterialTheme.colorScheme.onSurface
    val ring = MaterialTheme.explainSheetColor
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val r = 8.dp.toPx()
            val thickness = 6.dp.toPx()
            val y = size.height / 2
            // The track is inset by the marker's radius so the dot never clips at either end.
            val trackWidth = size.width - 2 * r
            drawRoundRect(
                brush = Brush.horizontalGradient(colors, startX = r, endX = r + trackWidth),
                topLeft = Offset(r, y - thickness / 2),
                size = Size(trackWidth, thickness),
                cornerRadius = CornerRadius(thickness / 2),
            )
            val x = r + trackWidth * scale.fraction.coerceIn(0f, 1f)
            drawCircle(ring, r, Offset(x, y))
            drawCircle(marker, r - 2.5.dp.toPx(), Offset(x, y))
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(scale.low, style = MaterialTheme.typography.labelSmall, color = muted)
            Text(scale.high, style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}

/**
 * The sun's path from sunrise (left) to sunset (right) over a horizon line, with the part already travelled in
 * the sun colour and the sun itself where it is now; at night the arc is empty. Hidden from screen readers, like
 * [ScaleBar]: the text carries the times and the countdown.
 */
@Composable
private fun DaylightArc(sun: Gauge.Sun) {
    val sunColor = MaterialTheme.weatherColors.sun
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val horizon = MaterialTheme.colorScheme.outlineVariant
    val ring = MaterialTheme.explainSheetColor
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val progress = sun.progress
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
        Canvas(Modifier.fillMaxWidth().height(76.dp)) {
            val sunRadius = 6.5.dp.toPx()
            val ringRadius = 9.dp.toPx()
            val stroke = 3.dp.toPx()
            // Room for the ringed sun disc at the ends and the top of the arc.
            val horizonY = size.height - ringRadius - 1.dp.toPx()
            val cx = size.width / 2
            val rx = cx - ringRadius - 4.dp.toPx()
            val ry = horizonY - ringRadius - 2.dp.toPx()
            val topLeft = Offset(cx - rx, horizonY - ry)
            val oval = Size(2 * rx, 2 * ry)
            drawLine(horizon, Offset(0f, horizonY), Offset(size.width, horizonY), strokeWidth = 1.dp.toPx())
            drawArc(track, 180f, 180f, useCenter = false, topLeft = topLeft, size = oval, style = Stroke(stroke, cap = StrokeCap.Round))
            if (progress != null) {
                drawArc(sunColor, 180f, 180f * progress, useCenter = false, topLeft = topLeft, size = oval, style = Stroke(stroke, cap = StrokeCap.Round))
                // Angles run clockwise from 3 o'clock with y down, so sunrise is at pi and noon at 1.5 pi.
                val angle = PI * (1 + progress)
                val at = Offset(cx + rx * cos(angle).toFloat(), horizonY + ry * sin(angle).toFloat())
                drawCircle(ring, ringRadius, at)
                drawCircle(sunColor, sunRadius, at)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Sunrise ${formatClock(sun.sunrise)}", style = MaterialTheme.typography.labelSmall, color = muted)
            Text("Sunset ${formatClock(sun.sunset)}", style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
}
