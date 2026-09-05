import SwiftUI

/// Campo de contraseña con el mismo estilo que `ElDraftTextField`, más el
/// ojo de visibilidad. Espejo de `PasswordVisibilityToggle` en
/// `LoginScreen.kt`: el ícono muestra la ACCIÓN, no el estado (ojo abierto =
/// "tócame para verla").
struct ElDraftPasswordField: View {
    let label: String
    @Binding var text: String
    var errorText: String? = nil
    /// Identificador para XCUITest (ver comentario en `ElDraftTextField`).
    var accessibilityId: String? = nil

    @State private var isVisible = false

    var body: some View {
        VStack(alignment: .leading, spacing: ElDraftSpacing.xs) {
            Text(label)
                .font(ElDraftTypography.labelMedium)
                .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))

            HStack(spacing: ElDraftSpacing.sm) {
                Group {
                    if isVisible {
                        TextField("", text: $text)
                    } else {
                        SecureField("", text: $text)
                    }
                }
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
                .accessibilityIdentifier(accessibilityId ?? "")
                // Bajo XCUITest, iOS ofrece guardar la contraseña nueva en su
                // Password Manager (sheet de sistema) apenas se escribe acá,
                // tapando el resto del formulario a mitad de tipeo. `.oneTimeCode`
                // le dice a iOS que esto no es una contraseña y apaga esa
                // sugerencia — solo bajo test, el usuario real conserva el
                // autofill de contraseñas normal.
                .textContentType(ProcessInfo.isRunningUITests ? .oneTimeCode : nil)

                Button {
                    isVisible.toggle()
                } label: {
                    Image(systemName: isVisible ? "eye.slash" : "eye")
                        .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.icon))
                }
                .accessibilityLabel(LocalizedStringKey(isVisible ? "login_password_hide" : "login_password_show"))
            }
            .padding(.horizontal, ElDraftSpacing.md)
            .padding(.vertical, ElDraftSpacing.sm)
            .background(ElDraftColors.surface, in: ElDraftShapes.field)
            .overlay(
                ElDraftShapes.field.stroke(
                    errorText == nil ? ElDraftColors.outline : ElDraftColors.error,
                    lineWidth: ElDraftSizes.borderHairline
                )
            )

            if let errorText {
                Text(errorText)
                    .font(ElDraftTypography.labelMedium)
                    .foregroundStyle(ElDraftColors.error)
            }
        }
    }
}
