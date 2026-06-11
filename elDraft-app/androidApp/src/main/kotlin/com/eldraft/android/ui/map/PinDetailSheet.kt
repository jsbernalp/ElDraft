package com.eldraft.android.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
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
import com.eldraft.android.ui.postulation.ApplyUiState
import com.eldraft.android.ui.postulation.ApplyViewModel
import com.eldraft.data.models.Convocatory
import org.koin.androidx.compose.koinViewModel

/**
 * Bottom sheet con el detalle de una convocatoria al tocar su pin.
 * El botón "Postularme" envía la postulación (Fase 3) y refleja el estado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinDetailSheet(
    convocatory: Convocatory,
    onDismiss: () -> Unit,
    onApplied: () -> Unit = {},
    viewModel: ApplyViewModel = koinViewModel(),
) {
    val applyState by viewModel.state.collectAsStateWithLifecycle()
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
                snackbarHostState.showSnackbar("¡Postulación enviada! El organizador la revisará.")
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text(
                convocatory.format.ifBlank { "Convocatoria" },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            convocatory.addressText?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }

            Spacer(Modifier.height(20.dp))

            DetailRow("Cupos necesarios", convocatory.slotsNeeded.toString())
            // Desglose de cupos por posición.
            if (convocatory.positionSlots.isNotEmpty()) {
                convocatory.positionSlots.forEach { ps ->
                    DetailRow(ps.position, "${ps.slots}")
                }
            } else if (convocatory.positionRequired.isNotBlank()) {
                DetailRow("Posición requerida", convocatory.positionRequired)
            }
            if (convocatory.ambiente.isNotBlank()) {
                DetailRow("Ambiente", convocatory.ambiente)
            }
            DetailRow("Cuota por jugador", if (convocatory.fee > 0) "$${convocatory.fee.toInt()}" else "Gratis")
            if (convocatory.scheduledAt.isNotBlank()) {
                DetailRow("Fecha", convocatory.scheduledAt.replace("T", " · "))
            }

            // Selector de posición para postularse (solo si pide más de una).
            if (convocatory.positionSlots.size > 1) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "¿En qué posición te postulas?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    convocatory.positionSlots.forEach { ps ->
                        FilterChip(
                            selected = selectedPosition == ps.position,
                            onClick = { selectedPosition = ps.position },
                            label = { Text(ps.position) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            val isSending = applyState is ApplyUiState.Sending
            val isApplied = applyState is ApplyUiState.Applied
            val position = selectedPosition
            Button(
                onClick = { position?.let { viewModel.apply(convocatory.id, it) } },
                enabled = !isSending && !isApplied && position != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                when {
                    isSending -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    isApplied -> Text("✓ Postulación enviada")
                    position == null -> Text("Elige una posición")
                    else -> Text("Postularme como $position")
                }
            }

            SnackbarHost(snackbarHostState)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    }
}
