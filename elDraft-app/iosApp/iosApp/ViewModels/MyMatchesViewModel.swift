import Foundation
import Observation
import shared

/// Espejo de `MyMatchesViewModel.kt`: convocatorias que organiza el usuario.
@MainActor
@Observable
final class MyMatchesViewModel {
    private(set) var matches: [Convocatory] = []
    private(set) var isLoading = false
    private(set) var error: String?

    init() {
        NotificationRefreshBus.shared.subscribe { [weak self] event in
            guard event == .myMatches else { return }
            self?.load()
        }
    }

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
