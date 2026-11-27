import SwiftUI
import shared

/// Pestañas de "Mis equipos". `rawValue` es el valor del segmento de la ruta
/// (`eldraft://teams/invitations`), igual que `TeamsTab.key` en Android.
enum TeamsTab: String, CaseIterable {
    case owned, member, invitations
}

/// "Mis equipos": los equipos propios, en los que soy miembro y las
/// invitaciones pendientes. Espejo de `TeamsScreen.kt`.
struct TeamsView: View {
    /// Pestaña pedida desde fuera (deep link `eldraft://teams/invitations`); se
    /// consume al aplicarla.
    @Binding var tabRequest: TeamsTab?
    let onOpenTeam: (String) -> Void

    @State private var viewModel = TeamsViewModel()
    @State private var tab: TeamsTab = .owned
    @State private var showCreate = false
    @State private var toastMessage: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: ElDraftSpacing.sm)
                ScreenHeader(
                    title: String(localized: "team_list_title"),
                    subtitle: String(localized: "team_list_subtitle")
                )
                Spacer().frame(height: ElDraftSpacing.lg)
                tabs
                Spacer().frame(height: ElDraftSpacing.md)

                let isEmptyLoad = viewModel.isLoading && viewModel.myTeams.isEmpty
                    && viewModel.memberships.isEmpty && viewModel.invitations.isEmpty
                if isEmptyLoad {
                    LoadingState().frame(maxWidth: .infinity, minHeight: 320)
                } else {
                    VStack(alignment: .leading, spacing: ElDraftSpacing.md) {
                        switch tab {
                        case .owned: ownedSection
                        case .member: memberSection
                        case .invitations: invitationsSection
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
        // Se recarga cada vez que se vuelve a la pantalla (p. ej. tras borrar un equipo).
        .onAppear { viewModel.load() }
        .task(id: tabRequest) {
            if let requested = tabRequest {
                tab = requested
                tabRequest = nil
            }
        }
        .onChange(of: viewModel.error) { _, newValue in
            // Con el sheet abierto el error se muestra dentro de él.
            if let newValue, !showCreate {
                toastMessage = newValue
                viewModel.clearError()
            }
        }
        .onChange(of: viewModel.createdTeamId) { _, id in
            if let id {
                showCreate = false
                viewModel.consumeCreatedTeam()
                onOpenTeam(id)
            }
        }
        .sheet(isPresented: $showCreate, onDismiss: {
            viewModel.resetNewTeam()
            viewModel.clearError()
        }) {
            TeamNameSheet(
                title: String(localized: "team_create_dialog_title"),
                confirmLabel: String(localized: "team_create_confirm"),
                name: $viewModel.newTeamName,
                isBusy: viewModel.isCreating,
                canSubmit: viewModel.canSubmitNewTeam,
                error: viewModel.error,
                onConfirm: { viewModel.create() },
                onCancel: { showCreate = false }
            )
        }
    }

    private func refresh() async {
        viewModel.load()
        while viewModel.isLoading {
            try? await Task.sleep(nanoseconds: 150_000_000)
        }
    }

    // MARK: - Pestañas

    private var tabs: some View {
        HStack(spacing: ElDraftSpacing.xxs) {
            segment("team_tab_owned", tab: .owned)
            segment("team_tab_member", tab: .member)
            segment("team_tab_invitations", tab: .invitations, badge: viewModel.invitations.count)
        }
        .padding(ElDraftSpacing.xxs)
        .background(ElDraftColors.surfaceVariant, in: ElDraftShapes.field)
    }

    private func segment(_ key: String, tab target: TeamsTab, badge: Int = 0) -> some View {
        let isSelected = tab == target
        let title = String(localized: String.LocalizationValue(key))
        return Button {
            tab = target
        } label: {
            HStack(spacing: ElDraftSpacing.xs) {
                Text(title)
                    .font(ElDraftTypography.labelLarge)
                    .lineLimit(1)
                if badge > 0 {
                    Text("\(badge)")
                        .font(ElDraftTypography.labelSmall)
                        .fontWeight(.semibold)
                        .foregroundStyle(ElDraftColors.onPrimary)
                        .padding(.horizontal, ElDraftSpacing.xs)
                        .background(ElDraftColors.primary, in: Capsule())
                        .accessibilityHidden(true)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, ElDraftSpacing.sm)
            .foregroundStyle(isSelected ? ElDraftColors.primary : ElDraftColors.onSurfaceVariant)
            .background(isSelected ? ElDraftColors.surface : Color.clear, in: ElDraftShapes.field)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(
            badge > 0
                ? String(format: String(localized: "team_invitations_badge"), badge)
                : title
        )
    }

    // MARK: - Secciones

    @ViewBuilder
    private var ownedSection: some View {
        VStack(alignment: .leading, spacing: ElDraftSpacing.xs) {
            Button {
                showCreate = true
            } label: {
                Label("team_create_button", systemImage: "plus")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(ElDraftColors.primary)
            .disabled(!viewModel.canCreateAnotherTeam)

            if !viewModel.canCreateAnotherTeam {
                Text(String(format: String(localized: "team_create_limit"), TeamRules.shared.MAX_TEAMS))
                    .font(ElDraftTypography.bodySmall)
                    .foregroundStyle(ElDraftColors.onBackground.opacity(ElDraftAlpha.textSecondary))
            }
        }
        if viewModel.myTeams.isEmpty {
            EmptyState(
                title: String(localized: "team_owned_empty_title"),
                message: String(localized: "team_owned_empty_message"),
                icon: "👥"
            )
        } else {
            ForEach(viewModel.myTeams, id: \.id) { team in
                TeamCard(team: team, showOwner: false) { onOpenTeam(team.id) }
            }
        }
    }

    @ViewBuilder
    private var memberSection: some View {
        if viewModel.memberships.isEmpty {
            EmptyState(
                title: String(localized: "team_member_empty_title"),
                message: String(localized: "team_member_empty_message"),
                icon: "🎽"
            )
        } else {
            ForEach(viewModel.memberships, id: \.id) { team in
                TeamCard(team: team, showOwner: true) { onOpenTeam(team.id) }
            }
        }
    }

    @ViewBuilder
    private var invitationsSection: some View {
        if viewModel.invitations.isEmpty {
            EmptyState(
                title: String(localized: "team_invitations_empty_title"),
                message: String(localized: "team_invitations_empty_message"),
                icon: "📨"
            )
        } else {
            ForEach(viewModel.invitations, id: \.teamId) { invitation in
                InvitationCard(
                    invitation: invitation,
                    isResponding: viewModel.respondingIds.contains(invitation.teamId),
                    onAccept: { viewModel.accept(teamId: invitation.teamId) },
                    onDecline: { viewModel.decline(teamId: invitation.teamId) }
                )
            }
        }
    }
}

private struct TeamCard: View {
    let team: Team
    let showOwner: Bool
    let onTap: () -> Void

    private var subtitle: String {
        var parts: [String] = []
        if showOwner {
            parts.append(String(format: String(localized: "team_owner_label"), team.ownerName))
        }
        parts.append(String(format: String(localized: "team_members_count"), Int(team.activeCount)))
        if let invited = team.invitedCount?.intValue, invited > 0 {
            parts.append(String(format: String(localized: "team_invited_count"), invited))
        }
        return parts.joined(separator: " · ")
    }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: ElDraftSpacing.md) {
                Image(systemName: "person.3.fill")
                    .font(.system(size: ElDraftSizes.iconLg))
                    .foregroundStyle(ElDraftColors.primary)
                VStack(alignment: .leading, spacing: 0) {
                    Text(team.name)
                        .font(ElDraftTypography.titleMedium)
                        .fontWeight(.semibold)
                        .foregroundStyle(ElDraftColors.onSurface)
                    Text(subtitle)
                        .font(ElDraftTypography.bodySmall)
                        .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))
                }
                Spacer()
            }
            .padding(ElDraftSpacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(ElDraftColors.surface, in: ElDraftShapes.md)
            .shadow(radius: ElDraftElevation.card)
        }
        .buttonStyle(.plain)
    }
}

private struct InvitationCard: View {
    let invitation: TeamInvitation
    let isResponding: Bool
    let onAccept: () -> Void
    let onDecline: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: ElDraftSpacing.lg) {
            HStack(spacing: ElDraftSpacing.md) {
                TeamPlayerAvatar(name: invitation.ownerName, avatarUrl: invitation.ownerAvatarUrl)
                VStack(alignment: .leading, spacing: 0) {
                    Text(invitation.teamName)
                        .font(ElDraftTypography.titleMedium)
                        .fontWeight(.semibold)
                        .foregroundStyle(ElDraftColors.onSurface)
                    Text(String(format: String(localized: "team_invitation_message"), invitation.ownerName))
                        .font(ElDraftTypography.bodySmall)
                        .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))
                }
                Spacer()
            }
            HStack(spacing: ElDraftSpacing.md) {
                Button(action: onDecline) {
                    Text("team_invitation_decline").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(isResponding)

                Button(action: onAccept) {
                    if isResponding {
                        ProgressView().frame(maxWidth: .infinity)
                    } else {
                        Text("action_accept").frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(isResponding)
            }
            .tint(ElDraftColors.primary)
        }
        .padding(ElDraftSpacing.lg)
        .background(ElDraftColors.surface, in: ElDraftShapes.md)
        .shadow(radius: ElDraftElevation.card)
    }
}
