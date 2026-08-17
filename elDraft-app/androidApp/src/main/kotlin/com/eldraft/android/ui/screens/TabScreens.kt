package com.eldraft.android.ui.screens

import com.eldraft.android.ui.theme.ElDraftTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.R
import com.eldraft.android.ui.attendance.NoShowViewModel
import com.eldraft.android.ui.components.EmptyState
import com.eldraft.android.ui.components.IconFee
import com.eldraft.android.ui.components.IconGroups
import com.eldraft.android.ui.components.IconPlace
import com.eldraft.android.ui.components.LoadingState
import com.eldraft.android.ui.components.MatchListTab
import com.eldraft.android.ui.components.MatchListTabs
import com.eldraft.android.ui.components.MetaItem
import com.eldraft.android.ui.components.ScheduleBanner
import com.eldraft.android.ui.components.ScreenHeader
import com.eldraft.android.ui.components.MatchStateBadge
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
    val cancelledMessage = stringResource(R.string.convocatory_cancelled_snackbar)
    // rememberSaveable: la pestaña sobrevive a rotar y a salir y volver al tab.
    var tab by rememberSaveable { mutableStateOf(MatchListTab.PROXIMOS) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }
    // El error de cancelación NO va al snackbar de la pantalla: el sheet se dibuja
    // en su propia ventana, por encima, y lo tapaba por completo (había que cerrar
    // el sheet para leerlo). Se muestra dentro del sheet, junto a los botones.
    LaunchedEffect(cancelState.success) {
        if (cancelState.success) {
            cancelTargetId = null
            cancelViewModel.resetSuccess()
            viewModel.load()
            snackbarHostState.showSnackbar(cancelledMessage)
        }
    }

    if (cancelTargetId != null) {
        CancelConvocatorySheet(
            isLoading = cancelState.isLoading,
            error = cancelState.error,
            onConfirm = { reason -> cancelViewModel.cancel(cancelTargetId!!, reason) },
            onDismiss = {
                cancelTargetId = null
                cancelViewModel.clearError()
            },
        )
    }

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { viewModel.load() },
        modifier = Modifier.fillMaxSize(),
    ) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = ElDraftTheme.spacing.lg)) {
            Spacer(Modifier.height(ElDraftTheme.spacing.lg))
            ScreenHeader(
                title = stringResource(R.string.organize_header_title),
                subtitle = stringResource(R.string.organize_header_subtitle),
            )
            Spacer(Modifier.height(ElDraftTheme.spacing.lg2))

            // Los terminados que siguen aquí es porque queda algo por hacer
            // (declarar asistencia o calificar): el backend ya retiró los que no
            // piden nada. Van en su propia pestaña para no mezclarse con los que
            // aún no se juegan, y el badge dice cuántos son.
            val (finished, upcoming) = state.matches.partition { isMatchOver(it.scheduledAt) }

            if (state.isLoading && state.matches.isEmpty()) {
                LoadingState()
            } else if (state.matches.isEmpty()) {
                // Sin nada que conmutar, el conmutador estorba.
                EmptyState(
                    icon = "⚽",
                    title = stringResource(R.string.organize_empty_title),
                    message = stringResource(R.string.organize_empty_message),
                )
            } else {
                MatchListTabs(
                    selected = tab,
                    pendingCount = finished.size,
                    onSelect = { tab = it },
                )
                Spacer(Modifier.height(ElDraftTheme.spacing.md))

                val shown = if (tab == MatchListTab.PROXIMOS) upcoming else finished
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.md),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    if (shown.isEmpty()) {
                        item {
                            if (tab == MatchListTab.PROXIMOS) {
                                // No es "aún no has creado": creaste, pero ya se
                                // jugaron y están en la otra pestaña.
                                EmptyState(
                                    icon = "⚽",
                                    title = stringResource(R.string.upcoming_empty_title),
                                    message = stringResource(R.string.organize_upcoming_empty_message),
                                    modifier = Modifier.fillParentMaxHeight(),
                                )
                            } else {
                                EmptyState(
                                    icon = "✅",
                                    title = stringResource(R.string.closed_empty_title),
                                    message = stringResource(R.string.closed_empty_message),
                                    modifier = Modifier.fillParentMaxHeight(),
                                )
                            }
                        }
                    } else {
                        if (tab == MatchListTab.CERRADOS) {
                            // Sin esto parece que la lista no se limpia sola.
                            item {
                                Text(
                                    stringResource(R.string.closed_caption_organize),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                        .copy(alpha = ElDraftTheme.alpha.textSecondary),
                                )
                            }
                        }
                        items(shown, key = { it.id }) { match ->
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
                .padding(end = ElDraftTheme.spacing.lg, bottom = ElDraftTheme.spacing.xl),
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            Text(stringResource(R.string.fab_add))
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
    /** Fallo del último intento de cancelar, o null. Se muestra dentro del sheet. */
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedReason by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ElDraftTheme.spacing.xl)
                .padding(bottom = ElDraftTheme.spacing.xxl),
        ) {
            // El encabezado y los motivos scrollean si no caben; los botones van
            // fuera de este bloque para que nunca queden fuera de pantalla
            // (weight(fill = false) deja que la hoja siga siendo wrap-content
            // cuando el contenido es corto).
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.sm),
            ) {
                Text(
                    stringResource(R.string.cancel_match_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.cancel_match_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Los motivos como chips: cinco filas de radio ocupaban casi toda
                // la hoja; envueltos caben en dos.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs2),
                    verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs2),
                ) {
                    CANCELLATION_REASONS.forEach { reason ->
                        FilterChip(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                            enabled = !isLoading,
                            label = { Text(reason) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.lg))

            // Va en el footer fijo, no en el bloque scrolleable: un error que hay
            // que buscar scrolleando es un error que no se lee.
            error?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(
                            horizontal = ElDraftTheme.spacing.md,
                            vertical = ElDraftTheme.spacing.sm,
                        ),
                    )
                }
                Spacer(Modifier.height(ElDraftTheme.spacing.sm))
            }

            Button(
                onClick = { selectedReason?.let { onConfirm(it) } },
                enabled = selectedReason != null && !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ElDraftTheme.size.iconMd),
                        strokeWidth = ElDraftTheme.size.stroke,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.cancel_match_confirm))
                }
            }
            Spacer(Modifier.height(ElDraftTheme.spacing.sm))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
            ) {
                Text(stringResource(R.string.action_back_short))
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
    val bg = if (primary) MaterialTheme.colorScheme.primary.copy(alpha = ElDraftTheme.alpha.containerSoft)
             else MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.divider)
    val textColor = if (primary) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textTertiary)
    Surface(color = bg, shape = ElDraftTheme.shape.pill) {
        Row(
            modifier = Modifier.padding(horizontal = ElDraftTheme.spacing.sm, vertical = ElDraftTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs),
        ) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(ElDraftTheme.size.iconXs), tint = textColor)
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
        Column(Modifier.padding(ElDraftTheme.spacing.lg)) {
            // Fecha/hora destacada + estado.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ScheduleBanner(match.scheduledAt)
                MatchStateBadge(match.scheduledAt)
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.md2))

            // Título: formato.
            Text(
                match.format.ifBlank { stringResource(R.string.match_card_fallback_title) },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(ElDraftTheme.spacing.sm))

            // Metadatos como chips: ambiente, dirección, cupos y cuota en una sola línea envolvente.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs2),
                verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs2),
            ) {
                match.ambiente.takeIf { it.isNotBlank() }?.let {
                    MetaChip(text = it, primary = true)
                }
                match.addressText?.takeIf { it.isNotBlank() }?.let {
                    MetaChip(icon = IconPlace, text = it)
                }
                MetaChip(icon = IconGroups, text = stringResource(R.string.match_slots_summary, match.slotsNeeded, match.positionRequired))
                MetaChip(icon = IconFee, text = formatFee(match.fee))
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.md2))

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
                    label = { Text(stringResource(R.string.organize_view_applicants)) },
                    leadingIcon = { Icon(IconGroups, contentDescription = null) },
                )
            }

            // Aviso si el consenso marcó al organizador como ausente: no podrá
            // declarar la asistencia (el botón queda deshabilitado).
            if (match.organizerNoShow) {
                Spacer(Modifier.height(ElDraftTheme.spacing.md))
                OrganizerNoShowBanner()
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.sm))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.divider))
            Spacer(Modifier.height(ElDraftTheme.spacing.xs))

            // Acciones del día del partido como accesos rápidos (ícono en pastilla
            // + etiqueta). "Ya llegué" es la acción primaria (pastilla sólida); las
            // demás usan pastilla tonal. La gestión de postulantes vive en el header.
            // Ya escaneó: ofrecerle otra vez "Ya llegué" es pedirle algo que ya
            // hizo. Se cambia por la confirmación de que quedó registrado.
            if (match.attended) {
                AttendanceRegistered()
                Spacer(Modifier.height(ElDraftTheme.spacing.xs))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                // El organizador marca su propia presencia escaneando el QR que le
                // genere un aprobado: ya no se asume presente.
                if (!match.attended) {
                    QuickAction(
                        icon = Icons.Filled.WhereToVote,
                        label = stringResource(R.string.action_arrived),
                        onClick = onOpenQrScanner,
                        primary = true,
                    )
                }
                QuickAction(
                    icon = Icons.Filled.QrCode2,
                    label = stringResource(R.string.action_show_qr),
                    onClick = onOpenQrGenerator,
                )
                // Tras el cierre del partido (inicio + 45 min): el organizador
                // declara quién no llegó y puede calificar a los presentes.
                if (isMatchOver(match.scheduledAt)) {
                    // Si el consenso lo marcó ausente, no puede declarar asistencia.
                    QuickAction(
                        icon = Icons.Filled.Checklist,
                        label = stringResource(R.string.action_attendance),
                        onClick = onOpenAttendance,
                        enabled = !match.organizerNoShow,
                    )
                    QuickAction(
                        icon = Icons.Filled.Star,
                        label = stringResource(R.string.action_rate),
                        onClick = onOpenRating,
                    )
                }
            }

            // Botón cancelar: solo visible si el partido aún no comenzó y está activo.
            if (match.status in listOf("active", "full") && !isMatchOver(match.scheduledAt)) {
                Spacer(Modifier.height(ElDraftTheme.spacing.sm))
                TextButton(
                    onClick = onCancelMatch,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.cancel_match_button))
                }
            }
        }
    }
}

/**
 * Acceso rápido: ícono en pastilla circular con etiqueta debajo. [primary]
 * resalta la acción principal con pastilla sólida (las demás van tonales).
 */
/**
 * Confirmación de que la asistencia quedó registrada. Sustituye al botón "Ya
 * llegué" en cuanto el escaneo se valida: repetir la acción no haría nada y deja
 * la duda de si funcionó.
 */
@Composable
private fun AttendanceRegistered() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs2),
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ElDraftTheme.size.iconMd),
        )
        Text(
            stringResource(R.string.attendance_registered),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

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
    val container = if (enabled) baseContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.containerSoft)
    val tint = if (enabled) baseTint else MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.disabled)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs2),
        modifier = Modifier
            .clip(ElDraftTheme.shape.sm)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = ElDraftTheme.spacing.sm, vertical = ElDraftTheme.spacing.xs),
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
    val withdrawnMessage = stringResource(R.string.postulation_withdrawn)
    var tab by rememberSaveable { mutableStateOf(MatchListTab.PROXIMOS) }

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
            snackbarHostState.showSnackbar(withdrawnMessage)
        }
    }

    if (withdrawTargetId != null) {
        WithdrawPostulationDialog(
            isLoading = state.withdrawingId != null,
            // Dentro del diálogo, no en el snackbar: el diálogo está encima y lo taparía.
            error = state.withdrawError,
            onConfirm = { viewModel.withdraw(withdrawTargetId!!) },
            onDismiss = {
                withdrawTargetId = null
                viewModel.clearWithdrawError()
            },
        )
    }

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = { viewModel.load() },
        modifier = Modifier.fillMaxSize(),
    ) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = ElDraftTheme.spacing.lg)) {
            Spacer(Modifier.height(ElDraftTheme.spacing.lg))
            ScreenHeader(
                title = stringResource(R.string.play_header_title),
                subtitle = stringResource(R.string.play_header_subtitle),
            )
            Spacer(Modifier.height(ElDraftTheme.spacing.lg2))

            // Cerrado es todo lo que ya no está en juego para el jugador: los
            // partidos terminados que aún le piden algo (calificar o reportar que
            // el organizador no llegó) y los rechazos, que no piden nada pero
            // tampoco son "próximos" — ese partido se juega sin él.
            val (closed, upcoming) = state.postulations.partition {
                isMatchOver(it.convocatory.scheduledAt) || it.status == "rejected"
            }
            // El badge cuenta SOLO lo accionable: sumar rechazos lo convertiría en
            // un aviso de nada, y esos se aprenden a ignorar.
            val pendingCount = closed.count { isMatchOver(it.convocatory.scheduledAt) }

            if (state.isLoading && state.postulations.isEmpty()) {
                LoadingState()
            } else if (state.postulations.isEmpty()) {
                EmptyState(
                    icon = "🏃",
                    title = stringResource(R.string.play_empty_title),
                    message = stringResource(R.string.play_empty_message),
                )
            } else {
                MatchListTabs(
                    selected = tab,
                    pendingCount = pendingCount,
                    onSelect = { tab = it },
                )
                Spacer(Modifier.height(ElDraftTheme.spacing.md))

                val shown = if (tab == MatchListTab.PROXIMOS) upcoming else closed
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.md),
                    contentPadding = PaddingValues(bottom = ElDraftTheme.spacing.xl),
                ) {
                    if (shown.isEmpty()) {
                        item {
                            if (tab == MatchListTab.PROXIMOS) {
                                EmptyState(
                                    icon = "🏃",
                                    title = stringResource(R.string.upcoming_empty_title),
                                    message = stringResource(R.string.play_empty_message),
                                    modifier = Modifier.fillParentMaxHeight(),
                                )
                            } else {
                                EmptyState(
                                    icon = "✅",
                                    title = stringResource(R.string.closed_empty_title),
                                    message = stringResource(R.string.closed_empty_message),
                                    modifier = Modifier.fillParentMaxHeight(),
                                )
                            }
                        }
                    } else {
                        if (tab == MatchListTab.CERRADOS) {
                            item {
                                Text(
                                    stringResource(R.string.closed_caption_play),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                        .copy(alpha = ElDraftTheme.alpha.textSecondary),
                                )
                            }
                        }
                        items(shown, key = { it.id }) { p ->
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
    /** Fallo del último intento de retirar, o null. Se muestra dentro del diálogo. */
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(stringResource(R.string.withdraw_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.withdraw_dialog_message))
                error?.let { message ->
                    Spacer(Modifier.height(ElDraftTheme.spacing.md))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(
                                horizontal = ElDraftTheme.spacing.md,
                                vertical = ElDraftTheme.spacing.sm,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ElDraftTheme.size.iconMd),
                        strokeWidth = ElDraftTheme.size.stroke,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.withdraw_confirm_short))
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isLoading) {
                Text(stringResource(R.string.action_cancel))
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
        Column(Modifier.padding(ElDraftTheme.spacing.lg)) {
            // Fecha/hora + estado de la postulación.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ScheduleBanner(c.scheduledAt)
                StatusChip(postulation.status)
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.md2))

            Text(
                c.format.ifBlank { stringResource(R.string.match_card_fallback_title) },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(ElDraftTheme.spacing.sm))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs2),
                verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs2),
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
                    Spacer(Modifier.height(ElDraftTheme.spacing.md))
                    MarkedNoShowBanner()
                }

                // "Calificar" solo tras el fin estimado del partido (inicio + 45 min)
                // o si ya se confirmó que el organizador no llegó (no hubo partido
                // que esperar).
                val showRate = isMatchOver(c.scheduledAt) || consensusNoShow

                Spacer(Modifier.height(ElDraftTheme.spacing.sm))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.divider))
                Spacer(Modifier.height(ElDraftTheme.spacing.xs))

                // Ya escaneó: "Ya llegué" sobra y en su lugar va la confirmación.
                if (postulation.attended) {
                    AttendanceRegistered()
                    Spacer(Modifier.height(ElDraftTheme.spacing.xs))
                }

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
                        val noMapsMessage = stringResource(R.string.directions_no_maps_app)
                        QuickAction(
                            icon = Icons.Filled.Directions,
                            label = stringResource(R.string.action_directions),
                            onClick = {
                                val ok = openDirections(context, c.lat, c.lng, c.addressText)
                                if (!ok) {
                                    Toast.makeText(
                                        context,
                                        noMapsMessage,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    }
                    if (!postulation.attended) {
                        QuickAction(
                            icon = Icons.Filled.WhereToVote,
                            label = stringResource(R.string.action_arrived),
                            onClick = onScanQr,
                            primary = true,
                        )
                    }
                    // Un aprobado puede generar el QR para que el organizador lo escane.
                    QuickAction(
                        icon = Icons.Filled.QrCode2,
                        label = stringResource(R.string.action_show_qr),
                        onClick = onGenerateQr,
                    )
                    if (showRate) {
                        QuickAction(
                            icon = Icons.Filled.Star,
                            label = stringResource(R.string.action_rate),
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
                    Spacer(Modifier.height(ElDraftTheme.spacing.sm))
                    TextButton(
                        onClick = onWithdraw,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.withdraw_confirm))
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
        shape = ElDraftTheme.shape.sm,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ElDraftTheme.spacing.md, vertical = ElDraftTheme.spacing.md2),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.sm),
        ) {
            Icon(
                Icons.Filled.ReportProblem,
                contentDescription = null,
                modifier = Modifier.size(ElDraftTheme.size.iconMd),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column {
                Text(
                    stringResource(R.string.organizer_no_show_banner_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    stringResource(R.string.attendance_no_show_consensus),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f), // design-tokens-ignore: legibilidad sobre errorContainer
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
        shape = ElDraftTheme.shape.sm,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ElDraftTheme.spacing.md, vertical = ElDraftTheme.spacing.md2),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.sm),
        ) {
            Icon(
                Icons.Filled.ReportProblem,
                contentDescription = null,
                modifier = Modifier.size(ElDraftTheme.size.iconMd),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column {
                Text(
                    stringResource(R.string.attendance_no_show_by_organizer),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    stringResource(R.string.marked_no_show_banner_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f), // design-tokens-ignore: legibilidad sobre errorContainer
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

    Spacer(Modifier.height(ElDraftTheme.spacing.md2))

    when {
        status.consensusReached -> Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.organizer_no_show_consensus),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = ElDraftTheme.spacing.md, vertical = ElDraftTheme.spacing.sm),
            )
        }
        status.alreadyReported -> Text(
            stringResource(R.string.organizer_no_show_reported, status.reports, status.attendees),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textTertiary),
        )
        canReportNow -> OutlinedButton(
            onClick = { showConfirm = true },
            enabled = !uiState.isReporting,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Filled.ReportProblem, contentDescription = null, modifier = Modifier.size(ElDraftTheme.size.iconMd))
            Spacer(Modifier.width(ElDraftTheme.spacing.sm))
            Text(stringResource(R.string.organizer_no_show_button))
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.no_show_report_title)) },
            text = {
                Text(stringResource(R.string.organizer_no_show_dialog))
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onReport()
                }) { Text(stringResource(R.string.organizer_no_show_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun StatusChip(status: String) {
    val (label, color) = when (status) {
        "approved" -> stringResource(R.string.game_status_approved) to MaterialTheme.colorScheme.primary
        "rejected" -> stringResource(R.string.game_status_rejected) to MaterialTheme.colorScheme.error
        "cancelled" -> stringResource(R.string.game_status_cancelled) to MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textMuted)
        else -> stringResource(R.string.game_status_pending) to MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textMuted)
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
    val postulationSentMessage = stringResource(R.string.postulation_sent)

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
                .padding(horizontal = ElDraftTheme.spacing.lg, vertical = ElDraftTheme.spacing.md),
        ) {
            SegmentedButton(
                selected = view == BuscarCupoView.LISTA,
                onClick = { view = BuscarCupoView.LISTA },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {},
                label = { Text(stringResource(R.string.search_toggle_list)) },
            )
            SegmentedButton(
                selected = view == BuscarCupoView.MAPA,
                onClick = { view = BuscarCupoView.MAPA },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {},
                label = { Text(stringResource(R.string.search_toggle_map)) },
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
                    snackbarHostState.showSnackbar(postulationSentMessage)
                }
            },
        )
    }
}
