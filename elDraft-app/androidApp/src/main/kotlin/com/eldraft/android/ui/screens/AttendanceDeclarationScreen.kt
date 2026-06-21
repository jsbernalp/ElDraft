package com.eldraft.android.ui.screens

import com.eldraft.android.ui.theme.ElDraftTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.eldraft.android.ui.attendance.AttendanceDeclarationViewModel
import com.eldraft.android.ui.components.BackTopBar
import com.eldraft.android.ui.components.EmptyState
import com.eldraft.android.ui.components.LoadingState
import com.eldraft.android.ui.components.ScreenHeader
import com.eldraft.data.models.PlayerAttendanceRow
import org.koin.androidx.compose.koinViewModel

/**
 * Pantalla del organizador para declarar quién de sus convocados NO llegó. Quien
 * escaneó el QR aparece como "Presente" firme (no editable); a los demás se les
 * puede marcar "No llegó". Lo no marcado cuenta como presente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceDeclarationScreen(
    convocatoryId: String,
    onBack: () -> Unit,
    viewModel: AttendanceDeclarationViewModel = koinViewModel(),
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
    LaunchedEffect(state.saved) {
        if (state.saved) snackbarHostState.showSnackbar("Asistencia guardada")
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { BackTopBar(onBack = onBack) },
        bottomBar = {
            if (state.rows.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Button(
                        onClick = { viewModel.save(convocatoryId) },
                        enabled = !state.isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ElDraftTheme.spacing.xl, vertical = ElDraftTheme.spacing.lg),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(ElDraftTheme.size.iconMd),
                                strokeWidth = ElDraftTheme.size.stroke,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Guardar asistencia")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = ElDraftTheme.spacing.xl),
        ) {
            ScreenHeader(title = "Asistencia", subtitle = "Marca quién no llegó al partido")
            Spacer(Modifier.height(ElDraftTheme.spacing.lg))

            when {
                state.isLoading && state.rows.isEmpty() -> LoadingState()
                // Si el organizador fue marcado no-show por consenso, el backend
                // responde 403 y la lista llega vacía: explicamos por qué.
                state.rows.isEmpty() && state.blocked -> EmptyState(
                    icon = "🚫",
                    title = "No puedes declarar la asistencia",
                    message = "El consenso de los convocados marcó que no llegaste a este partido.",
                )
                state.rows.isEmpty() -> EmptyState(
                    icon = "📋",
                    title = "Sin convocados",
                    message = "No hay jugadores aprobados en esta convocatoria.",
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.md),
                    contentPadding = PaddingValues(vertical = ElDraftTheme.spacing.md),
                ) {
                    items(state.rows, key = { it.playerId }) { row ->
                        AttendanceRowCard(
                            row = row,
                            absent = row.playerId in state.absent,
                            onToggle = { viewModel.toggleAbsent(row.playerId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceRowCard(
    row: PlayerAttendanceRow,
    absent: Boolean,
    onToggle: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ElDraftTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarCircle(name = row.name, avatarUrl = row.avatarUrl)
            Spacer(Modifier.width(ElDraftTheme.spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                row.positionPrimary?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textSecondary),
                    )
                }
            }

            when {
                // Escaneó el QR: presencia firme, no editable.
                row.scanned -> PresentBadge()
                // No escaneó: el organizador decide. Switch = ausente.
                else -> AbsentToggle(absent = absent, onToggle = onToggle)
            }
        }
    }
}

/** Distintivo fijo "Presente" para quien registró asistencia por QR. */
@Composable
private fun PresentBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = ElDraftTheme.alpha.containerSoft),
        shape = ElDraftTheme.shape.pill,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ElDraftTheme.spacing.md2, vertical = ElDraftTheme.spacing.xs2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs),
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(ElDraftTheme.size.iconSm),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Presente",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Control "Llegó / No llegó" para los convocados sin escaneo. */
@Composable
private fun AbsentToggle(absent: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.sm),
    ) {
        Text(
            if (absent) "No llegó" else "Llegó",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (absent) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textSecondary),
        )
        Switch(
            checked = absent,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onError,
                checkedTrackColor = MaterialTheme.colorScheme.error,
            ),
        )
    }
}

@Composable
private fun AvatarCircle(name: String, avatarUrl: String?) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = Modifier
            .size(ElDraftTheme.size.avatar)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = ElDraftTheme.alpha.containerStrong)),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Foto de $name",
                modifier = Modifier.size(ElDraftTheme.size.avatar).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                initial,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
