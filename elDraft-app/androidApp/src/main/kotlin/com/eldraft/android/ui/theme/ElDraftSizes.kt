package com.eldraft.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tamaños de ícono de la app. Unifican los `Modifier.size(N.dp)` que se aplicaban
 * a íconos por toda la UI. Las dimensiones one-off de componente (avatares,
 * alturas de mapa/QR, badges) NO viven aquí: se dejan como literal en su archivo.
 */
@Immutable
data class ElDraftSizes(
    /** Ícono diminuto (chips de metadatos compactos). */
    val iconXs: Dp = 12.dp,
    /** Ícono pequeño (metadatos de card). */
    val iconSm: Dp = 16.dp,
    /** Ícono mediano (acciones, banners). El más común. */
    val iconMd: Dp = 18.dp,
    /** Ícono grande (botones destacados, headers). */
    val iconLg: Dp = 20.dp,
    /** Avatar circular estándar (cards de personas). */
    val avatar: Dp = 44.dp,
    /** Avatar mediano (edición de perfil). */
    val avatarMd: Dp = 64.dp,
    /** Avatar grande (cabecera del Cromo). */
    val avatarLg: Dp = 88.dp,
    /** Grosor de línea de spinners e indicadores de progreso. */
    val stroke: Dp = 2.dp,
    /** Borde fino (outlines de campos en estado de error). */
    val borderHairline: Dp = 1.dp,
)

val LocalElDraftSizes = staticCompositionLocalOf { ElDraftSizes() }
