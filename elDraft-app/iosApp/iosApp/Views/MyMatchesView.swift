import SwiftUI
import shared

/// Sección "Organizo": convocatorias que el usuario ha creado. Espejo de
/// `OrganizoScreen` en `TabScreens.kt`.
struct MyMatchesView: View {
    let onCreateDraft: () -> Void
    let onOpenApplicants: (String) -> Void
    let onOpenQrGenerator: (String) -> Void
    let onOpenQrScanner: (String) -> Void
    let onOpenRating: (String) -> Void
    let onOpenAttendance: (String) -> Void

    @State private var viewModel = MyMatchesViewModel()
    @State private var cancelViewModel = CancelConvocatoryViewModel()
    @State private var cancelTargetId: String?
    // Sobrevive a cambiar de pestaña y volver, igual que `rememberSaveable` en Android.
    @State private var tab: MatchListTab = .proximos
    @State private var toastMessage: String?

    private var upcoming: [Convocatory] {
        viewModel.matches.filter { !MatchTiming.isMatchOver($0.scheduledAt) }
    }

    // Los terminados que siguen aquí es porque queda algo por hacer (declarar
    // asistencia o calificar): el backend ya retiró los que no piden nada.
    private var finished: [Convocatory] {
        viewModel.matches.filter { MatchTiming.isMatchOver($0.scheduledAt) }
    }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            content
            fab
        }
        .background(ElDraftColors.background)
        .toast(message: $toastMessage)
        .task { viewModel.load() }
        .onChange(of: viewModel.error) { _, newValue in
            if let newValue {
                toastMessage = newValue
            }
        }
        // El error de cancelación NO va al toast de la pantalla: el sheet se
        // dibuja encima y lo taparía. Se muestra dentro del propio sheet.
        .onChange(of: cancelViewModel.success) { _, success in
            if success {
                cancelTargetId = nil
                cancelViewModel.resetSuccess()
                viewModel.load()
                toastMessage = String(localized: "convocatory_cancelled_snackbar")
            }
        }
        .sheet(isPresented: cancelSheetPresented) {
            CancelConvocatorySheet(
                isLoading: cancelViewModel.isLoading,
                error: cancelViewModel.error,
                onConfirm: { reason in
                    if let id = cancelTargetId {
                        cancelViewModel.cancel(convocatoryId: id, reason: reason)
                    }
                },
                onDismiss: {
                    cancelTargetId = nil
                    cancelViewModel.clearError()
                }
            )
        }
    }

    private var cancelSheetPresented: Binding<Bool> {
        Binding(
            get: { cancelTargetId != nil },
            set: { presented in if !presented { cancelTargetId = nil } }
        )
    }

    @ViewBuilder
    private var content: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: ElDraftSpacing.lg)
                ScreenHeader(
                    title: String(localized: "organize_header_title"),
                    subtitle: String(localized: "organize_header_subtitle")
                )
                Spacer().frame(height: ElDraftSpacing.lg2)

                if viewModel.isLoading && viewModel.matches.isEmpty {
                    LoadingState()
                        .frame(maxWidth: .infinity, minHeight: 320)
                } else if viewModel.matches.isEmpty {
                    // Sin nada que conmutar, el conmutador estorba.
                    EmptyState(
                        title: String(localized: "organize_empty_title"),
                        message: String(localized: "organize_empty_message"),
                        icon: "⚽"
                    )
                    .frame(maxWidth: .infinity, minHeight: 320)
                } else {
                    MatchListTabsView(selected: tab, pendingCount: finished.count, onSelect: { tab = $0 })
                    Spacer().frame(height: ElDraftSpacing.md)

                    let shown = tab == .proximos ? upcoming : finished
                    if shown.isEmpty {
                        // No es "aún no has creado": creaste, pero ya se
                        // jugaron y están en la otra pestaña.
                        if tab == .proximos {
                            EmptyState(
                                title: String(localized: "upcoming_empty_title"),
                                message: String(localized: "organize_upcoming_empty_message"),
                                icon: "⚽"
                            )
                            .frame(maxWidth: .infinity, minHeight: 260)
                        } else {
                            EmptyState(
                                title: String(localized: "closed_empty_title"),
                                message: String(localized: "closed_empty_message"),
                                icon: "✅"
                            )
                            .frame(maxWidth: .infinity, minHeight: 260)
                        }
                    } else {
                        VStack(alignment: .leading, spacing: ElDraftSpacing.md) {
                            if tab == .cerrados {
                                Text(String(localized: "closed_caption_organize"))
                                    .font(ElDraftTypography.bodySmall)
                                    .foregroundStyle(ElDraftColors.onBackground.opacity(ElDraftAlpha.textSecondary))
                            }
                            ForEach(shown, id: \.id) { match in
                                MyMatchCard(
                                    match: match,
                                    onOpenApplicants: { onOpenApplicants(match.id) },
                                    onOpenQrGenerator: { onOpenQrGenerator(match.id) },
                                    onOpenQrScanner: { onOpenQrScanner(match.id) },
                                    onOpenRating: { onOpenRating(match.id) },
                                    onOpenAttendance: { onOpenAttendance(match.id) },
                                    onCancelMatch: { cancelTargetId = match.id }
                                )
                            }
                        }
                        .padding(.top, ElDraftSpacing.md)
                        .padding(.bottom, 88)
                    }
                }
            }
            .padding(.horizontal, ElDraftSpacing.lg)
        }
        .refreshable { await refresh() }
    }

    private var fab: some View {
        Button(action: onCreateDraft) {
            Text(String(localized: "fab_add"))
                .font(.system(size: 24, weight: .medium))
                .foregroundStyle(ElDraftColors.onPrimary)
                .frame(width: 56, height: 56)
                .background(ElDraftColors.primary, in: Circle())
                .shadow(radius: ElDraftElevation.overlay)
        }
        .padding(.trailing, ElDraftSpacing.lg)
        .padding(.bottom, ElDraftSpacing.xl)
    }

    private func refresh() async {
        viewModel.load()
        while viewModel.isLoading {
            try? await Task.sleep(nanoseconds: 150_000_000)
        }
    }
}

private struct ApplicantsButton: View {
    let pendingCount: Int
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: ElDraftSpacing.xs) {
                Image(systemName: ConvocatoryIcons.groups)
                Text(String(localized: "organize_view_applicants"))
                if pendingCount > 0 {
                    Text("\(pendingCount)")
                        .font(ElDraftTypography.labelSmall)
                        .fontWeight(.bold)
                        .foregroundStyle(ElDraftColors.onError)
                        .padding(.horizontal, ElDraftSpacing.xs)
                        .background(ElDraftColors.error, in: Capsule())
                }
            }
            .font(ElDraftTypography.labelLarge)
            .foregroundStyle(ElDraftColors.onSurface)
            .padding(.horizontal, ElDraftSpacing.md)
            .padding(.vertical, ElDraftSpacing.xs2)
            .background(ElDraftColors.surfaceVariant, in: ElDraftShapes.pill)
            .overlay(ElDraftShapes.pill.stroke(ElDraftColors.outline, lineWidth: ElDraftSizes.borderHairline))
        }
        .buttonStyle(.plain)
    }
}

/// Aviso en la card cuando el consenso marcó al organizador como ausente:
/// explica que no llegó y que no puede declarar la asistencia. Espejo de
/// `OrganizerNoShowBanner` en `TabScreens.kt`.
private struct OrganizerNoShowBanner: View {
    var body: some View {
        HStack(alignment: .top, spacing: ElDraftSpacing.sm) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: ElDraftSizes.iconMd))
                .foregroundStyle(ElDraftColors.onErrorContainer)
            VStack(alignment: .leading, spacing: ElDraftSpacing.xxs) {
                Text(String(localized: "organizer_no_show_banner_title"))
                    .font(ElDraftTypography.labelLarge)
                    .fontWeight(.semibold)
                    .foregroundStyle(ElDraftColors.onErrorContainer)
                Text(String(localized: "attendance_no_show_consensus"))
                    .font(ElDraftTypography.bodySmall)
                    .foregroundStyle(ElDraftColors.onErrorContainer.opacity(0.85)) // design-tokens-ignore: legibilidad sobre errorContainer
            }
        }
        .padding(.horizontal, ElDraftSpacing.md)
        .padding(.vertical, ElDraftSpacing.md2)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ElDraftColors.errorContainer, in: ElDraftShapes.sm)
    }
}

private struct MyMatchCard: View {
    let match: Convocatory
    let onOpenApplicants: () -> Void
    let onOpenQrGenerator: () -> Void
    let onOpenQrScanner: () -> Void
    let onOpenRating: () -> Void
    let onOpenAttendance: () -> Void
    let onCancelMatch: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center) {
                ScheduleBanner(scheduledAt: match.scheduledAt)
                Spacer()
                MatchStateBadge(scheduledAt: match.scheduledAt)
            }

            Spacer().frame(height: ElDraftSpacing.md2)

            Text(match.format.isEmpty ? String(localized: "match_card_fallback_title") : match.format)
                .font(ElDraftTypography.titleLarge)
                .fontWeight(.bold)
                .foregroundStyle(ElDraftColors.onSurface)

            Spacer().frame(height: ElDraftSpacing.sm)

            FlowLayout(hSpacing: ElDraftSpacing.xs2, vSpacing: ElDraftSpacing.xs2) {
                if !match.ambiente.isEmpty {
                    MetaChip(text: match.ambiente, primary: true)
                }
                if let address = match.addressText, !address.isEmpty {
                    MetaChip(text: address, icon: ConvocatoryIcons.place)
                }
                MetaChip(
                    text: String(format: String(localized: "match_slots_summary"), match.slotsNeeded, match.positionRequired),
                    icon: ConvocatoryIcons.groups
                )
                MetaChip(text: formatFee(match.fee), icon: ConvocatoryIcons.fee)
            }

            Spacer().frame(height: ElDraftSpacing.md2)

            HStack {
                ApplicantsButton(pendingCount: Int(match.pendingCount), action: onOpenApplicants)
                Spacer()
            }

            // Aviso si el consenso marcó al organizador como ausente: no podrá
            // declarar la asistencia (el botón queda deshabilitado).
            if match.organizerNoShow {
                Spacer().frame(height: ElDraftSpacing.md)
                OrganizerNoShowBanner()
            }

            Spacer().frame(height: ElDraftSpacing.sm)
            Divider().overlay(ElDraftColors.onSurface.opacity(ElDraftAlpha.divider))
            Spacer().frame(height: ElDraftSpacing.xs)

            // Ya escaneó: ofrecerle otra vez "Ya llegué" es pedirle algo que
            // ya hizo. Se cambia por la confirmación de que quedó registrado.
            if match.attended {
                AttendanceRegisteredView()
                Spacer().frame(height: ElDraftSpacing.xs)
            }

            HStack(spacing: 0) {
                Spacer(minLength: 0)
                // El organizador marca su propia presencia escaneando el QR
                // que le genere un aprobado: ya no se asume presente.
                if !match.attended {
                    QuickActionButton(icon: "location.fill", label: String(localized: "action_arrived"), primary: true, action: onOpenQrScanner)
                    Spacer(minLength: 0)
                }
                QuickActionButton(icon: "qrcode", label: String(localized: "action_show_qr"), action: onOpenQrGenerator)
                Spacer(minLength: 0)
                // Tras el cierre del partido (inicio + 45 min): el organizador
                // declara quién no llegó y puede calificar a los presentes.
                if MatchTiming.isMatchOver(match.scheduledAt) {
                    QuickActionButton(icon: "checklist", label: String(localized: "action_attendance"), enabled: !match.organizerNoShow, action: onOpenAttendance)
                    Spacer(minLength: 0)
                    QuickActionButton(icon: "star.fill", label: String(localized: "action_rate"), action: onOpenRating)
                    Spacer(minLength: 0)
                }
            }

            // Botón cancelar: solo visible si el partido aún no comenzó y
            // está activo. Una vez arrancado ya no hay nada que cancelar y el
            // backend lo rechaza igual.
            if ["active", "full"].contains(match.status), !MatchTiming.hasMatchStarted(match.scheduledAt) {
                Spacer().frame(height: ElDraftSpacing.sm)
                Button(action: onCancelMatch) {
                    Text(String(localized: "cancel_match_button"))
                        .font(ElDraftTypography.labelLarge)
                        .foregroundStyle(ElDraftColors.error)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, ElDraftSpacing.sm)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(ElDraftSpacing.lg)
        .background(ElDraftColors.surface, in: ElDraftShapes.md)
        .shadow(radius: ElDraftElevation.card)
        .contentShape(Rectangle())
        .onTapGesture(perform: onOpenApplicants)
        .accessibilityAddTraits(.isButton)
    }
}

private struct CancelConvocatorySheet: View {
    let isLoading: Bool
    /// Fallo del último intento de cancelar, o nil. Se muestra dentro del sheet.
    let error: String?
    let onConfirm: (String) -> Void
    let onDismiss: () -> Void

    @State private var selectedReason: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ElDraftSpacing.sm) {
                Text(String(localized: "cancel_match_title"))
                    .font(ElDraftTypography.titleLarge)
                    .fontWeight(.bold)
                    .foregroundStyle(ElDraftColors.onSurface)
                Text(String(localized: "cancel_match_message"))
                    .font(ElDraftTypography.bodyMedium)
                    .foregroundStyle(ElDraftColors.onSurfaceVariant)

                Spacer().frame(height: ElDraftSpacing.xs)

                // Los motivos como chips: cinco filas de radio ocupaban casi
                // toda la hoja; envueltos caben en dos.
                FlowLayout(hSpacing: ElDraftSpacing.xs2, vSpacing: ElDraftSpacing.xs2) {
                    ForEach(DomainCatalogs.shared.CANCELLATION_REASONS, id: \.self) { reason in
                        ReasonChip(
                            label: reason,
                            selected: selectedReason == reason,
                            enabled: !isLoading,
                            action: { selectedReason = reason }
                        )
                    }
                }

                // Va en el footer, no arriba: un error que hay que buscar
                // scrolleando es un error que no se lee.
                if let error {
                    Spacer().frame(height: ElDraftSpacing.sm)
                    Text(error)
                        .font(ElDraftTypography.bodyMedium)
                        .foregroundStyle(ElDraftColors.onErrorContainer)
                        .padding(.horizontal, ElDraftSpacing.md)
                        .padding(.vertical, ElDraftSpacing.sm)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(ElDraftColors.errorContainer, in: ElDraftShapes.md)
                }

                Spacer().frame(height: ElDraftSpacing.lg)

                Button {
                    if let selectedReason { onConfirm(selectedReason) }
                } label: {
                    Group {
                        if isLoading {
                            ProgressView().tint(ElDraftColors.onPrimary)
                        } else {
                            Text(String(localized: "cancel_match_confirm"))
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, ElDraftSpacing.md)
                }
                .buttonStyle(.borderedProminent)
                .tint(ElDraftColors.primary)
                .disabled(selectedReason == nil || isLoading)

                Button(String(localized: "action_back_short")) { onDismiss() }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, ElDraftSpacing.md)
                    .buttonStyle(.bordered)
                    .disabled(isLoading)
            }
            .padding(.horizontal, ElDraftSpacing.xl)
            .padding(.top, ElDraftSpacing.lg)
            .padding(.bottom, ElDraftSpacing.xxl)
        }
        .presentationDetents([.medium, .large])
    }
}

/// Chip seleccionable para elegir el motivo de cancelación. Mismo estilo que
/// `SelectableChip` en `PinDetailSheet.swift`.
private struct ReasonChip: View {
    let label: String
    let selected: Bool
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(ElDraftTypography.labelLarge)
                .foregroundStyle(selected ? ElDraftColors.onPrimaryContainer : ElDraftColors.onSurface.opacity(enabled ? 1 : ElDraftAlpha.disabled))
                .padding(.horizontal, ElDraftSpacing.md2)
                .padding(.vertical, ElDraftSpacing.xs)
                .background(selected ? ElDraftColors.primaryContainer : Color.clear, in: ElDraftShapes.pill)
                .overlay(ElDraftShapes.pill.stroke(ElDraftColors.outline, lineWidth: selected ? 0 : ElDraftSizes.borderHairline))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}
