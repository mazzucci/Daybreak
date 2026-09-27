package com.mazzucci.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Gets a fresh location if possible, falling back to the last known one. Caller must hold location permission. */
@SuppressLint("MissingPermission")
suspend fun getLocation(context: Context): Location? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .filter { lm.isProviderEnabled(it) }

    for (provider in providers) {
        val fresh = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    lm, provider, signal, ContextCompat.getMainExecutor(context)
                ) { cont.resume(it) }
            }
        }
        if (fresh != null) return fresh
    }

    return lm.getProviders(true)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
}

/** Best-effort "City, State" label for a location; null if the device can't resolve it. */
@Suppress("DEPRECATION")
suspend fun placeName(context: Context, loc: Location): String? = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext null
    runCatching {
        Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
            ?.firstOrNull()
            ?.let { listOfNotNull(it.locality ?: it.subAdminArea, it.adminArea).joinToString(", ") }
            ?.ifBlank { null }
    }.getOrNull()
}
