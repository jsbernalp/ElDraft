package com.eldraft.android.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.R
import com.eldraft.android.ui.components.formatFee
import com.eldraft.android.ui.components.formatSchedule
import com.eldraft.android.ui.postulation.ApplyUiState
import com.eldraft.android.ui.postulation.ApplyViewModel
import com.eldraft.android.ui.theme.ElDraftTheme
import com.eldraft.data.models.Convocatory
import org.koin.androidx.compose.koinViewModel

/**
 * Bottom sheet con el detalle de una convocatoria al tocar su pin.
 * El botón "Postularme" envía la postulación (Fase 3) y refleja el estado.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PinDetailSheet(
    convocatory: Convocatory,
    onDismiss: () -> Unit,
    onApplied: () -> Unit = {},
    /** Estado de mi postulación a esta convocatoria, o null si aún no me postulé. */
    postulationStatus: String? = null,
    viewModel: ApplyViewModel = koinViewModel(),
) {
    // Si ya me postulé (pendiente/aprobada/rechazada), el backend no admite otra:
    // mostramos el estado y deshabilitamos el botón en vez de dejar postular y fallar.
    val alreadyApplied = postulationStatus != null
    val applyState by viewModel.state.collectAsStateWithLifecycle()
    // Snackbar SOLO para errores: el éxito cierra el sheet y lo confirma la
    // pantalla principal (un snackbar dentro del sheet queda mal anclado).
    val snackbarHostState = remember { SnackbarHostState() }

    // Posición elegida por el jugador para postularse. Se preselecciona si la
    // convocatoria pide una sola posición.
    var selectedPosition by remember(convocatory.id) {
        mutableStateOf(convocatory.positionSlots.singleOrNull()?.position)
    }

    // Al cambiar de convocatoria, resetea el estado del botón.
    LaunchedEffect(convocatory.id) { viewModel.reset() }

    LaunchedEffect(applyState) {
        when (val s = applyState) {
            is ApplyUiState.Applied -> {
                // Cierra el sheet; BuscarCupoScreen muestra la confirmación.
                viewModel.reset()
                onApplied()
            }
            is ApplyUiState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.reset()
            }
            else -> Unit
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
      Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = ElDraftTheme.spacing.xl, end = ElDraftTheme.spacing.xl, bottom = ElDraftTheme.spacing.xxl),
        ) {
            // --- Encabezado: formato + ambiente + dirección ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    convocatory.format.ifBlank { "Convocatoria" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                convocatory.ambiente.takeIf { it.isNotBlank() }?.let { AmbienteChip(it) }
            }
            convocatory.addressText?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(ElDraftTheme.spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Place,
                        contentDescription = null,
                        modifier = Modifier.size(ElDraftTheme.size.iconMd),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textTertiary),
                    )
                    Spacer(Modifier.width(ElDraftTheme.spacing.xs2))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textSecondary),
                    )
                }
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.lg2))

            // --- Mini-tarjetas con los datos clave ---
            Row(horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.md2)) {
                InfoPill(
                    icon = Icons.Filled.Groups,
                    value = convocatory.slotsNeeded.toString(),
                    label = "cupos",
                    modifier = Modifier.weight(1f),
                )
                InfoPill(
                    icon = Icons.Filled.LocalOffer,
                    value = formatFee(convocatory.fee),
                    label = "cuota",
                    modifier = Modifier.weight(1f),
                )
                formatSchedule(convocatory.scheduledAt)?.let { schedule ->
                    InfoPill(
                        icon = Icons.Filled.CalendarMonth,
                        value = schedule.substringAfter("· ").trim().ifBlank { schedule },
                        label = schedule.substringBefore(" ·").trim(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // --- Posiciones: chips que informan los cupos y eligen la posición ---
            if (convocatory.positionSlots.isNotEmpty()) {
                Spacer(Modifier.height(ElDraftTheme.spacing.lg2))
                Text(
                    if (alreadyApplied) stringResource(R.string.pin_positions)
                    else stringResource(R.string.pin_which_position),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(ElDraftTheme.spacing.md2))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.sm)) {
                    convocatory.positionSlots.forEach { ps ->
                        // Si ya me postulé, los chips son informativos (no seleccionables).
                        FilterChip(
                            selected = !alreadyApplied && selectedPosition == ps.position,
                            onClick = { if (!alreadyApplied) selectedPosition = ps.position },
                            enabled = !alreadyApplied,
                            label = { Text(stringResource(R.string.pin_position_slots, ps.position, ps.slots)) },
                        )
                    }
                }
            } else if (convocatory.positionRequired.isNotBlank()) {
                Spacer(Modifier.height(ElDraftTheme.spacing.lg2))
                InfoPill(
                    icon = Icons.Filled.Groups,
                    value = convocatory.positionRequired,
                    label = stringResource(R.string.pin_required_position),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.xl))

            val isSending = applyState is ApplyUiState.Sending
            val position = selectedPosition
            Button(
                onClick = { position?.let { viewModel.apply(convocatory.id, it) } },
                enabled = !alreadyApplied && !isSending && position != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                when {
                    // Ya me había postulado antes de abrir el sheet: refleja el estado.
                    alreadyApplied -> Text(
                        when (postulationStatus) {
                            "approved" -> stringResource(R.string.postulation_approved)
                            "rejected" -> stringResource(R.string.postulation_rejected)
                            "cancelled" -> stringResource(R.string.postulation_cancelled)
                            else -> stringResource(R.string.postulation_already)
                        }
                    )
                    isSending -> CircularProgressIndicator(
                        modifier = Modifier.size(ElDraftTheme.size.iconLg),
                        strokeWidth = ElDraftTheme.size.stroke,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    position == null -> Text(stringResource(R.string.pin_choose_position))
                    else -> Text(stringResource(R.string.postulation_apply_as, position))
                }
            }
        }

        // Snackbar de error anclado al fondo del sheet (flotando sobre el Column).
        SnackbarHost(
            snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(ElDraftTheme.spacing.lg),
        )
      }
    }
}

/** Mini-tarjeta con un dato clave: ícono arriba, valor grande y etiqueta. */
@Composable
private fun InfoPill(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = ElDraftTheme.shape.field,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ElDraftTheme.alpha.textMuted),
    ) {
        Column(
            modifier = Modifier.padding(vertical = ElDraftTheme.spacing.md, horizontal = ElDraftTheme.spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(ElDraftTheme.size.iconLg),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textTertiary),
            )
        }
    }
}

/** Chip de ambiente (Recocha / Competitivo …) con color de marca. */
@Composable
private fun AmbienteChip(ambiente: String) {
    Surface(
        shape = ElDraftTheme.shape.pill,
        color = MaterialTheme.colorScheme.primary.copy(alpha = ElDraftTheme.alpha.container),
    ) {
        Text(
            ambiente,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = ElDraftTheme.spacing.md, vertical = ElDraftTheme.spacing.xs2),
        )
    }
}
