package app.daybreak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
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
        val r = 10.dp.toPx()
        val w = 2.dp.toPx()
        if (selected) drawCircle(color, r, c) else drawCircle(color, r - w / 2, c, style = Stroke(w))
        drawLine(hands, c, Offset(c.x, c.y - 5.dp.toPx()), w, StrokeCap.Round)
        drawLine(hands, c, Offset(c.x + 5.6.dp.toPx(), c.y + 3.25.dp.toPx()), w, StrokeCap.Round)
    }
}

/**
 * The Habits tab's icon: a check in a rounded square, drawn like the Clocks one: filled when selected with the
 * check cut out, an outline otherwise.
 */
@Composable
fun HabitsTabIcon(selected: Boolean) {
    val color = LocalContentColor.current
    val check = if (selected) MaterialTheme.colorScheme.secondaryContainer else color
    Canvas(Modifier.size(24.dp)) {
        val w = 2.dp.toPx()
        val side = 18.dp.toPx()
        val o = Offset((size.width - side) / 2, (size.height - side) / 2)
        val r = CornerRadius(4.5.dp.toPx())
        if (selected) {
            drawRoundRect(color, o, Size(side, side), r)
        } else {
            drawRoundRect(color, Offset(o.x + w / 2, o.y + w / 2), Size(side - w, side - w), r, style = Stroke(w))
        }
        val tick = Path().apply {
            moveTo(o.x + side * 0.27f, o.y + side * 0.52f)
            lineTo(o.x + side * 0.44f, o.y + side * 0.69f)
            lineTo(o.x + side * 0.75f, o.y + side * 0.35f)
        }
        drawPath(tick, check, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
