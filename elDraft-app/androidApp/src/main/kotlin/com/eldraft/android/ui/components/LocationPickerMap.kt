package com.eldraft.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState

/**
 * Mapa para seleccionar una ubicación tocándolo. Coloca un marcador donde el
 * usuario toque y notifica vía [onLocationSelected]. El estado de cámara se
 * recibe del padre para poder recentrarlo (p. ej. desde el buscador).
 */
@Composable
fun LocationPickerMap(
    cameraPositionState: CameraPositionState,
    selectedLocation: LatLng?,
    onLocationSelected: (LatLng) -> Unit,
    modifier: Modifier = Modifier,
) {
    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = remember { MapProperties(mapType = MapType.NORMAL) },
        uiSettings = remember {
            MapUiSettings(
                zoomControlsEnabled = true,   // botones +/- nativos del SDK
                zoomGesturesEnabled = true,   // pellizcar para hacer zoom
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
