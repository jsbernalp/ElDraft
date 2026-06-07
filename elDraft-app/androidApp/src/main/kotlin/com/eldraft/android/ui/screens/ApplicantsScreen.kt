package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.ui.postulation.ApplicantsViewModel
import com.eldraft.data.models.Postulation
import com.eldraft.data.models.PostulantSummary
import org.koin.androidx.compose.koinViewModel

@Composable
fun ApplicantsScreen(
    convocatoryId: String,
    onOpenPlayerCromo: (String) -> Unit,
    viewModel: ApplicantsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(convocatoryId) { viewModel.load(convocatoryId) }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Postulantes",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))

            when {
                state.isLoading && state.applicants.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                state.applicants.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Aún no hay postulantes para esta convocatoria.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        items(state.applicants, key = { it.id }) { postulation ->
                            ApplicantCard(
                                postulation = postulation,
                                isDeciding = postulation.id in state.decidingIds,
                                onApprove = { viewModel.approve(postulation.id) },
                                onReject = { viewModel.reject(postulation.id) },
                                onOpenCromo = { onOpenPlayerCromo(postulation.playerId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplicantCard(
    postulation: Postulation,
    isDeciding: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onOpenCromo: () -> Unit,
) {
    val player = postulation.player
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenCromo),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle(name = player?.name ?: "?")
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        player?.name ?: "Jugador",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val subtitle = listOfNotNull(
                        player?.positionPrimary,
                        player?.dominantFoot?.let { "Pierna $it" },
                    ).joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
                StatusChip(postulation.status)
            }

            if (player != null) {
                Spacer(Modifier.height(12.dp))
                StatsRow(player)
            }

            // Botones solo cuando la postulación sigue pendiente.
            if (postulation.status == "pending") {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onReject,
                        enabled = !isDeciding,
                        modifier = Modifier.weight(1f),
                    ) { Text("Rechazar") }
                    Button(
                        onClick = onApprove,
                        enabled = !isDeciding,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isDeciding) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Aprobar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsRow(player: PostulantSummary) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        player.speedRating?.let { Stat("Velocidad", it.toString()) }
        player.precisionRating?.let { Stat("Precisión", it.toString()) }
        player.attendancePct?.let { Stat("Asistencia", "${it.toInt()}%") }
        player.sportsmanshipScore?.let { Stat("Fair play", "%.1f".format(it)) }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun StatusChip(status: String) {
    val (label, color) = when (status) {
        "approved" -> "Aprobado" to MaterialTheme.colorScheme.primary
        "rejected" -> "Rechazado" to MaterialTheme.colorScheme.error
        else -> "Pendiente" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color)
}

@Composable
private fun AvatarCircle(name: String) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}
