package com.eldraft.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.eldraft.android.ui.theme.ElDraftTheme
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient

/** Una dirección elegida por el usuario en el buscador. */
data class PlaceSelection(
    val description: String,
    val location: LatLng,
)

/**
 * Campo de búsqueda de direcciones con sugerencias en vivo (Places Autocomplete).
 * Al elegir una sugerencia, resuelve sus coordenadas y notifica [onPlaceSelected].
 */
@Composable
fun PlaceAutocompleteField(
    onPlaceSelected: (PlaceSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Cliente de Places (requiere Places.initialize en la Application).
    val placesClient: PlacesClient? = remember {
        if (Places.isInitialized()) Places.createClient(context) else null
    }
    // Token de sesión: agrupa las búsquedas de una misma selección (facturación).
    var sessionToken by remember { mutableStateOf(AutocompleteSessionToken.newInstance()) }

    var query by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { text ->
                query = text
                error = null
                if (text.length < 3 || placesClient == null) {
                    predictions = emptyList()
                    return@OutlinedTextField
                }
                val request = FindAutocompletePredictionsRequest.builder()
                    .setSessionToken(sessionToken)
                    .setQuery(text)
                    .build()
                placesClient.findAutocompletePredictions(request)
                    .addOnSuccessListener { response ->
                        predictions = response.autocompletePredictions
                        error = null
                    }
                    .addOnFailureListener { e ->
                        predictions = emptyList()
                        android.util.Log.e("PlaceAutocomplete", "findAutocompletePredictions falló", e)
                        error = "Sugerencias no disponibles: ${e.message?.take(120)}"
                    }
            },
            label = { Text("Buscar dirección o lugar") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; predictions = emptyList() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (placesClient == null) {
            Text(
                "Buscador no disponible (Places SDK no inicializado).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = ElDraftTheme.spacing.xs),
            )
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = ElDraftTheme.spacing.xs))
        }

        if (predictions.isNotEmpty()) {
            Spacer(Modifier.height(ElDraftTheme.spacing.xs))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ElDraftTheme.shape.sm)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                predictions.take(5).forEach { prediction ->
                    PredictionRow(
                        primary = prediction.getPrimaryText(null).toString(),
                        secondary = prediction.getSecondaryText(null).toString(),
                        onClick = {
                            val client = placesClient ?: return@PredictionRow
                            val fields = listOf(Place.Field.LAT_LNG, Place.Field.NAME, Place.Field.ADDRESS)
                            val req = FetchPlaceRequest.builder(prediction.placeId, fields)
                                .setSessionToken(sessionToken)
                                .build()
                            client.fetchPlace(req)
                                .addOnSuccessListener { resp ->
                                    val latLng = resp.place.latLng
                                    if (latLng != null) {
                                        onPlaceSelected(
                                            PlaceSelection(
                                                description = prediction.getFullText(null).toString(),
                                                location = latLng,
                                            )
                                        )
                                        query = prediction.getPrimaryText(null).toString()
                                        predictions = emptyList()
                                        // Nuevo token para la siguiente búsqueda.
                                        sessionToken = AutocompleteSessionToken.newInstance()
                                    }
                                }
                                .addOnFailureListener {
                                    error = "No se pudo resolver la ubicación"
                                }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = ElDraftTheme.alpha.containerStrong))
                }
            }
        }
    }
}

@Composable
private fun PredictionRow(primary: String, secondary: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ElDraftTheme.spacing.lg, vertical = ElDraftTheme.spacing.md),
    ) {
        Text(primary, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        if (secondary.isNotBlank()) {
            Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textTertiary))
        }
    }
}
