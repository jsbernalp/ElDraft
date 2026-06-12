package com.eldraft.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.ui.components.EmptyState
import com.eldraft.android.ui.components.LoadingState
import com.eldraft.android.ui.draft.MyMatchesViewModel
import com.eldraft.android.ui.map.MapTabContent
import com.eldraft.android.ui.postulation.MyPostulationsViewModel
import com.eldraft.data.models.Convocatory
import com.eldraft.data.models.MyPostulation
import org.koin.androidx.compose.koinViewModel

/**
 * Pantallas de las secciones del NavigationBar (Organizo / Juego / Buscar Cupo).
 * Extraídas de la antigua HomeScreen (TabRow + HorizontalPager). El tab Perfil
 * vive en su propio archivo (ProfileTabScreen).
 */

/** Sección "Organizo": convocatorias que el usuario ha creado. */
@Composable
fun OrganizoScreen(
    onCreateDraft: () -> Unit,
    onOpenApplicants: (String) -> Unit,
    onOpenQrGenerator: (String) -> Unit,
    onOpenRating: (String) -> Unit,
    viewModel: MyMatchesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Recarga cada vez que el tab vuelve a mostrarse.
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                "Mis convocatorias",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))

            when {
                state.isLoading && state.matches.isEmpty() -> LoadingState()
                state.matches.isEmpty() -> EmptyState(
                    icon = "⚽",
                    title = "Aún no has creado convocatorias",
                    message = "Toca el botón + para crear tu primera convocatoria.",
                )
                else ->
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 88.dp),
                    ) {
                        items(state.matches, key = { it.id }) { match ->
                            MyMatchCard(
                                match = match,
                                onOpenApplicants = { onOpenApplicants(match.id) },
                                onOpenQrGenerator = { onOpenQrGenerator(match.id) },
                                onOpenRating = { onOpenRating(match.id) },
                            )
                        }
                    }
            }
        }

        FloatingActionButton(
            onClick = onCreateDraft,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            Text("+")
        }

        SnackbarHost(
            snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun MyMatchCard(
    match: Convocatory,
    onOpenApplicants: () -> Unit,
    onOpenQrGenerator: () -> Unit,
    onOpenRating: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenApplicants),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                match.format.ifBlank { "Convocatoria" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            match.addressText?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${match.slotsNeeded} cupos · ${match.positionRequired}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenApplicants, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Postulantes")
                }
                TextButton(onClick = onOpenQrGenerator, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Generar QR")
                }
                TextButton(onClick = onOpenRating, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Calificar")
                }
            }
        }
    }
}

/** Sección "Juego": postulaciones del usuario a convocatorias ajenas. */
@Composable
fun JuegoScreen(
    onOpenQrScanner: (String) -> Unit,
    onOpenRating: (String) -> Unit,
    viewModel: MyPostulationsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.error) { state.error?.let { snackbarHostState.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                "Mis postulaciones",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))

            when {
                state.isLoading && state.postulations.isEmpty() -> LoadingState()
                state.postulations.isEmpty() -> EmptyState(
                    icon = "🏃",
                    title = "Aún no te has postulado",
                    message = "Busca un cupo en el mapa y postúlate para jugar.",
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(state.postulations, key = { it.id }) { p ->
                        MyGameCard(
                            postulation = p,
                            onScanQr = { onOpenQrScanner(p.convocatory.id) },
                            onRate = { onOpenRating(p.convocatory.id) },
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun MyGameCard(
    postulation: MyPostulation,
    onScanQr: () -> Unit,
    onRate: () -> Unit,
) {
    val c = postulation.convocatory
    val approved = postulation.status == "approved"
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.format.ifBlank { "Convocatoria" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(postulation.status)
            }
            c.addressText?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }

            if (approved) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onScanQr, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Escanear QR")
                    }
                    TextButton(onClick = onRate, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Calificar")
                    }
                }
            }
        }
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

/** Sección "Buscar Cupo": mapa de convocatorias abiertas. */
@Composable
fun BuscarCupoScreen(onOpenPlayerCromo: (String) -> Unit) {
    MapTabContent(onOpenPlayerCromo = onOpenPlayerCromo)
}
