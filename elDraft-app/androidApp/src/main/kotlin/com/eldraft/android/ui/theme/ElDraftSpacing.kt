package com.eldraft.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Escala de spacing de la app. Cubre los valores de padding/gap recurrentes en la
 * UI. Las dimensiones one-off de un componente concreto (alturas fijas, tamaños de
 * avatar, etc.) NO viven aquí: se dejan como literal local en su archivo.
 */
@Immutable
data class ElDraftSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val xs2: Dp = 6.dp,
    val sm: Dp = 8.dp,
    val md2: Dp = 10.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val lg2: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

val LocalElDraftSpacing = staticCompositionLocalOf { ElDraftSpacing() }
