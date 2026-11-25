package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtMs: Long
)

object KitchenLocationHelper {

    private const val CACHED_MAX_AGE_MS = 30_000L
    private const val CACHED_MAX_ACCURACY_METERS = 35f

    fun hasLocationPermission(context: Context): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(
        context: Context,
        timeoutMs: Long = 8000L
    ): Result<LocationSample> {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            return Result.failure(SecurityException("Location permission is required for attendance check-in."))
        }

        val locationManager = context.applicationContext
            .getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return Result.failure(IllegalStateException("Location service is unavailable on this device."))

        val permittedProviders = buildList {
            if (hasFine && isProviderEnabledSafely(locationManager, LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if ((hasFine || hasCoarse) && isProviderEnabledSafely(locationManager, LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }

        // Check recent cached location from permitted providers (GPS if fine, Network if fine/coarse)
        val cachedCandidates = buildList {
            if (hasFine) {
                getLastKnownLocationSafely(locationManager, LocationManager.GPS_PROVIDER)?.let(::add)
            }
            if (hasFine || hasCoarse) {
                getLastKnownLocationSafely(locationManager, LocationManager.NETWORK_PROVIDER)?.let(::add)
            }
        }

        val nowMs = System.currentTimeMillis()
        val recentAccurate = cachedCandidates
            .filter { loc ->
                val ageMs = nowMs - loc.time
                loc.time > 0L &&
                    ageMs in 0L until CACHED_MAX_AGE_MS &&
                    loc.hasAccuracy() &&
                    loc.accuracy <= CACHED_MAX_ACCURACY_METERS
            }
            .minByOrNull { it.accuracy }

        if (recentAccurate != null) {
            return Result.success(
                LocationSample(
                    latitude = recentAccurate.latitude,
                    longitude = recentAccurate.longitude,
                    accuracyMeters = recentAccurate.accuracy,
                    capturedAtMs = recentAccurate.time
                )
            )
        }

        if (permittedProviders.isEmpty()) {
            return Result.failure(
                IllegalStateException("Location services (GPS/Network) are turned off. Please enable Location.")
            )
        }

        return try {
            val freshLocation = withTimeoutOrNull(timeoutMs.coerceAtLeast(1000L)) {
                requestSingleFreshLocation(locationManager, permittedProviders)
            }
            if (freshLocation != null) {
                Result.success(
                    LocationSample(
                        latitude = freshLocation.latitude,
                        longitude = freshLocation.longitude,
                        accuracyMeters = if (freshLocation.hasAccuracy()) freshLocation.accuracy else 0f,
                        capturedAtMs = if (freshLocation.time > 0L) freshLocation.time else System.currentTimeMillis()
                    )
                )
            } else {
                Result.failure(IllegalStateException("Timed out waiting for GPS/Network location fix."))
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleFreshLocation(
        locationManager: LocationManager,
        providers: List<String>
    ): Location? = suspendCancellableCoroutine { cont ->
        val completed = AtomicBoolean(false)

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (completed.compareAndSet(false, true)) {
                    runCatching { locationManager.removeUpdates(this) }
                    if (cont.isActive) {
                        cont.resume(location)
                    }
                }
            }

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        cont.invokeOnCancellation {
            completed.set(true)
            runCatching { locationManager.removeUpdates(listener) }
        }

        var registeredCount = 0
        for (provider in providers) {
            try {
                locationManager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
                registeredCount++
            } catch (_: SecurityException) {
                // Ignore provider if permission state changed concurrently
            } catch (_: IllegalArgumentException) {
                // Ignore unsupported provider
            }
        }

        if (registeredCount == 0 && completed.compareAndSet(false, true)) {
            runCatching { locationManager.removeUpdates(listener) }
            if (cont.isActive) {
                cont.resume(null)
            }
        }
    }

    private fun isProviderEnabledSafely(locationManager: LocationManager, provider: String): Boolean {
        return try {
            locationManager.isProviderEnabled(provider)
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocationSafely(locationManager: LocationManager, provider: String): Location? {
        return try {
            locationManager.getLastKnownLocation(provider)
        } catch (_: Exception) {
            null
        }
    }
}
