package com.eldraft.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.eldraft.android.ui.theme.ElDraftTheme
import com.eldraft.data.models.PlayerProfile

/**
 * Carta visual del Cromo de un jugador (ficha técnica + reputación), con estilo
 * de "carta coleccionable": cabecera de marca, posiciones en chips, ficha en
 * grid y reputación con barras de progreso.
 *
 * Componente compartido entre [com.eldraft.android.ui.screens.PlayerCromoScreen]
 * (cromo ajeno) y el tab Perfil (cromo propio). El parámetro [footer] permite
 * que el tab Perfil inyecte sus acciones ("Editar perfil" / "Cerrar sesión").
 */
@Composable
fun CromoContent(
    profile: PlayerProfile,
    modifier: Modifier = Modifier,
    name: String? = null,
    avatarUrl: String? = null,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ElDraftTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = ElDraftTheme.shape.lg,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CromoHeader(profile = profile, name = name, avatarUrl = avatarUrl)

            Column(Modifier.padding(ElDraftTheme.spacing.lg)) {
                TechnicalGrid(profile)

                Spacer(Modifier.height(ElDraftTheme.spacing.lg2))

                // Reputación entre pares (calificación post-partido en 3 criterios).
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs2)) {
                    Icon(
                        MetricIcons.Skill,
                        contentDescription = null,
                        modifier = Modifier.size(ElDraftTheme.spacing.lg),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Reputación entre pares",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textTertiary),
                    )
                }
                Spacer(Modifier.height(ElDraftTheme.spacing.md))

                ScoreBar(MetricIcons.Skill, "Habilidad", profile.skillScore)
                Spacer(Modifier.height(ElDraftTheme.spacing.md))
                ScoreBar(MetricIcons.Sportsmanship, "Deportividad", profile.sportsmanshipScore)
                Spacer(Modifier.height(ElDraftTheme.spacing.md))
                ScoreBar(MetricIcons.Responsibility, "Responsabilidad", profile.responsibilityScore)
            }
        }

        footer()
    }
}

/** Cabecera tipo carta: banda de marca con avatar, nombre, posiciones y partidos. */
@Composable
private fun CromoHeader(profile: PlayerProfile, name: String?, avatarUrl: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(vertical = ElDraftTheme.spacing.lg2, horizontal = ElDraftTheme.spacing.lg),
    ) {
        // Badge de partidos jugados, esquina superior derecha.
        Surface(
            color = Color.White.copy(alpha = ElDraftTheme.alpha.containerStrong),
            shape = ElDraftTheme.shape.pill,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Text(
                "${profile.totalMatches} partidos",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = ElDraftTheme.spacing.md2, vertical = ElDraftTheme.spacing.xs),
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Avatar con borde claro.
            Box(
                modifier = Modifier
                    .size(ElDraftTheme.size.avatarLg)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Foto de perfil",
                        modifier = Modifier.size(82.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        (name?.trim()?.firstOrNull() ?: profile.positionPrimary.firstOrNull())?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (!name.isNullOrBlank()) {
                Spacer(Modifier.height(ElDraftTheme.spacing.md2))
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs2)) {
                PositionChip(profile.positionPrimary, primary = true)
                profile.positionSecondary?.takeIf { it.isNotBlank() }?.let {
                    PositionChip(it, primary = false)
                }
            }
        }
    }
}

@Composable
private fun PositionChip(text: String, primary: Boolean) {
    Surface(
        color = Color.White.copy(alpha = if (primary) 0.28f else 0.16f),
        shape = ElDraftTheme.shape.pill,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
            color = Color.White,
            modifier = Modifier.padding(horizontal = ElDraftTheme.spacing.md, vertical = ElDraftTheme.spacing.xs),
        )
    }
}

/** Ficha técnica en grid 2×2 de mini-cards. */
@Composable
private fun TechnicalGrid(profile: PlayerProfile) {
    Row(horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.md2)) {
        InfoTile("Pierna hábil", profile.dominantFoot, Modifier.weight(1f))
        InfoTile("Altura", profile.height?.let { "$it cm" } ?: "—", Modifier.weight(1f))
    }
    Spacer(Modifier.height(ElDraftTheme.spacing.md2))
    Row(horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.md2)) {
        InfoTile("Contextura", profile.build ?: "—", Modifier.weight(1f))
        InfoTile("Asistencia", "${profile.attendancePct.toInt()}%", Modifier.weight(1f), accent = true)
    }
}

@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = ElDraftTheme.shape.sm,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = ElDraftTheme.spacing.md, vertical = ElDraftTheme.spacing.md2)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.icon),
            )
            Spacer(Modifier.height(ElDraftTheme.spacing.xxs))
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Una métrica de reputación: ícono + label + nota + barra de progreso (0..5). */
@Composable
private fun ScoreBar(icon: ImageVector, label: String, score: Double) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.xs2)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(ElDraftTheme.spacing.lg), tint = MaterialTheme.colorScheme.primary)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                fmtScore(score),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(ElDraftTheme.spacing.xs))
        LinearProgressIndicator(
            progress = { (score / 5.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(ElDraftTheme.shape.pill),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/** Formatea una nota 0..5 a un decimal (p. ej. 4.3), sin ceros sobrantes. */
private fun fmtScore(value: Double): String {
    val rounded = kotlin.math.round(value * 10) / 10
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
