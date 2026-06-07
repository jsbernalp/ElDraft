package com.eldraft.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta "Fuego y Asfalto"
val OrangeVibrant = Color(0xFFFF5722)
val RedVibrant = Color(0xFFE53935)
val AsphaltDark = Color(0xFF121212)
val AsphaltMedium = Color(0xFF1E1E1E)
val AsphaltLight = Color(0xFF2C2C2C)
val OnAsphalt = Color(0xFFF5F5F5)

private val ElDraftDarkScheme = darkColorScheme(
    primary = OrangeVibrant,
    secondary = RedVibrant,
    background = AsphaltDark,
    surface = AsphaltMedium,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = OnAsphalt,
    onSurface = OnAsphalt,
    surfaceVariant = AsphaltLight
)

@Composable
fun ElDraftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ElDraftDarkScheme,
        typography = ElDraftTypography,
        content = content
    )
}
