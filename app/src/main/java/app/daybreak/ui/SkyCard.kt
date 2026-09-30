package app.daybreak.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.daybreak.domain.MoonPhase
import app.daybreak.domain.SkyCopy
import kotlin.math.abs

/**
 * "Tonight's sky": the moon as it is tonight, drawn lit to the right fraction, with its phase, the next full
 * moon (by its old name), a meteor shower when one's close, and whether the forecast says to look up.
 */
@Composable
fun SkyCard(phase: MoonPhase, copy: SkyCopy, modifier: Modifier = Modifier) {
    val lines = listOfNotNull(copy.moon, copy.meteors, copy.tonight)
    Card(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = (listOf(copy.phase) + lines).joinToString(". ") + "." },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Always on a night-sky disc, in both themes: it's the moon's own backdrop.
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(NightSky),
                contentAlignment = Alignment.Center,
            ) { MoonGlyph(phase, Modifier.size(40.dp)) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(copy.phase, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                lines.forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private val NightSky = Color(0xFF1B2640)
private val MoonLit = Color(0xFFF4EBD0)
private val MoonDark = Color(0xFF34405C)

/**
 * The moon with the lit part drawn to its phase: a lit half-disc on the growing side and a terminator (half an
 * ellipse) whose width is how far from half lit it is, curving into the lit half for a crescent and out of it
 * for a gibbous moon. Waning moons are the mirror image (lit on the left, as seen from the north).
 */
@Composable
fun MoonGlyph(phase: MoonPhase, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(MoonDark, r, c)
        val k = phase.illumination.toFloat()
        if (k < 0.01f) return@Canvas
        val w = r * (1 - 2 * k) // > 0: crescent, < 0: gibbous
        val lit = Path().apply {
            moveTo(c.x, c.y - r)
            // The lit half: top, round the right side, to the bottom.
            arcTo(Rect(c, r), -90f, 180f, false)
            // Back up the terminator: through the right of the ellipse for a crescent, the left for a gibbous.
            arcTo(Rect(c.x - abs(w), c.y - r, c.x + abs(w), c.y + r), 90f, if (w > 0) -180f else 180f, false)
            close()
        }
        scale(if (phase.waxing) 1f else -1f, 1f, pivot = c) { drawPath(lit, MoonLit) }
    }
}
