import Foundation
import Observation
import shared

/// Espejo de `MyMatchesViewModel.kt`: convocatorias que organiza el usuario.
/// No porta la suscripción a `NotificationRefreshBus` (recarga por FCM): no
/// hay push en iOS todavía (Fase 8 del plan de port); `load()` se dispara
/// manualmente y por pull-to-refresh.
@MainActor
@Observable
final class MyMatchesViewModel {
    private(set) var matches: [Convocatory] = []
    private(set) var isLoading = false
    private(set) var error: String?

    func load() {
        isLoading = true
        error = nil
        Task {
            do {
                matches = try await IosKoin.shared.convocatoryRepository.getMine()
                isLoading = false
            } catch {
                isLoading = false
                self.error = (error as? KotlinThrowable)?.userMessage(fallback: "No se pudieron cargar tus convocatorias")
                    ?? "No se pudieron cargar tus convocatorias"
            }
        }
    }
}
