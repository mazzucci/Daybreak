package app.daybreak.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable

/** The Weather tab's icon: the app's own partly-cloudy glyph, in the bar's content colour like the Material icons. */
@Composable
fun WeatherTabIcon() {
    WeatherIcon(code = 2, night = false, palette = monoPalette(LocalContentColor.current), contentDescription = null)
}
