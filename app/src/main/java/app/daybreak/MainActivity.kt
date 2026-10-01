package app.daybreak

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.daybreak.data.DeviceLocationProvider
import app.daybreak.data.OpenMeteoApi
import app.daybreak.data.SavedPlacesRepository
import app.daybreak.data.SettingsRepository
import app.daybreak.data.SharedPrefsStore
import app.daybreak.data.UrlConnectionHttpClient
import app.daybreak.domain.AppSettings
import app.daybreak.domain.TempUnit
import app.daybreak.narration.GemmaModelStore
import app.daybreak.narration.GemmaNarrator
import app.daybreak.narration.MemeWriter
import app.daybreak.data.HolidayRepository
import app.daybreak.data.MemeRepository
import app.daybreak.data.WidgetStore
import app.daybreak.widget.GlanceWidgetPublisher
import app.daybreak.data.NagerHolidayApi
import app.daybreak.ui.WeatherApp
import app.daybreak.ui.ClocksViewModel
import app.daybreak.data.ClocksRepository
import app.daybreak.data.HabitsRepository
import app.daybreak.ui.HabitsViewModel
import app.daybreak.domain.ClockFormat
import android.text.format.DateFormat
import app.daybreak.ui.WeatherTheme
import app.daybreak.ui.WeatherViewModel
import java.util.Locale
import java.io.File
import app.daybreak.data.ImageDiskCache
import app.daybreak.data.OnThisDayRepository
import app.daybreak.data.WebImageLoader
import app.daybreak.data.WikipediaOnThisDayApi
import app.daybreak.ui.OnThisDayViewModel

class MainActivity : ComponentActivity() {
    private val vm: WeatherViewModel by viewModels { weatherViewModelFactory(applicationContext) }
    private val clocksVm: ClocksViewModel by viewModels {
        viewModelFactory { initializer { ClocksViewModel(ClocksRepository(SharedPrefsStore(applicationContext))) } }
    }
    // Habits are personal: kept in the private store, which isn't backed up.
    private val habitsVm: HabitsViewModel by viewModels {
        viewModelFactory { initializer { HabitsViewModel(HabitsRepository(SharedPrefsStore(applicationContext, PRIVATE_PREFS))) } }
    }

    // "On this day": one fetch a day, pictures cached on disk; nothing personal, so the ordinary store.
    private val onThisDayVm: OnThisDayViewModel by viewModels {
        viewModelFactory {
            initializer {
                OnThisDayViewModel(
                    OnThisDayRepository(WikipediaOnThisDayApi(UrlConnectionHttpClient(timeoutMs = 8_000)), SharedPrefsStore(applicationContext)),
                    images = WebImageLoader(ImageDiskCache(File(applicationContext.cacheDir, "images"))),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge() // Android 15 enforces this at targetSdk 35; do the same on older versions.
        super.onCreate(savedInstanceState)
        syncClockFormat(recreateOnChange = false) // about to compose anyway
        setContent { WeatherTheme { WeatherApp(vm, clocksVm, habitsVm, onThisDayVm) } }
    }

    override fun onResume() {
        super.onResume()
        syncClockFormat(recreateOnChange = true)
    }

    /**
     * Follows the phone's 12/24-hour setting. On a change (in onCreate too: a locale change recreates the activity
     * but keeps the ViewModel and its summaries) the summaries are rewritten; on resume the screen is redrawn.
     */
    private fun syncClockFormat(recreateOnChange: Boolean) {
        val use24Hour = DateFormat.is24HourFormat(this)
        if (use24Hour == ClockFormat.use24Hour) return
        ClockFormat.use24Hour = use24Hour
        vm.onClockFormatChanged()
        if (recreateOnChange) recreate() // every time on screen was formatted with the old clock
    }
}

/** Manual dependency wiring; the app is small enough not to need a DI framework. */
private fun weatherViewModelFactory(context: Context): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val store = SharedPrefsStore(context)
        val http = UrlConnectionHttpClient()
        val modelStore = GemmaModelStore.get(context)
        WeatherViewModel(
            api = OpenMeteoApi(http),
            places = SavedPlacesRepository(store),
            settingsRepo = SettingsRepository(
                store, AppSettings(primaryUnit = defaultUnit()),
                privateStore = SharedPrefsStore(context, PRIVATE_PREFS),
            ),
            location = DeviceLocationProvider(context),
            model = modelStore,
            memeWriter = MemeWriter(GemmaNarrator(context, modelStore::installedFile)),
            memes = MemeRepository(store),
            // Its own short timeout: holidays are a nice-to-have and shouldn't keep a page waiting.
            widget = GlanceWidgetPublisher(context.applicationContext, WidgetStore(SharedPrefsStore(context, WidgetStore.PREFS_FILE))),
            holidays = HolidayRepository(NagerHolidayApi(UrlConnectionHttpClient(timeoutMs = 5_000)), store),
        )
    }
}

/** Preferences that must never leave the phone; excluded in res/xml/backup_rules.xml and data_extraction_rules.xml. */
private const val PRIVATE_PREFS = "private"

/** °F first in the few countries that use it, °C everywhere else. */
private fun defaultUnit(): TempUnit =
    if (Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")) TempUnit.F else TempUnit.C
