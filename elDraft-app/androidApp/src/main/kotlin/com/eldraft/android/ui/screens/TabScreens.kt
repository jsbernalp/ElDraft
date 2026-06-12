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
import com.eldraft.android.ui.components.IconFee
import com.eldraft.android.ui.components.IconGroups
import com.eldraft.android.ui.components.IconPlace
import com.eldraft.android.ui.components.LoadingState
import com.eldraft.android.ui.components.MetaItem
import com.eldraft.android.ui.components.ScheduleBanner
import com.eldraft.android.ui.components.ScreenHeader
import com.eldraft.android.ui.components.StatusBadge
import com.eldraft.android.ui.components.formatFee
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
            ScreenHeader(title = "Mis convocatorias", subtitle = "Lo que organizas")
            Spacer(Modifier.height(20.dp))

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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenApplicants),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Fecha/hora destacada + estado.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ScheduleBanner(match.scheduledAt)
                StatusBadge(match.status)
            }

            Spacer(Modifier.height(10.dp))

            // Título: formato + ambiente.
            Text(
                match.format.ifBlank { "Convocatoria" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            match.ambiente.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Metadatos con íconos.
            match.addressText?.takeIf { it.isNotBlank() }?.let {
                MetaItem(IconPlace, it)
                Spacer(Modifier.height(6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetaItem(IconGroups, "${match.slotsNeeded} cupos · ${match.positionRequired}")
                MetaItem(IconFee, formatFee(match.fee))
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(Modifier.height(4.dp))

            // Acciones jerarquizadas: primaria + secundarias.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilledTonalButton(
                    onClick = onOpenApplicants,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("Postulantes")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenQrGenerator, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("QR")
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
            ScreenHeader(title = "Mis postulaciones", subtitle = "Donde juegas")
            Spacer(Modifier.height(20.dp))

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
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Fecha/hora + estado de la postulación.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ScheduleBanner(c.scheduledAt)
                StatusChip(postulation.status)
            }

            Spacer(Modifier.height(10.dp))

            Text(
                c.format.ifBlank { "Convocatoria" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            c.ambiente.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(10.dp))

            c.addressText?.takeIf { it.isNotBlank() }?.let {
                MetaItem(IconPlace, it)
                Spacer(Modifier.height(6.dp))
            }
            MetaItem(IconFee, formatFee(c.fee))

            if (approved) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilledTonalButton(
                        onClick = onScanQr,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text("Escanear QR")
                    }
                    Spacer(Modifier.weight(1f))
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
