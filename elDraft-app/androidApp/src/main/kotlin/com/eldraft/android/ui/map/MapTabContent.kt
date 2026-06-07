package com.eldraft.android.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.data.models.Convocatory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import org.koin.androidx.compose.koinViewModel

// Centro por defecto: Medellín (hasta tener ubicación del usuario)
private val DEFAULT_CENTER = LatLng(6.2442, -75.5812)

@Composable
fun MapTabContent(
    onOpenPlayerCromo: (String) -> Unit,
    viewModel: MapViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasLocationPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Carga inicial del área (centro por defecto). Con la ubicación real del
    // usuario se recargará en una iteración posterior (FusedLocationProvider).
    LaunchedEffect(Unit) {
        viewModel.loadArea(DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_CENTER, 13f)
    }

    var selectedPin by remember { mutableStateOf<Convocatory?>(null) }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = remember {
                MapUiSettings(
                    zoomControlsEnabled = true,
                    zoomGesturesEnabled = true,
                    mapToolbarEnabled = false,
                )
            },
        ) {
            state.pins.forEach { pin ->
                Marker(
                    state = MarkerState(position = LatLng(pin.lat, pin.lng)),
                    title = pin.format.ifBlank { "Convocatoria" },
                    snippet = "${pin.slotsNeeded} cupos",
                    onClick = {
                        selectedPin = pin
                        true
                    },
                )
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        state.error?.let { msg ->
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    msg,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    selectedPin?.let { pin ->
        PinDetailSheet(
            convocatory = pin,
            onDismiss = { selectedPin = null },
            onApply = {
                // Postularse se implementa en Fase 3. Por ahora, abrir el Cromo
                // del organizador no aplica; cerramos el sheet.
                selectedPin = null
            },
        )
    }
}
