package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PlayerCromoScreen(playerId: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Text("El Cromo", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        // TODO: mostrar ficha técnica completa:
        // avatar, nombre, posición, pierna hábil, altura, contextura
        // barras de velocidad y precisión
        // attendance_pct (porcentaje asistencia) - crítico para decisión de organizador
        // sportsmanship_score (compañerismo)
        // total de partidos
        Text("playerId: $playerId", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
    }
}
