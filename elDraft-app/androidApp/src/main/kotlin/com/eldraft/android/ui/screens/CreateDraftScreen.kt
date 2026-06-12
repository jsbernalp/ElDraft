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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.ui.components.BackTopBar
import com.eldraft.android.ui.components.CollapsibleFormSection
import com.eldraft.android.ui.components.ScreenHeader
import com.eldraft.android.ui.components.DropdownField
import com.eldraft.android.ui.components.LocationPickerMap
import com.eldraft.android.ui.components.PlaceAutocompleteField
import com.eldraft.android.ui.draft.CreateDraftUiState
import com.eldraft.android.ui.draft.CreateDraftViewModel
import com.eldraft.android.util.LOCATION_PERMISSIONS
import com.eldraft.android.util.rememberLocationProvider
import com.eldraft.data.models.CreateConvocatoryRequest
import com.eldraft.data.models.PositionSlot
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

/** Secciones plegables del formulario. NONE = todas colapsadas. */
private enum class FormSectionId { LOCATION, DATE, MATCH, SQUAD, NONE }
// Centro por defecto: Medellín (se ajusta si hay ubicación seleccionada)
private val DEFAULT_LOCATION = LatLng(6.2442, -75.5812)
// Formatos legibles para mostrar la fecha/hora elegida.
private val DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale("es"))
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDraftScreen(
    onDraftCreated: () -> Unit,
    onBack: () -> Unit,
    viewModel: CreateDraftViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val locationProvider = rememberLocationProvider(context)

    // Requerimientos por posición: la posición (clave) y sus cupos. Se preserva
    // el orden de inserción para mostrarlos como el organizador los agrega.
    val positionSlots = remember { mutableStateListOf<PositionSlot>() }
    val totalSlots = positionSlots.sumOf { it.slots }
    var fee by remember { mutableStateOf("0") }
    var format by remember { mutableStateOf("Fútbol 5") }
    var ambiente by remember { mutableStateOf("") }
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
    val canSave = positionSlots.isNotEmpty() &&
        totalSlots in 1..30 &&
        selectedLocation != null &&
        scheduledAtValid &&
        !isSaving

    // --- Formulario por secciones plegables ---
    // Fecha/hora arranca con un valor por defecto válido, así que NO debe marcarse
    // completa hasta que el usuario lo confirme: este flag se activa al usar el picker.
    var dateTouched by remember { mutableStateOf(false) }
    // Convocatoria se confirma manualmente (cuota es opcional): no auto-completa.
    var squadConfirmed by remember { mutableStateOf(false) }

    // Validez de cada sección (alimenta el check de "completada").
    // Ubicación, Datos del partido (ambiente sin preselección) y Convocatoria parten
    // sin valor, así que su validez ya implica que el usuario interactuó.
    val locationComplete = selectedLocation != null
    val dateComplete = dateTouched && scheduledAtValid
    val matchComplete = format.isNotBlank() && ambiente.isNotBlank()
    val squadComplete = squadConfirmed && positionSlots.isNotEmpty() && totalSlots in 1..30
    val completedCount = listOf(locationComplete, dateComplete, matchComplete, squadComplete).count { it }

    // Sección abierta actualmente (solo una a la vez). Empieza en Ubicación.
    var expandedSection by remember { mutableStateOf(FormSectionId.LOCATION) }

    // Resumen que se muestra al colapsar cada sección.
    val locationSummary = when {
        addressText.isNotBlank() -> addressText
        selectedLocation != null -> "Cancha marcada en el mapa"
        else -> null
    }
    val dateSummary = "${scheduledAt.format(DATE_FMT)} · ${scheduledAt.format(TIME_FMT)}"
    val matchSummary = if (ambiente.isBlank()) format else "$format · $ambiente"
    val squadSummary = if (positionSlots.isEmpty()) null
        else "$totalSlots ${if (totalSlots == 1) "cupo" else "cupos"} · ${positionSlots.size} ${if (positionSlots.size == 1) "posición" else "posiciones"}"

    // Auto-avance: al completarse la sección abierta, colapsa y abre la siguiente
    // que aún no esté completa. Si todas están completas, las colapsa todas.
    fun advanceFrom(current: FormSectionId) {
        val completeByOrder = listOf(
            FormSectionId.LOCATION to locationComplete,
            FormSectionId.DATE to dateComplete,
            FormSectionId.MATCH to matchComplete,
            FormSectionId.SQUAD to squadComplete,
        )
        val next = completeByOrder.firstOrNull { (id, complete) -> id != current && !complete }?.first
        expandedSection = next ?: FormSectionId.NONE
    }
    // Las secciones con default válido auto-avanzan al volverse completas. Squad NO
    // está aquí: se confirma con su botón "Listo" (que llama a advanceFrom directo),
    // porque al re-editarla ya está completa y el efecto no volvería a dispararse.
    LaunchedEffect(locationComplete) { if (expandedSection == FormSectionId.LOCATION && locationComplete) advanceFrom(FormSectionId.LOCATION) }
    LaunchedEffect(dateComplete) { if (expandedSection == FormSectionId.DATE && dateComplete) advanceFrom(FormSectionId.DATE) }
    LaunchedEffect(matchComplete) { if (expandedSection == FormSectionId.MATCH && matchComplete) advanceFrom(FormSectionId.MATCH) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { BackTopBar(onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader(title = "Nueva Convocatoria", subtitle = "Arma tu partido")

            Spacer(Modifier.height(24.dp))

            // Barra de progreso: cuántas secciones están completas.
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { completedCount / 4f },
                    modifier = Modifier.weight(1f).height(6.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "$completedCount de 4",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Alterna la sección: si ya está abierta la colapsa, si no la abre
            // (cerrando las demás, porque solo una está abierta a la vez).
            val toggle: (FormSectionId) -> Unit = { id ->
                expandedSection = if (expandedSection == id) FormSectionId.NONE else id
            }

            // --- Sección: Ubicación ---
            CollapsibleFormSection(
                title = "📍  Ubicación",
                expanded = expandedSection == FormSectionId.LOCATION,
                isComplete = locationComplete,
                summary = locationSummary,
                onHeaderClick = { toggle(FormSectionId.LOCATION) },
            ) {
                Text(
                    if (selectedLocation == null) "Busca una dirección o toca el mapa para marcar la cancha"
                    else "Lat ${"%.4f".format(selectedLocation!!.latitude)}, Lng ${"%.4f".format(selectedLocation!!.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = addressText,
                    onValueChange = { addressText = it },
                    label = { Text("Referencia / dirección (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))

            // --- Sección: Fecha y hora ---
            CollapsibleFormSection(
                title = "📅  Fecha y hora",
                expanded = expandedSection == FormSectionId.DATE,
                isComplete = dateComplete,
                summary = dateSummary,
                onHeaderClick = { toggle(FormSectionId.DATE) },
            ) {
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
            }

            Spacer(Modifier.height(12.dp))

            // --- Sección: Datos del partido ---
            CollapsibleFormSection(
                title = "⚽  Datos del partido",
                expanded = expandedSection == FormSectionId.MATCH,
                isComplete = matchComplete,
                summary = matchSummary,
                onHeaderClick = { toggle(FormSectionId.MATCH) },
            ) {
                DropdownField(label = "Formato", options = FORMATS, selected = format, onSelected = { format = it })

                Spacer(Modifier.height(16.dp))

                Text("Ambiente", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Row {
                    FilterChip(selected = ambiente == "Recocha", onClick = { ambiente = "Recocha" }, label = { Text("Recocha") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = ambiente == "Competitivo", onClick = { ambiente = "Competitivo" }, label = { Text("Competitivo") })
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- Sección: Convocatoria (cupos + cuota) ---
            CollapsibleFormSection(
                title = "👥  Convocatoria",
                expanded = expandedSection == FormSectionId.SQUAD,
                isComplete = squadComplete,
                summary = squadSummary,
                onHeaderClick = { toggle(FormSectionId.SQUAD) },
            ) {
                PositionSlotsEditor(
                    positionSlots = positionSlots,
                    total = totalSlots,
                    onAdd = { pos -> positionSlots.add(PositionSlot(pos, 1)) },
                    onRemove = { idx -> positionSlots.removeAt(idx) },
                    onChangeSlots = { idx, delta ->
                        val current = positionSlots[idx]
                        val next = (current.slots + delta).coerceIn(1, 30)
                        positionSlots[idx] = current.copy(slots = next)
                    },
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
                // La cuota es opcional, así que el usuario confirma manualmente que
                // terminó esta sección (marca el check y colapsa).
                Button(
                    onClick = {
                        squadConfirmed = true
                        // Colapsa/avanza directamente: no dependemos de un efecto, así
                        // funciona también al re-editar una sección ya completada.
                        advanceFrom(FormSectionId.SQUAD)
                    },
                    enabled = positionSlots.isNotEmpty() && totalSlots in 1..30,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Listo") }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    val loc = selectedLocation ?: return@Button
                    viewModel.create(
                        CreateConvocatoryRequest(
                            lat = loc.latitude,
                            lng = loc.longitude,
                            addressText = addressText.ifBlank { null },
                            positionSlots = positionSlots.toList(),
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
                    dateTouched = true
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
                    dateTouched = true
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

/**
 * Editor de cupos por posición: una fila por posición elegida (con stepper de
 * cupos y botón de quitar) y un menú "Agregar posición" que solo ofrece las
 * posiciones aún no añadidas. Muestra el total de cupos.
 */
@Composable
private fun PositionSlotsEditor(
    positionSlots: List<PositionSlot>,
    total: Int,
    onAdd: (String) -> Unit,
    onRemove: (Int) -> Unit,
    onChangeSlots: (Int, Int) -> Unit,
) {
    val available = POSITIONS.filter { pos -> positionSlots.none { it.position == pos } }
    var menuExpanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Cupos por posición *", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Total: $total",
                style = MaterialTheme.typography.bodySmall,
                color = if (total in 1..30) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(8.dp))

        if (positionSlots.isEmpty()) {
            Text(
                "Agrega las posiciones que necesitas y cuántos cupos para cada una",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))
        }

        positionSlots.forEachIndexed { index, ps ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(ps.position, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
                // Stepper de cupos.
                FilledTonalIconButton(
                    onClick = { onChangeSlots(index, -1) },
                    enabled = ps.slots > 1,
                ) { Text("–") }
                Text(
                    ps.slots.toString(),
                    modifier = Modifier.widthIn(min = 24.dp).padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                FilledTonalIconButton(onClick = { onChangeSlots(index, +1) }) { Text("+") }
                IconButton(onClick = { onRemove(index) }) { Text("✕") }
            }
        }

        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { menuExpanded = true },
                enabled = available.isNotEmpty(),
            ) { Text("＋ Agregar posición") }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                available.forEach { pos ->
                    DropdownMenuItem(
                        text = { Text(pos) },
                        onClick = {
                            onAdd(pos)
                            menuExpanded = false
                        },
                    )
                }
            }
        }
    }
}
