package com.eldraft.android.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Obtiene la ubicación actual del dispositivo vía FusedLocationProvider.
 *
 * El permiso de ubicación debe estar concedido ANTES de llamar a [current];
 * el llamador es responsable de solicitarlo. Devuelve `null` si no hay
 * permiso, si el GPS no entrega una posición, o si ocurre cualquier error.
 */
class LocationProvider(context: Context) {

    private val fused = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    suspend fun current(): LatLng? = suspendCancellableCoroutine { cont ->
        try {
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    cont.resume(loc?.let { LatLng(it.latitude, it.longitude) })
                }
                .addOnFailureListener { cont.resume(null) }
        } catch (e: SecurityException) {
            cont.resume(null)
        }
    }
}

/** Recuerda un [LocationProvider] atado al contexto de Compose. */
@Composable
fun rememberLocationProvider(context: Context): LocationProvider =
    remember(context) { LocationProvider(context) }

/** Permisos de ubicación que la app solicita. */
val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)
