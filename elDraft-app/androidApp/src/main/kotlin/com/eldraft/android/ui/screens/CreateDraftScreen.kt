package com.eldraft.android.ui.screens

import com.eldraft.android.ui.theme.ElDraftTheme

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eldraft.android.R
import com.eldraft.android.ui.components.BackTopBar
import com.eldraft.android.ui.components.CollapsibleFormSection
import com.eldraft.android.ui.components.ScreenHeader
import com.eldraft.android.ui.components.DropdownField
import com.eldraft.android.ui.components.formatSchedule
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

private val FORMATS = listOf("Fútbol 5", "Fútbol 7", "Fútbol 8", "Fútbol 9", "Fútbol 11")
private val POSITIONS = listOf("Arquero", "Defensa", "Mediocampista", "Delantero", "Extremo")

/** Secciones plegables del formulario. NONE = todas colapsadas. */
private enum class FormSectionId { LOCATION, DATE, MATCH, SQUAD, NONE }
// Centro por defecto: Medellín (se ajusta si hay ubicación seleccionada)
private val DEFAULT_LOCATION = LatLng(6.2442, -75.5812)
// Formatos legibles para mostrar la fecha/hora elegida.
private val DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale("es"))
private val TIME_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale("es"))

/** Hora habitual de partido y anticipación mínima para publicar uno. */
private const val DEFAULT_MATCH_HOUR = 19
private const val MIN_HOURS_AHEAD = 1L

/**
 * Fecha/hora sugerida al abrir el formulario: hoy a las 7 p.m. si todavía cumple
 * la anticipación mínima, si no mañana a la misma hora. Antes siempre proponía
 * mañana, lo que obligaba a corregir la fecha para armar un partido de esta noche.
 */
private fun defaultScheduledAt(): LocalDateTime {
    val todayAtDefaultHour = LocalDate.now().atTime(DEFAULT_MATCH_HOUR, 0)
    // Estricto a propósito: es la misma comparación que valida el formulario, así
    // que proponer un horario "justo en el límite" lo dejaría inválido de entrada.
    val earliest = LocalDateTime.now().plusHours(MIN_HOURS_AHEAD)
    return if (todayAtDefaultHour.isAfter(earliest)) todayAtDefaultHour else todayAtDefaultHour.plusDays(1)
}

/**
 * El DatePicker de Material3 no habla en instantes sino en "medianoche UTC del
 * día" de cada celda. Convertir con la zona local corre el día cuando la hora
 * local ya cae en otro día UTC: 7 p.m. en Colombia (UTC-5) son las 00:00 UTC del
 * día siguiente, y el calendario marcaba un día de más.
 */
private fun LocalDate.toPickerMillis(): Long =
    atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

/** Inversa de [toPickerMillis]: el día que representa la celda elegida. */
private fun pickerMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()

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

    // Fecha/hora del partido. Por defecto: el próximo horario habitual (19:00).
    var scheduledAt by remember { mutableStateOf(defaultScheduledAt()) }
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
    // La hora del partido debe ser al menos 1 hora en el futuro.
    val minScheduledAt = LocalDateTime.now().plusHours(MIN_HOURS_AHEAD)
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
        selectedLocation != null -> stringResource(R.string.create_location_summary_map)
        else -> null
    }
    val dateSummary = stringResource(
        R.string.create_date_time_summary, scheduledAt.format(DATE_FMT), scheduledAt.format(TIME_FMT),
    )
    val matchSummary = if (ambiente.isBlank()) format
        else stringResource(R.string.create_match_summary, format, ambiente)
    val slotsText = pluralStringResource(R.plurals.create_slots_count, totalSlots, totalSlots)
    val positionsText = pluralStringResource(R.plurals.create_positions_count, positionSlots.size, positionSlots.size)
    val squadSummary = if (positionSlots.isEmpty()) null
        else stringResource(R.string.create_slots_summary, slotsText, positionsText)

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
                .padding(horizontal = ElDraftTheme.spacing.xl)
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader(
                title = stringResource(R.string.create_header_title),
                subtitle = stringResource(R.string.create_header_subtitle),
            )

            Spacer(Modifier.height(ElDraftTheme.spacing.xl))

            // Barra de progreso: cuántas secciones están completas.
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { completedCount / 4f },
                    modifier = Modifier.weight(1f).height(6.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.width(ElDraftTheme.spacing.md))
                Text(
                    stringResource(R.string.create_progress, completedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = ElDraftTheme.alpha.textTertiary),
                )
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.lg))

            // Alterna la sección: si ya está abierta la colapsa, si no la abre
            // (cerrando las demás, porque solo una está abierta a la vez).
            val toggle: (FormSectionId) -> Unit = { id ->
                expandedSection = if (expandedSection == id) FormSectionId.NONE else id
            }

            // --- Sección: Ubicación ---
            CollapsibleFormSection(
                title = stringResource(R.string.create_location_title),
                expanded = expandedSection == FormSectionId.LOCATION,
                isComplete = locationComplete,
                summary = locationSummary,
                onHeaderClick = { toggle(FormSectionId.LOCATION) },
            ) {
                Text(
                    if (selectedLocation == null) stringResource(R.string.create_location_placeholder)
                    else stringResource(
                        R.string.create_location_coords,
                        "%.4f".format(selectedLocation!!.latitude),
                        "%.4f".format(selectedLocation!!.longitude),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = ElDraftTheme.alpha.textTertiary),
                )
                Spacer(Modifier.height(ElDraftTheme.spacing.sm))

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

                Spacer(Modifier.height(ElDraftTheme.spacing.sm))
                LocationPickerMap(
                    cameraPositionState = cameraPositionState,
                    selectedLocation = selectedLocation,
                    onLocationSelected = { selectedLocation = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )

                Spacer(Modifier.height(ElDraftTheme.spacing.md))

                OutlinedTextField(
                    value = addressText,
                    onValueChange = { addressText = it },
                    label = { Text(stringResource(R.string.create_reference_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.md))

            // --- Sección: Fecha y hora ---
            CollapsibleFormSection(
                title = stringResource(R.string.create_section_date),
                expanded = expandedSection == FormSectionId.DATE,
                isComplete = dateComplete,
                summary = dateSummary,
                onHeaderClick = { toggle(FormSectionId.DATE) },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(ElDraftTheme.spacing.md)) {
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
                            androidx.compose.foundation.BorderStroke(ElDraftTheme.size.borderHairline, MaterialTheme.colorScheme.error)
                        else null,
                    ) {
                        Text(scheduledAt.format(TIME_FMT))
                    }
                }
                if (!scheduledAtValid) {
                    Spacer(Modifier.height(ElDraftTheme.spacing.xs))
                    Text(
                        stringResource(R.string.create_time_error),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.md))

            // --- Sección: Datos del partido ---
            CollapsibleFormSection(
                title = stringResource(R.string.create_section_match),
                expanded = expandedSection == FormSectionId.MATCH,
                isComplete = matchComplete,
                summary = matchSummary,
                onHeaderClick = { toggle(FormSectionId.MATCH) },
            ) {
                DropdownField(label = stringResource(R.string.create_format_label), options = FORMATS, selected = format, onSelected = { format = it })

                Spacer(Modifier.height(ElDraftTheme.spacing.lg))

                Text(stringResource(R.string.create_ambiente_label), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(ElDraftTheme.spacing.sm))
                Row {
                    FilterChip(selected = ambiente == "Recocha", onClick = { ambiente = "Recocha" }, label = { Text("Recocha") })
                    Spacer(Modifier.width(ElDraftTheme.spacing.sm))
                    FilterChip(selected = ambiente == "Competitivo", onClick = { ambiente = "Competitivo" }, label = { Text("Competitivo") })
                }
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.md))

            // --- Sección: Convocatoria (cupos + cuota) ---
            CollapsibleFormSection(
                title = stringResource(R.string.create_section_squad),
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

                Spacer(Modifier.height(ElDraftTheme.spacing.lg))
                OutlinedTextField(
                    value = fee,
                    // El campo arranca en "0" (gratis), así que escribir encima
                    // dejaba "010000". Se quitan los ceros a la izquierda, pero un
                    // "0" solo se conserva: es un valor válido, no un sobrante.
                    onValueChange = { input ->
                        if (input.all(Char::isDigit) && input.length <= 7) {
                            fee = if (input.isEmpty()) "" else input.trimStart('0').ifEmpty { "0" }
                        }
                    },
                    label = { Text(stringResource(R.string.create_fee_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(ElDraftTheme.spacing.lg))
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
                ) { Text(stringResource(R.string.create_squad_done)) }
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.xl))

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
                    CircularProgressIndicator(modifier = Modifier.size(ElDraftTheme.size.iconLg), strokeWidth = ElDraftTheme.size.stroke, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.create_publish))
                }
            }

            Spacer(Modifier.height(ElDraftTheme.spacing.lg))
        }
    }

    // Diálogo: la convocatoria choca con postulaciones del organizador. Si
    // confirma, se crea y se cancelan esas postulaciones.
    (state as? CreateDraftUiState.ConfirmCancel)?.let { s ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirm() },
            title = { Text(stringResource(R.string.create_conflict_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.create_replace_postulation),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(ElDraftTheme.spacing.sm))
                    s.conflicts.forEach { c ->
                        val schedule = formatSchedule(c.scheduledAt)
                        Text(
                            stringResource(R.string.create_conflict_item, c.format, schedule?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmCancelConflicts() }) {
                    Text(stringResource(R.string.create_conflict_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConfirm() }) { Text(stringResource(R.string.action_back_short)) }
            },
        )
    }

    // --- Diálogos de selección de fecha/hora ---
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = scheduledAt.toLocalDate().toPickerMillis(),
            selectableDates = object : SelectableDates {
                // "Hoy" se calcula en la zona local del usuario; usar
                // LocalDate.now(UTC) adelanta el día por la noche en zonas UTC-
                // (p.ej. UTC-5) y rechazaría incorrectamente el día actual.
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis >= LocalDate.now(ZoneId.systemDefault()).toPickerMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val picked = pickerMillisToLocalDate(millis)
                        val candidate = picked.atTime(scheduledAt.toLocalTime())
                        val min = LocalDateTime.now().plusHours(MIN_HOURS_AHEAD)
                            .withSecond(0).withNano(0)
                        // Si al cambiar la fecha la hora queda inválida, ajustamos al mínimo.
                        scheduledAt = if (candidate.isBefore(min)) min else candidate
                    }
                    dateTouched = true
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = scheduledAt.hour,
            initialMinute = scheduledAt.minute,
            is24Hour = false,
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
                    val min = LocalDateTime.now().plusHours(MIN_HOURS_AHEAD)
                        .withSecond(0).withNano(0)
                    // Si la fecha/hora elegida no cumple el mínimo, ajustamos al mínimo.
                    scheduledAt = if (candidate.isBefore(min)) min else candidate
                    dateTouched = true
                    showTimePicker = false
                }) { Text(stringResource(R.string.action_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.action_cancel)) }
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
            Text(stringResource(R.string.create_slots_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
            Text(
                stringResource(R.string.create_total, total),
                style = MaterialTheme.typography.bodySmall,
                color = if (total in 1..30) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(ElDraftTheme.spacing.sm))

        if (positionSlots.isEmpty()) {
            Text(
                stringResource(R.string.create_slots_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = ElDraftTheme.alpha.textTertiary),
            )
            Spacer(Modifier.height(ElDraftTheme.spacing.sm))
        }

        positionSlots.forEachIndexed { index, ps ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = ElDraftTheme.spacing.xs),
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
                    modifier = Modifier.widthIn(min = 24.dp).padding(horizontal = ElDraftTheme.spacing.sm),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                FilledTonalIconButton(onClick = { onChangeSlots(index, +1) }) { Text("+") }
                IconButton(onClick = { onRemove(index) }) { Text("✕") }
            }
        }

        Spacer(Modifier.height(ElDraftTheme.spacing.sm))
        Box {
            OutlinedButton(
                onClick = { menuExpanded = true },
                enabled = available.isNotEmpty(),
            ) { Text(stringResource(R.string.create_add_position)) }
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
