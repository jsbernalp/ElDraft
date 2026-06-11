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
import com.eldraft.android.ui.components.BackTopBar
import com.eldraft.android.ui.profile.CromoUiState
import com.eldraft.android.ui.profile.ProfileViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerCromoScreen(
    playerId: String,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel()
) {
    val state by viewModel.cromo.collectAsStateWithLifecycle()

    LaunchedEffect(playerId) {
        if (playerId.isNotBlank()) viewModel.loadCromo(playerId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { BackTopBar(onBack = onBack) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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

        // Reputación entre pares (calificación post-partido en 3 criterios).
        InfoRow("⚽ Habilidad", "${fmtScore(profile.skillScore)} / 5")
        InfoRow("🤝 Deportividad", "${fmtScore(profile.sportsmanshipScore)} / 5")
        InfoRow("📋 Responsabilidad", "${fmtScore(profile.responsibilityScore)} / 5")
        InfoRow("Asistencia", "${profile.attendancePct.toInt()}%")
        InfoRow("Partidos jugados", profile.totalMatches.toString())
    }
}

/** Formatea una nota 0..5 a un decimal (p. ej. 4.3), sin ceros sobrantes. */
private fun fmtScore(value: Double): String {
    val rounded = kotlin.math.round(value * 10) / 10
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
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
