package com.eldraft.android.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.ui.attendance.AttendanceViewModel
import com.eldraft.android.util.generateQrBitmap
import org.koin.androidx.compose.koinViewModel

@Composable
fun QRGeneratorScreen(
    convocatoryId: String,
    viewModel: AttendanceViewModel = koinViewModel(),
) {
    val state by viewModel.qrState.collectAsStateWithLifecycle()

    LaunchedEffect(convocatoryId) { viewModel.loadQr(convocatoryId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Código de asistencia", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Muéstralo en la cancha para que los jugadores marquen asistencia",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val qr = state.qrCode
            when {
                state.error != null -> Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                qr == null -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                else -> {
                    val bitmap = remember(qr) { generateQrBitmap(qr) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Código QR de asistencia",
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text("No se pudo dibujar el QR", color = Color.Black)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (state.qrCode != null) {
            Text(
                "Expira en ${formatMmSs(state.secondsLeft)}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Se renueva automáticamente",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
        }

        Spacer(Modifier.weight(1f))
    }
}

private fun formatMmSs(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}
