package com.eldraft.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Roles de color semánticos que el [androidx.compose.material3.ColorScheme] de
 * Material no cubre. Los roles que SÍ existen en Material (primary, surface,
 * error, tertiary…) se siguen leyendo de `MaterialTheme.colorScheme` y NO se
 * duplican aquí.
 */
@Immutable
data class ElDraftColors(
    /** Verde de éxito: sección completada, asistencia registrada. */
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    /** Fondo a sangre del splash (igual a surface, pero explícito). */
    val splashBackground: Color,
    /** Gris acero del wordmark del splash. */
    val splashSteel: Color,
    /** Gris del tagline del splash. */
    val splashTagline: Color,
)

/** Paleta clara de la app (modo claro siempre). */
fun defaultElDraftColors() = ElDraftColors(
    success = Color(0xFF1E9E5A),
    onSuccess = Color.White,
    successContainer = Color(0xFFE5F5EC),
    splashBackground = Color(0xFFFFFFFF),
    splashSteel = Color(0xFF1C1D20),
    splashTagline = Color(0xFF8A8B90),
)

val LocalElDraftColors = staticCompositionLocalOf { defaultElDraftColors() }
