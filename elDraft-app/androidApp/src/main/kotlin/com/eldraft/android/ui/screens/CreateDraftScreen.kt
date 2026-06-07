package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CreateDraftScreen(onDraftCreated: () -> Unit) {
    var slots by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("0") }
    var format by remember { mutableStateOf("Fútbol 5") }
    var ambiente by remember { mutableStateOf("Recocha") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Text("Nueva Convocatoria", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text("El Draft", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(value = slots, onValueChange = { slots = it }, label = { Text("Cupos necesarios") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = position, onValueChange = { position = it }, label = { Text("Posición requerida") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = fee, onValueChange = { fee = it }, label = { Text("Cuota ($)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))

        // TODO: reemplazar con DropdownMenu
        OutlinedTextField(value = format, onValueChange = { format = it }, label = { Text("Formato") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))

        Row {
            FilterChip(selected = ambiente == "Recocha", onClick = { ambiente = "Recocha" }, label = { Text("Recocha") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = ambiente == "Competitivo", onClick = { ambiente = "Competitivo" }, label = { Text("Competitivo") })
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onDraftCreated,
            modifier = Modifier.fillMaxWidth(),
            enabled = slots.isNotBlank() && position.isNotBlank()
        ) {
            Text("Publicar convocatoria")
        }
    }
}
