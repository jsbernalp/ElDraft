package com.eldraft.android.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eldraft.data.models.Convocatory

/**
 * Bottom sheet con el detalle de una convocatoria al tocar su pin.
 * La acción de postularse se conecta en Fase 3 (Postulaciones).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinDetailSheet(
    convocatory: Convocatory,
    onDismiss: () -> Unit,
    onApply: (Convocatory) -> Unit,
) {
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
            if (convocatory.positionRequired.isNotBlank()) {
                DetailRow("Posición requerida", convocatory.positionRequired)
            }
            if (convocatory.ambiente.isNotBlank()) {
                DetailRow("Ambiente", convocatory.ambiente)
            }
            DetailRow("Cuota por jugador", if (convocatory.fee > 0) "$${convocatory.fee.toInt()}" else "Gratis")
            if (convocatory.scheduledAt.isNotBlank()) {
                DetailRow("Fecha", convocatory.scheduledAt.replace("T", " · "))
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { onApply(convocatory) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Postularme a este cupo")
            }
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
