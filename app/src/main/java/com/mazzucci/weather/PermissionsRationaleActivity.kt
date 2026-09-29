package com.mazzucci.weather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mazzucci.weather.ui.PermissionsRationaleScreen
import com.mazzucci.weather.ui.WeatherTheme

/**
 * What Health Connect shows when the user asks why the app wants their data ("privacy policy" link on the
 * permission screen, and Settings → Permission usage on Android 14+).
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            WeatherTheme { PermissionsRationaleScreen(onBack = { finish() }) }
        }
    }
}
