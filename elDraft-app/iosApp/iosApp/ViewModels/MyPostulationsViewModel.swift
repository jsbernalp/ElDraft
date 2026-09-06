import Foundation
import Observation
import shared

/// Espejo de `MyPostulationsViewModel.kt`: postulaciones propias del jugador.
/// No porta la suscripción a `NotificationRefreshBus` (recarga por FCM): no
/// hay push en iOS todavía (Fase 8 del plan de port); `load()` se dispara
/// manualmente y por pull-to-refresh.
@MainActor
@Observable
final class MyPostulationsViewModel {
    private(set) var postulations: [MyPostulation] = []
    private(set) var isLoading = false
    private(set) var error: String?

    private(set) var withdrawingId: String?
    /// Fallo del último intento de retirar, o nil. Va aparte de `error`
    /// porque se muestra dentro del sheet de confirmación.
    private(set) var withdrawError: String?
    private(set) var withdrawSuccess = false

    func load() {
        isLoading = true
        error = nil
        Task {
            do {
                postulations = try await IosKoin.shared.getMyPostulations.invoke()
                isLoading = false
            } catch {
                isLoading = false
                self.error = (error as? KotlinThrowable)?.userMessage(fallback: "No se pudieron cargar tus postulaciones")
                    ?? "No se pudieron cargar tus postulaciones"
            }
        }
    }

    func withdraw(postulationId: String) {
        withdrawingId = postulationId
        withdrawError = nil
        Task {
            do {
                try await IosKoin.shared.withdrawPostulation.invoke(postulationId: postulationId)
                withdrawingId = nil
                withdrawSuccess = true
                load()
            } catch {
                withdrawingId = nil
                withdrawError = (error as? KotlinThrowable)?.userMessage(fallback: "No se pudo retirar la postulación")
                    ?? "No se pudo retirar la postulación"
            }
        }
    }

    func clearError() { error = nil }
    func clearWithdrawError() { withdrawError = nil }
    func resetWithdrawSuccess() { withdrawSuccess = false }
}
