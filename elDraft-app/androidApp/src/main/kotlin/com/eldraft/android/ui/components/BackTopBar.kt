package com.eldraft.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

/**
 * Barra superior con botón de "Volver". Patrón único para las pantallas
 * secundarias (las que se abren por navegación push), para no depender solo del
 * gesto/botón de back del sistema.
 *
 * El [title] es opcional: déjalo vacío cuando la pantalla ya muestra su propio
 * encabezado (título + subtítulo) en el cuerpo y solo quieres la flecha.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(
    onBack: () -> Unit,
    title: String = "",
) {
    TopAppBar(
        title = { if (title.isNotBlank()) Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}
