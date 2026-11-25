import SwiftUI
import shared

/// Pantalla del organizador para declarar quién de sus convocados NO llegó.
/// Quien escaneó el QR aparece como "Presente" firme (no editable); a los
/// demás se les puede marcar "No llegó". Lo no marcado cuenta como presente.
/// Espejo de `AttendanceDeclarationScreen.kt`.
struct AttendanceDeclarationView: View {
    let convocatoryId: String

    @State private var viewModel = AttendanceDeclarationViewModel()
    @State private var toastMessage: String?

    var body: some View {
        VStack(spacing: 0) {
            content
            if !viewModel.state.rows.isEmpty {
                saveButton
            }
        }
        .background(ElDraftColors.background)
        .toast(message: $toastMessage)
        .task { viewModel.load(convocatoryId: convocatoryId) }
        .onChange(of: viewModel.state.error) { _, newValue in
            if let newValue {
                toastMessage = newValue
                viewModel.clearError()
            }
        }
        .onChange(of: viewModel.state.saved) { _, saved in
            if saved {
                toastMessage = String(localized: "attendance_saved")
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: ElDraftSpacing.sm)
                ScreenHeader(
                    title: String(localized: "attendance_header_title"),
                    subtitle: String(localized: "attendance_header_subtitle")
                )
                Spacer().frame(height: ElDraftSpacing.lg)

                if viewModel.state.isLoading && viewModel.state.rows.isEmpty {
                    LoadingState().frame(maxWidth: .infinity, minHeight: 320)
                } else if viewModel.state.rows.isEmpty && viewModel.state.blocked {
                    // Si el organizador fue marcado no-show por consenso, el
                    // backend responde 403 y la lista llega vacía: explicamos por qué.
                    EmptyState(
                        title: String(localized: "attendance_blocked_title"),
                        message: String(localized: "attendance_blocked_message"),
                        icon: "🚫"
                    )
                    .frame(maxWidth: .infinity, minHeight: 320)
                } else if viewModel.state.rows.isEmpty {
                    EmptyState(
                        title: String(localized: "attendance_empty_title"),
                        message: String(localized: "attendance_empty_message"),
                        icon: "📋"
                    )
                    .frame(maxWidth: .infinity, minHeight: 320)
                } else {
                    LazyVStack(spacing: ElDraftSpacing.md) {
                        ForEach(viewModel.state.rows, id: \.playerId) { row in
                            AttendanceRowCard(
                                row: row,
                                absent: viewModel.state.absent.contains(row.playerId),
                                onToggle: { viewModel.toggleAbsent(playerId: row.playerId) }
                            )
                        }
                    }
                    .padding(.vertical, ElDraftSpacing.md)
                }
            }
            .padding(.horizontal, ElDraftSpacing.xl)
        }
    }

    private var saveButton: some View {
        Button {
            viewModel.save(convocatoryId: convocatoryId)
        } label: {
            Group {
                if viewModel.state.isSaving {
                    ProgressView().tint(ElDraftColors.onPrimary)
                } else {
                    Text(String(localized: "attendance_save"))
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, ElDraftSpacing.md)
        }
        .buttonStyle(.borderedProminent)
        .tint(ElDraftColors.primary)
        .disabled(viewModel.state.isSaving)
        .padding(.horizontal, ElDraftSpacing.xl)
        .padding(.vertical, ElDraftSpacing.lg)
        .background(ElDraftColors.background)
    }
}

private struct AttendanceRowCard: View {
    let row: PlayerAttendanceRow
    let absent: Bool
    let onToggle: () -> Void

    var body: some View {
        HStack(spacing: ElDraftSpacing.md) {
            AvatarCircle(name: row.name, avatarUrl: row.avatarUrl)
            VStack(alignment: .leading, spacing: 0) {
                Text(row.name)
                    .font(ElDraftTypography.titleMedium)
                    .fontWeight(.semibold)
                    .foregroundStyle(ElDraftColors.onSurface)
                if let position = row.positionPrimary, !position.isEmpty {
                    Text(position)
                        .font(ElDraftTypography.bodySmall)
                        .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))
                }
            }
            Spacer()

            if row.scanned {
                PresentBadge()
            } else {
                AbsentToggle(absent: absent, onToggle: onToggle)
            }
        }
        .padding(ElDraftSpacing.lg)
        .background(ElDraftColors.surface, in: ElDraftShapes.md)
        .shadow(radius: ElDraftElevation.card)
    }
}

/// Distintivo fijo "Presente" para quien registró asistencia por QR.
private struct PresentBadge: View {
    var body: some View {
        HStack(spacing: ElDraftSpacing.xs) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: ElDraftSizes.iconSm))
            Text(String(localized: "attendance_present"))
                .font(ElDraftTypography.labelMedium)
                .fontWeight(.semibold)
        }
        .foregroundStyle(ElDraftColors.primary)
        .padding(.horizontal, ElDraftSpacing.md2)
        .padding(.vertical, ElDraftSpacing.xs2)
        .background(ElDraftColors.primary.opacity(ElDraftAlpha.containerSoft), in: ElDraftShapes.pill)
    }
}

/// Control "Llegó / No llegó" para los convocados sin escaneo.
private struct AbsentToggle: View {
    let absent: Bool
    let onToggle: () -> Void

    var body: some View {
        HStack(spacing: ElDraftSpacing.sm) {
            Text(absent ? String(localized: "attendance_absent") : String(localized: "attendance_arrived"))
                .font(ElDraftTypography.labelMedium)
                .fontWeight(.semibold)
                .foregroundStyle(absent ? ElDraftColors.error : ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))
            Toggle("", isOn: Binding(get: { absent }, set: { _ in onToggle() }))
                .labelsHidden()
                .tint(ElDraftColors.error)
        }
    }
}

private struct AvatarCircle: View {
    let name: String
    let avatarUrl: String?

    private var initial: String {
        name.trimmingCharacters(in: .whitespaces).first.map(String.init)?.uppercased()
            ?? String(localized: "rating_initial_fallback")
    }

    var body: some View {
        ZStack {
            Circle().fill(ElDraftColors.primary.opacity(ElDraftAlpha.containerStrong))
            if let avatarUrl, !avatarUrl.isEmpty, let url = URL(string: avatarUrl) {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    ProgressView()
                }
                .frame(width: ElDraftSizes.avatar, height: ElDraftSizes.avatar)
                .clipShape(Circle())
                .accessibilityLabel(String(format: String(localized: "rating_avatar_content_description"), name))
            } else {
                Text(initial)
                    .font(ElDraftTypography.titleMedium)
                    .fontWeight(.bold)
                    .foregroundStyle(ElDraftColors.primary)
            }
        }
        .frame(width: ElDraftSizes.avatar, height: ElDraftSizes.avatar)
    }
}
