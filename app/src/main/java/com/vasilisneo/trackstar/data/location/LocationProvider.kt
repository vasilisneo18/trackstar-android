package com.vasilisneo.trackstar.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

// Fetches a single fresh, accurate GPS fix for the gym check-in geofence (mirrors iOS's
// LocationProvider). Requests a high-accuracy current location, falling back to last-known.
class LocationProvider(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    sealed interface Result {
        data class Success(val lat: Double, val lng: Double) : Result
        data class Error(val message: String) : Result
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Result {
        if (!hasPermission()) return Result.Error("Location permission is needed to check in at a gym.")
        return try {
            val cts = CancellationTokenSource()
            val loc = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
                ?: client.lastLocation.await()
            if (loc != null) Result.Success(loc.latitude, loc.longitude)
            else Result.Error("Couldn't get your location. Make sure location is on and try again.")
        } catch (e: Exception) {
            Result.Error("Couldn't get your location. Try again.")
        }
    }
}
