import SwiftUI

/// Relleno temporal para un destino todavía no implementado. Cada pantalla
/// real llega en su propia fase (ver el plan de port); esto solo prueba que
/// la navegación hacia ella funciona.
struct PlaceholderScreenView: View {
    let title: String

    var body: some View {
        VStack(spacing: ElDraftSpacing.md) {
            Text(title)
                .font(ElDraftTypography.titleLarge)
                .foregroundStyle(ElDraftColors.onSurface)
            Text("Pantalla pendiente")
                .font(ElDraftTypography.bodyLarge)
                .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(ElDraftColors.background)
        .navigationTitle(title)
    }
}
