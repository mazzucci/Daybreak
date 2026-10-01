package app.daybreak.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * "Open sky" palette: cool blue-greys for surfaces, one saturated sky blue as the primary, warm amber for the
 * sun and for the Gemma attribution. All text/background pairs used below were checked for WCAG AA (>= 4.5:1).
 */

private val SkyBlue = Color(0xFF1565C0)
private val SkyBlueDeep = Color(0xFF0D47A1)
private val SkyBlueLight = Color(0xFF8AB4F8)
private val Amber = Color(0xFFB45309)
private val AmberLight = Color(0xFFFFB74D)

private val LightScheme = lightColorScheme(
    primary = SkyBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6FB),
    onPrimaryContainer = Color(0xFF0B2E5C),
    secondary = Color(0xFF4A5F70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E9F2),
    onSecondaryContainer = Color(0xFF1C2937),
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE6C2),
    onTertiaryContainer = Color(0xFF4A2A00),
    background = Color(0xFFF2F5F9),
    onBackground = Color(0xFF16202B),
    surface = Color(0xFFF2F5F9),
    onSurface = Color(0xFF16202B),
    surfaceVariant = Color(0xFFE4ECF5),
    onSurfaceVariant = Color(0xFF5A6878),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEAF0F7),
    surfaceContainerHighest = Color(0xFFE1E8F1),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainerLowest = Color.White,
    outline = Color(0xFF7F8EA3),
    outlineVariant = Color(0xFFD9E2EC),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFCE4E1),
    onErrorContainer = Color(0xFF5C1710),
)

private val DarkScheme = darkColorScheme(
    primary = SkyBlueLight,
    onPrimary = Color(0xFF0B2E5C),
    primaryContainer = Color(0xFF1F3B63),
    onPrimaryContainer = Color(0xFFD6E6FB),
    secondary = Color(0xFFAAB6C6),
    onSecondary = Color(0xFF1C2937),
    secondaryContainer = Color(0xFF2A3648),
    onSecondaryContainer = Color(0xFFE1E9F2),
    tertiary = AmberLight,
    onTertiary = Color(0xFF4A2A00),
    tertiaryContainer = Color(0xFF5C3A08),
    onTertiaryContainer = Color(0xFFFFE6C2),
    background = Color(0xFF0F1522),
    onBackground = Color(0xFFE4ECF5),
    surface = Color(0xFF0F1522),
    onSurface = Color(0xFFE4ECF5),
    surfaceVariant = Color(0xFF243044),
    onSurfaceVariant = Color(0xFFAAB6C6),
    surfaceContainer = Color(0xFF1A2233),
    surfaceContainerHigh = Color(0xFF212B3F),
    surfaceContainerHighest = Color(0xFF29344A),
    surfaceContainerLow = Color(0xFF141B2A),
    surfaceContainerLowest = Color(0xFF0B101B),
    outline = Color(0xFF7F8EA3),
    outlineVariant = Color(0xFF344056),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** App-specific colors that don't map onto Material roles. */
data class WeatherColors(
    /** Sun disc in icons. */
    val sun: Color,
    /** Cloud body in icons drawn on cards (on the hero they're white). */
    val cloud: Color,
    /** Rain/snow marks in icons and "rain chance" highlights. */
    val rain: Color,
    /** The Gemma attribution line. */
    val gemma: Color,
    /** Installed / success accents. */
    val success: Color,
    /** Text that asks for attention without being an error (a stale forecast). AA on the background. */
    val attention: Color,
    /** Skeleton blocks while loading. */
    val skeleton: Color,
    /** The "This week" strip's bars by tier: green, soft green, neutral, muted blue-grey. */
    val outlookGreat: Color,
    val outlookGood: Color,
    val outlookMeh: Color,
    val outlookStayIn: Color,
)

private val LightWeatherColors = WeatherColors(
    sun = Color(0xFFF59E0B),
    cloud = Color(0xFF7F8EA3),
    rain = Color(0xFF1E6FC0),
    gemma = Amber,
    success = Color(0xFF2E7D32),
    attention = Amber,
    skeleton = Color(0xFFDCE4EE),
    outlookGreat = Color(0xFF2E7D32),
    outlookGood = Color(0xFF8BC48E),
    outlookMeh = Color(0xFFA9B3BF),
    outlookStayIn = Color(0xFF6E86A8),
)

private val DarkWeatherColors = WeatherColors(
    sun = AmberLight,
    cloud = Color(0xFFAAB6C6),
    rain = SkyBlueLight,
    gemma = AmberLight,
    success = Color(0xFF5BC38A),
    attention = AmberLight,
    skeleton = Color(0xFF29344A),
    outlookGreat = Color(0xFF5BC38A),
    outlookGood = Color(0xFF3D7D5B),
    outlookMeh = Color(0xFF5A6474),
    outlookStayIn = Color(0xFF5F7A9F),
)

val LocalWeatherColors = staticCompositionLocalOf { LightWeatherColors }

/** Shortcut for the app-specific colors of the current theme. */
val MaterialTheme.weatherColors: WeatherColors
    @Composable get() = LocalWeatherColors.current

/** Whether the dark scheme is active, read from the theme itself rather than the system setting. */
val MaterialTheme.isDark: Boolean
    @Composable get() = colorScheme.background.luminance() < 0.5f

/** System sans-serif only: nothing to download, renders the same in Paparazzi and on devices. */
private val Sans = FontFamily.SansSerif

private val WeatherTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Light, fontSize = 104.sp, lineHeight = 104.sp, letterSpacing = (-4).sp),
    displayMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Light, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp),
)

private val WeatherShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun WeatherTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWeatherColors provides if (darkTheme) DarkWeatherColors else LightWeatherColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = WeatherTypography,
            shapes = WeatherShapes,
        ) {
            Surface(Modifier.fillMaxSize(), content = content)
        }
    }
}

/** Deep blue used for the launcher icon and the neutral hero backdrop; kept here so the two stay in sync. */
internal val BrandBlue = SkyBlueDeep
