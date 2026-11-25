import SwiftUI

/// Selector de una opción de una lista fija, con el mismo estilo que
/// `ElDraftTextField`. Espejo de `DropdownField.kt` (Android), que ahí es un
/// `ExposedDropdownMenuBox` de solo lectura; en SwiftUI el equivalente
/// nativo es un `Menu` sobre el mismo campo con borde.
struct DropdownField: View {
    let label: String
    let options: [String]
    @Binding var selected: String
    /// Identificador para XCUITest (ver comentario en `ElDraftTextField`).
    var accessibilityId: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: ElDraftSpacing.xs) {
            Text(label)
                .font(ElDraftTypography.labelMedium)
                .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))

            Menu {
                ForEach(options, id: \.self) { option in
                    Button(option) { selected = option }
                }
            } label: {
                HStack {
                    Text(selected.isEmpty ? " " : selected)
                        .foregroundStyle(selected.isEmpty ? ElDraftColors.onSurface.opacity(ElDraftAlpha.textMuted) : ElDraftColors.onSurface)
                    Spacer()
                    Image(systemName: "chevron.down")
                        .font(.system(size: ElDraftSizes.iconSm))
                        .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.icon))
                }
                .padding(.horizontal, ElDraftSpacing.md)
                .padding(.vertical, ElDraftSpacing.sm)
                .background(ElDraftColors.surface, in: ElDraftShapes.field)
                .overlay(ElDraftShapes.field.stroke(ElDraftColors.outline, lineWidth: ElDraftSizes.borderHairline))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier(accessibilityId ?? "")
        }
    }
}
