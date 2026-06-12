package com.eldraft.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 * Cabecera estándar de pantalla: título grande + subtítulo de acento.
 *
 * Política de la app: TODA pantalla de contenido encabeza con este componente
 * (excepto Splash y Login, que tienen layout de marca centrado). El subtítulo
 * es contextual a cada pantalla. Mantiene la jerarquía y el tono coherentes en
 * toda la navegación.
 *
 * En pantallas de layout centrado (p. ej. el código QR) pasar
 * [horizontalAlignment] = [Alignment.CenterHorizontally].
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    val textAlign = if (horizontalAlignment == Alignment.CenterHorizontally) TextAlign.Center else TextAlign.Start
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = textAlign,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = textAlign,
        )
    }
}
