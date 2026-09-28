package com.mazzucci.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazzucci.weather.narration.Meme
import com.mazzucci.weather.narration.MemeMood
import com.mazzucci.weather.narration.NarrationSource

/**
 * Today's weather meme: a backdrop and big icon for the day's mood, with the caption in the classic white,
 * black-outlined capitals at the top and bottom. Everything is drawn in code; nothing is downloaded.
 */
@Composable
fun MemeCard(meme: Meme, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.large
    Column(modifier) {
        // Stacked rather than overlaid, so a caption that wraps pushes the icon down instead of covering it.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp)
                .clip(shape)
                .background(Brush.verticalGradient(memeGradient(meme.mood, MaterialTheme.isDark)))
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Weather meme: ${meme.top}. ${meme.bottom}."
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            MemeText(meme.top)
            WeatherIcon(
                moodCode(meme.mood), night = false, monoPalette(Color.White),
                Modifier.padding(vertical = 10.dp).alpha(0.9f), size = 96.dp, contentDescription = null,
            )
            MemeText(meme.bottom)
        }
        if (meme.source == NarrationSource.GEMMA) {
            Spacer(Modifier.height(8.dp))
            Text(
                "✦ Written by Gemma on this device",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.weatherColors.gemma,
            )
        }
    }
}

/** White capitals with a black outline, drawn as a stroke layer under a fill layer. */
@Composable
private fun MemeText(text: String, modifier: Modifier = Modifier) {
    val style = TextStyle(
        fontSize = 24.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.5.sp,
        textAlign = TextAlign.Center,
    )
    val upper = text.uppercase()
    Box(modifier.fillMaxWidth()) {
        Text(
            upper,
            Modifier.fillMaxWidth(),
            style = style.copy(color = Color.Black, drawStyle = Stroke(width = 7f, join = StrokeJoin.Round)),
        )
        Text(upper, Modifier.fillMaxWidth(), style = style.copy(color = Color.White))
    }
}

/** A representative WMO code, so the meme reuses the app's own weather icons. */
private fun moodCode(mood: MemeMood): Int = when (mood) {
    MemeMood.STORM -> 95
    MemeMood.SNOW, MemeMood.COLD -> 73
    MemeMood.RAIN -> 63
    MemeMood.HEAT, MemeMood.SUN -> 0
    MemeMood.FOG -> 45
    MemeMood.WIND, MemeMood.GLOOM -> 3
    MemeMood.MIXED -> 2
}

private fun memeGradient(mood: MemeMood, dark: Boolean): List<Color> = when (mood) {
    MemeMood.HEAT -> listOf(Color(0xFFF08C2E), Color(0xFFB8360B))
    MemeMood.COLD -> listOf(Color(0xFF5B8DBE), Color(0xFF26486E))
    else -> heroGradient(skyOf(moodCode(mood)), night = false, darkTheme = dark)
}
