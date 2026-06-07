package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ApplicantsScreen(convocatoryId: String, onOpenPlayerCromo: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Text("Postulantes", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(16.dp))
        // TODO: lista de postulantes con ficha técnica (El Cromo)
        // cada item: avatar + nombre + posición + attendance_pct + sportsmanship + botones Aprobar/Rechazar
        Text("convocatoryId: $convocatoryId", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
    }
}
