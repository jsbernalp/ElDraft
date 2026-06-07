package com.eldraft.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Mapa para seleccionar una ubicación tocándolo. Coloca un marcador donde el
 * usuario toque y notifica vía [onLocationSelected].
 */
@Composable
fun LocationPickerMap(
    initialLocation: LatLng,
    selectedLocation: LatLng?,
    onLocationSelected: (LatLng) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(selectedLocation ?: initialLocation, 14f)
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = remember { MapProperties(mapType = MapType.NORMAL) },
        uiSettings = remember {
            MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
            )
        },
        onMapClick = { latLng -> onLocationSelected(latLng) },
    ) {
        selectedLocation?.let { loc ->
            Marker(
                state = MarkerState(position = loc),
                title = "Cancha",
            )
        }
    }
}
