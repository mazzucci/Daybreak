package app.daybreak.widget

import android.content.Context
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.daybreak.MainActivity
import app.daybreak.data.SharedPrefsStore
import app.daybreak.data.WidgetStore
import app.daybreak.domain.WidgetSnapshot
import app.daybreak.domain.describeWeatherCode
import app.daybreak.domain.formatBothUnits
import app.daybreak.domain.formatDegrees
import app.daybreak.domain.formatTemp
import app.daybreak.domain.other
import app.daybreak.ui.Sky
import app.daybreak.ui.heroGradient
import app.daybreak.ui.monoPalette
import app.daybreak.ui.skyOf
import app.daybreak.ui.weatherIconBitmap
import kotlin.math.roundToInt

/**
 * The home-screen widget: the first page's place, the temperature in both units, today's numbers and the summary
 * line, on the same sky colour as the app's hero. Tapping it opens the app. It only reads the saved
 * [WidgetSnapshot]. Each [WidgetSize] gets its own layout, so a 2×1 shows just the icon and temperature.
 */
class WeatherWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSize.all)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = WidgetStore(SharedPrefsStore(context, WidgetStore.PREFS_FILE))
        val first = store.load()
        // Glance keeps a session alive for a while and only runs provideGlance once per session, so the data is
        // read inside the composition, from the store's changes: a new snapshot (Gemma's line landing seconds
        // after the template) recomposes the widget instead of being ignored.
        val snapshots = snapshotChanges(context, store)
        provideContent {
            val snapshot by snapshots.collectAsState(initial = first)
            val icon = remember(snapshot?.code, snapshot?.night) {
                snapshot?.let {
                    // Rendered at the largest size it's shown at, on this screen's density, so it's never upscaled.
                    val px = (WidgetSize.IconLarge.value * context.resources.displayMetrics.density).roundToInt()
                    ImageProvider(weatherIconBitmap(it.code, it.night, monoPalette(Color.White), px).asAndroidBitmap())
                }
            }
            WidgetContent(snapshot, icon)
        }
    }

    /** Every change to the saved snapshot (including it being cleared), starting with the current one. */
    private fun snapshotChanges(context: Context, store: WidgetStore): Flow<WidgetSnapshot?> = callbackFlow {
        val prefs = context.getSharedPreferences(WidgetStore.PREFS_FILE, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(store.load()) }
        prefs.registerOnSharedPreferenceChangeListener(listener) // held strongly here: prefs keep only a weak ref
        trySend(store.load())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()
}

/**
 * The widget's breakpoints. Glance renders one layout per size and the launcher shows the largest that fits, so
 * [WidgetContent] only ever sees one of these as [LocalSize]. The minimums follow the launcher grid
 * (70 × cells − 30 dp): 2×1, 2×2, 4×1, and 3×2 and up.
 */
internal object WidgetSize {
    /** Icon and temperature. */
    val Compact = DpSize(110.dp, 40.dp)
    /** Icon over the temperature and the place. */
    val Square = DpSize(110.dp, 110.dp)
    /** One row: icon, temperature, then the place and today's numbers. */
    val Wide = DpSize(250.dp, 40.dp)
    /** Everything, with the summary. 200 rather than 180 wide so a 4×2 at its minimum isn't equidistant from [Wide]. */
    val Full = DpSize(200.dp, 110.dp)

    val all = setOf(Compact, Square, Wide, Full)
    val IconLarge = 48.dp
}

@Composable
internal fun WidgetContent(s: WidgetSnapshot?, icon: ImageProvider?) {
    val size = LocalSize.current
    val root = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .background(skyColor(s))
        .widgetCorners()
        .clickable(actionStartActivity<MainActivity>())
    when {
        s == null -> EmptyLayout(root, size)
        size.width >= WidgetSize.Full.width && size.height >= WidgetSize.Full.height -> FullLayout(s, icon, root)
        size.height >= WidgetSize.Square.height -> SquareLayout(s, icon, root)
        size.width >= WidgetSize.Wide.width -> WideLayout(s, icon, root)
        else -> CompactLayout(s, icon, root)
    }
}

/** 3×2 and up: the place, the temperature with the icon beside it, today's numbers, then the summary. */
@Composable
private fun FullLayout(s: WidgetSnapshot, icon: ImageProvider?, root: GlanceModifier) {
    Column(root.describe(s).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(s.placeName, style = Type.place, maxLines = 1)
                Temperature(s, primarySp = 42, otherSp = 16, otherLift = 7.dp)
            }
            if (icon != null) {
                Spacer(GlanceModifier.width(8.dp))
                Image(icon, contentDescription = null, modifier = GlanceModifier.size(WidgetSize.IconLarge))
            }
        }
        Text(details(s), style = Type.details, maxLines = 1)
        // The summary sits at the bottom and is what gets cut when a launcher gives less room than the numbers need.
        Spacer(GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.height(8.dp))
        Text(s.summary, style = Type.summary, maxLines = 3)
    }
}

/** 4×1: one row with the icon and temperature, then the place over today's numbers. */
@Composable
private fun WideLayout(s: WidgetSnapshot, icon: ImageProvider?, root: GlanceModifier) {
    Row(root.describe(s).padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = GlanceModifier.size(30.dp))
            Spacer(GlanceModifier.width(10.dp))
        }
        Text(formatTemp(s.tempC, s.unit), style = Type.temperature(28), maxLines = 1)
        Spacer(GlanceModifier.width(12.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(s.placeName, style = Type.place, maxLines = 1)
            Text(shortDetails(s), style = Type.details, maxLines = 1)
        }
    }
}

/** 2×2: the icon over the temperature and the place, centred. */
@Composable
private fun SquareLayout(s: WidgetSnapshot, icon: ImageProvider?, root: GlanceModifier) {
    Column(
        root.describe(s).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = GlanceModifier.size(36.dp))
            Spacer(GlanceModifier.height(2.dp))
        }
        Text(formatTemp(s.tempC, s.unit), style = Type.temperature(28), maxLines = 1)
        Text(s.placeName, style = Type.details, maxLines = 1)
    }
}

/** 2×1: just the icon and the temperature. */
@Composable
private fun CompactLayout(s: WidgetSnapshot, icon: ImageProvider?, root: GlanceModifier) {
    // Sized so "71°F" and the icon fit the 110dp minimum.
    Row(
        root.describe(s).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = GlanceModifier.size(26.dp))
            Spacer(GlanceModifier.width(6.dp))
        }
        Text(formatTemp(s.tempC, s.unit), style = Type.temperature(26), maxLines = 1)
    }
}

@Composable
private fun EmptyLayout(root: GlanceModifier, size: DpSize) {
    val full = size.height >= WidgetSize.Square.height
    Column(root.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (full) {
            Text("Weather", style = Type.title, maxLines = 1)
            Spacer(GlanceModifier.height(4.dp))
        }
        Text("Open the app to choose a place.", style = Type.details, maxLines = 2)
    }
}

/** The primary temperature with the other unit small beside it, lifted so the two sit on roughly the same baseline. */
@Composable
private fun Temperature(s: WidgetSnapshot, primarySp: Int, otherSp: Int, otherLift: Dp) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(formatTemp(s.tempC, s.unit), style = Type.temperature(primarySp), maxLines = 1)
        Spacer(GlanceModifier.width(6.dp))
        Text(
            formatTemp(s.tempC, s.unit.other()),
            style = Type.temperatureOther(otherSp),
            modifier = GlanceModifier.padding(bottom = otherLift),
            maxLines = 1,
        )
    }
}

/** "High 74° · Low 56° · Rain 60%": the same words as the hero's pills. */
private fun details(s: WidgetSnapshot) =
    "High ${formatDegrees(s.highC, s.unit)} · Low ${formatDegrees(s.lowC, s.unit)} · Rain ${s.precipChance}%"

/** "↑74° ↓56° · Rain 60%": the same numbers where a row has no room for the words. */
private fun shortDetails(s: WidgetSnapshot) =
    "↑${formatDegrees(s.highC, s.unit)} ↓${formatDegrees(s.lowC, s.unit)} · Rain ${s.precipChance}%"

/** One description for the whole widget, so a screen reader reads it as a single item rather than five fragments. */
private fun GlanceModifier.describe(s: WidgetSnapshot): GlanceModifier = semantics {
    contentDescription = buildString {
        append(s.placeName).append(", ")
        append(formatBothUnits(s.tempC, s.unit)).append(", ")
        append(describeWeatherCode(s.code)).append(". ")
        append("High ${formatDegrees(s.highC, s.unit)}, low ${formatDegrees(s.lowC, s.unit)}, ${s.precipChance}% chance of rain. ")
        append(s.summary)
    }
}

/**
 * The hero's top colour for the snapshot's sky, and the app's dark-theme version of it at night so the widget doesn't
 * glow on a dark home screen. Every one keeps white text readable.
 */
private fun skyColor(s: WidgetSnapshot?): ColorProvider {
    val sky = if (s == null) Sky.UNKNOWN else skyOf(s.code)
    val night = s?.night ?: false
    return androidx.glance.color.ColorProvider(
        heroGradient(sky, night, darkTheme = false).first(),
        heroGradient(sky, night, darkTheme = true).first(),
    )
}

/** The launcher's own widget radius on Android 12+; earlier versions can't round a RemoteViews' corners. */
private fun GlanceModifier.widgetCorners(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) cornerRadius(android.R.dimen.system_app_widget_background_radius) else this

/** The widget's type scale: white on the sky, with the secondary lines slightly softened, as in the hero. */
private object Type {
    private val white = ColorProvider(Color.White)
    private val soft = ColorProvider(Color.White.copy(alpha = 0.85f))

    val title = TextStyle(color = white, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    val place = TextStyle(color = white, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val details = TextStyle(color = soft, fontSize = 13.sp)
    val summary = TextStyle(color = white, fontSize = 14.sp)
    fun temperature(sp: Int) = TextStyle(color = white, fontSize = sp.sp)
    fun temperatureOther(sp: Int) = TextStyle(color = soft, fontSize = sp.sp)
}
