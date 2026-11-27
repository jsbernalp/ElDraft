import SwiftUI
import shared

/// Sección "Juego": postulaciones propias del jugador. Espejo de `JuegoScreen`
/// en `TabScreens.kt`.
struct MyPostulationsView: View {
    let onOpenQrScanner: (String) -> Void
    let onOpenQrGenerator: (String) -> Void
    let onOpenRating: (String) -> Void

    @State private var viewModel = MyPostulationsViewModel()
    @State private var noShowViewModel = NoShowViewModel()
    @State private var priorityViewModel = TeamPriorityViewModel()
    @State private var withdrawTargetId: String?
    // Sobrevive a cambiar de pestaña y volver, igual que `rememberSaveable` en Android.
    @State private var tab: MatchListTab = .proximos
    @State private var toastMessage: String?

    // Cerrado es todo lo que ya no está en juego para el jugador: los
    // partidos terminados que aún le piden algo (calificar o reportar que el
    // organizador no llegó) y los rechazos, que no piden nada pero tampoco
    // son "próximos" — ese partido se juega sin él.
    private var upcoming: [MyPostulation] {
        viewModel.postulations.filter { !MatchTiming.isMatchOver($0.convocatory.scheduledAt) && $0.status != "rejected" }
    }
    private var closed: [MyPostulation] {
        viewModel.postulations.filter { MatchTiming.isMatchOver($0.convocatory.scheduledAt) || $0.status == "rejected" }
    }
    // El badge cuenta SOLO lo accionable: sumar rechazos lo convertiría en un
    // aviso de nada, y esos se aprenden a ignorar.
    private var pendingCount: Int {
        closed.filter { MatchTiming.isMatchOver($0.convocatory.scheduledAt) }.count
    }

    var body: some View {
        content
            .background(ElDraftColors.background)
            .toast(message: $toastMessage)
            .task {
                viewModel.load()
                priorityViewModel.load()
            }
            .onChange(of: priorityViewModel.error) { _, newValue in
                if let newValue {
                    toastMessage = newValue
                    priorityViewModel.clearError()
                }
            }
            .onChange(of: priorityViewModel.responded) { _, responded in
                if responded {
                    priorityViewModel.clearResponded()
                    toastMessage = String(localized: "team_priority_responded_snackbar")
                }
            }
            .onChange(of: viewModel.error) { _, newValue in
                if let newValue {
                    toastMessage = newValue
                    viewModel.clearError()
                }
            }
            .onChange(of: viewModel.withdrawSuccess) { _, success in
                if success {
                    withdrawTargetId = nil
                    viewModel.resetWithdrawSuccess()
                    toastMessage = String(localized: "postulation_withdrawn")
                }
            }
            // El error de retirar NO va al toast de la pantalla: el sheet se
            // dibuja encima y lo taparía. Se muestra dentro del propio sheet.
            .sheet(isPresented: withdrawSheetPresented) {
                WithdrawPostulationSheet(
                    isLoading: viewModel.withdrawingId != nil,
                    error: viewModel.withdrawError,
                    onConfirm: {
                        if let id = withdrawTargetId { viewModel.withdraw(postulationId: id) }
                    },
                    onDismiss: {
                        withdrawTargetId = nil
                        viewModel.clearWithdrawError()
                    }
                )
            }
    }

    private var withdrawSheetPresented: Binding<Bool> {
        Binding(
            get: { withdrawTargetId != nil },
            set: { presented in if !presented { withdrawTargetId = nil } }
        )
    }

    @ViewBuilder
    private var content: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: ElDraftSpacing.lg)
                ScreenHeader(
                    title: String(localized: "play_header_title"),
                    subtitle: String(localized: "play_header_subtitle")
                )
                Spacer().frame(height: ElDraftSpacing.lg2)

                if viewModel.isLoading && viewModel.postulations.isEmpty {
                    LoadingState()
                        .frame(maxWidth: .infinity, minHeight: 320)
                } else if viewModel.postulations.isEmpty && priorityViewModel.invites.isEmpty {
                    EmptyState(
                        title: String(localized: "play_empty_title"),
                        message: String(localized: "play_empty_message"),
                        icon: "🏃"
                    )
                    .frame(maxWidth: .infinity, minHeight: 320)
                } else {
                    MatchListTabsView(selected: tab, pendingCount: pendingCount, onSelect: { tab = $0 })
                    Spacer().frame(height: ElDraftSpacing.md)

                    let shown = tab == .proximos ? upcoming : closed
                    // Lo que espera tu respuesta va primero: tiene cuenta regresiva.
                    if tab == .proximos {
                        VStack(alignment: .leading, spacing: ElDraftSpacing.md) {
                            ForEach(priorityViewModel.invites, id: \.convocatory.id) { invite in
                                TeamPriorityCard(
                                    invite: invite,
                                    isResponding: priorityViewModel.respondingId == invite.convocatory.id,
                                    onGo: { position in
                                        priorityViewModel.respond(convocatoryId: invite.convocatory.id, accept: true, position: position)
                                    },
                                    onCant: {
                                        priorityViewModel.respond(convocatoryId: invite.convocatory.id, accept: false, position: nil)
                                    }
                                )
                            }
                        }
                        .padding(.top, priorityViewModel.invites.isEmpty ? 0 : ElDraftSpacing.md)
                    }
                    if shown.isEmpty && !(tab == .proximos && !priorityViewModel.invites.isEmpty) {
                        if tab == .proximos {
                            EmptyState(
                                title: String(localized: "upcoming_empty_title"),
                                message: String(localized: "play_empty_message"),
                                icon: "🏃"
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
                                Text(String(localized: "closed_caption_play"))
                                    .font(ElDraftTypography.bodySmall)
                                    .foregroundStyle(ElDraftColors.onBackground.opacity(ElDraftAlpha.textSecondary))
                            }
                            ForEach(shown, id: \.id) { p in
                                MyGameCard(
                                    postulation: p,
                                    onScanQr: { onOpenQrScanner(p.convocatory.id) },
                                    onGenerateQr: { onOpenQrGenerator(p.convocatory.id) },
                                    onRate: { onOpenRating(p.convocatory.id) },
                                    noShowViewModel: noShowViewModel,
                                    onWithdraw: { withdrawTargetId = p.id }
                                )
                            }
                        }
                        .padding(.top, ElDraftSpacing.md)
                        .padding(.bottom, ElDraftSpacing.xl)
                    }
                }
            }
            .padding(.horizontal, ElDraftSpacing.lg)
        }
        .refreshable { await refresh() }
    }

    private func refresh() async {
        viewModel.load()
        while viewModel.isLoading {
            try? await Task.sleep(nanoseconds: 150_000_000)
        }
    }
}

private struct WithdrawPostulationSheet: View {
    let isLoading: Bool
    /// Fallo del último intento de retirar, o nil. Se muestra dentro del sheet.
    let error: String?
    let onConfirm: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: ElDraftSpacing.sm) {
            Text(String(localized: "withdraw_dialog_title"))
                .font(ElDraftTypography.titleLarge)
                .fontWeight(.bold)
                .foregroundStyle(ElDraftColors.onSurface)
            Text(String(localized: "withdraw_dialog_message"))
                .font(ElDraftTypography.bodyMedium)
                .foregroundStyle(ElDraftColors.onSurfaceVariant)

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

            Button(action: onConfirm) {
                Group {
                    if isLoading {
                        ProgressView().tint(ElDraftColors.onPrimary)
                    } else {
                        Text(String(localized: "withdraw_confirm_short"))
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, ElDraftSpacing.md)
            }
            .buttonStyle(.borderedProminent)
            .tint(ElDraftColors.primary)
            .disabled(isLoading)

            Button(String(localized: "action_cancel")) { onDismiss() }
                .frame(maxWidth: .infinity)
                .padding(.vertical, ElDraftSpacing.md)
                .buttonStyle(.bordered)
                .disabled(isLoading)
        }
        .padding(.horizontal, ElDraftSpacing.xl)
        .padding(.top, ElDraftSpacing.lg)
        .padding(.bottom, ElDraftSpacing.xxl)
        .presentationDetents([.medium])
    }
}

private struct MyGameCard: View {
    let postulation: MyPostulation
    let onScanQr: () -> Void
    let onGenerateQr: () -> Void
    let onRate: () -> Void
    let noShowViewModel: NoShowViewModel
    let onWithdraw: () -> Void

    private var c: Convocatory { postulation.convocatory }
    private var approved: Bool { postulation.status == "approved" }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center) {
                ScheduleBanner(scheduledAt: c.scheduledAt)
                Spacer()
                StatusChip(status: postulation.status)
            }

            Spacer().frame(height: ElDraftSpacing.md2)

            Text(c.format.isEmpty ? String(localized: "match_card_fallback_title") : c.format)
                .font(ElDraftTypography.titleLarge)
                .fontWeight(.bold)
                .foregroundStyle(ElDraftColors.onSurface)

            Spacer().frame(height: ElDraftSpacing.sm)

            FlowLayout(hSpacing: ElDraftSpacing.xs2, vSpacing: ElDraftSpacing.xs2) {
                if !c.ambiente.isEmpty {
                    MetaChip(text: c.ambiente, primary: true)
                }
                if let address = c.addressText, !address.isEmpty {
                    MetaChip(text: address, icon: ConvocatoryIcons.place)
                }
                MetaChip(text: formatFee(c.fee), icon: ConvocatoryIcons.fee)
            }

            // El resto de la card (reporte de no-show, accesos rápidos,
            // retirar postulación) solo aplica a postulaciones aprobadas:
            // así lo decide `JuegoScreen` en Android — pendiente/rechazada/
            // cancelada se quedan solo con lo de arriba.
            if approved {
                approvedContent
            }
        }
        .padding(ElDraftSpacing.lg)
        .background(ElDraftColors.surface, in: ElDraftShapes.md)
        .shadow(radius: ElDraftElevation.card)
        .task(id: c.id) {
            if approved { noShowViewModel.load(convocatoryId: c.id) }
        }
    }

    @ViewBuilder
    private var approvedContent: some View {
        let noShowState = noShowViewModel.state(for: c.id)
        let consensusNoShow = noShowState.status?.consensusReached == true
        let markedNoShow = noShowState.status?.markedNoShow == true
        // "Calificar" solo tras el fin estimado del partido (inicio + 45 min)
        // o si ya se confirmó que el organizador no llegó (no hubo partido
        // que esperar).
        let showRate = MatchTiming.isMatchOver(c.scheduledAt) || consensusNoShow
        let hasLocation = c.lat != 0.0 || c.lng != 0.0

        // Aviso si el organizador marcó a este convocado como ausente:
        // explica por qué bajó su % de asistencia y su responsabilidad.
        if markedNoShow {
            Spacer().frame(height: ElDraftSpacing.md)
            MarkedNoShowBanner()
        }

        Spacer().frame(height: ElDraftSpacing.sm)
        Divider().overlay(ElDraftColors.onSurface.opacity(ElDraftAlpha.divider))
        Spacer().frame(height: ElDraftSpacing.xs)

        // Ya escaneó: "Ya llegué" sobra y en su lugar va la confirmación.
        if postulation.attended {
            AttendanceRegisteredView()
            Spacer().frame(height: ElDraftSpacing.xs)
        }

        // Accesos rápidos del día del partido. "Calificar" aparece según la fase.
        HStack(spacing: 0) {
            Spacer(minLength: 0)
            // "Cómo llegar": abre Maps con la ruta al destino (solo si la
            // convocatoria tiene una ubicación definida).
            if hasLocation {
                QuickActionButton(
                    icon: "arrow.triangle.turn.up.right.circle.fill",
                    label: String(localized: "action_directions"),
                    action: { openDirections(lat: c.lat, lng: c.lng, label: c.addressText) }
                )
                Spacer(minLength: 0)
            }
            if !postulation.attended {
                QuickActionButton(icon: "figure.walk.arrival", label: String(localized: "action_arrived"), primary: true, action: onScanQr)
                Spacer(minLength: 0)
            }
            // Un aprobado puede generar el QR para que el organizador lo escane.
            QuickActionButton(icon: "qrcode", label: String(localized: "action_show_qr"), action: onGenerateQr)
            Spacer(minLength: 0)
            if showRate {
                QuickActionButton(icon: "star.fill", label: String(localized: "action_rate"), action: onRate)
                Spacer(minLength: 0)
            }
        }

        // Reporte "el organizador no llegó": visible tras la tolerancia de
        // inicio (+15 min) para que el convocado no se quede atascado en el
        // escáner sin un QR que leer.
        NoShowSectionView(
            scheduledAt: c.scheduledAt,
            uiState: noShowState,
            onReport: { noShowViewModel.report(convocatoryId: c.id) }
        )

        // Retirar postulación: solo si aún no comenzó el partido, la
        // postulación sigue activa (pending o approved) y no registró
        // asistencia todavía. Quien ya escaneó está en la cancha: retirarse
        // a esas alturas no significa nada.
        if ["pending", "approved"].contains(postulation.status),
           !MatchTiming.hasMatchStarted(c.scheduledAt),
           !postulation.attended {
            Spacer().frame(height: ElDraftSpacing.sm)
            Button(action: onWithdraw) {
                Text(String(localized: "withdraw_confirm"))
                    .font(ElDraftTypography.labelLarge)
                    .foregroundStyle(ElDraftColors.error)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, ElDraftSpacing.sm)
            }
            .buttonStyle(.plain)
        }
    }
}

/// Aviso en la card del convocado cuando el organizador lo marcó como
/// ausente. Explica el impacto (asistencia + responsabilidad) en tono
/// neutro. Espejo de `MarkedNoShowBanner` en `TabScreens.kt`.
private struct MarkedNoShowBanner: View {
    var body: some View {
        HStack(alignment: .top, spacing: ElDraftSpacing.sm) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: ElDraftSizes.iconMd))
                .foregroundStyle(ElDraftColors.onErrorContainer)
            VStack(alignment: .leading, spacing: ElDraftSpacing.xxs) {
                Text(String(localized: "attendance_no_show_by_organizer"))
                    .font(ElDraftTypography.labelLarge)
                    .fontWeight(.semibold)
                    .foregroundStyle(ElDraftColors.onErrorContainer)
                Text(String(localized: "marked_no_show_banner_message"))
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

/// Bloque de reporte "el organizador no se presentó". Recibe el estado ya
/// cargado y un callback para emitir el voto. Solo aparece cuando aplica:
///  - ya hay consenso (aviso) o el usuario ya reportó (progreso), o
///  - puede reportar Y ya pasó la tolerancia de inicio (scheduled_at + 15
///    min), que es justo cuando el convocado se quedaría sin QR que escanear.
/// Espejo de `NoShowSection` en `TabScreens.kt`.
private struct NoShowSectionView: View {
    let scheduledAt: String
    let uiState: NoShowUiState
    let onReport: () -> Void

    @State private var showConfirm = false

    var body: some View {
        if let status = uiState.status {
            let canReportNow = status.canReport && MatchTiming.canReportNoShowByTime(scheduledAt)
            let visible = status.consensusReached || status.alreadyReported || canReportNow
            if visible {
                VStack(alignment: .leading, spacing: 0) {
                    Spacer().frame(height: ElDraftSpacing.md2)
                    if status.consensusReached {
                        Text(String(localized: "organizer_no_show_consensus"))
                            .font(ElDraftTypography.labelMedium)
                            .foregroundStyle(ElDraftColors.onErrorContainer)
                            .padding(.horizontal, ElDraftSpacing.md)
                            .padding(.vertical, ElDraftSpacing.sm)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(ElDraftColors.errorContainer, in: ElDraftShapes.md)
                    } else if status.alreadyReported {
                        Text(String(format: String(localized: "organizer_no_show_reported"), status.reports, status.attendees))
                            .font(ElDraftTypography.labelMedium)
                            .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textTertiary))
                    } else if canReportNow {
                        Button {
                            showConfirm = true
                        } label: {
                            HStack(spacing: ElDraftSpacing.sm) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .font(.system(size: ElDraftSizes.iconMd))
                                Text(String(localized: "organizer_no_show_button"))
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, ElDraftSpacing.sm)
                        }
                        .buttonStyle(.bordered)
                        .tint(ElDraftColors.error)
                        .disabled(uiState.isReporting)
                    }
                }
                .alert(String(localized: "no_show_report_title"), isPresented: $showConfirm) {
                    Button(String(localized: "organizer_no_show_dialog_confirm")) { onReport() }
                    Button(String(localized: "action_cancel"), role: .cancel) {}
                } message: {
                    Text(String(localized: "organizer_no_show_dialog"))
                }
            }
        }
    }
}

private struct StatusChip: View {
    let status: String

    private var labelAndColor: (String, Color) {
        switch status {
        case "approved": return (String(localized: "game_status_approved"), ElDraftColors.primary)
        case "rejected": return (String(localized: "game_status_rejected"), ElDraftColors.error)
        case "cancelled": return (String(localized: "game_status_cancelled"), ElDraftColors.onSurface.opacity(ElDraftAlpha.textMuted))
        default: return (String(localized: "game_status_pending"), ElDraftColors.onSurface.opacity(ElDraftAlpha.textMuted))
        }
    }

    var body: some View {
        let (label, color) = labelAndColor
        Text(label)
            .font(ElDraftTypography.labelMedium)
            .fontWeight(.semibold)
            .foregroundStyle(color)
    }
}
