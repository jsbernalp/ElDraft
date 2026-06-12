package com.eldraft.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Íconos canónicos de las métricas de reputación del jugador. Punto único de
 * verdad: todas las pantallas que muestran habilidad/deportividad/
 * responsabilidad/asistencia usan estos íconos vectoriales (no emojis) para
 * mantener un lenguaje visual consistente y tinte de marca.
 */
object MetricIcons {
    val Skill: ImageVector = Icons.Filled.SportsSoccer
    val Sportsmanship: ImageVector = Icons.Filled.Handshake
    val Responsibility: ImageVector = Icons.Filled.AssignmentTurnedIn
    val Attendance: ImageVector = Icons.Filled.EventAvailable
}
