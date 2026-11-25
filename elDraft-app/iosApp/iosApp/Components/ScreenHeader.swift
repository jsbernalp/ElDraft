import SwiftUI

/// Cabecera estándar de pantalla: título grande + subtítulo de acento.
/// Espejo de `ScreenHeader.kt`. Política de la app: toda pantalla de
/// contenido encabeza con este componente, excepto Splash y Login (layout de
/// marca centrado).
struct ScreenHeader: View {
    let title: String
    let subtitle: String
    var alignment: HorizontalAlignment = .leading

    var body: some View {
        VStack(alignment: alignment, spacing: ElDraftSpacing.xxs) {
            Text(title)
                .font(ElDraftTypography.headlineMedium)
                .foregroundStyle(ElDraftColors.onBackground)
                .multilineTextAlignment(alignment == .center ? .center : .leading)
            Text(subtitle)
                .font(ElDraftTypography.bodyLarge)
                .foregroundStyle(ElDraftColors.primary)
                .multilineTextAlignment(alignment == .center ? .center : .leading)
        }
        .frame(maxWidth: .infinity, alignment: alignment == .center ? .center : .leading)
    }
}
