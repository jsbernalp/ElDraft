import Foundation
import Observation
import shared

/// Espejo de `CancelConvocatoryViewModel.kt`.
@MainActor
@Observable
final class CancelConvocatoryViewModel {
    private(set) var isLoading = false
    private(set) var success = false
    private(set) var error: String?

    func cancel(convocatoryId: String, reason: String) {
        isLoading = true
        error = nil
        Task {
            do {
                try await IosKoin.shared.cancelConvocatory.invoke(id: convocatoryId, reason: reason)
                isLoading = false
                success = true
            } catch {
                isLoading = false
                self.error = (error as? KotlinThrowable)?.userMessage(fallback: "No se pudo cancelar el partido")
                    ?? "No se pudo cancelar el partido"
            }
        }
    }

    func clearError() { error = nil }
    func resetSuccess() { success = false }
}
