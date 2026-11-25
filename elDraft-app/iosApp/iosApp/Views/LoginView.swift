import SwiftUI

/// Login y registro en la misma vista, alternados por chips. Espejo de
/// `LoginScreen.kt`. La validación local (formato de email, longitud mínima
/// de contraseña, coincidencia de confirmación) vive acá, igual que en
/// Android — el ViewModel no valida, solo orquesta la llamada.
struct LoginView: View {
    let onLoginSuccess: (Bool) -> Void

    @State private var viewModel = AuthViewModel()
    @State private var isRegisterMode = false
    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var errorMessage: String?

    private var emailValid: Bool { email.contains("@") && email.contains(".") }
    private var passwordValid: Bool { password.count >= 6 }
    private var isLoading: Bool { viewModel.state == .loading }
    private var canSubmitEmail: Bool {
        guard !isLoading else { return false }
        if isRegisterMode {
            return !name.trimmingCharacters(in: .whitespaces).isEmpty
                && emailValid && passwordValid && password == confirmPassword
        }
        return !email.isEmpty && !password.isEmpty
    }

    var body: some View {
        ScrollView {
            VStack(spacing: ElDraftSpacing.lg) {
                Spacer().frame(height: ElDraftSpacing.xxl)

                Text("app_name")
                    .font(ElDraftTypography.headlineLarge)
                    .foregroundStyle(ElDraftColors.primary)

                Text("login_tagline")
                    .font(ElDraftTypography.bodyLarge)
                    .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))

                Spacer().frame(height: ElDraftSpacing.md)

                modeChips

                if isRegisterMode {
                    ElDraftTextField(
                        label: String(localized: "login_name_label"),
                        text: $name,
                        accessibilityId: "login_name_field"
                    )
                }

                ElDraftTextField(
                    label: String(localized: "login_email_label"),
                    text: $email,
                    keyboardType: .emailAddress,
                    accessibilityId: "login_email_field"
                )

                ElDraftPasswordField(
                    label: String(localized: "login_password_label"),
                    text: $password,
                    errorText: (isRegisterMode && !password.isEmpty && !passwordValid)
                        ? String(localized: "login_password_hint_min") : nil,
                    accessibilityId: "login_password_field"
                )

                if isRegisterMode {
                    ElDraftPasswordField(
                        label: String(localized: "login_confirm_password_label"),
                        text: $confirmPassword,
                        errorText: (!confirmPassword.isEmpty && confirmPassword != password)
                            ? String(localized: "login_passwords_mismatch") : nil,
                        accessibilityId: "login_confirm_password_field"
                    )
                }

                if let errorMessage {
                    Text(errorMessage)
                        .font(ElDraftTypography.labelMedium)
                        .foregroundStyle(ElDraftColors.error)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                submitButton

                orDivider

                googleButton

                Spacer().frame(height: ElDraftSpacing.xxl)
            }
            .padding(.horizontal, ElDraftSpacing.xxl)
        }
        .background(ElDraftColors.background)
        .scrollDismissesKeyboard(.interactively)
        .onChange(of: viewModel.state) { _, newValue in
            switch newValue {
            case .loading:
                errorMessage = nil
            case .success(let needsOnboarding):
                onLoginSuccess(needsOnboarding)
            case .error(let message):
                errorMessage = message
                viewModel.resetError()
            case .idle:
                break
            }
        }
    }

    private var modeChips: some View {
        HStack(spacing: ElDraftSpacing.sm) {
            chip(title: "login_tab_signin", selected: !isRegisterMode, id: "login_chip_signin") { isRegisterMode = false }
            chip(title: "login_create_account", selected: isRegisterMode, id: "login_chip_signup") { isRegisterMode = true }
            Spacer()
        }
    }

    private func chip(title: LocalizedStringKey, selected: Bool, id: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(ElDraftTypography.labelMedium)
                .padding(.horizontal, ElDraftSpacing.lg)
                .padding(.vertical, ElDraftSpacing.sm)
                .background(selected ? ElDraftColors.primaryContainer : ElDraftColors.surfaceVariant, in: ElDraftShapes.pill)
                .foregroundStyle(selected ? ElDraftColors.onPrimaryContainer : ElDraftColors.onSurfaceVariant)
        }
        .accessibilityIdentifier(id)
    }

    private var submitButton: some View {
        Button {
            if isRegisterMode {
                viewModel.registerWithEmail(name: name, email: email, password: password)
            } else {
                viewModel.signInWithEmail(email: email, password: password)
            }
        } label: {
            Group {
                if isLoading {
                    ProgressView().tint(ElDraftColors.onPrimary)
                } else {
                    Text(isRegisterMode ? "login_create_account" : "login_tab_signin")
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, ElDraftSpacing.md)
        }
        .background(
            canSubmitEmail ? ElDraftColors.primary : ElDraftColors.primary.opacity(ElDraftAlpha.disabled),
            in: ElDraftShapes.pill
        )
        .foregroundStyle(ElDraftColors.onPrimary)
        .disabled(!canSubmitEmail)
        .accessibilityIdentifier("login_submit_button")
    }

    private var orDivider: some View {
        HStack(spacing: ElDraftSpacing.sm) {
            Rectangle().fill(ElDraftColors.outline).frame(height: ElDraftSizes.borderHairline)
            Text("login_divider_or")
                .font(ElDraftTypography.labelMedium)
                .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textMuted))
            Rectangle().fill(ElDraftColors.outline).frame(height: ElDraftSizes.borderHairline)
        }
    }

    private var googleButton: some View {
        Button {
            viewModel.signInWithGoogle()
        } label: {
            Text("login_google")
                .frame(maxWidth: .infinity)
                .padding(.vertical, ElDraftSpacing.md)
        }
        .overlay(ElDraftShapes.pill.stroke(ElDraftColors.outline, lineWidth: ElDraftSizes.borderHairline))
        .foregroundStyle(ElDraftColors.onSurface)
        .disabled(isLoading)
        .accessibilityIdentifier("login_google_button")
    }
}
