import Foundation
import Observation
import shared

/// Espejo de `MyPostulationsViewModel.kt`: postulaciones propias del jugador.
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

    init() {
        NotificationRefreshBus.shared.subscribe { [weak self] event in
            guard event == .myPostulations else { return }
            self?.load()
        }
    }

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
