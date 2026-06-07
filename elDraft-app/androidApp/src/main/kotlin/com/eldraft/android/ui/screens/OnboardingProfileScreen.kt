package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingProfileScreen(onProfileComplete: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var positionPrimary by remember { mutableStateOf("") }
    var dominantFoot by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Text("Tu Ficha Técnica", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text("El Cromo", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Número de teléfono") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = positionPrimary,
            onValueChange = { positionPrimary = it },
            label = { Text("Posición principal") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        // TODO: reemplazar con DropdownMenu para Zurdo/Diestro/Ambidiestro
        OutlinedTextField(
            value = dominantFoot,
            onValueChange = { dominantFoot = it },
            label = { Text("Pierna hábil") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onProfileComplete,
            modifier = Modifier.fillMaxWidth(),
            enabled = phone.isNotBlank() && positionPrimary.isNotBlank()
        ) {
            Text("Guardar y continuar")
        }
    }
}
