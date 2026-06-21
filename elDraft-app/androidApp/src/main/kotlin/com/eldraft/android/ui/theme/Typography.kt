package com.eldraft.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ElDraftTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * Estilos tipográficos de marca que no encajan en un rol de [Typography]
 * (se usan en un único sitio muy específico). Viven aparte para no alterar los
 * roles estándar de Material que comparten muchas pantallas.
 */
object ElDraftTextStyles {
    /** Wordmark "elDraft" del splash. */
    val Wordmark = TextStyle(
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        letterSpacing = (-0.5).sp,
    )

    /** Tagline del splash: mayúsculas con tracking ancho (overline de marca). */
    val Tagline = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 4.sp,
    )
}
