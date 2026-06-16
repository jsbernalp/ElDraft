package com.eldraft.android.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Abre la navegación hacia ([lat], [lng]) en una app de mapas para que el
 * usuario solo tenga que pulsar "Iniciar".
 *
 * Intenta primero Google Maps en modo navegación (`google.navigation:`, que
 * abre directamente la pantalla de ruta con el botón Iniciar). Si Google Maps
 * no está instalado, cae a un `geo:` genérico con un marcador etiquetado, que
 * cualquier app de mapas puede abrir.
 *
 * Usa try/catch en vez de `resolveActivity` porque desde Android 11 la
 * visibilidad de paquetes ([queries]) ocultaría a Maps y daría un falso
 * negativo. Best-effort: si nada lo resuelve, devuelve false sin lanzar.
 */
fun openDirections(context: Context, lat: Double, lng: Double, label: String? = null): Boolean {
    // 1) Google Maps en modo navegación (arranca la ruta lista para "Iniciar").
    val navIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng")).apply {
        setPackage("com.google.android.apps.maps")
    }
    try {
        context.startActivity(navIntent)
        return true
    } catch (_: ActivityNotFoundException) {
        // Google Maps no está: seguimos con el fallback genérico.
    }

    // 2) Fallback genérico: marcador en el destino, cualquier app de mapas.
    val encodedLabel = label?.takeIf { it.isNotBlank() }?.let { Uri.encode(it) }
    val geoUri = if (encodedLabel != null) {
        "geo:$lat,$lng?q=$lat,$lng($encodedLabel)"
    } else {
        "geo:$lat,$lng?q=$lat,$lng"
    }
    val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri))
    return try {
        context.startActivity(geoIntent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
