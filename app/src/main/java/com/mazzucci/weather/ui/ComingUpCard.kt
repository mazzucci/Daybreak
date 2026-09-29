package com.mazzucci.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.mazzucci.weather.domain.Countdown
import com.mazzucci.weather.domain.Forecast
import com.mazzucci.weather.domain.TempUnit
import com.mazzucci.weather.domain.describeWeatherCode
import com.mazzucci.weather.domain.formatCountdown
import com.mazzucci.weather.domain.formatDegrees
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Holidays, long weekends and the next season, soonest first, with the countdown on the right. When the date is
 * inside the forecast, the row also shows that day's weather, so "Monday off" comes with "and it'll be sunny".
 */
@Composable
fun ComingUpCard(items: List<Countdown>, forecast: Forecast, unit: TempUnit, modifier: Modifier = Modifier) {
    val today = forecast.current.time.toLocalDate()
    val palette = cardIconPalette()
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
                val day = forecast.days.firstOrNull { it.date == item.date }
                val date = item.date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US))
                val countdown = formatCountdown(item.daysFrom(today))
                val weather = day?.let { "${describeWeatherCode(it.code)}, high ${formatDegrees(it.highC, unit)}" }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clearAndSetSemantics {
                            contentDescription = listOfNotNull(item.title, date, item.note, countdown, weather).joinToString(", ")
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KindGlyph(item.kind)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(date, item.note).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(countdown, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (day != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                WeatherIcon(day.code, night = false, palette, size = 18.dp, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(formatDegrees(day.highC, unit), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A calendar page for holidays and long weekends, a leaf for seasons. */
@Composable
private fun KindGlyph(kind: Countdown.Kind) {
    val color = if (kind == Countdown.Kind.SEASON) MaterialTheme.weatherColors.success else MaterialTheme.colorScheme.primary
    Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(18.dp)) {
            val s = size.minDimension
            val stroke = Stroke(width = s * 0.1f)
            if (kind == Countdown.Kind.SEASON) {
                // A leaf: two arcs meeting at the tips, and a vein.
                drawArc(color, 200f, 140f, false, Offset(s * 0.05f, s * 0.15f), Size(s * 0.9f, s * 0.9f), style = stroke)
                drawArc(color, 20f, 140f, false, Offset(s * 0.05f, -s * 0.05f), Size(s * 0.9f, s * 0.9f), style = stroke)
                drawLine(color, Offset(s * 0.15f, s * 0.85f), Offset(s * 0.85f, s * 0.15f), stroke.width)
            } else {
                drawRoundRect(
                    color, Offset(s * 0.1f, s * 0.18f), Size(s * 0.8f, s * 0.72f),
                    androidx.compose.ui.geometry.CornerRadius(s * 0.12f), style = stroke,
                )
                drawLine(color, Offset(s * 0.1f, s * 0.4f), Offset(s * 0.9f, s * 0.4f), stroke.width)
                drawLine(color, Offset(s * 0.32f, s * 0.08f), Offset(s * 0.32f, s * 0.26f), stroke.width)
                drawLine(color, Offset(s * 0.68f, s * 0.08f), Offset(s * 0.68f, s * 0.26f), stroke.width)
                drawCircle(color, s * 0.08f, Offset(s * 0.5f, s * 0.64f))
            }
        }
    }
}

