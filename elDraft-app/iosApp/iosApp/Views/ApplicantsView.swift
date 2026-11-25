import SwiftUI
import shared

/// Vista del organizador: postulantes de una convocatoria. Espejo de
/// `ApplicantsScreen.kt`.
struct ApplicantsView: View {
    let convocatoryId: String
    let onOpenPlayerCromo: (String) -> Void

    @State private var viewModel = ApplicantsViewModel()
    @State private var toastMessage: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: ElDraftSpacing.sm)
                ScreenHeader(
                    title: String(localized: "applicants_header_title"),
                    subtitle: String(localized: "applicants_header_subtitle")
                )
                Spacer().frame(height: ElDraftSpacing.lg)

                if viewModel.isLoading && viewModel.applicants.isEmpty {
                    LoadingState()
                        .frame(maxWidth: .infinity, minHeight: 320)
                } else if viewModel.applicants.isEmpty {
                    EmptyState(
                        title: String(localized: "applicants_empty_title"),
                        message: String(localized: "applicants_empty_message"),
                        icon: "📋"
                    )
                    .frame(maxWidth: .infinity, minHeight: 320)
                } else {
                    VStack(alignment: .leading, spacing: ElDraftSpacing.md) {
                        ForEach(viewModel.applicants, id: \.id) { postulation in
                            ApplicantCard(
                                postulation: postulation,
                                isDeciding: viewModel.decidingIds.contains(postulation.id),
                                onApprove: { viewModel.approve(postulationId: postulation.id) },
                                onReject: { viewModel.reject(postulationId: postulation.id) },
                                onOpenCromo: { onOpenPlayerCromo(postulation.playerId) }
                            )
                        }
                    }
                    .padding(.vertical, ElDraftSpacing.md)
                    .padding(.bottom, ElDraftSpacing.xl)
                }
            }
            .padding(.horizontal, ElDraftSpacing.xl)
        }
        .background(ElDraftColors.background)
        .refreshable { await refresh() }
        .toast(message: $toastMessage)
        .task { viewModel.load(convocatoryId: convocatoryId) }
        .onChange(of: viewModel.error) { _, newValue in
            if let newValue {
                toastMessage = newValue
                viewModel.clearError()
            }
        }
    }

    private func refresh() async {
        viewModel.load(convocatoryId: convocatoryId)
        while viewModel.isLoading {
            try? await Task.sleep(nanoseconds: 150_000_000)
        }
    }
}

private struct ApplicantCard: View {
    let postulation: Postulation
    let isDeciding: Bool
    let onApprove: () -> Void
    let onReject: () -> Void
    let onOpenCromo: () -> Void

    private var player: PostulantSummary? { postulation.player }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: ElDraftSpacing.md) {
                AvatarCircle(
                    name: player?.name ?? String(localized: "rating_initial_fallback"),
                    avatarUrl: player?.avatarUrl
                )
                VStack(alignment: .leading, spacing: 0) {
                    Text(player?.name ?? String(localized: "applicants_player_fallback"))
                        .font(ElDraftTypography.titleMedium)
                        .fontWeight(.semibold)
                        .foregroundStyle(ElDraftColors.onSurface)

                    let subtitle = [
                        player?.positionPrimary,
                        player?.dominantFoot.map { String(format: String(localized: "applicants_dominant_foot"), $0) },
                    ]
                    .compactMap { $0 }
                    .joined(separator: " · ")
                    if !subtitle.isEmpty {
                        Text(subtitle)
                            .font(ElDraftTypography.bodySmall)
                            .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))
                    }

                    // Posición a la que se postuló en ESTE partido (distinta
                    // de la posición habitual de su ficha, que va arriba).
                    if let position = postulation.position, !position.isEmpty {
                        Spacer().frame(height: ElDraftSpacing.xs)
                        Text(String(format: String(localized: "applicants_applied_as"), position))
                            .font(ElDraftTypography.labelMedium)
                            .fontWeight(.semibold)
                            .foregroundStyle(ElDraftColors.primary)
                    }
                }
                Spacer()
                StatusChip(status: postulation.status)
            }

            if let player {
                Spacer().frame(height: ElDraftSpacing.md)
                Divider().overlay(ElDraftColors.onSurface.opacity(ElDraftAlpha.divider))
                Spacer().frame(height: ElDraftSpacing.md)
                StatsRow(player: player)
            }

            // Botones solo cuando la postulación sigue pendiente.
            if postulation.status == "pending" {
                Spacer().frame(height: ElDraftSpacing.lg)
                HStack(spacing: ElDraftSpacing.md) {
                    Button(String(localized: "applicants_reject")) { onReject() }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, ElDraftSpacing.sm)
                        .buttonStyle(.bordered)
                        .disabled(isDeciding)

                    Button {
                        onApprove()
                    } label: {
                        Group {
                            if isDeciding {
                                ProgressView().tint(ElDraftColors.onPrimary)
                            } else {
                                Text(String(localized: "applicants_approve"))
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, ElDraftSpacing.sm)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(ElDraftColors.primary)
                    .disabled(isDeciding)
                }
            }
        }
        .padding(ElDraftSpacing.lg)
        .background(ElDraftColors.surface, in: ElDraftShapes.md)
        .shadow(radius: ElDraftElevation.card)
        .contentShape(Rectangle())
        .onTapGesture(perform: onOpenCromo)
        .accessibilityAddTraits(.isButton)
    }
}

/// Métricas del jugador: se acomodan en varias líneas si no caben, evitando
/// que la última se desborde. Cada métrica lleva un ícono de dominio junto
/// al valor. Espejo de `StatsRow` en `ApplicantsScreen.kt`.
private struct StatsRow: View {
    let player: PostulantSummary

    var body: some View {
        FlowLayout(hSpacing: ElDraftSpacing.lg2, vSpacing: ElDraftSpacing.md) {
            if let skill = player.skillScore {
                Stat(icon: MetricIcons.skill, label: String(localized: "cromo_metric_skill"), value: String(format: "%.1f", skill))
            }
            if let sportsmanship = player.sportsmanshipScore {
                Stat(icon: MetricIcons.sportsmanship, label: String(localized: "cromo_metric_sportsmanship"), value: String(format: "%.1f", sportsmanship))
            }
            if let responsibility = player.responsibilityScore {
                Stat(icon: MetricIcons.responsibility, label: String(localized: "cromo_metric_responsibility"), value: String(format: "%.1f", responsibility))
            }
            if let attendance = player.attendancePct {
                Stat(
                    icon: MetricIcons.attendance,
                    label: String(localized: "cromo_tile_attendance"),
                    value: String(format: String(localized: "applicants_attendance_value"), Int(attendance))
                )
            }
        }
    }
}

private struct Stat: View {
    let icon: String
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: ElDraftSpacing.xs) {
                Image(systemName: icon)
                    .font(.system(size: ElDraftSizes.iconSm))
                    .foregroundStyle(ElDraftColors.primary)
                Text(value)
                    .font(ElDraftTypography.titleSmall)
                    .fontWeight(.bold)
                    .foregroundStyle(ElDraftColors.primary)
            }
            Text(label)
                .font(ElDraftTypography.labelSmall)
                .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textTertiary))
        }
    }
}

private struct StatusChip: View {
    let status: String

    private var labelAndColor: (String, Color) {
        switch status {
        case "approved": return (String(localized: "applicants_status_approved"), ElDraftColors.primary)
        case "rejected": return (String(localized: "applicants_status_rejected"), ElDraftColors.error)
        case "cancelled": return (String(localized: "applicants_status_cancelled"), ElDraftColors.onSurface.opacity(ElDraftAlpha.textMuted))
        default: return (String(localized: "applicants_status_pending"), ElDraftColors.onSurface.opacity(ElDraftAlpha.textMuted))
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

private struct AvatarCircle: View {
    let name: String
    var avatarUrl: String? = nil

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
