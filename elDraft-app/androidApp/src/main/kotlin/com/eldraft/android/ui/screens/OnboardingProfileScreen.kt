package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.ui.elDraftViewModel
import com.eldraft.android.ui.profile.OnboardingUiState
import com.eldraft.android.ui.profile.ProfileViewModel

private val POSITIONS = listOf("Arquero", "Defensa", "Mediocampista", "Delantero", "Extremo")
private val FEET = listOf("Derecho", "Zurdo", "Ambidiestro")
private val BUILDS = listOf("Delgado", "Atlético", "Robusto")

@Composable
fun OnboardingProfileScreen(
    onProfileComplete: () -> Unit,
    viewModel: ProfileViewModel = elDraftViewModel()
) {
    val state by viewModel.onboarding.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var phone by remember { mutableStateOf("") }
    var positionPrimary by remember { mutableStateOf("") }
    var positionSecondary by remember { mutableStateOf("") }
    var dominantFoot by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var build by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        when (val s = state) {
            is OnboardingUiState.Saved -> onProfileComplete()
            is OnboardingUiState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.resetOnboardingError()
            }
            else -> Unit
        }
    }

    val isSaving = state is OnboardingUiState.Saving
    val canSave = positionPrimary.isNotBlank() && dominantFoot.isNotBlank() && !isSaving

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        // El contenedor raíz (ElDraftApp) ya aplica safeDrawing (incluye barras + IME);
        // evitamos doble padding poniendo los insets del Scaffold en cero.
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Tu Ficha Técnica", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text("El Cromo", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Número de teléfono") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            DropdownField(
                label = "Posición principal *",
                options = POSITIONS,
                selected = positionPrimary,
                onSelected = { positionPrimary = it }
            )

            Spacer(Modifier.height(16.dp))

            DropdownField(
                label = "Posición secundaria",
                options = POSITIONS,
                selected = positionSecondary,
                onSelected = { positionSecondary = it }
            )

            Spacer(Modifier.height(16.dp))

            DropdownField(
                label = "Pierna hábil *",
                options = FEET,
                selected = dominantFoot,
                onSelected = { dominantFoot = it }
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = height,
                onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 3) height = it },
                label = { Text("Altura (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            DropdownField(
                label = "Contextura física",
                options = BUILDS,
                selected = build,
                onSelected = { build = it }
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    viewModel.saveProfile(
                        positionPrimary = positionPrimary,
                        positionSecondary = positionSecondary,
                        dominantFoot = dominantFoot,
                        height = height.toIntOrNull(),
                        build = build,
                        phone = phone
                    )
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Guardar y continuar")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
