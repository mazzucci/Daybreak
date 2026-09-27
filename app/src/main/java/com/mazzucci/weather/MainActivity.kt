package com.mazzucci.weather

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed interface UiState {
    data object NeedsPermission : UiState
    data object Loading : UiState
    data class Loaded(val weather: Weather, val place: String?) : UiState
    data class Failed(val message: String) : UiState
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { WeatherScreen() }
            }
        }
    }
}

@Composable
fun WeatherScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UiState>(UiState.Loading) }

    fun refresh() {
        state = UiState.Loading
        scope.launch {
            state = try {
                val loc = getLocation(context)
                if (loc == null) {
                    UiState.Failed("Couldn't get your location. Is location turned on?")
                } else {
                    UiState.Loaded(fetchWeather(loc.latitude, loc.longitude), placeName(context, loc))
                }
            } catch (e: Exception) {
                UiState.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) refresh() else state = UiState.NeedsPermission }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) refresh() else permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val s = state) {
            UiState.Loading -> CircularProgressIndicator()
            UiState.NeedsPermission -> {
                Text("Location permission is needed to show your local weather.")
                Spacer(Modifier.height(16.dp))
                Button({ permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
                    Text("Grant permission")
                }
            }
            is UiState.Failed -> {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Button(::refresh) { Text("Try again") }
            }
            is UiState.Loaded -> WeatherContent(s, ::refresh)
        }
    }
}

@Composable
private fun WeatherContent(s: UiState.Loaded, onRefresh: () -> Unit) {
    val w = s.weather
    val (f, c) = formatTemp(w.tempC)
    val (feelsF, feelsC) = formatTemp(w.feelsLikeC)

    Text(s.place ?: "Your location", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Text(w.description, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(24.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(f, fontSize = 72.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.width(20.dp))
        Text(c, fontSize = 72.sp, fontWeight = FontWeight.Light)
    }
    Spacer(Modifier.height(16.dp))
    Text("Feels like $feelsF / $feelsC")
    Text("Humidity ${w.humidity}%")
    Text("Wind ${(w.windKmh * 0.621371).roundToInt()} mph / ${w.windKmh.roundToInt()} km/h")
    Spacer(Modifier.height(32.dp))
    OutlinedButton(onRefresh) { Text("Refresh") }
}
