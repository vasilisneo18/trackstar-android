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
            // A fresh, high-accuracy fix is the reliable source for the geofence check.
            val fresh = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            if (fresh != null) return Result.Success(fresh.latitude, fresh.longitude)
            // Only fall back to last-known if it's recent — a stale fix can be miles away and would
            // make the gym geofence reject the check-in.
            val last = client.lastLocation.await()
            if (last != null && System.currentTimeMillis() - last.time < 120_000) {
                Result.Success(last.latitude, last.longitude)
            } else {
                Result.Error("Couldn't get an accurate location. Make sure GPS is on and try again, ideally outdoors or near a window.")
            }
        } catch (e: Exception) {
            Result.Error("Couldn't get your location. Try again.")
        }
    }
}
