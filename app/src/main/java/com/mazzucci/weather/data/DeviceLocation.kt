package com.mazzucci.weather.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.mazzucci.weather.domain.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

interface LocationProvider {
    fun hasPermission(): Boolean

    /** The device's location as a [Place], or null if it can't be determined. Caller must hold permission. */
    suspend fun currentPlace(): Place?
}

/** Uses Android's built-in LocationManager and Geocoder (no Google Play Services). */
class DeviceLocationProvider(private val context: Context) : LocationProvider {

    override fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    override suspend fun currentPlace(): Place? {
        val loc = getLocation() ?: return null
        val address = address(loc)
        return Place(
            id = Place.CURRENT_LOCATION_ID,
            name = address?.let { it.locality ?: it.subAdminArea ?: it.adminArea }?.ifBlank { null } ?: "Current location",
            latitude = loc.latitude,
            longitude = loc.longitude,
            countryCode = address?.countryCode?.uppercase(),
        )
    }

    /** Gets a fresh location if possible, falling back to the last known one. */
    @SuppressLint("MissingPermission")
    private suspend fun getLocation(): Location? {
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

    /** Best-effort address (city, country) for a location; null if the device can't resolve it. */
    @Suppress("DEPRECATION")
    private suspend fun address(loc: Location): Address? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
        }.getOrNull()
    }
}
