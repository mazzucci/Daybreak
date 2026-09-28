package com.mazzucci.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mazzucci.weather.domain.describeWeatherCode
import kotlin.math.cos
import kotlin.math.sin

/**
 * Colors for one icon. [sun] and [rain] are optional accents; when null the whole glyph is drawn in [cloud],
 * which is what the white-on-gradient hero wants.
 */
data class IconPalette(val cloud: Color, val sun: Color = cloud, val rain: Color = cloud)

/** Single-color palette (e.g. white on the hero gradient). */
fun monoPalette(color: Color) = IconPalette(color)

/** Themed palette for icons on cards: amber sun, blue rain, grey clouds. */
@Composable
fun cardIconPalette(): IconPalette {
    val c = MaterialTheme.weatherColors
    return IconPalette(cloud = c.cloud, sun = c.sun, rain = c.rain)
}

/**
 * Weather glyph for a WMO [code], drawn with Canvas so it looks identical on every device and in Paparazzi.
 * Everything is laid out on a 0..1 unit square and scaled to [size].
 */
@Composable
fun WeatherIcon(
    code: Int,
    night: Boolean,
    palette: IconPalette,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = describeWeatherCode(code),
) {
    val sky = skyOf(code)
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else Modifier
    Canvas(modifier.size(size).then(semantics)) {
        drawSky(sky, night, palette)
    }
}

private fun DrawScope.drawSky(sky: Sky, night: Boolean, p: IconPalette) {
    val s = size.minDimension
    when (sky) {
        Sky.CLEAR -> if (night) moon(Offset(s * 0.5f, s * 0.5f), s * 0.36f, p.sun) else sun(Offset(s * 0.5f, s * 0.5f), s * 0.24f, p.sun)
        Sky.PARTLY_CLOUDY -> {
            if (night) moon(Offset(s * 0.64f, s * 0.34f), s * 0.24f, p.sun)
            else sun(Offset(s * 0.64f, s * 0.34f), s * 0.16f, p.sun)
            cloud(Rect(s * 0.06f, s * 0.42f, s * 0.78f, s * 0.86f), p.cloud)
        }
        Sky.CLOUDY -> cloud(Rect(s * 0.08f, s * 0.24f, s * 0.92f, s * 0.78f), p.cloud)
        Sky.FOG -> {
            cloud(Rect(s * 0.12f, s * 0.14f, s * 0.88f, s * 0.6f), p.cloud)
            val stroke = s * 0.075f
            listOf(0.7f, 0.86f).forEachIndexed { i, y ->
                val inset = if (i == 0) 0.14f else 0.24f
                drawLine(p.cloud, Offset(s * inset, s * y), Offset(s * (1f - inset), s * y), stroke, StrokeCap.Round)
            }
        }
        Sky.DRIZZLE -> {
            cloud(Rect(s * 0.08f, s * 0.12f, s * 0.92f, s * 0.62f), p.cloud)
            drops(s, p.rain, length = 0.08f)
        }
        Sky.RAIN -> {
            cloud(Rect(s * 0.08f, s * 0.12f, s * 0.92f, s * 0.62f), p.cloud)
            drops(s, p.rain, length = 0.18f)
        }
        Sky.SNOW -> {
            cloud(Rect(s * 0.08f, s * 0.12f, s * 0.92f, s * 0.62f), p.cloud)
            listOf(0.28f, 0.5f, 0.72f).forEachIndexed { i, x ->
                flake(Offset(s * x, s * (if (i == 1) 0.86f else 0.78f)), s * 0.07f, p.rain)
            }
        }
        Sky.STORM -> {
            cloud(Rect(s * 0.08f, s * 0.1f, s * 0.92f, s * 0.6f), p.cloud)
            bolt(s, p.sun)
        }
        Sky.UNKNOWN -> drawCircle(p.cloud, s * 0.3f, Offset(s * 0.5f, s * 0.5f), style = Stroke(s * 0.08f))
    }
}

private fun DrawScope.sun(center: Offset, radius: Float, color: Color) {
    drawCircle(color, radius, center)
    val stroke = radius * 0.3f
    val inner = radius * 1.45f
    val outer = radius * 1.95f
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        drawLine(
            color,
            Offset(center.x + dx * inner, center.y + dy * inner),
            Offset(center.x + dx * outer, center.y + dy * outer),
            stroke,
            StrokeCap.Round,
        )
    }
}

private fun DrawScope.moon(center: Offset, radius: Float, color: Color) {
    val disc = Path().apply { addOval(Rect(center - Offset(radius, radius), Size(radius * 2, radius * 2))) }
    val biteCenter = center + Offset(radius * 0.55f, -radius * 0.35f)
    val biteRadius = radius * 0.8f
    val bite = Path().apply {
        addOval(Rect(biteCenter - Offset(biteRadius, biteRadius), Size(biteRadius * 2, biteRadius * 2)))
    }
    drawPath(Path.combine(PathOperation.Difference, disc, bite), color)
}

/** A cloud that fills [box]: a flat-bottomed base with two bumps on top. */
private fun DrawScope.cloud(box: Rect, color: Color) {
    val h = box.height
    val w = box.width
    val path = Path().apply {
        // Base: rounded bar across the bottom.
        val baseTop = box.top + h * 0.45f
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                Rect(box.left, baseTop, box.right, box.bottom),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius((box.bottom - baseTop) / 2f),
            )
        )
        // Big bump, off-centre to the right, and a smaller one to the left.
        val bigR = h * 0.5f
        addOval(Rect(Offset(box.left + w * 0.58f, box.top + bigR), bigR))
        val smallR = h * 0.36f
        addOval(Rect(Offset(box.left + w * 0.3f, box.top + h * 0.3f + smallR * 0.35f), smallR))
    }
    drawPath(path, color)
}

private fun DrawScope.drops(s: Float, color: Color, length: Float) {
    val stroke = s * 0.075f
    listOf(0.3f, 0.5f, 0.7f).forEachIndexed { i, x ->
        val top = s * (if (i == 1) 0.74f else 0.68f)
        drawLine(color, Offset(s * x, top), Offset(s * (x - 0.04f), top + s * length), stroke, StrokeCap.Round)
    }
}

private fun DrawScope.flake(center: Offset, radius: Float, color: Color) {
    val stroke = radius * 0.5f
    for (i in 0 until 3) {
        val a = Math.toRadians(i * 60.0)
        val d = Offset(cos(a).toFloat() * radius, sin(a).toFloat() * radius)
        drawLine(color, center - d, center + d, stroke, StrokeCap.Round)
    }
}

private fun DrawScope.bolt(s: Float, color: Color) {
    val path = Path().apply {
        moveTo(s * 0.54f, s * 0.5f)
        lineTo(s * 0.38f, s * 0.76f)
        lineTo(s * 0.5f, s * 0.76f)
        lineTo(s * 0.44f, s * 0.96f)
        lineTo(s * 0.64f, s * 0.68f)
        lineTo(s * 0.52f, s * 0.68f)
        lineTo(s * 0.6f, s * 0.5f)
        close()
    }
    drawPath(path, color)
}

/** The app's mark (sun peeking over a cloud), used on the empty state; the launcher icon draws the same shape. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val palette = cardIconPalette()
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        sun(Offset(s * 0.62f, s * 0.36f), s * 0.17f, palette.sun)
        cloud(Rect(s * 0.06f, s * 0.44f, s * 0.8f, s * 0.88f), palette.cloud)
    }
}
