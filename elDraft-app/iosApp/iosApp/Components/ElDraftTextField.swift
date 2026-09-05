import SwiftUI

/// Campo de texto con etiqueta y borde, equivalente al `OutlinedTextField`
/// de Material 3 que usa Android en Login/Onboarding. `SwiftUI` no trae un
/// estilo "outlined con label flotante" nativo, así que se arma con un
/// `VStack` (label arriba, campo con borde abajo).
struct ElDraftTextField: View {
    let label: String
    @Binding var text: String
    var keyboardType: UIKeyboardType = .default
    var errorText: String? = nil
    /// Identificador para XCUITest (ver `iosAppUITests`). Sin esto, un
    /// `TextField("", text:)` no tiene forma estable de ubicarse: su label de
    /// accesibilidad por defecto está vacío.
    var accessibilityId: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: ElDraftSpacing.xs) {
            Text(label)
                .font(ElDraftTypography.labelMedium)
                .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))

            TextField("", text: $text)
                .keyboardType(keyboardType)
                .autocorrectionDisabled(keyboardType == .emailAddress)
                .textInputAutocapitalization(keyboardType == .emailAddress ? .never : .sentences)
                .accessibilityIdentifier(accessibilityId ?? "")
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
