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
import com.mazzucci.weather.data.MemeRepository
import com.mazzucci.weather.narration.ValidatingNarrator
import com.mazzucci.weather.ui.WeatherApp
import com.mazzucci.weather.ui.WeatherTheme
import com.mazzucci.weather.ui.WeatherViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: WeatherViewModel by viewModels { weatherViewModelFactory(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge() // Android 15 enforces this at targetSdk 35; do the same on older versions.
        super.onCreate(savedInstanceState)
        setContent { WeatherTheme { WeatherApp(vm) } }
    }
}

/** Manual dependency wiring; the app is small enough not to need a DI framework. */
private fun weatherViewModelFactory(context: Context): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val store = SharedPrefsStore(context)
        val modelStore = GemmaModelStore.get(context)
        val gemma = GemmaNarrator(context, modelStore::installedFile) // one engine for the summary and the meme
        WeatherViewModel(
            api = OpenMeteoApi(UrlConnectionHttpClient()),
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
        )
    }
}

/** Preferences that must never leave the phone; excluded in res/xml/backup_rules.xml and data_extraction_rules.xml. */
private const val PRIVATE_PREFS = "private"

/** °F first in the few countries that use it, °C everywhere else. */
private fun defaultUnit(): TempUnit =
    if (Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")) TempUnit.F else TempUnit.C
