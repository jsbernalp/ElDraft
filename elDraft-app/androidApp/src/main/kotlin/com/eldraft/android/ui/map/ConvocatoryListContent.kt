package com.eldraft.android.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.eldraft.android.ui.components.EmptyState
import com.eldraft.android.ui.components.IconFee
import com.eldraft.android.ui.components.IconGroups
import com.eldraft.android.ui.components.IconPlace
import com.eldraft.android.ui.components.LoadingState
import com.eldraft.android.ui.components.MetaItem
import com.eldraft.android.ui.components.ScheduleBanner
import com.eldraft.android.ui.components.formatFee
import com.eldraft.android.ui.theme.ElDraftTheme
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
    /** False hasta la primera carga: muestra loading inicial en vez del estado vacío. */
    hasLoadedOnce: Boolean,
    /** convocatoryId -> estado de mi postulación ("pending"/"approved"/"rejected"); ausente si no me postulé. */
    myPostulations: Map<String, String>,
    onClick: (Convocatory) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // La carga inicial (sin datos todavía) muestra LoadingState de pantalla completa.
    if (!hasLoadedOnce && pins.isEmpty()) {
        LoadingState(modifier)
        return
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (pins.isEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(ElDraftTheme.spacing.lg),
            ) {
                item {
                    EmptyState(
                        title = "No hay partidos cerca",
                        message = "Aún no hay convocatorias abiertas en tu zona. Vuelve más tarde o amplía el área en el mapa.",
                        icon = "⚽",
                        modifier = Modifier.fillParentMaxHeight(),
                    )
                }
            }
        } else {
            val sorted = pins.sortedBy { it.scheduledAt }
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(ElDraftTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.md),
            ) {
                items(sorted, key = { it.id }) { convocatory ->
                    ConvocatoryListCard(
                        convocatory = convocatory,
                        postulationStatus = myPostulations[convocatory.id],
                        onClick = { onClick(convocatory) },
                    )
                }
            }
        }
    }
}

/** Tarjeta completa de una convocatoria: hora, formato, cupos, cuota, dirección, ambiente y posiciones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConvocatoryListCard(
    convocatory: Convocatory,
    postulationStatus: String?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ElDraftTheme.shape.md,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = ElDraftTheme.elevation.card),
    ) {
        Column(modifier = Modifier.padding(ElDraftTheme.spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ScheduleBanner(convocatory.scheduledAt)
                postulationStatus?.let { PostulationBadge(it) }
            }
            Spacer(Modifier.height(ElDraftTheme.spacing.sm))

            Text(
                convocatory.format.ifBlank { "Convocatoria" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            convocatory.addressText?.takeIf { it.isNotBlank() }?.let { address ->
                Spacer(Modifier.height(ElDraftTheme.spacing.xs2))
                MetaItem(icon = IconPlace, text = address)
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.md2))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.lg)) {
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
                Spacer(Modifier.height(ElDraftTheme.spacing.md))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.sm)) {
                    positions.forEach { PositionChip(it) }
                }
            }
        }
    }
}

/**
 * Etiqueta legible del estado de mi postulación. Cualquier estado bloquea
 * volver a postularse (el backend solo admite una postulación por
 * convocatoria), por eso todas se muestran. "cancelled" = la canceló el sistema
 * por chocar con otro partido en el que te aceptaron.
 */
fun postulationLabel(status: String): String = when (status) {
    "approved" -> "Aprobada"
    "rejected" -> "Rechazada"
    "cancelled" -> "Cancelada"
    else -> "Ya te postulaste"
}

/** Chip que indica el estado de mi postulación en la card. */
@Composable
private fun PostulationBadge(status: String) {
    val color = when (status) {
        "approved" -> MaterialTheme.colorScheme.primary
        "rejected" -> MaterialTheme.colorScheme.error
        "cancelled" -> MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textTertiary)
        else -> MaterialTheme.colorScheme.tertiary
    }
    Surface(shape = ElDraftTheme.shape.pill, color = color.copy(alpha = ElDraftTheme.alpha.container)) {
        Text(
            postulationLabel(status),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = ElDraftTheme.spacing.md2, vertical = ElDraftTheme.spacing.xs),
        )
    }
}

@Composable
private fun PositionChip(text: String) {
    Surface(
        shape = ElDraftTheme.shape.pill,
        color = MaterialTheme.colorScheme.primary.copy(alpha = ElDraftTheme.alpha.containerSoft),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = ElDraftTheme.spacing.md2, vertical = ElDraftTheme.spacing.xs),
        )
    }
}
