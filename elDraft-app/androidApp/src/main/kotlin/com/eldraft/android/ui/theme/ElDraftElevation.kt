package com.eldraft.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevaciones de la app. Unifican los `defaultElevation`/`tonalElevation` que
 * estaban sueltos en cards y superficies.
 */
@Immutable
data class ElDraftElevation(
    /** Card estándar (lista de convocatorias). */
    val card: Dp = 2.dp,
    /** Card destacada (secciones del formulario). */
    val cardRaised: Dp = 3.dp,
    /** Superficie flotante sobre contenido (overlays, pills sobre cámara). */
    val overlay: Dp = 4.dp,
)

val LocalElDraftElevation = staticCompositionLocalOf { ElDraftElevation() }
