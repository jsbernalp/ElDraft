package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.eldraft.android.ui.components.BackTopBar
import com.eldraft.android.ui.components.EmptyState
import com.eldraft.android.ui.components.ScreenHeader
import com.eldraft.android.ui.components.LoadingState
import com.eldraft.android.ui.postulation.ApplicantsViewModel
import com.eldraft.data.models.Postulation
import com.eldraft.data.models.PostulantSummary
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicantsScreen(
    convocatoryId: String,
    onOpenPlayerCromo: (String) -> Unit,
    onBack: () -> Unit,
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
        topBar = { BackTopBar(onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            ScreenHeader(title = "Postulantes", subtitle = "Quién quiere entrar a tu partido")
            Spacer(Modifier.height(16.dp))

            when {
                state.isLoading && state.applicants.isEmpty() -> LoadingState()
                state.applicants.isEmpty() -> EmptyState(
                    icon = "📋",
                    title = "Sin postulantes todavía",
                    message = "Cuando alguien se postule a esta convocatoria, aparecerá aquí.",
                )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApplicantCard(
    postulation: Postulation,
    isDeciding: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onOpenCromo: () -> Unit,
) {
    val player = postulation.player
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenCromo),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                AvatarCircle(name = player?.name ?: "?", avatarUrl = player?.avatarUrl)
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
                    // Posición a la que se postuló en ESTE partido (distinta de la
                    // posición habitual de su ficha, que va en el subtítulo).
                    postulation.position?.takeIf { it.isNotBlank() }?.let { pos ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Se postuló como: $pos",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                StatusChip(postulation.status)
            }

            if (player != null) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(12.dp))
                StatsRow(player)
            }

            // Botones solo cuando la postulación sigue pendiente.
            if (postulation.status == "pending") {
                Spacer(Modifier.height(16.dp))
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

/**
 * Métricas del jugador en FlowRow: se acomodan en varias líneas si no caben,
 * evitando que la última ("Asistencia") se desborde verticalmente. Cada métrica
 * lleva un ícono de dominio junto al valor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsRow(player: PostulantSummary) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        player.skillScore?.let { Stat(Icons.Filled.SportsSoccer, "Habilidad", "%.1f".format(it)) }
        player.sportsmanshipScore?.let { Stat(Icons.Filled.Handshake, "Deportividad", "%.1f".format(it)) }
        player.responsibilityScore?.let { Stat(Icons.Filled.AssignmentTurnedIn, "Responsabilidad", "%.1f".format(it)) }
        player.attendancePct?.let { Stat(Icons.Filled.EventAvailable, "Asistencia", "${it.toInt()}%") }
    }
}

@Composable
private fun Stat(icon: ImageVector, label: String, value: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
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
private fun AvatarCircle(name: String, avatarUrl: String? = null) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Foto de $name",
                modifier = Modifier.size(44.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(initial, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}
