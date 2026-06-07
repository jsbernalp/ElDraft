package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PostMatchRatingScreen(convocatoryId: String, onRatingComplete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Text("¿Cómo estuvo el partido?", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text("Califica el compañerismo de tus compañeros", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))

        Spacer(Modifier.height(24.dp))

        // TODO: listar jugadores del partido + slider/estrellas de compañerismo (1-5) por jugador
        // Llamar POST /ratings para cada calificación

        Spacer(Modifier.weight(1f))

        Button(onClick = onRatingComplete, modifier = Modifier.fillMaxWidth()) {
            Text("Enviar calificaciones")
        }
    }
}
