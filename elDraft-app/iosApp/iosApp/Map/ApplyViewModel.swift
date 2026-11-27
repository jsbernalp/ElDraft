import Foundation
import Observation
import shared

/// Estado de la acción "Postularme a este cupo". Espejo de `ApplyUiState`/
/// `ApplyViewModel.kt`.
enum ApplyUiState: Equatable {
    case idle
    case sending
    case applied
    case error(message: String)
}

@MainActor
@Observable
final class ApplyViewModel {
    private(set) var state: ApplyUiState = .idle

    func apply(convocatoryId: String, position: String) {
        guard state != .sending else { return }
        state = .sending
        Task {
            do {
                _ = try await IosKoin.shared.applyToConvocatory.invoke(convocatoryId: convocatoryId, position: position)
                state = .applied
            } catch {
                let message = error.kotlinMessage(fallback: "No se pudo enviar tu postulación")
                state = .error(message: message)
            }
        }
    }

    func reset() {
        state = .idle
    }
}
