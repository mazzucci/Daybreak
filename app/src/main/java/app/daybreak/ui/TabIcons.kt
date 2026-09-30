package app.daybreak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** The Weather tab's icon: the app's own partly-cloudy glyph, in the bar's content colour like the Material icons. */
@Composable
fun WeatherTabIcon() {
    WeatherIcon(code = 2, night = false, palette = monoPalette(LocalContentColor.current), contentDescription = null)
}

/** The Clocks tab's icon: a clock face, filled when selected with the hands cut out, a ring otherwise. */
@Composable
fun ClocksTabIcon(selected: Boolean) {
    val color = LocalContentColor.current
    val hands = if (selected) MaterialTheme.colorScheme.secondaryContainer else color
    Canvas(Modifier.size(24.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = 9.dp.toPx()
        val w = 2.dp.toPx()
        if (selected) drawCircle(color, r, c) else drawCircle(color, r - w / 2, c, style = Stroke(w))
        drawLine(hands, c, Offset(c.x, c.y - 5.dp.toPx()), w, StrokeCap.Round)
        drawLine(hands, c, Offset(c.x + 5.6.dp.toPx(), c.y + 3.25.dp.toPx()), w, StrokeCap.Round)
    }
}
