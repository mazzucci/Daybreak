package app.daybreak.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.daybreak.TestData
import app.daybreak.domain.Forecast
import app.daybreak.domain.Place
import app.daybreak.domain.TempUnit
import app.daybreak.domain.WidgetSnapshot
import app.daybreak.domain.formatDegrees
import app.daybreak.domain.formatTemp
import app.daybreak.domain.other
import app.daybreak.domain.widgetSnapshotOf
import app.daybreak.narration.NarrationInput
import app.daybreak.narration.TemplateNarrator
import java.util.Locale
import app.daybreak.ui.WeatherIcon
import app.daybreak.ui.heroGradient
import app.daybreak.ui.monoPalette
import app.daybreak.ui.skyOf
import org.junit.Rule
import org.junit.Test

/**
 * A DESIGN REFERENCE, NOT THE WIDGET. Paparazzi can't render Glance (layoutlib can't inflate its RemoteViews), so
 * this is a plain Compose copy of [WidgetContent]'s four layouts, kept in step by hand, to judge proportions at
 * each breakpoint and in dark mode. `widget_mock_4x2` is also the picker's previewImage on Android 11 and older
 * (copy it to res/drawable-nodpi/weather_widget_preview.png after `recordPaparazziDebug`).
 */
class WidgetMockScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, showSystemUi = false)

    private val day = snapshot(TestData.sanFrancisco, TestData.forecast(), TempUnit.F)
    private val night = snapshot(TestData.london, TestData.rainyNight(), TempUnit.C)

    private fun snapshot(place: Place, forecast: Forecast, unit: TempUnit) = widgetSnapshotOf(
        place, forecast, unit, TemplateNarrator(Locale.US).describe(NarrationInput(place.name, forecast, unit)), nowMillis = 0,
    )

    /** Realistic launcher sizes (a Pixel's 4×2 is about 300 × 180 dp) and the grid minimums the breakpoints are keyed to. */
    private val sizes = listOf(
        "4×2" to DpSize(300.dp, 180.dp), "4×2 min" to DpSize(250.dp, 110.dp),
        "4×1" to DpSize(300.dp, 76.dp), "4×1 min" to DpSize(250.dp, 40.dp),
        "2×2" to DpSize(150.dp, 180.dp), "2×2 min" to DpSize(110.dp, 110.dp),
        "2×1" to DpSize(150.dp, 76.dp), "2×1 min" to DpSize(110.dp, 40.dp),
    )

    @Test fun widgetMock4x2() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(screenWidth = dpToPx(300), screenHeight = dpToPx(180)))
        paparazzi.snapshot("widget_mock_4x2") { WidgetMock(day, DpSize(300.dp, 180.dp), dark = false) }
    }

    @Test fun widgetMockSizes() = gallery("widget_mock_sizes", day, dark = false, wallpaper = Color(0xFFE9EEF4))

    @Test fun widgetMockSizesNightDark() = gallery("widget_mock_sizes_night_dark", night, dark = true, wallpaper = Color(0xFF14181F))

    private fun gallery(name: String, s: WidgetSnapshot, dark: Boolean, wallpaper: Color) {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_5.copy(screenWidth = dpToPx(340), screenHeight = dpToPx(1180)))
        paparazzi.snapshot(name) {
            Column(
                Modifier.fillMaxSize().background(wallpaper).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for ((label, size) in sizes) {
                    Text(label, color = if (dark) Color.White else Color.Black, fontSize = 11.sp)
                    WidgetMock(s, size, dark)
                }
            }
        }
    }

    private fun dpToPx(dp: Int) = dp * DeviceConfig.PIXEL_5.density.dpiValue / 160
}

/** Mirrors WidgetContent: same breakpoints, type sizes, spacing and colours, in plain Compose. */
@Composable
private fun WidgetMock(s: WidgetSnapshot, size: DpSize, dark: Boolean) {
    val sky = heroGradient(skyOf(s.code), s.night, darkTheme = dark).first()
    val modifier = Modifier.size(size).clip(RoundedCornerShape(28.dp)).background(sky)
    when {
        size.width >= WidgetSize.Full.width && size.height >= WidgetSize.Full.height -> FullMock(s, modifier)
        size.height >= WidgetSize.Square.height -> SquareMock(s, modifier)
        size.width >= WidgetSize.Wide.width -> WideMock(s, modifier)
        else -> CompactMock(s, modifier)
    }
}

@Composable
private fun FullMock(s: WidgetSnapshot, modifier: Modifier) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.placeName, style = MockType.place, maxLines = 1, overflow = TextOverflow.Clip)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatTemp(s.tempC, s.unit), style = MockType.temperature(42), maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Text(formatTemp(s.tempC, s.unit.other()), Modifier.padding(bottom = 7.dp), style = MockType.temperatureOther(16), maxLines = 1)
                }
            }
            Spacer(Modifier.width(8.dp))
            WeatherIcon(s.code, s.night, monoPalette(Color.White), size = 48.dp, contentDescription = null)
        }
        Text(details(s), style = MockType.details, maxLines = 1, overflow = TextOverflow.Clip)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(8.dp))
        Text(s.summary, style = MockType.summary, maxLines = 3, overflow = TextOverflow.Clip)
    }
}

@Composable
private fun WideMock(s: WidgetSnapshot, modifier: Modifier) {
    Row(modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        WeatherIcon(s.code, s.night, monoPalette(Color.White), size = 30.dp, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Text(formatTemp(s.tempC, s.unit), style = MockType.temperature(28), maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.placeName, style = MockType.place, maxLines = 1, overflow = TextOverflow.Clip)
            Text(shortDetails(s), style = MockType.details, maxLines = 1, overflow = TextOverflow.Clip)
        }
    }
}

@Composable
private fun SquareMock(s: WidgetSnapshot, modifier: Modifier) {
    Column(
        modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WeatherIcon(s.code, s.night, monoPalette(Color.White), size = 36.dp, contentDescription = null)
        Spacer(Modifier.height(2.dp))
        Text(formatTemp(s.tempC, s.unit), style = MockType.temperature(28), maxLines = 1)
        Text(s.placeName, style = MockType.details, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

@Composable
private fun CompactMock(s: WidgetSnapshot, modifier: Modifier) {
    Row(
        modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeatherIcon(s.code, s.night, monoPalette(Color.White), size = 26.dp, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(formatTemp(s.tempC, s.unit), style = MockType.temperature(26), maxLines = 1)
    }
}

private fun details(s: WidgetSnapshot) =
    "High ${formatDegrees(s.highC, s.unit)} · Low ${formatDegrees(s.lowC, s.unit)} · Rain ${s.precipChance}%"

private fun shortDetails(s: WidgetSnapshot) =
    "↑${formatDegrees(s.highC, s.unit)} ↓${formatDegrees(s.lowC, s.unit)} · Rain ${s.precipChance}%"

private object MockType {
    private val white = Color.White
    private val soft = Color.White.copy(alpha = 0.85f)
    val place = TextStyle(color = white, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val details = TextStyle(color = soft, fontSize = 13.sp)
    val summary = TextStyle(color = white, fontSize = 14.sp)
    fun temperature(sp: Int) = TextStyle(color = white, fontSize = sp.sp)
    fun temperatureOther(sp: Int) = TextStyle(color = soft, fontSize = sp.sp)
}
