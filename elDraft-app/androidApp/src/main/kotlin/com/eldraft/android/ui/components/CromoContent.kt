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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.eldraft.data.models.PlayerProfile

/**
 * Tarjeta visual del Cromo de un jugador (ficha técnica + reputación).
 *
 * Componente compartido entre [com.eldraft.android.ui.screens.PlayerCromoScreen]
 * (cromo ajeno, abierto desde el mapa/postulantes) y el tab Perfil
 * (cromo propio, dentro del NavigationBar). El parámetro [footer] permite que
 * el tab Perfil inyecte sus acciones ("Editar perfil" / "Cerrar sesión") sin
 * duplicar el cuerpo de la ficha.
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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("El Cromo", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)

        Spacer(Modifier.height(24.dp))

        // Avatar: foto real si hay URL, si no la inicial de la posición.
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Foto de perfil",
                    modifier = Modifier.size(96.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    profile.positionPrimary.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (!name.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(Modifier.height(16.dp))

        InfoRow("Posición principal", profile.positionPrimary)
        profile.positionSecondary?.let { InfoRow("Posición secundaria", it) }
        InfoRow("Pierna hábil", profile.dominantFoot)
        profile.height?.let { InfoRow("Altura", "$it cm") }
        profile.build?.let { InfoRow("Contextura", it) }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
        Spacer(Modifier.height(24.dp))

        // Reputación entre pares (calificación post-partido en 3 criterios).
        InfoRow("⚽ Habilidad", "${fmtScore(profile.skillScore)} / 5")
        InfoRow("🤝 Deportividad", "${fmtScore(profile.sportsmanshipScore)} / 5")
        InfoRow("📋 Responsabilidad", "${fmtScore(profile.responsibilityScore)} / 5")
        InfoRow("Asistencia", "${profile.attendancePct.toInt()}%")
        InfoRow("Partidos jugados", profile.totalMatches.toString())

        footer()
    }
}

/** Formatea una nota 0..5 a un decimal (p. ej. 4.3), sin ceros sobrantes. */
private fun fmtScore(value: Double): String {
    val rounded = kotlin.math.round(value * 10) / 10
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Text(value, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
    }
}
