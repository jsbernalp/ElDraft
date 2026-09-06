import SwiftUI
import shared

/// Tab "Perfil" del `TabView`: muestra el Cromo del usuario actual (solo
/// lectura) con acciones para editar el perfil y cerrar sesión. La edición
/// vive en `ProfileEditView`, empujada encima de este tab. Reutiliza
/// `ProfileEditViewModel`, que ya carga la cuenta + ficha técnica del
/// usuario. Espejo de `ProfileTabScreen.kt`.
struct ProfileTabView: View {
    let onEditProfile: () -> Void
    let onLoggedOut: () -> Void

    @State private var viewModel = ProfileEditViewModel()
    @State private var toastMessage: String?
    @State private var showLogoutDialog = false

    var body: some View {
        content
            .background(ElDraftColors.background)
            .toast(message: $toastMessage)
            // Recargar al volver al tab (p. ej. tras editar el perfil).
            .task { viewModel.load() }
            .onChange(of: viewModel.state.error) { _, newValue in
                if let newValue {
                    toastMessage = newValue
                    viewModel.clearError()
                }
            }
            .onChange(of: viewModel.state.loggedOut) { _, loggedOut in
                if loggedOut { onLoggedOut() }
            }
            .alert(String(localized: "logout_dialog_title"), isPresented: $showLogoutDialog) {
                Button(String(localized: "action_logout"), role: .destructive) {
                    viewModel.logout()
                }
                Button(String(localized: "action_cancel"), role: .cancel) {}
            } message: {
                Text(String(localized: "logout_dialog_message"))
            }
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.state.isLoading {
            LoadingState()
        } else if let profile = viewModel.state.profile {
            CromoContentView(
                profile: profile,
                name: viewModel.state.user?.name,
                avatarUrl: viewModel.state.user?.avatarUrl
            ) {
                Spacer().frame(height: ElDraftSpacing.xxl)
                editProfileButton
                Spacer().frame(height: ElDraftSpacing.md)
                logoutButton
                Spacer().frame(height: ElDraftSpacing.lg)
            }
        } else {
            ProfileEmptyState(
                onEditProfile: onEditProfile,
                onLogout: { showLogoutDialog = true }
            )
        }
    }

    private var editProfileButton: some View {
        Button {
            onEditProfile()
        } label: {
            Text(String(localized: "profile_edit"))
                .frame(maxWidth: .infinity)
                .padding(.vertical, ElDraftSpacing.md)
        }
        .buttonStyle(.borderedProminent)
        .tint(ElDraftColors.primary)
    }

    private var logoutButton: some View {
        Button {
            showLogoutDialog = true
        } label: {
            Text(String(localized: "action_logout"))
                .frame(maxWidth: .infinity)
                .padding(.vertical, ElDraftSpacing.md)
        }
        .buttonStyle(.bordered)
        .tint(ElDraftColors.error)
    }
}

/// Usuario sin ficha técnica todavía: invita a completarla. Espejo de
/// `ProfileEmptyState` en `ProfileTabScreen.kt`.
private struct ProfileEmptyState: View {
    let onEditProfile: () -> Void
    let onLogout: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Text("⚽").font(ElDraftTypography.displaySmall)
            Spacer().frame(height: ElDraftSpacing.md)
            Text(String(localized: "profile_complete_card_title"))
                .font(ElDraftTypography.titleMedium)
                .foregroundStyle(ElDraftColors.onBackground)
                .multilineTextAlignment(.center)
            Spacer().frame(height: ElDraftSpacing.sm)
            Text(String(localized: "profile_complete_card_message"))
                .font(ElDraftTypography.bodyMedium)
                .foregroundStyle(ElDraftColors.onBackground.opacity(ElDraftAlpha.textSecondary))
                .multilineTextAlignment(.center)
            Spacer().frame(height: ElDraftSpacing.xl)

            Button {
                onEditProfile()
            } label: {
                Text(String(localized: "profile_edit"))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, ElDraftSpacing.md)
            }
            .buttonStyle(.borderedProminent)
            .tint(ElDraftColors.primary)

            Spacer().frame(height: ElDraftSpacing.md)
            Button {
                onLogout()
            } label: {
                Text(String(localized: "action_logout"))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, ElDraftSpacing.md)
            }
            .buttonStyle(.bordered)
            .tint(ElDraftColors.error)
        }
        .padding(ElDraftSpacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
