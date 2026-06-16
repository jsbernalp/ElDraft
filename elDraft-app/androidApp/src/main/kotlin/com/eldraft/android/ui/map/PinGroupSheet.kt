package com.eldraft.android.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eldraft.android.ui.components.formatFee
import com.eldraft.android.ui.components.formatSchedule
import com.eldraft.data.models.Convocatory

/**
 * Bottom sheet que aparece al tocar un grupo de pines (varias convocatorias en
 * la misma ubicación). Lista cada convocatoria; al elegir una se abre su
 * detalle ([PinDetailSheet]) vía [onSelect].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinGroupSheet(
    convocatories: List<Convocatory>,
    /** convocatoryId -> estado de mi postulación; ausente si no me postulé. */
    myPostulations: Map<String, String>,
    onSelect: (Convocatory) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.width(8.dp))
                val address = convocatories.firstOrNull()
                    ?.addressText
                    ?.takeIf { it.isNotBlank() }
                    ?: "Esta ubicación"
                Text(
                    "$address · ${convocatories.size} partidos",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(convocatories, key = { it.id }) { convocatory ->
                    GroupRow(
                        convocatory = convocatory,
                        postulationStatus = myPostulations[convocatory.id],
                        onClick = { onSelect(convocatory) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupRow(convocatory: Convocatory, postulationStatus: String?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    convocatory.format.ifBlank { "Convocatoria" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val subtitle = buildString {
                    formatSchedule(convocatory.scheduledAt)?.let { append(it) }
                    if (isNotEmpty()) append(" · ")
                    append("${convocatory.slotsNeeded} cupos")
                    val fee = formatFee(convocatory.fee)
                    append(" · ").append(fee)
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            postulationStatus?.let {
                Spacer(Modifier.width(8.dp))
                RowPostulationBadge(it)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
    }
}

/** Chip de estado de mi postulación en una fila del grupo. */
@Composable
private fun RowPostulationBadge(status: String) {
    val color = when (status) {
        "approved" -> MaterialTheme.colorScheme.primary
        "rejected" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f)) {
        Text(
            postulationLabel(status),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
