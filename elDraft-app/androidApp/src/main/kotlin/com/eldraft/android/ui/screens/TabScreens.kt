package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WhereToVote
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.ui.attendance.NoShowViewModel
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
    onOpenQrScanner: (String) -> Unit,
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
                                onOpenQrScanner = { onOpenQrScanner(match.id) },
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
    onOpenQrScanner: () -> Unit,
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

            Spacer(Modifier.height(8.dp))

            // Acceso a la gestión de postulantes (separado de las acciones del
            // día del partido). La card entera también abre esta pantalla.
            AssistChip(
                onClick = onOpenApplicants,
                label = { Text("Ver postulantes") },
                leadingIcon = { Icon(IconGroups, contentDescription = null) },
            )

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

            // Acciones del día del partido como accesos rápidos (ícono en pastilla
            // + etiqueta). "Ya llegué" es la acción primaria (pastilla sólida); las
            // demás usan pastilla tonal. La gestión de postulantes vive en el header.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                // El organizador marca su propia presencia escaneando el QR que le
                // genere un aprobado: ya no se asume presente.
                QuickAction(
                    icon = Icons.Filled.WhereToVote,
                    label = "Ya llegué",
                    onClick = onOpenQrScanner,
                    primary = true,
                )
                QuickAction(
                    icon = Icons.Filled.QrCode2,
                    label = "Mostrar QR",
                    onClick = onOpenQrGenerator,
                )
                QuickAction(
                    icon = Icons.Filled.Star,
                    label = "Calificar",
                    onClick = onOpenRating,
                )
            }
        }
    }
}

/**
 * Acceso rápido: ícono en pastilla circular con etiqueta debajo. [primary]
 * resalta la acción principal con pastilla sólida (las demás van tonales).
 */
@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    val container = if (primary) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.primaryContainer
    val tint = if (primary) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Sección "Juego": postulaciones del usuario a convocatorias ajenas. */
@Composable
fun JuegoScreen(
    onOpenQrScanner: (String) -> Unit,
    onOpenQrGenerator: (String) -> Unit,
    onOpenRating: (String) -> Unit,
    viewModel: MyPostulationsViewModel = koinViewModel(),
    noShowViewModel: NoShowViewModel = koinViewModel(),
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
                            onGenerateQr = { onOpenQrGenerator(p.convocatory.id) },
                            onRate = { onOpenRating(p.convocatory.id) },
                            noShowViewModel = noShowViewModel,
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
    onGenerateQr: () -> Unit,
    onRate: () -> Unit,
    noShowViewModel: NoShowViewModel,
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
                // Mismos accesos rápidos que la card del organizador, para mantener
                // un lenguaje visual consistente entre ambas vistas.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    QuickAction(
                        icon = Icons.Filled.WhereToVote,
                        label = "Ya llegué",
                        onClick = onScanQr,
                        primary = true,
                    )
                    // Un aprobado puede generar el QR para que el organizador lo escane.
                    QuickAction(
                        icon = Icons.Filled.QrCode2,
                        label = "Mostrar QR",
                        onClick = onGenerateQr,
                    )
                    QuickAction(
                        icon = Icons.Filled.Star,
                        label = "Calificar",
                        onClick = onRate,
                    )
                }

                // Reporte de no-show del organizador (solo asistentes, dentro de
                // la ventana de reporte). El estado decide si se muestra.
                NoShowSection(convocatoryId = c.id, viewModel = noShowViewModel)
            }
        }
    }
}

/**
 * Sección de reporte "el organizador no se presentó". Carga el estado y, según
 * el consenso/ventana, muestra el botón, el progreso de votos o el resultado.
 * No ocupa espacio si el reporte no aplica (organizador, fuera de ventana, etc.).
 */
@Composable
private fun NoShowSection(
    convocatoryId: String,
    viewModel: NoShowViewModel,
) {
    val uiState by viewModel.stateFor(convocatoryId).collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(convocatoryId) { viewModel.load(convocatoryId) }

    val status = uiState.status ?: return
    // Solo tiene sentido mostrar algo si ya hay consenso, ya reportó, o puede reportar.
    val visible = status.consensusReached || status.alreadyReported || status.canReport
    if (!visible) return

    Spacer(Modifier.height(8.dp))

    when {
        status.consensusReached -> Text(
            "⚠️ El organizador no se presentó (confirmado por los asistentes)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
        status.alreadyReported -> Text(
            "Reportaste que el organizador no llegó · ${status.reports}/${status.attendees} asistentes",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        status.canReport -> TextButton(
            onClick = { showConfirm = true },
            enabled = !uiState.isReporting,
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text("El organizador no llegó", color = MaterialTheme.colorScheme.error)
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Reportar al organizador") },
            text = {
                Text(
                    "¿Confirmas que el organizador no se presentó al partido? " +
                        "Tu reporte se suma al de los demás asistentes; si la mayoría " +
                        "coincide, se marcará la ausencia.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.report(convocatoryId)
                }) { Text("Sí, no llegó") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancelar") }
            },
        )
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
