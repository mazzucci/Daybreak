package com.mazzucci.weather

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mazzucci.weather.data.DeviceLocationProvider
import com.mazzucci.weather.data.OpenMeteoApi
import com.mazzucci.weather.data.SavedPlacesRepository
import com.mazzucci.weather.data.SettingsRepository
import com.mazzucci.weather.data.SharedPrefsStore
import com.mazzucci.weather.data.UrlConnectionHttpClient
import com.mazzucci.weather.domain.AppSettings
import com.mazzucci.weather.domain.TempUnit
import com.mazzucci.weather.narration.GemmaModelStore
import com.mazzucci.weather.narration.GemmaNarrator
import com.mazzucci.weather.narration.MemeWriter
import com.mazzucci.weather.data.HolidayRepository
import com.mazzucci.weather.data.MemeRepository
import com.mazzucci.weather.data.WidgetStore
import com.mazzucci.weather.widget.GlanceWidgetPublisher
import com.mazzucci.weather.data.NagerHolidayApi
import com.mazzucci.weather.narration.ValidatingNarrator
import com.mazzucci.weather.ui.WeatherApp
import com.mazzucci.weather.domain.ClockFormat
import android.text.format.DateFormat
import com.mazzucci.weather.ui.ActivityLogViewModel
import com.mazzucci.weather.data.HealthConnectExerciseSource
import java.io.IOException
import android.net.Uri
import com.mazzucci.weather.data.OpenMeteoRideWeather
import com.mazzucci.weather.ui.RideViewModel
import com.mazzucci.weather.ui.WeatherTheme
import com.mazzucci.weather.ui.WeatherViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: WeatherViewModel by viewModels { weatherViewModelFactory(applicationContext) }
    private val rideVm: RideViewModel by viewModels { rideViewModelFactory(applicationContext) }
    private val logVm: ActivityLogViewModel by viewModels {
        viewModelFactory {
            initializer {
                ActivityLogViewModel(
                    HealthConnectExerciseSource(applicationContext),
                    OpenMeteoRideWeather(UrlConnectionHttpClient(timeoutMs = 15_000)),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge() // Android 15 enforces this at targetSdk 35; do the same on older versions.
        super.onCreate(savedInstanceState)
        syncClockFormat(recreateOnChange = false) // about to compose anyway
        setContent { WeatherTheme { WeatherApp(vm, rideVm, logVm) } }
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
        val gemma = GemmaNarrator(context, modelStore::installedFile) // one engine for the summary and the meme
        WeatherViewModel(
            api = OpenMeteoApi(http),
            places = SavedPlacesRepository(store),
            settingsRepo = SettingsRepository(
                store, AppSettings(primaryUnit = defaultUnit()),
                privateStore = SharedPrefsStore(context, PRIVATE_PREFS),
            ),
            location = DeviceLocationProvider(context),
            model = modelStore,
            llm = ValidatingNarrator(gemma),
            memeWriter = MemeWriter(gemma),
            memes = MemeRepository(store),
            // Its own short timeout: holidays are a nice-to-have and shouldn't keep a page waiting.
            widget = GlanceWidgetPublisher(context.applicationContext, WidgetStore(SharedPrefsStore(context, WidgetStore.PREFS_FILE))),
            holidays = HolidayRepository(NagerHolidayApi(UrlConnectionHttpClient(timeoutMs = 5_000)), store),
        )
    }
}

/** Preferences that must never leave the phone; excluded in res/xml/backup_rules.xml and data_extraction_rules.xml. */
private const val PRIVATE_PREFS = "private"

private fun rideViewModelFactory(context: Context): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        RideViewModel(
            weather = OpenMeteoRideWeather(UrlConnectionHttpClient(timeoutMs = 15_000)),
            // Streamed and size-capped by the parser.
            openStream = { uri -> context.contentResolver.openInputStream(Uri.parse(uri)) ?: throw IOException("Couldn't open that file") },
        )
    }
}

/** °F first in the few countries that use it, °C everywhere else. */
private fun defaultUnit(): TempUnit =
    if (Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")) TempUnit.F else TempUnit.C
