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
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

/**
 * Obtiene la ubicación actual del dispositivo vía FusedLocationProvider.
 *
 * Estrategia de dos pasos para máxima fiabilidad en dispositivo físico:
 * 1. Intenta [getCurrentLocation] con alta precisión (puede tardar en GPS frío).
 * 2. Si devuelve null (GPS frío o sin fix), cae a [lastLocation] que retorna
 *    la última posición conocida cacheada por el sistema (casi instantáneo).
 *
 * El permiso de ubicación debe estar concedido ANTES de llamar a [current].
 * Devuelve `null` solo si no hay absolutamente ninguna ubicación disponible.
 */
class LocationProvider(context: Context) {

    private val fused = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    suspend fun current(): LatLng? {
        return try {
            // Paso 1: ubicación fresca con alta precisión
            val fresh = suspendCancellableCoroutine<LatLng?> { cont ->
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        cont.resume(loc?.let { LatLng(it.latitude, it.longitude) })
                    }
                    .addOnFailureListener { cont.resume(null) }
            }
            if (fresh != null) return fresh

            // Paso 2: última ubicación cacheada (instantánea, puede ser de hace unos minutos)
            val last = fused.lastLocation.await()
            last?.let { LatLng(it.latitude, it.longitude) }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
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
