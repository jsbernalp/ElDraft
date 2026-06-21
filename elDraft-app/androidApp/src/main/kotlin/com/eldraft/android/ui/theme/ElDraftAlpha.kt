package com.eldraft.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opacidades semánticas. Reemplazan los `copy(alpha = X)` con números mágicos que
 * estaban repartidos por la UI. Se aplican sobre un color base (normalmente
 * `onSurface`/`onBackground`, a veces White/Black) según el contexto.
 */
@Immutable
data class ElDraftAlpha(
    /** Texto secundario (subtítulos, descripciones). */
    val textSecondary: Float = 0.7f,
    /** Texto terciario / metadatos / captions. */
    val textTertiary: Float = 0.6f,
    /** Texto atenuado: hints, placeholders, estados neutros. */
    val textMuted: Float = 0.5f,
    /** Tint de íconos de metadatos. */
    val icon: Float = 0.55f,
    /** Contenido deshabilitado (alpha estándar de M3). */
    val disabled: Float = 0.38f,
    /** Fondo de contenedor fuerte (chips destacados). */
    val containerStrong: Float = 0.2f,
    /** Fondo de contenedor (status badges). */
    val container: Float = 0.15f,
    /** Fondo de contenedor suave (chips sutiles). */
    val containerSoft: Float = 0.12f,
    /** Separadores / divisores. */
    val divider: Float = 0.08f,
    /** Bordes muy sutiles (hairline). */
    val hairline: Float = 0.1f,
    /** Overlays oscuros sobre contenido (scrim). */
    val scrim: Float = 0.55f,
)

val LocalElDraftAlpha = staticCompositionLocalOf { ElDraftAlpha() }
