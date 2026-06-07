package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun QRScannerScreen(convocatoryId: String, onScanComplete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Escáner de asistencia",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(24.dp)
        )

        // TODO: implementar CameraX + ML Kit BarcodeScanning
        // PreviewView de CameraX con análisis de imagen en tiempo real
        // Cuando detecta QR válido: llamar POST /attendance/scan -> onScanComplete()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("Cámara placeholder\n(CameraX + ML Kit)", color = MaterialTheme.colorScheme.onSurface)
        }

        Button(
            onClick = onScanComplete,
            modifier = Modifier.padding(24.dp).fillMaxWidth()
        ) {
            Text("Simular escaneo exitoso")
        }
    }
}
