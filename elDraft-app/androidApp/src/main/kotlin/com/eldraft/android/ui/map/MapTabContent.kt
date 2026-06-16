package com.eldraft.android.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.util.LOCATION_PERMISSIONS
import com.eldraft.android.util.rememberLocationProvider
import com.eldraft.data.models.Convocatory
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
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
    val locationProvider = rememberLocationProvider(context)

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // Marca que ya intentamos centrar en la ubicación real (para no repetir).
    var centeredOnUser by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_CENTER, 13f)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasLocationPermission = result.values.any { it }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    // Centra el mapa y carga el área en la ubicación REAL del usuario en cuanto
    // hay permiso. Si no se puede obtener (GPS frío), carga con Medellín como
    // fallback y vuelve a intentar en segundo plano para recentrar cuando llegue.
    LaunchedEffect(hasLocationPermission) {
        if (centeredOnUser) return@LaunchedEffect
        if (!hasLocationPermission) {
            // Sin permiso: cargar con fallback y esperar a que el usuario lo conceda
            viewModel.loadArea(DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude)
            return@LaunchedEffect
        }
        val location = locationProvider.current()
        if (location != null) {
            // Ubicación disponible de inmediato (GPS caliente o caché)
            cameraPositionState.position = CameraPosition.fromLatLngZoom(location, 14f)
            viewModel.loadArea(location.latitude, location.longitude)
            // Reportar la ubicación REAL para notificaciones de convocatorias cercanas.
            viewModel.reportLocation(location.latitude, location.longitude)
            centeredOnUser = true
        } else {
            // GPS frío: carga con Medellín mientras esperamos el fix real
            viewModel.loadArea(DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude)
            // Segundo intento: el GPS ya debería tener fix tras unos segundos
            val retry = locationProvider.current()
            if (retry != null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(retry, 14f)
                )
                viewModel.loadArea(retry.latitude, retry.longitude)
                // Reportar solo la ubicación real, nunca el centro por defecto.
                viewModel.reportLocation(retry.latitude, retry.longitude)
            }
            centeredOnUser = true
        }
    }

    // Convocatoria mostrada en el detalle (sheet de postulación).
    var selectedPin by remember { mutableStateOf<Convocatory?>(null) }
    // Grupo de convocatorias en una misma ubicación (sheet de lista). Al elegir
    // una de la lista se pasa a selectedPin.
    var selectedGroup by remember { mutableStateOf<List<Convocatory>?>(null) }

    // Agrupa los pines por coordenada exacta: varias convocatorias en el mismo
    // punto comparten un marcador (con badge numérico) en vez de solaparse.
    val groups = remember(state.pins) {
        state.pins.groupBy { it.lat to it.lng }.values.toList()
    }

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
            groups.forEach { group ->
                val first = group.first()
                val position = LatLng(first.lat, first.lng)
                if (group.size == 1) {
                    // Una convocatoria: gota de marca con un balón en el centro.
                    MarkerComposable(
                        state = MarkerState(position = position),
                        title = first.format.ifBlank { "Convocatoria" },
                        onClick = {
                            selectedPin = first
                            true
                        },
                    ) {
                        MapPin()
                    }
                } else {
                    // Varias convocatorias en el mismo punto: la misma gota con el
                    // número; al tocarla se abre la lista para elegir cuál ver.
                    MarkerComposable(
                        state = MarkerState(position = position),
                        title = "${group.size} partidos aquí",
                        onClick = {
                            selectedGroup = group
                            true
                        },
                    ) {
                        MapPin(count = group.size)
                    }
                }
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

    // Lista de convocatorias de una ubicación con varias (oculta si ya se eligió
    // una, para no apilar dos sheets).
    selectedGroup?.takeIf { selectedPin == null }?.let { group ->
        PinGroupSheet(
            convocatories = group,
            onSelect = { selectedPin = it },
            onDismiss = { selectedGroup = null },
        )
    }

    selectedPin?.let { pin ->
        PinDetailSheet(
            convocatory = pin,
            onDismiss = {
                selectedPin = null
                selectedGroup = null
            },
            // Mantenemos el sheet abierto mostrando "✓ Postulación enviada";
            // el usuario lo cierra al deslizar.
            onApplied = {},
        )
    }
}

// Geometría de la gota (en dp). El círculo va arriba; la punta cuelga debajo.
private val PIN_WIDTH = 44.dp
private val PIN_HEIGHT = 56.dp
private val PIN_CIRCLE = 40.dp        // diámetro del círculo de la gota
private val PIN_BORDER = 3.dp         // grosor del borde blanco
private val PIN_INNER = 20.dp         // diámetro del disco blanco interior

/**
 * Marcador de marca con forma de gota: círculo de color primary con borde
 * blanco y una punta inferior. En el centro va un balón (convocatoria única) o
 * el [count] de convocatorias en esa ubicación (grupo).
 */
@Composable
private fun MapPin(count: Int? = null) {
    val pinColor = MaterialTheme.colorScheme.primary
    val borderColor = Color.White

    Box(modifier = Modifier.size(PIN_WIDTH, PIN_HEIGHT)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val border = PIN_BORDER.toPx()
            val rOuter = PIN_CIRCLE.toPx() / 2f
            val cx = size.width / 2f
            val cy = rOuter + border / 2f               // centro del círculo
            val tipY = size.height - 1.dp.toPx()        // punta inferior

            // Silueta de lágrima como UN solo contorno continuo: arco superior del
            // círculo + dos curvas que descienden tangentes hasta la punta. Así el
            // borde blanco rodea toda la forma (incluida la punta) sin cortes.
            fun teardrop(r: Float, tip: Float) = Path().apply {
                // Ángulo donde las curvas se separan del círculo (~120° de arco arriba).
                val a = Math.toRadians(60.0)
                val sx = (r * kotlin.math.sin(a)).toFloat()
                val sy = (r * kotlin.math.cos(a)).toFloat()
                val rightX = cx + sx
                val sideY = cy + sy
                moveTo(cx - sx, sideY)
                // Lado izquierdo baja a la punta (control bajo el flanco del círculo).
                quadraticBezierTo(cx - r * 0.32f, cy + r * 1.7f, cx, tip)
                // Lado derecho sube de la punta de vuelta al círculo.
                quadraticBezierTo(cx + r * 0.32f, cy + r * 1.7f, rightX, sideY)
                // Arco superior que cierra el círculo (por arriba).
                arcTo(
                    rect = Rect(center = Offset(cx, cy), radius = r),
                    startAngleDegrees = 120f,
                    sweepAngleDegrees = -300f,
                    forceMoveTo = false,
                )
                close()
            }

            // Sombra suave proyectada en el suelo (elipse bajo la punta).
            drawOval(
                color = Color.Black.copy(alpha = 0.15f),
                topLeft = Offset(cx - rOuter * 0.45f, tipY - 2.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(rOuter * 0.9f, rOuter * 0.5f),
            )

            drawPath(teardrop(rOuter, tipY), color = borderColor)                  // borde blanco
            drawPath(teardrop(rOuter - border, tipY - border * 1.4f), color = pinColor) // relleno
            drawCircle(borderColor, PIN_INNER.toPx() / 2f, Offset(cx, cy))          // disco central
        }

        // Contenido centrado EXACTAMENTE sobre el círculo (no sobre todo el Box,
        // que incluye la punta y descentraría el ícono/número).
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = PIN_BORDER / 2)
                .size(PIN_CIRCLE)
                .padding((PIN_CIRCLE - PIN_INNER) / 2),
            contentAlignment = Alignment.Center,
        ) {
            if (count == null) {
                Icon(
                    Icons.Filled.SportsSoccer,
                    contentDescription = null,
                    tint = pinColor,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    count.toString(),
                    color = pinColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
