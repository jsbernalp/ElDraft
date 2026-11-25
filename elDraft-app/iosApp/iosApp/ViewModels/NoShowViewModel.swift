import Foundation
import Observation
import shared

/// Estado del botón "el organizador no se presentó" por convocatoria. Espejo
/// de `NoShowUiState` en `NoShowViewModel.kt`.
struct NoShowUiState: Equatable {
    var status: NoShowStatus?
    var isLoading = false
    var isReporting = false
    var error: String?
}

/// Maneja el reporte de no-show del organizador para una convocatoria
/// concreta. Carga el estado (¿puedo reportar?, votos, consenso) y emite el
/// voto del asistente. El estado se cachea por convocatoryId para
/// reutilizarse entre cards. Espejo de `NoShowViewModel.kt`.
@MainActor
@Observable
final class NoShowViewModel {
    private(set) var states: [String: NoShowUiState] = [:]

    func state(for convocatoryId: String) -> NoShowUiState {
        states[convocatoryId] ?? NoShowUiState()
    }

    func load(convocatoryId: String) {
        states[convocatoryId, default: NoShowUiState()].isLoading = true
        states[convocatoryId]?.error = nil
        Task {
            do {
                let status = try await IosKoin.shared.getNoShowStatus.invoke(convocatoryId: convocatoryId)
                states[convocatoryId, default: NoShowUiState()].status = status
                states[convocatoryId]?.isLoading = false
            } catch {
                states[convocatoryId]?.isLoading = false
                states[convocatoryId]?.error = (error as? KotlinThrowable)?.userMessage(fallback: "No se pudo cargar el estado")
                    ?? "No se pudo cargar el estado"
            }
        }
    }

    func report(convocatoryId: String) {
        guard states[convocatoryId]?.isReporting != true else { return }
        states[convocatoryId, default: NoShowUiState()].isReporting = true
        states[convocatoryId]?.error = nil
        Task {
            do {
                let status = try await IosKoin.shared.reportOrganizerNoShow.invoke(convocatoryId: convocatoryId)
                states[convocatoryId, default: NoShowUiState()].status = status
                states[convocatoryId]?.isReporting = false
            } catch {
                states[convocatoryId]?.isReporting = false
                states[convocatoryId]?.error = (error as? KotlinThrowable)?.userMessage(fallback: "No se pudo reportar")
                    ?? "No se pudo reportar"
            }
        }
    }
}
