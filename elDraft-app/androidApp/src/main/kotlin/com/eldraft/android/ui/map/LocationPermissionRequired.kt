package com.eldraft.android.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eldraft.android.ui.theme.ElDraftTheme

/**
 * Pantalla que se muestra en "Buscar Cupo" cuando no hay permiso de ubicación.
 * Explica por qué lo necesitamos y ofrece un botón que abre los Ajustes del
 * sistema para concederlo (más fiable que reintentar el diálogo, que Android
 * deja de mostrar tras varias negativas).
 */
@Composable
fun LocationPermissionRequired(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ElDraftTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.LocationOff,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(ElDraftTheme.spacing.lg))
        Text(
            "Activa tu ubicación",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(ElDraftTheme.spacing.sm))
        Text(
            "Usamos tu ubicación para mostrarte los partidos abiertos más cerca de ti. " +
                "Sin este permiso no podemos buscar cupos en tu zona.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = ElDraftTheme.alpha.textSecondary),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(ElDraftTheme.spacing.xl))
        Button(onClick = onOpenSettings) {
            Text("Abrir ajustes")
        }
    }
}
