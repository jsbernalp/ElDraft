package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun QRGeneratorScreen(convocatoryId: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Tu código de acceso", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text("Muéstralo en la cancha", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))

        Spacer(Modifier.height(48.dp))

        // TODO: generar QR con ZXing/ML Kit a partir del qrCode recibido del servidor
        // El QR expira a los 10 minutos (mostrar countdown)
        Box(
            modifier = Modifier
                .size(250.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("QR Placeholder", color = MaterialTheme.colorScheme.onSurface)
        }

        Spacer(Modifier.height(24.dp))
        Text("Expira en: 10:00", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}
