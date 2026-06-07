package com.eldraft.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.ui.components.DropdownField
import com.eldraft.android.ui.components.LocationPickerMap
import com.eldraft.android.ui.draft.CreateDraftUiState
import com.eldraft.android.ui.draft.CreateDraftViewModel
import com.eldraft.data.models.CreateConvocatoryRequest
import com.google.android.gms.maps.model.LatLng
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val FORMATS = listOf("Fútbol 5", "Fútbol 7", "Fútbol 11")
private val POSITIONS = listOf("Arquero", "Defensa", "Mediocampista", "Delantero", "Extremo")
// Centro por defecto: Medellín (se ajusta si hay ubicación seleccionada)
private val DEFAULT_LOCATION = LatLng(6.2442, -75.5812)

@Composable
fun CreateDraftScreen(
    onDraftCreated: () -> Unit,
    viewModel: CreateDraftViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var slots by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("0") }
    var format by remember { mutableStateOf("Fútbol 5") }
    var ambiente by remember { mutableStateOf("Recocha") }
    var addressText by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf<LatLng?>(null) }

    LaunchedEffect(state) {
        when (val s = state) {
            is CreateDraftUiState.Created -> onDraftCreated()
            is CreateDraftUiState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.resetError()
            }
            else -> Unit
        }
    }

    val isSaving = state is CreateDraftUiState.Saving
    val canSave = slots.toIntOrNull() != null &&
        position.isNotBlank() &&
        selectedLocation != null &&
        !isSaving

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Nueva Convocatoria", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text("El Draft", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)

            Spacer(Modifier.height(24.dp))

            Text("Ubicación de la cancha", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
            Text(
                if (selectedLocation == null) "Toca el mapa para marcar la cancha"
                else "Lat ${"%.4f".format(selectedLocation!!.latitude)}, Lng ${"%.4f".format(selectedLocation!!.longitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))
            LocationPickerMap(
                initialLocation = DEFAULT_LOCATION,
                selectedLocation = selectedLocation,
                onLocationSelected = { selectedLocation = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = addressText,
                onValueChange = { addressText = it },
                label = { Text("Referencia / dirección (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            DropdownField(label = "Formato", options = FORMATS, selected = format, onSelected = { format = it })
            Spacer(Modifier.height(16.dp))
            DropdownField(label = "Posición requerida *", options = POSITIONS, selected = position, onSelected = { position = it })

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = slots,
                onValueChange = { if (it.all(Char::isDigit) && it.length <= 2) slots = it },
                label = { Text("Cupos necesarios *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = fee,
                onValueChange = { if (it.all(Char::isDigit) && it.length <= 7) fee = it },
                label = { Text("Cuota por jugador ($)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            Text("Ambiente", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Row {
                FilterChip(selected = ambiente == "Recocha", onClick = { ambiente = "Recocha" }, label = { Text("Recocha") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = ambiente == "Competitivo", onClick = { ambiente = "Competitivo" }, label = { Text("Competitivo") })
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    val loc = selectedLocation ?: return@Button
                    viewModel.create(
                        CreateConvocatoryRequest(
                            lat = loc.latitude,
                            lng = loc.longitude,
                            addressText = addressText.ifBlank { null },
                            slotsNeeded = slots.toInt(),
                            positionRequired = position,
                            fee = fee.toDoubleOrNull() ?: 0.0,
                            format = format,
                            ambiente = ambiente,
                            // MVP: programada para mañana a las 19:00. Un date/time picker
                            // completo se añadirá más adelante.
                            scheduledAt = LocalDateTime.now()
                                .plusDays(1)
                                .withHour(19).withMinute(0).withSecond(0).withNano(0)
                                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        )
                    )
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Publicar convocatoria")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
