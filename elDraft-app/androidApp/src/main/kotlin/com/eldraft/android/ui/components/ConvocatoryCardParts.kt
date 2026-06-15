package com.eldraft.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CARD_DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale("es"))
private val CARD_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Formatea un `scheduledAt` en ISO_LOCAL_DATE_TIME a algo legible
 * (p. ej. "vie 13 jun · 20:30"). Devuelve null si no se puede parsear.
 */
fun formatSchedule(scheduledAt: String): String? = runCatching {
    val dt = LocalDateTime.parse(scheduledAt)
    "${dt.format(CARD_DATE_FMT)} · ${dt.format(CARD_TIME_FMT)}"
}.getOrNull()

/**
 * Fase temporal del partido respecto a su hora de inicio, calculada con la hora
 * del dispositivo. Sirve para decidir qué acciones mostrar en las cards:
 *  - [BEFORE_REPORT]   < inicio + 15 min  (recién empieza / aún no inicia)
 *  - [REPORT_WINDOW]   inicio + 15 min .. inicio + 45 min
 *  - [AFTER_END]       >= inicio + 45 min (partido considerado terminado)
 */
enum class MatchPhase { BEFORE_REPORT, REPORT_WINDOW, AFTER_END, UNKNOWN }

// Umbrales de fase del partido (minutos desde el inicio).
private const val NO_SHOW_OPEN_MINUTES = 15L
private const val MATCH_END_MINUTES = 45L

/** Minutos transcurridos desde la hora de inicio (negativo si aún no inicia). */
fun minutesSinceStart(scheduledAt: String): Long? = runCatching {
    val start = LocalDateTime.parse(scheduledAt)
    java.time.Duration.between(start, LocalDateTime.now()).toMinutes()
}.getOrNull()

/** Fase del partido según los minutos transcurridos desde el inicio. */
fun matchPhase(scheduledAt: String): MatchPhase {
    val mins = minutesSinceStart(scheduledAt) ?: return MatchPhase.UNKNOWN
    return when {
        mins >= MATCH_END_MINUTES -> MatchPhase.AFTER_END
        mins >= NO_SHOW_OPEN_MINUTES -> MatchPhase.REPORT_WINDOW
        else -> MatchPhase.BEFORE_REPORT
    }
}

/** True si ya pasó la ventana de tolerancia para reportar no-show (inicio + 15 min). */
fun canReportNoShowByTime(scheduledAt: String): Boolean =
    (minutesSinceStart(scheduledAt) ?: Long.MIN_VALUE) >= NO_SHOW_OPEN_MINUTES

/** True si el partido ya se considera terminado (inicio + 45 min). */
fun isMatchOver(scheduledAt: String): Boolean =
    (minutesSinceStart(scheduledAt) ?: Long.MIN_VALUE) >= MATCH_END_MINUTES

/** Formatea el costo: "Gratis" si es 0, si no "$X". */
fun formatFee(fee: Double): String =
    if (fee <= 0.0) "Gratis" else "$" + (if (fee % 1.0 == 0.0) fee.toInt().toString() else fee.toString())

/** Banda superior con fecha/hora del partido sobre fondo de acento. */
@Composable
fun ScheduleBanner(scheduledAt: String, modifier: Modifier = Modifier) {
    val text = formatSchedule(scheduledAt) ?: return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Filled.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Chip de estado de la convocatoria (Activo / Cerrado / Finalizado / Cancelado). */
@Composable
fun StatusBadge(status: String) {
    val (label, container, content) = when (status) {
        "active" -> Triple("Activo", MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), MaterialTheme.colorScheme.primary)
        "closed", "full" -> Triple("Cerrado", MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f), MaterialTheme.colorScheme.tertiary)
        "finished" -> Triple("Finalizado", MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        "cancelled" -> Triple("Cancelado", MaterialTheme.colorScheme.error.copy(alpha = 0.12f), MaterialTheme.colorScheme.error)
        else -> Triple(status.replaceFirstChar { it.uppercase() }, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Una entrada de la fila de metadatos: ícono + texto. */
@Composable
fun MetaItem(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

// Íconos reexportados para que las cards no importen cada uno por separado.
val IconPlace: ImageVector get() = Icons.Filled.Place
val IconGroups: ImageVector get() = Icons.Filled.Groups
val IconFee: ImageVector get() = Icons.Filled.LocalOffer
