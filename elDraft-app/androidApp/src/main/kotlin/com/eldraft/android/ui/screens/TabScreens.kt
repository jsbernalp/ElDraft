package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WhereToVote
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.eldraft.android.util.LOCATION_PERMISSIONS
import com.eldraft.android.util.rememberLocationProvider
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.eldraft.android.ui.components.canReportNoShowByTime
import com.eldraft.android.ui.components.formatFee
import com.eldraft.android.ui.components.isMatchOver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.eldraft.android.ui.map.LocationPermissionRequired
import com.eldraft.android.util.openAppSettings
import com.eldraft.android.util.openDirections
import com.eldraft.android.ui.draft.CancelConvocatoryViewModel
import com.eldraft.android.ui.draft.MyMatchesViewModel
import com.eldraft.android.ui.map.ConvocatoryListContent
import com.eldraft.android.ui.map.MapTabContent
import com.eldraft.android.ui.map.MapViewModel
import com.eldraft.android.ui.map.PinDetailSheet
import com.eldraft.android.ui.map.PinGroupSheet
import com.eldraft.android.ui.postulation.MyPostulationsViewModel
import com.eldraft.data.models.Convocatory
import com.eldraft.data.models.MyPostulation
import org.koin.androidx.compose.koinViewModel

/**
 * Pantallas de las secciones del NavigationBar (Organizo / Juego / Buscar Cupo).
 * Extraídas de la antigua HomeScreen (TabRow + HorizontalPager). El tab Perfil
 * vive en su propio archivo (ProfileTabScreen).
 */

private val CANCELLATION_REASONS = listOf(
    "Lluvia / mal clima",
    "Cancha no disponible",
    "Pocos jugadores confirmados",
    "Problema personal",
    "Otro",
)

/** Sección "Organizo": convocatorias que el usuario ha creado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizoScreen(
    onCreateDraft: () -> Unit,
    onOpenApplicants: (String) -> Unit,
    onOpenQrGenerator: (String) -> Unit,
    onOpenQrScanner: (String) -> Unit,
    onOpenRating: (String) -> Unit,
    onOpenAttendance: (String) -> Unit,
    viewModel: MyMatchesViewModel = koinViewModel(),
    cancelViewModel: CancelConvocatoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cancelState by cancelViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var cancelTargetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(cancelState.error) {
        cancelState.error?.let {
            snackbarHostState.showSnackbar(it)
            cancelViewModel.clearError()
        }
    }
    LaunchedEffect(cancelState.success) {
        if (cancelState.success) {
            cancelTargetId = null
            cancelViewModel.resetSuccess()
            viewModel.load()
            snackbarHostState.showSnackbar("Convocatoria cancelada")
        }
    }

    if (cancelTargetId != null) {
        CancelConvocatorySheet(
            isLoading = cancelState.isLoading,
            onConfirm = { reason -> cancelViewModel.cancel(cancelTargetId!!, reason) },
            onDismiss = { cancelTargetId = null },
        )
    }

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { viewModel.load() },
        modifier = Modifier.fillMaxSize(),
    ) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            ScreenHeader(title = "Mis convocatorias", subtitle = "Lo que organizas")
            Spacer(Modifier.height(20.dp))

            if (state.isLoading && state.matches.isEmpty()) {
                LoadingState()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    if (state.matches.isEmpty()) {
                        item {
                            EmptyState(
                                icon = "⚽",
                                title = "Aún no has creado convocatorias",
                                message = "Toca el botón + para crear tu primera convocatoria.",
                                modifier = Modifier.fillParentMaxHeight(),
                            )
                        }
                    } else {
                        items(state.matches, key = { it.id }) { match ->
                            MyMatchCard(
                                match = match,
                                onOpenApplicants = { onOpenApplicants(match.id) },
                                onOpenQrGenerator = { onOpenQrGenerator(match.id) },
                                onOpenQrScanner = { onOpenQrScanner(match.id) },
                                onOpenRating = { onOpenRating(match.id) },
                                onOpenAttendance = { onOpenAttendance(match.id) },
                                onCancelMatch = { cancelTargetId = match.id },
                            )
                        }
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
    } // PullToRefreshBox
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CancelConvocatorySheet(
    isLoading: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedReason by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Cancelar convocatoria",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "¿Por qué cancelás el partido? Tus jugadores serán notificados.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            CANCELLATION_REASONS.forEach { reason ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selectedReason = reason }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(
                        selected = selectedReason == reason,
                        onClick = { selectedReason = reason },
                    )
                    Text(reason, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { selectedReason?.let { onConfirm(it) } },
                enabled = selectedReason != null && !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Confirmar cancelación")
                }
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
            ) {
                Text("Volver")
            }
        }
    }
}

@Composable
private fun MetaChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    primary: Boolean = false,
) {
    val bg = if (primary) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
             else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val textColor = if (primary) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(11.dp), tint = textColor)
            }
            Text(text, style = MaterialTheme.typography.labelSmall, color = textColor)
        }
    }
}

@Composable
private fun MyMatchCard(
    match: Convocatory,
    onOpenApplicants: () -> Unit,
    onOpenQrGenerator: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onOpenRating: () -> Unit,
    onOpenAttendance: () -> Unit,
    onCancelMatch: () -> Unit,
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

            // Título: formato.
            Text(
                match.format.ifBlank { "Convocatoria" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(8.dp))

            // Metadatos como chips: ambiente, dirección, cupos y cuota en una sola línea envolvente.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                match.ambiente.takeIf { it.isNotBlank() }?.let {
                    MetaChip(text = it, primary = true)
                }
                match.addressText?.takeIf { it.isNotBlank() }?.let {
                    MetaChip(icon = IconPlace, text = it)
                }
                MetaChip(icon = IconGroups, text = "${match.slotsNeeded} cupos · ${match.positionRequired}")
                MetaChip(icon = IconFee, text = formatFee(match.fee))
            }

            Spacer(Modifier.height(10.dp))

            // Acceso a la gestión de postulantes. Badge con pendientes sin gestionar.
            BadgedBox(
                badge = {
                    if (match.pendingCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ) { Text(match.pendingCount.toString()) }
                    }
                },
            ) {
                AssistChip(
                    onClick = onOpenApplicants,
                    label = { Text("Ver postulantes") },
                    leadingIcon = { Icon(IconGroups, contentDescription = null) },
                )
            }

            // Aviso si el consenso marcó al organizador como ausente: no podrá
            // declarar la asistencia (el botón queda deshabilitado).
            if (match.organizerNoShow) {
                Spacer(Modifier.height(12.dp))
                OrganizerNoShowBanner()
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
                // Tras el cierre del partido (inicio + 45 min): el organizador
                // declara quién no llegó y puede calificar a los presentes.
                if (isMatchOver(match.scheduledAt)) {
                    // Si el consenso lo marcó ausente, no puede declarar asistencia.
                    QuickAction(
                        icon = Icons.Filled.Checklist,
                        label = "Asistencia",
                        onClick = onOpenAttendance,
                        enabled = !match.organizerNoShow,
                    )
                    QuickAction(
                        icon = Icons.Filled.Star,
                        label = "Calificar",
                        onClick = onOpenRating,
                    )
                }
            }

            // Botón cancelar: solo visible si el partido aún no comenzó y está activo.
            if (match.status in listOf("active", "full") && !isMatchOver(match.scheduledAt)) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onCancelMatch,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Cancelar partido")
                }
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
    enabled: Boolean = true,
) {
    val baseContainer = if (primary) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.primaryContainer
    val baseTint = if (primary) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.primary
    // Deshabilitado: pastilla y contenido atenuados, sin acción.
    val container = if (enabled) baseContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val tint = if (enabled) baseTint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

/** Sección "Juego": postulaciones del usuario a convocatorias ajenas. */
@OptIn(ExperimentalMaterial3Api::class)
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

    var withdrawTargetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.withdrawSuccess) {
        if (state.withdrawSuccess) {
            withdrawTargetId = null
            viewModel.clearWithdrawSuccess()
            snackbarHostState.showSnackbar("Postulación retirada")
        }
    }

    if (withdrawTargetId != null) {
        WithdrawPostulationDialog(
            isLoading = state.withdrawingId != null,
            onConfirm = { viewModel.withdraw(withdrawTargetId!!) },
            onDismiss = { withdrawTargetId = null },
        )
    }

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { viewModel.load() },
        modifier = Modifier.fillMaxSize(),
    ) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            ScreenHeader(title = "Mis postulaciones", subtitle = "Donde juegas")
            Spacer(Modifier.height(20.dp))

            if (state.isLoading && state.postulations.isEmpty()) {
                LoadingState()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (state.postulations.isEmpty()) {
                        item {
                            EmptyState(
                                icon = "🏃",
                                title = "Aún no te has postulado",
                                message = "Busca un cupo en el mapa y postúlate para jugar.",
                                modifier = Modifier.fillParentMaxHeight(),
                            )
                        }
                    } else {
                        items(state.postulations, key = { it.id }) { p ->
                            MyGameCard(
                                postulation = p,
                                onScanQr = { onOpenQrScanner(p.convocatory.id) },
                                onGenerateQr = { onOpenQrGenerator(p.convocatory.id) },
                                onRate = { onOpenRating(p.convocatory.id) },
                                noShowViewModel = noShowViewModel,
                                onWithdraw = { withdrawTargetId = p.id },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
    } // PullToRefreshBox
}

@Composable
private fun WithdrawPostulationDialog(
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("¿Retirar postulación?") },
        text = {
            Text("Si te retiras con menos de 1 hora de anticipación y estás aprobado, se registrará una penalización en tu perfil.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Confirmar")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun MyGameCard(
    postulation: MyPostulation,
    onScanQr: () -> Unit,
    onGenerateQr: () -> Unit,
    onRate: () -> Unit,
    noShowViewModel: NoShowViewModel,
    onWithdraw: () -> Unit,
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

            Spacer(Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                c.ambiente.takeIf { it.isNotBlank() }?.let {
                    MetaChip(text = it, primary = true)
                }
                c.addressText?.takeIf { it.isNotBlank() }?.let {
                    MetaChip(icon = IconPlace, text = it)
                }
                MetaChip(icon = IconFee, text = formatFee(c.fee))
            }

            if (approved) {
                // Estado del reporte de no-show (votos, consenso). Decide tanto el
                // bloque de reporte como si "Calificar" debe adelantarse.
                val noShowState by noShowViewModel.stateFor(c.id).collectAsStateWithLifecycle()
                LaunchedEffect(c.id) { noShowViewModel.load(c.id) }
                val consensusNoShow = noShowState.status?.consensusReached == true
                val markedNoShow = noShowState.status?.markedNoShow == true

                // Aviso si el organizador marcó a este convocado como ausente: explica
                // por qué bajó su % de asistencia y su responsabilidad.
                if (markedNoShow) {
                    Spacer(Modifier.height(12.dp))
                    MarkedNoShowBanner()
                }

                // "Calificar" solo tras el fin estimado del partido (inicio + 45 min)
                // o si ya se confirmó que el organizador no llegó (no hubo partido
                // que esperar).
                val showRate = isMatchOver(c.scheduledAt) || consensusNoShow

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(Modifier.height(4.dp))
                // Accesos rápidos del día del partido. "Calificar" aparece según la fase.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    // "Cómo llegar": abre Maps con la ruta al destino (solo si la
                    // convocatoria tiene una ubicación definida).
                    val hasLocation = c.lat != 0.0 || c.lng != 0.0
                    if (hasLocation) {
                        val context = LocalContext.current
                        QuickAction(
                            icon = Icons.Filled.Directions,
                            label = "Cómo llegar",
                            onClick = {
                                val ok = openDirections(context, c.lat, c.lng, c.addressText)
                                if (!ok) {
                                    Toast.makeText(
                                        context,
                                        "No encontramos una app de mapas instalada",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    }
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
                    if (showRate) {
                        QuickAction(
                            icon = Icons.Filled.Star,
                            label = "Calificar",
                            onClick = onRate,
                        )
                    }
                }

                // Reporte "el organizador no llegó": visible tras la tolerancia de
                // inicio (+15 min) para que el convocado no se quede atascado en el
                // escáner sin un QR que leer.
                NoShowSection(
                    scheduledAt = c.scheduledAt,
                    uiState = noShowState,
                    onReport = { noShowViewModel.report(c.id) },
                )

                // Retirar postulación: solo si aún no comenzó el partido y la
                // postulación sigue activa (pending o approved).
                if (postulation.status in listOf("pending", "approved") && !isMatchOver(c.scheduledAt)) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = onWithdraw,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Retirar postulación")
                    }
                }
            }
        }
    }
}

/**
 * Aviso en la card del organizador cuando el consenso lo marcó como ausente.
 * Coherente con la pantalla de asistencia bloqueada: explica que no llegó y el
 * impacto en su responsabilidad.
 */
@Composable
private fun OrganizerNoShowBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.ReportProblem,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column {
                Text(
                    "No llegaste a este partido",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "El consenso de los convocados marcó que no llegaste. Esto afecta tu responsabilidad y no puedes declarar la asistencia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/**
 * Aviso en la card del convocado cuando el organizador lo marcó como ausente.
 * Explica el impacto (asistencia + responsabilidad) en tono neutro.
 */
@Composable
private fun MarkedNoShowBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.ReportProblem,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column {
                Text(
                    "El organizador marcó que no llegaste",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "Esto afecta tu porcentaje de asistencia y tu responsabilidad en este partido.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/**
 * Bloque de reporte "el organizador no se presentó". Recibe el estado ya cargado
 * y un callback para emitir el voto. Solo aparece cuando aplica:
 *  - ya hay consenso (aviso) o el usuario ya reportó (progreso), o
 *  - puede reportar Y ya pasó la tolerancia de inicio (scheduled_at + 15 min),
 *    que es justo cuando el convocado se quedaría sin QR que escanear.
 */
@Composable
private fun NoShowSection(
    scheduledAt: String,
    uiState: com.eldraft.android.ui.attendance.NoShowUiState,
    onReport: () -> Unit,
) {
    var showConfirm by remember { mutableStateOf(false) }
    val status = uiState.status ?: return

    val canReportNow = status.canReport && canReportNoShowByTime(scheduledAt)
    val visible = status.consensusReached || status.alreadyReported || canReportNow
    if (!visible) return

    Spacer(Modifier.height(10.dp))

    when {
        status.consensusReached -> Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "El organizador no se presentó (confirmado por los asistentes)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        status.alreadyReported -> Text(
            "Reportaste que el organizador no llegó · ${status.reports}/${status.attendees} convocados",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        canReportNow -> OutlinedButton(
            onClick = { showConfirm = true },
            enabled = !uiState.isReporting,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Filled.ReportProblem, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("El organizador no llegó")
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
                    onReport()
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
        "cancelled" -> "Cancelado" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        else -> "Pendiente" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color)
}

// Centro por defecto: Medellín (hasta tener la ubicación del usuario).
private val BUSCAR_CUPO_DEFAULT_CENTER = LatLng(6.2442, -75.5812)

/** Modo de visualización de "Buscar Cupo". La lista es la vista por defecto. */
private enum class BuscarCupoView { LISTA, MAPA }

/**
 * Sección "Buscar Cupo": convocatorias abiertas como lista (vista por defecto,
 * escaneable y ordenada por hora) o como mapa, alternables con un toggle.
 *
 * Ambas vistas comparten el mismo [MapViewModel] (un solo snapshot REST +
 * WebSocket) y abren el mismo [PinDetailSheet] al elegir una convocatoria.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuscarCupoScreen() {
    val viewModel: MapViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locationProvider = rememberLocationProvider(context)

    var view by remember { mutableStateOf(BuscarCupoView.LISTA) }
    // Convocatoria en detalle (sheet de postulación), abierta desde lista o mapa.
    var selectedPin by remember { mutableStateOf<Convocatory?>(null) }
    // Grupo de convocatorias en una misma ubicación (sheet de lista del mapa).
    var selectedGroup by remember { mutableStateOf<List<Convocatory>?>(null) }
    // Confirmación de postulación: el sheet se cierra y el snackbar se muestra
    // aquí, en la pantalla principal (no dentro del sheet, donde queda mal).
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Permiso, cámara y carga viven AQUÍ (no en el mapa) para que los datos se
    // carguen al entrar a la pestaña aunque la vista por defecto sea la lista.
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var centeredOnUser by remember { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(BUSCAR_CUPO_DEFAULT_CENTER, 13f)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> hasLocationPermission = result.values.any { it } }

    // Solo pedimos el diálogo automáticamente la primera vez. Si lo niega, se
    // muestra la pantalla explicativa con el botón a Ajustes.
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) permissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    // Al volver de Ajustes (ON_RESUME) re-chequeamos el permiso: si lo concedió,
    // hasLocationPermission pasa a true y dispara la carga.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Obtiene la ubicación REAL y carga el área en cuanto hay permiso. Sin
    // permiso no cargamos nada: se muestra la pantalla explicativa. Si el GPS
    // está frío, carga con Medellín como fallback y reintenta para recentrar.
    LaunchedEffect(hasLocationPermission) {
        if (centeredOnUser || !hasLocationPermission) return@LaunchedEffect
        val location = locationProvider.current()
        if (location != null) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(location, 14f)
            viewModel.loadArea(location.latitude, location.longitude)
            viewModel.reportLocation(location.latitude, location.longitude)
            centeredOnUser = true
        } else {
            viewModel.loadArea(BUSCAR_CUPO_DEFAULT_CENTER.latitude, BUSCAR_CUPO_DEFAULT_CENTER.longitude)
            val retry = locationProvider.current()
            if (retry != null) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(retry, 14f))
                viewModel.loadArea(retry.latitude, retry.longitude)
                viewModel.reportLocation(retry.latitude, retry.longitude)
            }
            centeredOnUser = true
        }
    }

    // Sin permiso de ubicación no podemos buscar cupos cerca: pantalla
    // explicativa con acceso a Ajustes, en vez de bloquear con una lista vacía.
    if (!hasLocationPermission) {
        LocationPermissionRequired(onOpenSettings = { openAppSettings(context) })
        return
    }

    Box(Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            SegmentedButton(
                selected = view == BuscarCupoView.LISTA,
                onClick = { view = BuscarCupoView.LISTA },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {},
                label = { Text("Lista") },
            )
            SegmentedButton(
                selected = view == BuscarCupoView.MAPA,
                onClick = { view = BuscarCupoView.MAPA },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {},
                label = { Text("Mapa") },
            )
        }

        when (view) {
            BuscarCupoView.LISTA -> ConvocatoryListContent(
                pins = state.pins,
                isLoading = state.isLoading,
                hasLoadedOnce = state.hasLoadedOnce,
                myPostulations = state.myPostulations,
                onClick = { selectedPin = it },
                onRefresh = { viewModel.reload() },
                modifier = Modifier.weight(1f),
            )
            // El mapa comparte el viewModel; al estar oculto no recarga porque
            // loadArea solo se dispara desde sus LaunchedEffect una vez.
            BuscarCupoView.MAPA -> Box(Modifier.weight(1f)) {
                MapTabContent(
                    viewModel = viewModel,
                    cameraPositionState = cameraPositionState,
                    hasLocationPermission = hasLocationPermission,
                    onPinClick = { selectedPin = it },
                    onGroupClick = { selectedGroup = it },
                )
            }
        }
      }

      SnackbarHost(
          snackbarHostState,
          modifier = Modifier.align(Alignment.BottomCenter),
      )
    }

    // Lista de convocatorias de una ubicación con varias (oculta si ya se eligió
    // una, para no apilar dos sheets).
    selectedGroup?.takeIf { selectedPin == null }?.let { group ->
        PinGroupSheet(
            convocatories = group,
            myPostulations = state.myPostulations,
            onSelect = { selectedPin = it },
            onDismiss = { selectedGroup = null },
        )
    }

    selectedPin?.let { pin ->
        PinDetailSheet(
            convocatory = pin,
            postulationStatus = state.myPostulations[pin.id],
            onDismiss = {
                selectedPin = null
                selectedGroup = null
            },
            // Al postularse: cierra el sheet, confirma con un snackbar en la
            // pantalla y recarga mis postulaciones para reflejar el nuevo estado.
            onApplied = {
                selectedPin = null
                selectedGroup = null
                viewModel.refreshMyPostulations()
                scope.launch {
                    snackbarHostState.showSnackbar("¡Postulación enviada! El organizador la revisará.")
                }
            },
        )
    }
}
