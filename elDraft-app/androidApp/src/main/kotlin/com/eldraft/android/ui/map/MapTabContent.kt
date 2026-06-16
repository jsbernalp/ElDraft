package com.eldraft.android.ui.map

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.data.models.Convocatory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import org.koin.androidx.compose.koinViewModel

/**
 * Mapa de convocatorias. No gestiona sheets ni la carga de datos: notifica la
 * selección de un pin único ([onPinClick]) o de un grupo de varias
 * convocatorias en la misma ubicación ([onGroupClick]) al contenedor
 * ([BuscarCupoScreen]), que comparte el [viewModel] con la vista de lista y es
 * quien obtiene la ubicación y llama a loadArea. La cámara
 * ([cameraPositionState]) también la posee el contenedor para poder centrarla
 * aunque el mapa aún no se haya montado.
 */
@Composable
fun MapTabContent(
    cameraPositionState: com.google.maps.android.compose.CameraPositionState,
    hasLocationPermission: Boolean,
    onPinClick: (Convocatory) -> Unit,
    onGroupClick: (List<Convocatory>) -> Unit,
    viewModel: MapViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
                            onPinClick(first)
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
                            onGroupClick(group)
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
