package com.eldraft.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Paleta "Fuego claro": la identidad naranja/rojo de la marca sobre fondo claro.
// La app es SIEMPRE clara (no hay modo oscuro ni se sigue el del sistema).
val OrangeVibrant = Color(0xFFFF5722) // primary (marca)
val OrangeContainer = Color(0xFFFFEDE6) // contenedor suave para chips/avatares
val OnOrangeContainer = Color(0xFFC5340A) // texto/íconos sobre el contenedor naranja
val RedVibrant = Color(0xFFE53935) // secondary / acento
val BlueSteel = Color(0xFF3A6EA5) // tertiary: estado "Cerrado / lleno" (neutro, no marca)

// Fondo gris neutro: contrasta sutilmente con las cards blancas para que
// "floten".
val LightBackground = Color(0xFFF2F2F4) // fondo de pantalla
val LightSurface = Color(0xFFFFFFFF) // superficies (cards)
val LightSurfaceVariant = Color(0xFFF2F1EC) // variante (campos, separadores suaves)
val OnLight = Color(0xFF1A1A1A) // texto principal
val OnLightVariant = Color(0xFF5F5F5F) // texto secundario
val LightOutline = Color(0xFFD9D7D0) // bordes/outline

private val ElDraftLightScheme = lightColorScheme(
    primary = OrangeVibrant,
    onPrimary = Color.White,
    primaryContainer = OrangeContainer,
    onPrimaryContainer = OnOrangeContainer,
    secondary = RedVibrant,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCDAD9),
    onSecondaryContainer = Color(0xFF8E1B19),
    tertiary = BlueSteel,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7E4F2),
    onTertiaryContainer = Color(0xFF1B3A5C),
    background = LightBackground,
    onBackground = OnLight,
    surface = LightSurface,
    onSurface = OnLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = OnLightVariant,
    outline = LightOutline,
    outlineVariant = Color(0xFFE6E4DD),
    error = Color(0xFFC5340A),
    onError = Color.White,
    errorContainer = Color(0xFFFCDAD9),
    onErrorContainer = Color(0xFF8E1B19),
)

@Composable
fun ElDraftTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalElDraftColors provides defaultElDraftColors(),
        LocalElDraftSpacing provides ElDraftSpacing(),
        LocalElDraftAlpha provides ElDraftAlpha(),
        LocalElDraftShapes provides ElDraftShapes(),
        LocalElDraftSizes provides ElDraftSizes(),
        LocalElDraftElevation provides ElDraftElevation(),
    ) {
        MaterialTheme(
            colorScheme = ElDraftLightScheme,
            typography = ElDraftTypography,
            shapes = ElDraftMaterialShapes,
            content = content,
        )
    }
}

/**
 * Accessor de los design tokens de la app: `ElDraftTheme.colors`, `.spacing`,
 * `.alpha`, `.shape`. Coexiste con el composable [ElDraftTheme] (mismo patrón que
 * el `MaterialTheme` de Material 3).
 */
object ElDraftTheme {
    val colors: ElDraftColors
        @Composable @ReadOnlyComposable get() = LocalElDraftColors.current
    val spacing: ElDraftSpacing
        @Composable @ReadOnlyComposable get() = LocalElDraftSpacing.current
    val alpha: ElDraftAlpha
        @Composable @ReadOnlyComposable get() = LocalElDraftAlpha.current
    val shape: ElDraftShapes
        @Composable @ReadOnlyComposable get() = LocalElDraftShapes.current
    val size: ElDraftSizes
        @Composable @ReadOnlyComposable get() = LocalElDraftSizes.current
    val elevation: ElDraftElevation
        @Composable @ReadOnlyComposable get() = LocalElDraftElevation.current
}
