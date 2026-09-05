import SwiftUI

/// Banner inferior de confirmación/error. Compose tiene `SnackbarHost` nativo;
/// SwiftUI no, así que esto es el reemplazo mínimo (usado por primera vez en
/// Fase 3, para "postulación enviada" — se reutiliza en fases siguientes).
private struct ToastBanner: View {
    let message: String

    var body: some View {
        Text(message)
            .font(ElDraftTypography.bodyMedium)
            .foregroundStyle(ElDraftColors.onSurface)
            .multilineTextAlignment(.center)
            .padding(.horizontal, ElDraftSpacing.lg)
            .padding(.vertical, ElDraftSpacing.md)
            .background(ElDraftColors.surface, in: ElDraftShapes.md)
            .shadow(radius: ElDraftElevation.overlay)
            .padding(.horizontal, ElDraftSpacing.xxl)
            .padding(.bottom, ElDraftSpacing.xl)
    }
}

private struct ToastModifier: ViewModifier {
    @Binding var message: String?
    let duration: TimeInterval

    func body(content: Content) -> some View {
        content.overlay(alignment: .bottom) {
            if let message {
                ToastBanner(message: message)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .task(id: message) {
                        try? await Task.sleep(nanoseconds: UInt64(duration * 1_000_000_000))
                        if !Task.isCancelled {
                            withAnimation { self.message = nil }
                        }
                    }
            }
        }
        .animation(.easeInOut, value: message)
    }
}

extension View {
    /// Muestra `message.wrappedValue` como banner inferior auto-dismiss tras
    /// `duration` segundos; `nil` no muestra nada.
    func toast(message: Binding<String?>, duration: TimeInterval = 3) -> some View {
        modifier(ToastModifier(message: message, duration: duration))
    }
}
