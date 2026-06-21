package com.eldraft.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Radios de esquina de la app. [pill] es totalmente redondeado (chips/badges); el
 * resto escalan de menor a mayor. Para los call sites que hoy escriben
 * `RoundedCornerShape(N)` a mano.
 */
@Immutable
data class ElDraftShapes(
    val pill: Shape = RoundedCornerShape(percent = 50),
    val sm: Shape = RoundedCornerShape(12.dp),
    val field: Shape = RoundedCornerShape(14.dp),
    val md: Shape = RoundedCornerShape(16.dp),
    val lg: Shape = RoundedCornerShape(20.dp),
)

val LocalElDraftShapes = staticCompositionLocalOf { ElDraftShapes() }

/**
 * Shapes de Material 3 derivados de la misma escala, para que los componentes
 * Material (Card, Button, TextField…) adopten el radio de marca por defecto.
 */
val ElDraftMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
