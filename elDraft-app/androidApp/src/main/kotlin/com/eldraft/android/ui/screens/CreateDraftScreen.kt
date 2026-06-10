package com.eldraft.android.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.ui.components.DropdownField
import com.eldraft.android.ui.components.LocationPickerMap
import com.eldraft.android.ui.components.PlaceAutocompleteField
import com.eldraft.android.ui.draft.CreateDraftUiState
import com.eldraft.android.ui.draft.CreateDraftViewModel
import com.eldraft.android.util.LOCATION_PERMISSIONS
import com.eldraft.android.util.rememberLocationProvider
import com.eldraft.data.models.CreateConvocatoryRequest
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FORMATS = listOf("Fútbol 5", "Fútbol 7", "Fútbol 11")
private val POSITIONS = listOf("Arquero", "Defensa", "Mediocampista", "Delantero", "Extremo")
// Centro por defecto: Medellín (se ajusta si hay ubicación seleccionada)
private val DEFAULT_LOCATION = LatLng(6.2442, -75.5812)
// Formatos legibles para mostrar la fecha/hora elegida.
private val DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale("es"))
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDraftScreen(
    onDraftCreated: () -> Unit,
    viewModel: CreateDraftViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val locationProvider = rememberLocationProvider(context)

    var slots by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("0") }
    var format by remember { mutableStateOf("Fútbol 5") }
    var ambiente by remember { mutableStateOf("Recocha") }
    var addressText by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf<LatLng?>(null) }

    // Fecha/hora del partido. Por defecto: mañana a las 19:00.
    var scheduledAt by remember {
        mutableStateOf(
            LocalDateTime.now().plusDays(1).withHour(19).withMinute(0).withSecond(0).withNano(0),
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Estado de cámara hoisted: el buscador lo recentra al elegir una dirección.
    // (this.position evita el shadowing con la variable local `position`.)
    val cameraPositionState = rememberCameraPositionState {
        this.position = CameraPosition.fromLatLngZoom(DEFAULT_LOCATION, 14f)
    }

    // Centra el mapa en la ubicación actual del usuario al abrir el formulario
    // (sin marcar el punto: el usuario igual elige la cancha tocando/buscando).
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var centeredOnUser by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> hasLocationPermission = result.values.any { it } }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) permissionLauncher.launch(LOCATION_PERMISSIONS)
    }
    LaunchedEffect(hasLocationPermission) {
        if (centeredOnUser || !hasLocationPermission) return@LaunchedEffect
        locationProvider.current()?.let { here ->
            cameraPositionState.position = CameraPosition.fromLatLngZoom(here, 15f)
        }
        centeredOnUser = true
    }

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
    // scheduledAt es válido si está al menos 1 hora en el futuro.
    val minScheduledAt = LocalDateTime.now().plusHours(1)
    val scheduledAtValid = scheduledAt.isAfter(minScheduledAt)
    val canSave = slots.toIntOrNull() != null &&
        position.isNotBlank() &&
        selectedLocation != null &&
        scheduledAtValid &&
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
                if (selectedLocation == null) "Busca una dirección o toca el mapa para marcar la cancha"
                else "Lat ${"%.4f".format(selectedLocation!!.latitude)}, Lng ${"%.4f".format(selectedLocation!!.longitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))

            // Buscador de direcciones (Places Autocomplete): centra el mapa y
            // coloca el marcador al elegir una sugerencia.
            PlaceAutocompleteField(
                onPlaceSelected = { selection ->
                    selectedLocation = selection.location
                    if (addressText.isBlank()) addressText = selection.description
                    cameraPositionState.position =
                        CameraPosition.fromLatLngZoom(selection.location, 16f)
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            LocationPickerMap(
                cameraPositionState = cameraPositionState,
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

            // Fecha y hora del partido
            Text("Fecha y hora", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Text(scheduledAt.format(DATE_FMT))
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f),
                    colors = if (!scheduledAtValid) ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ) else ButtonDefaults.outlinedButtonColors(),
                    border = if (!scheduledAtValid)
                        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    else null,
                ) {
                    Text(scheduledAt.format(TIME_FMT))
                }
            }
            if (!scheduledAtValid) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "La hora debe ser al menos 1 hora en el futuro",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

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
                            scheduledAt = scheduledAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
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

    // --- Diálogos de selección de fecha/hora ---
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = scheduledAt
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                // utcTimeMillis representa la medianoche UTC del día de cada celda.
                // "Hoy" debe calcularse en la zona horaria local del usuario; usar
                // LocalDate.now(UTC) adelanta el día por la noche en zonas UTC- (p.ej.
                // UTC-5) y rechazaría incorrectamente el día actual.
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val todayLocalAsUtcMidnight = LocalDate.now(ZoneId.systemDefault())
                        .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
                    return utcTimeMillis >= todayLocalAsUtcMidnight
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        val candidate = picked.atTime(scheduledAt.toLocalTime())
                        val min = LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)
                        // Si al cambiar la fecha la hora queda inválida, ajustamos al mínimo.
                        scheduledAt = if (candidate.isBefore(min)) min else candidate
                    }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = scheduledAt.hour,
            initialMinute = scheduledAt.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val candidate = scheduledAt
                        .withHour(timePickerState.hour)
                        .withMinute(timePickerState.minute)
                        .withSecond(0)
                        .withNano(0)
                    val min = LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)
                    // Si la fecha/hora elegida no cumple el mínimo, ajustamos al mínimo.
                    scheduledAt = if (candidate.isBefore(min)) min else candidate
                    showTimePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
            },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
        )
    }
}
