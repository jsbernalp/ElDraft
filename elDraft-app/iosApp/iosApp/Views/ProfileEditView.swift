import SwiftUI
import UIKit
import shared

/// `ProfileEditView` — el equivalente de `ProfileEditScreen.kt`: cuenta
/// (nombre, avatar, teléfono) + ficha técnica, logout, borrado de cuenta
/// (irreversible) y enlace externo a la política de privacidad.
struct ProfileEditView: View {
    let onSaved: () -> Void
    let onLoggedOut: () -> Void

    @State private var viewModel = ProfileEditViewModel()
    @State private var toastMessage: String?
    @State private var showLogoutDialog = false
    @State private var showDeleteAccountDialog = false

    // Campos editables — se inicializan cuando llegan los datos.
    @State private var name = ""
    @State private var avatarUrl = ""
    @State private var phone = ""
    @State private var positionPrimary = ""
    @State private var positionSecondary = ""
    @State private var dominantFoot = ""
    @State private var height = ""
    @State private var build = ""

    private let positions = DomainCatalogs.shared.POSITIONS
    private let feet = DomainCatalogs.shared.FEET
    private let builds = DomainCatalogs.shared.BUILDS

    private var canSave: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
            && !dominantFoot.isEmpty
            && !positionPrimary.isEmpty
            && !viewModel.state.isSaving
            && !viewModel.state.isLoading
    }

    private var heightBinding: Binding<String> {
        Binding(
            get: { height },
            set: { height = String($0.filter(\.isNumber).prefix(3)) }
        )
    }

    var body: some View {
        Group {
            if viewModel.state.isLoading {
                LoadingState()
            } else {
                form
            }
        }
        .background(ElDraftColors.background)
        .scrollDismissesKeyboard(.interactively)
        .toast(message: $toastMessage)
        .task { viewModel.load() }
        .onChange(of: viewModel.state.isLoading) { _, isLoading in
            if !isLoading { prefillIfNeeded() }
        }
        .onChange(of: viewModel.state.saved) { _, saved in
            if saved {
                onSaved()
                viewModel.clearSaved()
            }
        }
        .onChange(of: viewModel.state.loggedOut) { _, loggedOut in
            if loggedOut { onLoggedOut() }
        }
        .onChange(of: viewModel.state.error) { _, newValue in
            if let newValue {
                toastMessage = newValue
                viewModel.clearError()
            }
        }
        .alert(String(localized: "logout_dialog_title"), isPresented: $showLogoutDialog) {
            Button(String(localized: "action_logout"), role: .destructive) {
                viewModel.logout()
            }
            Button(String(localized: "action_cancel"), role: .cancel) {}
        } message: {
            Text(String(localized: "logout_dialog_message"))
        }
        .sheet(isPresented: $showDeleteAccountDialog) {
            DeleteAccountSheet(
                isDeleting: viewModel.state.isDeleting,
                onConfirm: { viewModel.deleteAccount() },
                onDismiss: { showDeleteAccountDialog = false }
            )
            .interactiveDismissDisabled(viewModel.state.isDeleting)
        }
    }

    private func prefillIfNeeded() {
        if let user = viewModel.state.user {
            if name.isEmpty { name = user.name }
            if avatarUrl.isEmpty { avatarUrl = user.avatarUrl ?? "" }
            if phone.isEmpty { phone = user.phone ?? "" }
        }
        if let profile = viewModel.state.profile {
            if positionPrimary.isEmpty { positionPrimary = profile.positionPrimary }
            if positionSecondary.isEmpty { positionSecondary = profile.positionSecondary ?? "" }
            if dominantFoot.isEmpty { dominantFoot = profile.dominantFoot }
            if height.isEmpty { height = profile.height.map { String($0.intValue) } ?? "" }
            if build.isEmpty { build = profile.build ?? "" }
        }
    }

    private var form: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: ElDraftSpacing.lg)

                AvatarPreview(name: name, avatarUrl: avatarUrl.isEmpty ? nil : avatarUrl)
                    .frame(maxWidth: .infinity)

                Spacer().frame(height: ElDraftSpacing.lg)

                ElDraftTextField(label: String(localized: "profile_name_label"), text: $name)
                Spacer().frame(height: ElDraftSpacing.lg)
                ElDraftTextField(label: String(localized: "profile_phone_label_short"), text: $phone, keyboardType: .phonePad)

                Spacer().frame(height: ElDraftSpacing.xl)
                SectionTitle(String(localized: "profile_section_card"))
                Spacer().frame(height: ElDraftSpacing.md)

                DropdownField(label: String(localized: "profile_position_primary_label"), options: positions, selected: $positionPrimary)
                Spacer().frame(height: ElDraftSpacing.lg)
                DropdownField(label: String(localized: "profile_position_secondary_label"), options: positions, selected: $positionSecondary)
                Spacer().frame(height: ElDraftSpacing.lg)
                DropdownField(label: String(localized: "profile_dominant_foot_label"), options: feet, selected: $dominantFoot)
                Spacer().frame(height: ElDraftSpacing.lg)
                ElDraftTextField(label: String(localized: "profile_height_label"), text: heightBinding, keyboardType: .numberPad)
                Spacer().frame(height: ElDraftSpacing.lg)
                DropdownField(label: String(localized: "profile_build_label"), options: builds, selected: $build)

                if let profile = viewModel.state.profile {
                    StatsSection(profile: profile)
                }

                Spacer().frame(height: ElDraftSpacing.xl)
                saveButton
                Spacer().frame(height: ElDraftSpacing.md)
                logoutButton
                Spacer().frame(height: ElDraftSpacing.md)
                deleteAccountButton
                privacyPolicyButton
                Spacer().frame(height: ElDraftSpacing.xxl)
            }
            .padding(.horizontal, ElDraftSpacing.xl)
        }
    }

    private var saveButton: some View {
        Button {
            viewModel.save(
                name: name,
                avatarUrl: avatarUrl.isEmpty ? nil : avatarUrl,
                phone: phone.isEmpty ? nil : phone,
                positionPrimary: positionPrimary,
                positionSecondary: positionSecondary.isEmpty ? nil : positionSecondary,
                dominantFoot: dominantFoot,
                height: Int(height),
                build: build.isEmpty ? nil : build
            )
        } label: {
            Group {
                if viewModel.state.isSaving {
                    ProgressView().tint(ElDraftColors.onPrimary)
                } else {
                    Text(String(localized: "profile_save_changes"))
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, ElDraftSpacing.md)
        }
        .buttonStyle(.borderedProminent)
        .tint(ElDraftColors.primary)
        .disabled(!canSave)
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

    // Va como texto plano (no borde) para que no compita visualmente con
    // "Cerrar sesión": son acciones de peso muy distinto y confundirlas aquí
    // es irreversible.
    private var deleteAccountButton: some View {
        Button {
            showDeleteAccountDialog = true
        } label: {
            Text(String(localized: "action_delete_account"))
                .frame(maxWidth: .infinity)
                .padding(.vertical, ElDraftSpacing.md)
        }
        .foregroundStyle(ElDraftColors.error)
    }

    // Play/App Store no la exige dentro de la app, pero los revisores la
    // buscan aquí. Color neutro para que no se lea como una tercera acción
    // destructiva pegada a las dos de arriba.
    private var privacyPolicyButton: some View {
        Button {
            openPrivacyPolicy()
        } label: {
            Text(String(localized: "action_privacy_policy"))
                .font(ElDraftTypography.bodySmall)
                .frame(maxWidth: .infinity)
                .padding(.vertical, ElDraftSpacing.sm)
        }
        .foregroundStyle(ElDraftColors.onSurfaceVariant)
    }

    private func openPrivacyPolicy() {
        guard let url = URL(string: "\(AppConfig.legalBaseURL)/privacidad"), UIApplication.shared.canOpenURL(url) else {
            toastMessage = String(localized: "privacy_policy_no_browser")
            return
        }
        UIApplication.shared.open(url)
    }
}

private struct AvatarPreview: View {
    let name: String
    let avatarUrl: String?

    private var initial: String {
        name.trimmingCharacters(in: .whitespaces).first.map(String.init)?.uppercased()
            ?? String(localized: "profile_initial_fallback")
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
                .frame(width: ElDraftSizes.avatarMd, height: ElDraftSizes.avatarMd)
                .clipShape(Circle())
                .accessibilityLabel(String(localized: "profile_avatar_preview_content_description"))
            } else {
                Text(initial)
                    .font(ElDraftTypography.headlineMedium)
                    .fontWeight(.bold)
                    .foregroundStyle(ElDraftColors.primary)
            }
        }
        .frame(width: ElDraftSizes.avatarMd, height: ElDraftSizes.avatarMd)
    }
}

private struct SectionTitle: View {
    let title: String
    init(_ title: String) { self.title = title }

    var body: some View {
        Text(title)
            .font(ElDraftTypography.titleMedium)
            .fontWeight(.semibold)
            .foregroundStyle(ElDraftColors.primary)
    }
}

private struct StatsSection: View {
    let profile: PlayerProfile

    var body: some View {
        Spacer().frame(height: ElDraftSpacing.xl)
        SectionTitle(String(localized: "profile_section_stats"))
        Spacer().frame(height: ElDraftSpacing.md)
        VStack(spacing: ElDraftSpacing.sm) {
            StatRow(label: String(localized: "profile_stat_matches"), value: "\(profile.totalMatches)")
            StatRow(
                label: String(localized: "cromo_tile_attendance"),
                value: String(format: String(localized: "profile_stat_attendance_value"), String(format: "%.0f", profile.attendancePct)),
                icon: MetricIcons.attendance
            )
            StatRow(
                label: String(localized: "cromo_metric_skill"),
                value: String(format: String(localized: "profile_stat_score_value"), String(format: "%.1f", profile.skillScore)),
                icon: MetricIcons.skill
            )
            StatRow(
                label: String(localized: "cromo_metric_sportsmanship"),
                value: String(format: String(localized: "profile_stat_score_value"), String(format: "%.1f", profile.sportsmanshipScore)),
                icon: MetricIcons.sportsmanship
            )
            StatRow(
                label: String(localized: "cromo_metric_responsibility"),
                value: String(format: String(localized: "profile_stat_score_value"), String(format: "%.1f", profile.responsibilityScore)),
                icon: MetricIcons.responsibility
            )
        }
        .padding(ElDraftSpacing.lg)
        .background(ElDraftColors.surface, in: ElDraftShapes.md)
        .shadow(radius: ElDraftElevation.card)
    }
}

private struct StatRow: View {
    let label: String
    let value: String
    var icon: String? = nil

    var body: some View {
        HStack {
            HStack(spacing: ElDraftSpacing.sm) {
                if let icon {
                    Image(systemName: icon)
                        .font(.system(size: ElDraftSizes.iconSm))
                        .foregroundStyle(ElDraftColors.primary)
                }
                Text(label)
                    .font(ElDraftTypography.bodyMedium)
                    .foregroundStyle(ElDraftColors.onSurfaceVariant)
            }
            Spacer()
            Text(value)
                .font(ElDraftTypography.bodyMedium)
                .fontWeight(.semibold)
                .foregroundStyle(ElDraftColors.onSurface)
        }
    }
}

/// Confirmación de borrado de cuenta. A diferencia del logout, esto es
/// irreversible: el sheet se mantiene abierto mientras la operación corre
/// (no se puede cerrar solo, como sí hace `.alert`) para que no queden dos
/// peticiones en vuelo si el usuario vuelve a tocar. Espejo del
/// `AlertDialog` de `ProfileEditScreen.kt`.
private struct DeleteAccountSheet: View {
    let isDeleting: Bool
    let onConfirm: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ElDraftSpacing.sm) {
                Text(String(localized: "delete_account_dialog_title"))
                    .font(ElDraftTypography.titleLarge)
                    .fontWeight(.bold)
                    .foregroundStyle(ElDraftColors.onSurface)
                Text(isDeleting ? String(localized: "delete_account_in_progress") : String(localized: "delete_account_dialog_message"))
                    .font(ElDraftTypography.bodyMedium)
                    .foregroundStyle(ElDraftColors.onSurfaceVariant)

                Spacer().frame(height: ElDraftSpacing.lg)

                Button {
                    onConfirm()
                } label: {
                    Group {
                        if isDeleting {
                            ProgressView().tint(ElDraftColors.onError)
                        } else {
                            Text(String(localized: "delete_account_confirm"))
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, ElDraftSpacing.md)
                }
                .buttonStyle(.borderedProminent)
                .tint(ElDraftColors.error)
                .disabled(isDeleting)

                Button(String(localized: "action_cancel")) { onDismiss() }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, ElDraftSpacing.md)
                    .buttonStyle(.bordered)
                    .disabled(isDeleting)
            }
            .padding(.horizontal, ElDraftSpacing.xl)
            .padding(.top, ElDraftSpacing.lg)
            .padding(.bottom, ElDraftSpacing.xxl)
        }
        .presentationDetents([.medium])
    }
}
