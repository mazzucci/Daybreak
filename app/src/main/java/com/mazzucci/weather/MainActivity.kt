package com.mazzucci.weather

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.mazzucci.weather.narration.ValidatingNarrator
import com.mazzucci.weather.ui.WeatherApp
import com.mazzucci.weather.ui.WeatherTheme
import com.mazzucci.weather.ui.WeatherViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: WeatherViewModel by viewModels { weatherViewModelFactory(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WeatherTheme { WeatherApp(vm) } }
    }
}

/** Manual dependency wiring; the app is small enough not to need a DI framework. */
private fun weatherViewModelFactory(context: Context): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val store = SharedPrefsStore(context)
        val modelStore = GemmaModelStore(context)
        WeatherViewModel(
            api = OpenMeteoApi(UrlConnectionHttpClient()),
            places = SavedPlacesRepository(store),
            settingsRepo = SettingsRepository(store, AppSettings(primaryUnit = defaultUnit())),
            location = DeviceLocationProvider(context),
            model = modelStore,
            llm = ValidatingNarrator(GemmaNarrator(context, modelStore::installedFile)),
        )
    }
}

/** °F first in the few countries that use it, °C everywhere else. */
private fun defaultUnit(): TempUnit =
    if (Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")) TempUnit.F else TempUnit.C
