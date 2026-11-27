import SwiftUI
import shared

/// Detalle de un equipo: jugadores, y para el dueño renombrar / borrar /
/// agregar. Espejo de `TeamDetailScreen.kt`.
struct TeamDetailView: View {
    let teamId: String
    let onBack: () -> Void

    @State private var viewModel = TeamDetailViewModel()
    @State private var toastMessage: String?
    @State private var showAddSheet = false
    @State private var showRename = false
    @State private var renameText = ""
    @State private var showDelete = false
    @State private var showLeave = false
    @State private var memberToRemove: TeamMember?

    var body: some View {
        Group {
            if let team = viewModel.team {
                content(team)
            } else if viewModel.isLoading {
                LoadingState()
            } else {
                Color.clear
            }
        }
        .background(ElDraftColors.background)
        .toast(message: $toastMessage)
        .task { viewModel.load(teamId: teamId) }
        .onChange(of: viewModel.error) { _, newValue in
            // Con el sheet de renombrar abierto el error se muestra dentro de él.
            if let newValue, !showRename {
                toastMessage = newValue
                viewModel.clearError()
            }
        }
        .onChange(of: viewModel.closed) { _, closed in if closed { onBack() } }
        .onChange(of: viewModel.renamed) { _, renamed in
            if renamed {
                showRename = false
                viewModel.consumeRenamed()
            }
        }
        .sheet(isPresented: $showAddSheet) {
            AddTeamPlayerSheet(viewModel: viewModel) { showAddSheet = false }
        }
        .sheet(isPresented: $showRename, onDismiss: { viewModel.clearError() }) {
            TeamNameSheet(
                title: String(localized: "team_rename_dialog_title"),
                confirmLabel: String(localized: "team_rename_confirm"),
                name: $renameText,
                isBusy: viewModel.isRenaming,
                canSubmit: !viewModel.isRenaming && TeamRules.shared.isValidName(raw: renameText),
                error: viewModel.error,
                onConfirm: { viewModel.rename(name: renameText) },
                onCancel: { showRename = false }
            )
        }
        .alert(
            String(localized: "team_delete_dialog_title"),
            isPresented: $showDelete
        ) {
            Button("action_cancel", role: .cancel) {}
            Button("team_delete_confirm", role: .destructive) { viewModel.delete() }
        } message: {
            Text(String(format: String(localized: "team_delete_dialog_message"), viewModel.team?.name ?? ""))
        }
        .alert(
            String(format: String(localized: "team_leave_dialog_title"), viewModel.team?.name ?? ""),
            isPresented: $showLeave
        ) {
            Button("action_cancel", role: .cancel) {}
            Button("team_leave_confirm", role: .destructive) { viewModel.leave() }
        } message: {
            Text("team_leave_dialog_message")
        }
        .alert(
            String(format: String(localized: "team_remove_dialog_title"), memberToRemove?.name ?? ""),
            isPresented: Binding(get: { memberToRemove != nil }, set: { if !$0 { memberToRemove = nil } })
        ) {
            Button("action_cancel", role: .cancel) { memberToRemove = nil }
            Button("team_remove_confirm", role: .destructive) {
                if let member = memberToRemove { viewModel.remove(playerId: member.playerId) }
                memberToRemove = nil
            }
        } message: {
            Text("team_remove_dialog_message")
        }
    }

    private var isBusy: Bool { viewModel.isDeleting || viewModel.isLeaving }

    private func content(_ team: TeamDetail) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ElDraftSpacing.md) {
                header(team)

                if team.isOwner {
                    VStack(alignment: .leading, spacing: ElDraftSpacing.xs) {
                        Button {
                            viewModel.openAddPlayer()
                            showAddSheet = true
                        } label: {
                            Label("team_detail_add_player", systemImage: "person.badge.plus")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(ElDraftColors.primary)
                        .disabled(!viewModel.canAddMember || isBusy)

                        if !viewModel.canAddMember {
                            Text(String(format: String(localized: "team_detail_full"), TeamRules.shared.MAX_MEMBERS))
                                .font(ElDraftTypography.bodySmall)
                                .foregroundStyle(ElDraftColors.onBackground.opacity(ElDraftAlpha.textSecondary))
                        }
                    }
                }

                Text("team_detail_members_title")
                    .font(ElDraftTypography.titleSmall)
                    .fontWeight(.semibold)
                    .foregroundStyle(ElDraftColors.onBackground)

                if team.members.isEmpty {
                    EmptyState(
                        title: String(localized: "team_detail_empty_title"),
                        message: String(localized: "team_detail_empty_message"),
                        icon: "👥"
                    )
                } else {
                    ForEach(team.members, id: \.playerId) { member in
                        MemberRow(
                            member: member,
                            canRemove: team.isOwner,
                            isRemoving: viewModel.removingIds.contains(member.playerId),
                            onRemove: { memberToRemove = member }
                        )
                    }
                }

                if !team.isOwner {
                    Button(role: .destructive) {
                        showLeave = true
                    } label: {
                        Text("team_leave_button").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .disabled(isBusy)
                    .padding(.top, ElDraftSpacing.lg)
                }
            }
            .padding(.horizontal, ElDraftSpacing.xl)
            .padding(.vertical, ElDraftSpacing.md)
            .padding(.bottom, ElDraftSpacing.xl)
        }
        .refreshable {
            viewModel.load(teamId: teamId)
            while viewModel.isLoading {
                try? await Task.sleep(nanoseconds: 150_000_000)
            }
        }
    }

    private func header(_ team: TeamDetail) -> some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 0) {
                Text(team.name)
                    .font(ElDraftTypography.headlineMedium)
                    .foregroundStyle(ElDraftColors.onBackground)
                Text(String(format: String(localized: "team_detail_owner"), team.ownerName))
                    .font(ElDraftTypography.bodyMedium)
                    .foregroundStyle(ElDraftColors.onBackground.opacity(ElDraftAlpha.textSecondary))
            }
            Spacer()
            if team.isOwner {
                Menu {
                    Button("team_menu_rename") {
                        renameText = team.name
                        showRename = true
                    }
                    Button("team_menu_delete", role: .destructive) { showDelete = true }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: ElDraftSizes.iconLg))
                        .foregroundStyle(ElDraftColors.onBackground)
                        .frame(width: ElDraftSizes.avatar, height: ElDraftSizes.avatar)
                }
                .disabled(isBusy)
                .accessibilityLabel(String(localized: "team_menu_content_description"))
            }
        }
    }
}

private struct MemberRow: View {
    let member: TeamMember
    let canRemove: Bool
    let isRemoving: Bool
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: ElDraftSpacing.md) {
            TeamPlayerAvatar(name: member.name, avatarUrl: member.avatarUrl)
            VStack(alignment: .leading, spacing: 0) {
                Text(member.name)
                    .font(ElDraftTypography.titleSmall)
                    .fontWeight(.semibold)
                    .foregroundStyle(ElDraftColors.onSurface)
                if let position = member.positionPrimary, !position.trimmingCharacters(in: .whitespaces).isEmpty {
                    Text(position)
                        .font(ElDraftTypography.bodySmall)
                        .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))
                }
            }
            Spacer()
            statusChip
            if canRemove {
                if isRemoving {
                    ProgressView()
                } else {
                    Button(action: onRemove) {
                        Image(systemName: "xmark")
                            .font(.system(size: ElDraftSizes.iconMd))
                            .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.icon))
                            .frame(width: ElDraftSizes.iconLg * 2, height: ElDraftSizes.iconLg * 2)
                    }
                    .accessibilityLabel(String(format: String(localized: "team_remove_content_description"), member.name))
                }
            }
        }
        .padding(ElDraftSpacing.md)
        .background(ElDraftColors.surface, in: ElDraftShapes.md)
        .shadow(radius: ElDraftElevation.card)
    }

    /// Chip Invitado / Miembro. Un estado desconocido no se pinta.
    @ViewBuilder
    private var statusChip: some View {
        if member.isActive {
            chip(String(localized: "team_member_status_active"), color: ElDraftColors.primary)
        } else if member.isInvited {
            chip(
                String(localized: "team_member_status_invited"),
                color: ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary)
            )
        }
    }

    private func chip(_ label: String, color: Color) -> some View {
        Text(label)
            .font(ElDraftTypography.labelMedium)
            .fontWeight(.semibold)
            .foregroundStyle(color)
            .padding(.horizontal, ElDraftSpacing.md)
            .padding(.vertical, ElDraftSpacing.xs)
            .background(color.opacity(ElDraftAlpha.container), in: ElDraftShapes.pill)
    }
}
