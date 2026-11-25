import SwiftUI

/// Pantalla que se muestra en "Buscar Cupo" cuando no hay permiso de
/// ubicación. Explica por qué se necesita y ofrece un botón a los Ajustes del
/// sistema (más fiable que reintentar el diálogo, que iOS deja de mostrar
/// tras la primera negativa). Espejo de `LocationPermissionRequired.kt`.
struct LocationPermissionRequiredView: View {
    var body: some View {
        VStack(spacing: 0) {
            Image(systemName: "location.slash.fill")
                .font(.system(size: 56)) // ícono grande de estado vacío, no un tamaño reutilizado
                .foregroundStyle(ElDraftColors.primary)
            Text(String(localized: "location_permission_title"))
                .font(ElDraftTypography.titleLarge)
                .foregroundStyle(ElDraftColors.onBackground)
                .multilineTextAlignment(.center)
                .padding(.top, ElDraftSpacing.lg)
            Text(String(localized: "location_permission_message"))
                .font(ElDraftTypography.bodyMedium)
                .foregroundStyle(ElDraftColors.onBackground.opacity(ElDraftAlpha.textSecondary))
                .multilineTextAlignment(.center)
                .padding(.top, ElDraftSpacing.sm)
            Button {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            } label: {
                Text(String(localized: "action_open_settings"))
                    .padding(.horizontal, ElDraftSpacing.lg)
            }
            .buttonStyle(.borderedProminent)
            .tint(ElDraftColors.primary)
            .padding(.top, ElDraftSpacing.xl)
        }
        .padding(ElDraftSpacing.xxl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(ElDraftColors.background)
    }
}
