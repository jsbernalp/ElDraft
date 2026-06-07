package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.data.models.PlayerProfile
import com.eldraft.android.ui.profile.CromoUiState
import com.eldraft.android.ui.profile.ProfileViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun PlayerCromoScreen(
    playerId: String,
    viewModel: ProfileViewModel = koinViewModel()
) {
    val state by viewModel.cromo.collectAsStateWithLifecycle()

    LaunchedEffect(playerId) {
        if (playerId.isNotBlank()) viewModel.loadCromo(playerId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        when (val s = state) {
            is CromoUiState.Loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            is CromoUiState.Error -> Text(
                s.message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(24.dp)
            )
            is CromoUiState.Loaded -> CromoContent(s.profile)
        }
    }
}

@Composable
private fun CromoContent(profile: PlayerProfile) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("El Cromo", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)

        Spacer(Modifier.height(24.dp))

        // Avatar placeholder (la URL real se mostrará con Coil cuando haya datos)
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                profile.positionPrimary.take(1).uppercase(),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(16.dp))

        InfoRow("Posición principal", profile.positionPrimary)
        profile.positionSecondary?.let { InfoRow("Posición secundaria", it) }
        InfoRow("Pierna hábil", profile.dominantFoot)
        profile.height?.let { InfoRow("Altura", "$it cm") }
        profile.build?.let { InfoRow("Contextura", it) }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
        Spacer(Modifier.height(24.dp))

        // Stats con barras (0–100)
        StatBar("Velocidad", profile.speedRating)
        Spacer(Modifier.height(12.dp))
        StatBar("Precisión", profile.precisionRating)

        Spacer(Modifier.height(24.dp))

        // Reputación — crítica para la decisión del organizador
        InfoRow("Asistencia", "${profile.attendancePct.toInt()}%")
        InfoRow("Compañerismo", "${profile.sportsmanshipScore} / 5")
        InfoRow("Partidos jugados", profile.totalMatches.toString())
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Text(value, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatBar(label: String, value: Int) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = MaterialTheme.colorScheme.onBackground)
            Text(value.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (value.coerceIn(0, 100)) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
        )
    }
}
