import CoreLocation
import GoogleMaps
import SwiftUI
import shared

/// Secciones plegables del formulario. `none` = todas colapsadas. Espejo de
/// `FormSectionId` en `CreateDraftScreen.kt`.
private enum FormSectionId: Equatable {
    case location, date, match, squad, none
}

// Centro por defecto: Medellín (se ajusta si hay ubicación seleccionada).
private let createDraftDefaultCenter = CLLocationCoordinate2D(latitude: 6.2442, longitude: -75.5812)

// Formatos legibles para mostrar la fecha/hora elegida. `scheduledAt` es un
// `Date` construido siempre con `Calendar.current` (hora local real del
// dispositivo), así que estos formatters usan la zona local del sistema — sin
// el truco de "zona UTC de mentiras" que usa `ConvocatoryCardParts.swift` para
// mostrar el `scheduledAt` naive que YA llegó del backend.
private let createDraftDateFormatter: DateFormatter = {
    let f = DateFormatter()
    f.locale = Locale(identifier: "es")
    f.dateFormat = "EEE d MMM yyyy"
    return f
}()

private let createDraftTimeFormatter: DateFormatter = {
    let f = DateFormatter()
    f.locale = Locale(identifier: "es")
    f.dateFormat = "h:mm a"
    return f
}()

/// `CreateDraftView` — el equivalente de `CreateDraftScreen.kt`: cuatro
/// secciones plegables con auto-avance y barra de progreso.
struct CreateDraftView: View {
    let onDraftCreated: () -> Void

    @State private var viewModel = CreateDraftViewModel()

    // Requerimientos por posición: la posición (clave) y sus cupos. El orden
    // de inserción se conserva (Array), para mostrarlos como el organizador
    // los agrega.
    @State private var positionSlots: [PositionSlot] = []
    @State private var fee = "0"
    @State private var format = "Fútbol 5"
    @State private var ambiente = ""
    @State private var addressText = ""
    @State private var selectedCoordinate: CLLocationCoordinate2D?

    // Fecha/hora del partido. Por defecto: el próximo horario habitual (19:00).
    @State private var scheduledAt = ScheduleDefaults.defaultScheduledAt()
    @State private var showDatePicker = false
    @State private var showTimePicker = false

    // Fecha/hora arranca con un valor por defecto VÁLIDO, así que no debe
    // marcarse completa hasta que el usuario lo confirme: este flag se activa
    // al usar el picker.
    @State private var dateTouched = false
    // Convocatoria se confirma manualmente (cuota es opcional): no auto-completa.
    @State private var squadConfirmed = false
    // Sección abierta actualmente (solo una a la vez). Empieza en Ubicación.
    @State private var expandedSection: FormSectionId = .location

    @State private var toastMessage: String?

    // Centra el mapa en la ubicación actual del usuario al abrir el
    // formulario (sin marcar el punto: el usuario igual elige la cancha
    // tocando/buscando).
    @State private var cameraController = MapCameraController()
    @State private var locationProvider = LocationProvider()
    @State private var centeredOnUser = false

    private var totalSlots: Int { positionSlots.reduce(0) { $0 + Int($1.slots) } }

    // La hora del partido debe ser al menos `MIN_HOURS_AHEAD` en el futuro.
    private var minScheduledAt: Date {
        Date().addingTimeInterval(Double(ScheduleDefaults.minHoursAhead) * 3600)
    }
    private var scheduledAtValid: Bool { scheduledAt > minScheduledAt }
    private var isSaving: Bool { viewModel.state == .saving }
    private var canSave: Bool {
        !positionSlots.isEmpty && (1...30).contains(totalSlots)
            && selectedCoordinate != nil && scheduledAtValid && !isSaving
    }

    // Validez de cada sección (alimenta el check de "completada"). Ubicación,
    // Datos del partido (ambiente sin preselección) y Convocatoria parten sin
    // valor, así que su validez ya implica que el usuario interactuó.
    private var locationComplete: Bool { selectedCoordinate != nil }
    private var dateComplete: Bool { dateTouched && scheduledAtValid }
    private var matchComplete: Bool { !format.isEmpty && !ambiente.isEmpty }
    private var squadComplete: Bool { squadConfirmed && !positionSlots.isEmpty && (1...30).contains(totalSlots) }
    private var completedCount: Int {
        [locationComplete, dateComplete, matchComplete, squadComplete].filter { $0 }.count
    }

    // Resumen que se muestra al colapsar cada sección.
    private var locationSummary: String? {
        if !addressText.isEmpty { return addressText }
        if selectedCoordinate != nil { return String(localized: "create_location_summary_map") }
        return nil
    }
    private var dateSummary: String {
        String(
            format: String(localized: "create_date_time_summary"),
            createDraftDateFormatter.string(from: scheduledAt),
            createDraftTimeFormatter.string(from: scheduledAt)
        )
    }
    private var matchSummary: String {
        ambiente.isEmpty ? format : String(format: String(localized: "create_match_summary"), format, ambiente)
    }
    private var squadSummary: String? {
        guard !positionSlots.isEmpty else { return nil }
        let slotsText = String(format: String(localized: "create_slots_count"), totalSlots)
        let positionsText = String(format: String(localized: "create_positions_count"), positionSlots.count)
        return String(format: String(localized: "create_slots_summary"), slotsText, positionsText)
    }

    private var confirmCancelConflicts: [ScheduleConflictItem] {
        if case .confirmCancel(let conflicts) = viewModel.state { return conflicts }
        return []
    }
    private var confirmCancelPresented: Binding<Bool> {
        Binding(
            get: {
                if case .confirmCancel = viewModel.state { return true }
                return false
            },
            set: { presented in if !presented { viewModel.dismissConfirm() } }
        )
    }
    private var conflictMessage: String {
        var lines = [String(localized: "create_replace_postulation")]
        for conflict in confirmCancelConflicts {
            let schedule = formatSchedule(conflict.scheduledAt)
            lines.append(String(format: String(localized: "create_conflict_item"), conflict.format, schedule.map { " · \($0)" } ?? ""))
        }
        return lines.joined(separator: "\n")
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                ScreenHeader(
                    title: String(localized: "create_header_title"),
                    subtitle: String(localized: "create_header_subtitle")
                )

                Spacer().frame(height: ElDraftSpacing.xl)

                // Barra de progreso: cuántas secciones están completas.
                HStack(alignment: .center, spacing: ElDraftSpacing.md) {
                    ProgressView(value: Double(completedCount), total: 4)
                        .tint(ElDraftColors.primary)
                    Text(String(format: String(localized: "create_progress"), completedCount))
                        .font(ElDraftTypography.bodySmall)
                        .foregroundStyle(ElDraftColors.onBackground.opacity(ElDraftAlpha.textTertiary))
                }

                Spacer().frame(height: ElDraftSpacing.lg)

                CollapsibleFormSection(
                    title: String(localized: "create_location_title"),
                    expanded: expandedSection == .location,
                    isComplete: locationComplete,
                    summary: locationSummary,
                    onHeaderClick: { toggle(.location) },
                    content: { locationSectionContent }
                )
                Spacer().frame(height: ElDraftSpacing.md)

                CollapsibleFormSection(
                    title: String(localized: "create_section_date"),
                    expanded: expandedSection == .date,
                    isComplete: dateComplete,
                    summary: dateSummary,
                    onHeaderClick: { toggle(.date) },
                    content: { dateSectionContent }
                )
                Spacer().frame(height: ElDraftSpacing.md)

                CollapsibleFormSection(
                    title: String(localized: "create_section_match"),
                    expanded: expandedSection == .match,
                    isComplete: matchComplete,
                    summary: matchSummary,
                    onHeaderClick: { toggle(.match) },
                    content: { matchSectionContent }
                )
                Spacer().frame(height: ElDraftSpacing.md)

                CollapsibleFormSection(
                    title: String(localized: "create_section_squad"),
                    expanded: expandedSection == .squad,
                    isComplete: squadComplete,
                    summary: squadSummary,
                    onHeaderClick: { toggle(.squad) },
                    content: { squadSectionContent }
                )

                Spacer().frame(height: ElDraftSpacing.xl)

                Button(action: submit) {
                    Group {
                        if isSaving {
                            ProgressView().tint(ElDraftColors.onPrimary)
                        } else {
                            Text(String(localized: "create_publish"))
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, ElDraftSpacing.md)
                }
                .buttonStyle(.borderedProminent)
                .tint(ElDraftColors.primary)
                .disabled(!canSave)

                Spacer().frame(height: ElDraftSpacing.lg)
            }
            .padding(.horizontal, ElDraftSpacing.xl)
        }
        .background(ElDraftColors.background)
        .toast(message: $toastMessage)
        .task {
            guard !centeredOnUser else { return }
            centeredOnUser = true
            if !locationProvider.hasPermission { locationProvider.requestPermission() }
            if locationProvider.hasPermission, let here = await locationProvider.current() {
                cameraController.move(to: here, zoom: 15)
            }
        }
        .onChange(of: viewModel.state) { _, newState in
            switch newState {
            case .created:
                onDraftCreated()
            case .error(let message):
                toastMessage = message
                viewModel.resetError()
            default:
                break
            }
        }
        // Las secciones con default válido auto-avanzan al volverse
        // completas. Squad NO está acá: se confirma con su botón "Listo".
        .onChange(of: locationComplete) { _, complete in
            if expandedSection == .location && complete { advanceFrom(.location) }
        }
        .onChange(of: dateComplete) { _, complete in
            if expandedSection == .date && complete { advanceFrom(.date) }
        }
        .onChange(of: matchComplete) { _, complete in
            if expandedSection == .match && complete { advanceFrom(.match) }
        }
        .sheet(isPresented: $showDatePicker) {
            DatePickerSheet(
                initialDate: scheduledAt,
                minDate: Calendar.current.startOfDay(for: Date()),
                onConfirm: { picked in
                    let candidate = combining(datePart: picked, timePart: scheduledAt)
                    scheduledAt = candidate < minScheduledAtTruncated ? minScheduledAtTruncated : candidate
                    dateTouched = true
                    showDatePicker = false
                },
                onCancel: { showDatePicker = false }
            )
            .presentationDetents([.medium, .large])
        }
        .sheet(isPresented: $showTimePicker) {
            TimePickerSheet(
                initialDate: scheduledAt,
                onConfirm: { picked in
                    let candidate = combining(datePart: scheduledAt, timePart: picked)
                    scheduledAt = candidate < minScheduledAtTruncated ? minScheduledAtTruncated : candidate
                    dateTouched = true
                    showTimePicker = false
                },
                onCancel: { showTimePicker = false }
            )
            .presentationDetents([.medium])
        }
        // Diálogo: la convocatoria choca con postulaciones del organizador.
        // Si confirma, se crea y se cancelan esas postulaciones.
        .alert(String(localized: "create_conflict_title"), isPresented: confirmCancelPresented) {
            Button(String(localized: "create_conflict_confirm")) { viewModel.confirmCancelConflicts() }
            Button(String(localized: "action_back_short"), role: .cancel) { viewModel.dismissConfirm() }
        } message: {
            Text(conflictMessage)
        }
    }

    @ViewBuilder
    private var locationSectionContent: some View {
        Text(
            selectedCoordinate == nil
                ? String(localized: "create_location_placeholder")
                : String(
                    format: String(localized: "create_location_coords"),
                    String(format: "%.4f", selectedCoordinate!.latitude),
                    String(format: "%.4f", selectedCoordinate!.longitude)
                )
        )
        .font(ElDraftTypography.bodySmall)
        .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textTertiary))

        Spacer().frame(height: ElDraftSpacing.sm)

        // Buscador de direcciones (Places Autocomplete): centra el mapa y
        // coloca el marcador al elegir una sugerencia.
        PlaceAutocompleteField { selection in
            selectedCoordinate = selection.coordinate
            if addressText.isEmpty { addressText = selection.description }
            cameraController.move(to: selection.coordinate, zoom: 16)
        }

        Spacer().frame(height: ElDraftSpacing.sm)

        // Mismo guard que `MapTabView`: sin key, ni construir el `GMSMapView`
        // (lanza `GMSServicesException` al hacerlo).
        if AppConfig.mapsApiKey.isEmpty {
            VStack(spacing: ElDraftSpacing.sm) {
                Image(systemName: "map.fill")
                    .font(.system(size: 32))
                    .foregroundStyle(ElDraftColors.onBackground.opacity(ElDraftAlpha.textTertiary))
                Text("Mapa no disponible")
                    .font(ElDraftTypography.bodySmall)
                    .foregroundStyle(ElDraftColors.onBackground.opacity(ElDraftAlpha.textSecondary))
            }
            .frame(maxWidth: .infinity, minHeight: 220)
            .background(ElDraftColors.surfaceVariant, in: ElDraftShapes.sm)
        } else {
            LocationPickerMapView(
                selectedCoordinate: selectedCoordinate,
                cameraController: cameraController,
                initialCamera: GMSCameraPosition(
                    latitude: createDraftDefaultCenter.latitude,
                    longitude: createDraftDefaultCenter.longitude,
                    zoom: 14
                ),
                onLocationSelected: { coordinate in selectedCoordinate = coordinate }
            )
            .frame(height: 220)
            .clipShape(ElDraftShapes.sm)
        }

        Spacer().frame(height: ElDraftSpacing.md)

        TextField(String(localized: "create_reference_label"), text: $addressText)
            .padding(.horizontal, ElDraftSpacing.md)
            .padding(.vertical, ElDraftSpacing.sm)
            .background(ElDraftColors.surface, in: ElDraftShapes.field)
            .overlay(ElDraftShapes.field.stroke(ElDraftColors.outline, lineWidth: ElDraftSizes.borderHairline))
    }

    @ViewBuilder
    private var dateSectionContent: some View {
        HStack(spacing: ElDraftSpacing.md) {
            Button {
                showDatePicker = true
            } label: {
                Text(createDraftDateFormatter.string(from: scheduledAt))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, ElDraftSpacing.sm)
            }
            .buttonStyle(.bordered)

            Button {
                showTimePicker = true
            } label: {
                Text(createDraftTimeFormatter.string(from: scheduledAt))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, ElDraftSpacing.sm)
                    .foregroundStyle(scheduledAtValid ? ElDraftColors.primary : ElDraftColors.error)
            }
            .buttonStyle(.bordered)
        }
        if !scheduledAtValid {
            Spacer().frame(height: ElDraftSpacing.xs)
            Text(String(localized: "create_time_error"))
                .font(ElDraftTypography.labelSmall)
                .foregroundStyle(ElDraftColors.error)
        }
    }

    @ViewBuilder
    private var matchSectionContent: some View {
        DropdownField(
            label: String(localized: "create_format_label"),
            options: DomainCatalogs.shared.FORMATS,
            selected: $format
        )

        Spacer().frame(height: ElDraftSpacing.lg)

        Text(String(localized: "create_ambiente_label"))
            .font(ElDraftTypography.titleSmall)
            .foregroundStyle(ElDraftColors.onSurface)
        Spacer().frame(height: ElDraftSpacing.sm)
        HStack(spacing: ElDraftSpacing.sm) {
            ForEach(DomainCatalogs.shared.AMBIENTES, id: \.self) { opcion in
                FilterChipView(label: opcion, selected: ambiente == opcion, action: { ambiente = opcion })
            }
        }
    }

    @ViewBuilder
    private var squadSectionContent: some View {
        PositionSlotsEditor(positionSlots: $positionSlots)

        Spacer().frame(height: ElDraftSpacing.lg)

        TextField(String(localized: "create_fee_label"), text: feeBinding)
            .keyboardType(.numberPad)
            .padding(.horizontal, ElDraftSpacing.md)
            .padding(.vertical, ElDraftSpacing.sm)
            .background(ElDraftColors.surface, in: ElDraftShapes.field)
            .overlay(ElDraftShapes.field.stroke(ElDraftColors.outline, lineWidth: ElDraftSizes.borderHairline))

        Spacer().frame(height: ElDraftSpacing.lg)

        // La cuota es opcional, así que el usuario confirma manualmente que
        // terminó esta sección (marca el check y colapsa).
        Button {
            squadConfirmed = true
            advanceFrom(.squad)
        } label: {
            Text(String(localized: "create_squad_done"))
                .frame(maxWidth: .infinity)
                .padding(.vertical, ElDraftSpacing.md)
        }
        .buttonStyle(.borderedProminent)
        .tint(ElDraftColors.primary)
        .disabled(positionSlots.isEmpty || !(1...30).contains(totalSlots))
    }

    // El campo arranca en "0" (gratis), así que escribir encima dejaba
    // "010000". Se quitan los ceros a la izquierda, pero un "0" solo se
    // conserva: es un valor válido, no un sobrante.
    private var feeBinding: Binding<String> {
        Binding(
            get: { fee },
            set: { input in
                guard input.allSatisfy(\.isNumber), input.count <= 7 else { return }
                if input.isEmpty {
                    fee = ""
                } else {
                    let trimmed = input.drop(while: { $0 == "0" })
                    fee = trimmed.isEmpty ? "0" : String(trimmed)
                }
            }
        )
    }

    private func toggle(_ id: FormSectionId) {
        expandedSection = expandedSection == id ? .none : id
    }

    // Auto-avance: al completarse la sección abierta, colapsa y abre la
    // siguiente que aún no esté completa. Si todas están completas, las
    // colapsa todas.
    private func advanceFrom(_ current: FormSectionId) {
        let completeByOrder: [(FormSectionId, Bool)] = [
            (.location, locationComplete),
            (.date, dateComplete),
            (.match, matchComplete),
            (.squad, squadComplete),
        ]
        let next = completeByOrder.first { $0.0 != current && !$0.1 }?.0
        expandedSection = next ?? .none
    }

    private var minScheduledAtTruncated: Date {
        let raw = Date().addingTimeInterval(Double(ScheduleDefaults.minHoursAhead) * 3600)
        var c = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: raw)
        c.second = 0
        return Calendar.current.date(from: c) ?? raw
    }

    private func combining(datePart: Date, timePart: Date) -> Date {
        let cal = Calendar.current
        var merged = cal.dateComponents([.year, .month, .day], from: datePart)
        let time = cal.dateComponents([.hour, .minute, .second], from: timePart)
        merged.hour = time.hour
        merged.minute = time.minute
        merged.second = 0
        return cal.date(from: merged) ?? datePart
    }

    /// Serializa `scheduledAt` (hora LOCAL real del dispositivo) a la misma
    /// forma naive `ISO_LOCAL_DATE_TIME` que espera el backend, extrayendo los
    /// componentes de calendario tal cual el usuario los eligió — sin pasar
    /// por ningún truco de zona horaria (no hace falta: acá no hay una
    /// cadena que reinterpretar, solo números que escribir).
    private func isoLocalString(from date: Date) -> String {
        let c = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
        return String(
            format: "%04d-%02d-%02dT%02d:%02d:%02d",
            c.year ?? 1970, c.month ?? 1, c.day ?? 1, c.hour ?? 0, c.minute ?? 0, c.second ?? 0
        )
    }

    private func submit() {
        guard let coordinate = selectedCoordinate else { return }
        let request = CreateConvocatoryRequest(
            lat: coordinate.latitude,
            lng: coordinate.longitude,
            addressText: addressText.isEmpty ? nil : addressText,
            positionSlots: positionSlots,
            fee: Double(fee) ?? 0.0,
            format: format,
            ambiente: ambiente,
            scheduledAt: isoLocalString(from: scheduledAt),
            cancelConflicts: false
        )
        viewModel.create(request)
    }
}

/// Chip de selección simple (una opción entre pocas, sin estado deshabilitado
/// propio). Espejo visual del `FilterChip` de Material3 que usa Android para
/// "Ambiente".
private struct FilterChipView: View {
    let label: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(ElDraftTypography.labelLarge)
                .foregroundStyle(selected ? ElDraftColors.onPrimaryContainer : ElDraftColors.onSurface)
                .padding(.horizontal, ElDraftSpacing.md2)
                .padding(.vertical, ElDraftSpacing.xs)
                .background(selected ? ElDraftColors.primaryContainer : Color.clear, in: ElDraftShapes.pill)
                .overlay(ElDraftShapes.pill.stroke(ElDraftColors.outline, lineWidth: selected ? 0 : ElDraftSizes.borderHairline))
        }
        .buttonStyle(.plain)
    }
}

/// Editor de cupos por posición: una fila por posición elegida (con stepper
/// de cupos y botón de quitar) y un menú "Agregar posición" que solo ofrece
/// las posiciones aún no añadidas. Muestra el total de cupos. Espejo de
/// `PositionSlotsEditor` en `CreateDraftScreen.kt`.
private struct PositionSlotsEditor: View {
    @Binding var positionSlots: [PositionSlot]

    private var total: Int { positionSlots.reduce(0) { $0 + Int($1.slots) } }
    private var available: [String] {
        DomainCatalogs.shared.POSITIONS.filter { pos in !positionSlots.contains { $0.position == pos } }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(String(localized: "create_slots_title"))
                    .font(ElDraftTypography.titleSmall)
                    .foregroundStyle(ElDraftColors.onBackground)
                Spacer()
                Text(String(format: String(localized: "create_total"), total))
                    .font(ElDraftTypography.bodySmall)
                    .foregroundStyle((1...30).contains(total) ? ElDraftColors.primary : ElDraftColors.error)
            }
            Spacer().frame(height: ElDraftSpacing.sm)

            if positionSlots.isEmpty {
                Text(String(localized: "create_slots_help"))
                    .font(ElDraftTypography.bodySmall)
                    .foregroundStyle(ElDraftColors.onBackground.opacity(ElDraftAlpha.textTertiary))
                Spacer().frame(height: ElDraftSpacing.sm)
            }

            ForEach(positionSlots, id: \.position) { ps in
                HStack(spacing: ElDraftSpacing.sm) {
                    Text(ps.position)
                        .foregroundStyle(ElDraftColors.onBackground)
                    Spacer()
                    Button {
                        changeSlots(for: ps.position, delta: -1)
                    } label: {
                        Text("–").frame(width: 32, height: 32)
                    }
                    .buttonStyle(.bordered)
                    .disabled(ps.slots <= 1)

                    Text("\(ps.slots)")
                        .frame(minWidth: 24)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(ElDraftColors.onBackground)

                    Button {
                        changeSlots(for: ps.position, delta: 1)
                    } label: {
                        Text("+").frame(width: 32, height: 32)
                    }
                    .buttonStyle(.bordered)

                    Button {
                        positionSlots.removeAll { $0.position == ps.position }
                    } label: {
                        Image(systemName: "xmark")
                            .foregroundStyle(ElDraftColors.onBackground.opacity(ElDraftAlpha.textTertiary))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.vertical, ElDraftSpacing.xs)
            }

            Spacer().frame(height: ElDraftSpacing.sm)

            Menu {
                ForEach(available, id: \.self) { pos in
                    Button(pos) {
                        positionSlots.append(PositionSlot(position: pos, slots: 1))
                    }
                }
            } label: {
                Text(String(localized: "create_add_position"))
                    .font(ElDraftTypography.labelLarge)
                    .foregroundStyle(available.isEmpty ? ElDraftColors.onSurface.opacity(ElDraftAlpha.disabled) : ElDraftColors.primary)
                    .padding(.horizontal, ElDraftSpacing.md)
                    .padding(.vertical, ElDraftSpacing.sm)
                    .overlay(ElDraftShapes.field.stroke(ElDraftColors.outline, lineWidth: ElDraftSizes.borderHairline))
            }
            .disabled(available.isEmpty)
        }
    }

    private func changeSlots(for position: String, delta: Int) {
        guard let idx = positionSlots.firstIndex(where: { $0.position == position }) else { return }
        let current = positionSlots[idx]
        let next = min(max(Int(current.slots) + delta, 1), 30)
        positionSlots[idx] = PositionSlot(position: current.position, slots: Int32(next))
    }
}

/// Sheet con un `DatePicker` de calendario + confirmar/cancelar. SwiftUI no
/// tiene el diálogo de fecha de Material3, así que el "Aceptar" explícito se
/// arma con un footer propio en vez de un `DatePickerDialog`.
private struct DatePickerSheet: View {
    let minDate: Date
    let onConfirm: (Date) -> Void
    let onCancel: () -> Void

    @State private var draft: Date

    init(initialDate: Date, minDate: Date, onConfirm: @escaping (Date) -> Void, onCancel: @escaping () -> Void) {
        self.minDate = minDate
        self.onConfirm = onConfirm
        self.onCancel = onCancel
        _draft = State(initialValue: initialDate)
    }

    var body: some View {
        VStack(spacing: ElDraftSpacing.lg) {
            DatePicker("", selection: $draft, in: minDate..., displayedComponents: [.date])
                .datePickerStyle(.graphical)
                .labelsHidden()
            HStack {
                Button(String(localized: "action_cancel"), action: onCancel)
                Spacer()
                Button(String(localized: "action_accept")) { onConfirm(draft) }
                    .fontWeight(.semibold)
            }
        }
        .padding(ElDraftSpacing.lg)
    }
}

private struct TimePickerSheet: View {
    let onConfirm: (Date) -> Void
    let onCancel: () -> Void

    @State private var draft: Date

    init(initialDate: Date, onConfirm: @escaping (Date) -> Void, onCancel: @escaping () -> Void) {
        self.onConfirm = onConfirm
        self.onCancel = onCancel
        _draft = State(initialValue: initialDate)
    }

    var body: some View {
        VStack(spacing: ElDraftSpacing.lg) {
            DatePicker("", selection: $draft, displayedComponents: [.hourAndMinute])
                .datePickerStyle(.wheel)
                .labelsHidden()
            HStack {
                Button(String(localized: "action_cancel"), action: onCancel)
                Spacer()
                Button(String(localized: "action_accept")) { onConfirm(draft) }
                    .fontWeight(.semibold)
            }
        }
        .padding(ElDraftSpacing.lg)
    }
}
