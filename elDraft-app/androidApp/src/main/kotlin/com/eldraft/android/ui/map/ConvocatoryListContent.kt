package com.eldraft.android.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eldraft.android.ui.components.EmptyState
import com.eldraft.android.ui.components.IconFee
import com.eldraft.android.ui.components.IconGroups
import com.eldraft.android.ui.components.IconPlace
import com.eldraft.android.ui.components.LoadingState
import com.eldraft.android.ui.components.MetaItem
import com.eldraft.android.ui.components.ScheduleBanner
import com.eldraft.android.ui.components.formatFee
import com.eldraft.data.models.Convocatory

/**
 * Vista de lista de "Buscar Cupo": las mismas convocatorias del mapa, pero como
 * tarjetas escaneables verticalmente. Comparte el estado con el mapa (los pines
 * vienen de [MapViewModel]); al tocar una tarjeta se abre el mismo
 * [PinDetailSheet] vía [onClick].
 *
 * Orden por defecto: por hora del partido (los más próximos a empezar primero).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvocatoryListContent(
    pins: List<Convocatory>,
    isLoading: Boolean,
    onClick: (Convocatory) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading && pins.isEmpty() -> LoadingState(modifier)
        pins.isEmpty() -> EmptyState(
            title = "No hay partidos cerca",
            message = "Aún no hay convocatorias abiertas en tu zona. Vuelve más tarde o amplía el área en el mapa.",
            icon = "⚽",
            modifier = modifier,
        )
        else -> {
            // Orden por hora de inicio; las que no parsean van al final.
            val sorted = pins.sortedBy { it.scheduledAt }
            LazyColumn(
                modifier = modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sorted, key = { it.id }) { convocatory ->
                    ConvocatoryListCard(convocatory = convocatory, onClick = { onClick(convocatory) })
                }
            }
        }
    }
}

/** Tarjeta completa de una convocatoria: hora, formato, cupos, cuota, dirección, ambiente y posiciones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConvocatoryListCard(convocatory: Convocatory, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ScheduleBanner(convocatory.scheduledAt)
            Spacer(Modifier.height(8.dp))

            Text(
                convocatory.format.ifBlank { "Convocatoria" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            convocatory.addressText?.takeIf { it.isNotBlank() }?.let { address ->
                Spacer(Modifier.height(6.dp))
                MetaItem(icon = IconPlace, text = address)
            }

            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetaItem(icon = IconGroups, text = "${convocatory.slotsNeeded} cupos")
                MetaItem(icon = IconFee, text = formatFee(convocatory.fee))
                if (convocatory.ambiente.isNotBlank()) {
                    MetaItem(icon = IconGroups, text = convocatory.ambiente)
                }
            }

            // Desglose de posiciones requeridas como chips.
            val positions = convocatory.positionSlots
                .takeIf { it.isNotEmpty() }
                ?.map { "${it.position} ×${it.slots}" }
                ?: convocatory.positionRequired
                    .takeIf { it.isNotBlank() }
                    ?.let { listOf(it) }
                    .orEmpty()
            if (positions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    positions.forEach { PositionChip(it) }
                }
            }
        }
    }
}

@Composable
private fun PositionChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
