import SwiftUI

/// Indicador de carga centrado, para estados de carga a pantalla completa.
/// Espejo de `LoadingState` en `StateViews.kt`.
struct LoadingState: View {
    var body: some View {
        ProgressView()
            .tint(ElDraftColors.primary)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Estado vacío consistente: un emoji/título grande y un mensaje secundario,
/// todo centrado. Espejo de `EmptyState` en `StateViews.kt`.
struct EmptyState: View {
    let title: String
    let message: String
    var icon: String? = nil

    var body: some View {
        VStack(spacing: ElDraftSpacing.sm) {
            if let icon {
                Text(icon)
                    .font(ElDraftTypography.displaySmall)
            }
            Text(title)
                .font(ElDraftTypography.titleMedium)
                .foregroundStyle(ElDraftColors.onBackground)
                .multilineTextAlignment(.center)
            Text(message)
                .font(ElDraftTypography.bodyMedium)
                .foregroundStyle(ElDraftColors.onBackground.opacity(ElDraftAlpha.textTertiary))
                .multilineTextAlignment(.center)
        }
        .padding(ElDraftSpacing.xxl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
