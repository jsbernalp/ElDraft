import SwiftUI
import shared

/// Sheet "Agregar jugador": sugeridos (jugadores con los que ya jugué) o por
/// correo. Espejo de `AddTeamPlayerSheet.kt`; comparte el estado con
/// `TeamDetailViewModel`, igual que Android recibe sus campos desde
/// `TeamDetailScreen`.
struct AddTeamPlayerSheet: View {
    let viewModel: TeamDetailViewModel
    let onDismiss: () -> Void

    private enum Tab: Hashable { case suggested, email }
    @State private var tab: Tab = .suggested

    var body: some View {
        VStack(alignment: .leading, spacing: ElDraftSpacing.lg) {
            Text("team_add_sheet_title")
                .font(ElDraftTypography.titleLarge)
                .foregroundStyle(ElDraftColors.onSurface)

            Picker("", selection: $tab) {
                Text("team_add_tab_suggested").tag(Tab.suggested)
                Text("team_add_tab_email").tag(Tab.email)
            }
            .pickerStyle(.segmented)

            switch tab {
            case .suggested: suggestedTab
            case .email: emailTab
            }
            Spacer(minLength: 0)
        }
        .padding(ElDraftSpacing.xl)
        .background(ElDraftColors.background)
        .presentationDetents([.medium, .large])
    }

    // MARK: - Sugeridos

    @ViewBuilder
    private var suggestedTab: some View {
        if viewModel.suggestionsLoading && viewModel.suggestions.isEmpty {
            ProgressView()
                .tint(ElDraftColors.primary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, ElDraftSpacing.xxl)
        } else if let error = viewModel.suggestionsError, viewModel.suggestions.isEmpty {
            Text(error)
                .font(ElDraftTypography.bodyMedium)
                .foregroundStyle(ElDraftColors.error)
        } else if viewModel.suggestions.isEmpty {
            EmptyState(
                title: String(localized: "team_suggestions_empty_title"),
                message: String(localized: "team_suggestions_empty_message"),
                icon: "🤝"
            )
        } else {
            ScrollView {
                VStack(spacing: ElDraftSpacing.md) {
                    ForEach(viewModel.suggestions, id: \.playerId) { suggestion in
                        SuggestionRow(
                            suggestion: suggestion,
                            isInviting: viewModel.invitingIds.contains(suggestion.playerId),
                            enabled: viewModel.canAddMember,
                            onInvite: { viewModel.inviteSuggested(playerId: suggestion.playerId) }
                        )
                    }
                }
            }
        }
    }

    // MARK: - Por correo

    private var emailTab: some View {
        VStack(alignment: .leading, spacing: ElDraftSpacing.md) {
            ElDraftTextField(
                label: String(localized: "team_email_label"),
                text: Binding(
                    get: { viewModel.email },
                    set: { viewModel.onEmailChange($0) }
                ),
                keyboardType: .emailAddress,
                errorText: viewModel.emailError
            )
            if viewModel.emailError == nil {
                Text("team_email_hint")
                    .font(ElDraftTypography.bodySmall)
                    .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))
            }
            if let name = viewModel.emailInvitedName {
                Text(String(format: String(localized: "team_email_invited"), name))
                    .font(ElDraftTypography.bodyMedium)
                    .fontWeight(.semibold)
                    .foregroundStyle(ElDraftColors.primary)
            }
            Button {
                viewModel.inviteByEmail()
            } label: {
                if viewModel.isInvitingByEmail {
                    ProgressView().frame(maxWidth: .infinity)
                } else {
                    Text("team_invite_button").frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .tint(ElDraftColors.primary)
            .disabled(!viewModel.canSendEmail)
        }
    }
}

private struct SuggestionRow: View {
    let suggestion: TeamSuggestion
    let isInviting: Bool
    let enabled: Bool
    let onInvite: () -> Void

    private var subtitle: String {
        [
            suggestion.positionPrimary.flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : $0 },
            String(format: String(localized: "team_matches_together"), Int(suggestion.matchesTogether)),
        ]
        .compactMap { $0 }
        .joined(separator: " · ")
    }

    var body: some View {
        HStack(spacing: ElDraftSpacing.md) {
            TeamPlayerAvatar(name: suggestion.name, avatarUrl: suggestion.avatarUrl)
            VStack(alignment: .leading, spacing: 0) {
                Text(suggestion.name)
                    .font(ElDraftTypography.titleSmall)
                    .fontWeight(.semibold)
                    .foregroundStyle(ElDraftColors.onSurface)
                Text(subtitle)
                    .font(ElDraftTypography.bodySmall)
                    .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))
            }
            Spacer()
            Button(action: onInvite) {
                if isInviting {
                    ProgressView()
                } else {
                    Text("team_invite_button")
                }
            }
            .buttonStyle(.bordered)
            .tint(ElDraftColors.primary)
            .disabled(!enabled || isInviting)
        }
    }
}
