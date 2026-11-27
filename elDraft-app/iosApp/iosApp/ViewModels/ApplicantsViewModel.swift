import Foundation
import Observation
import shared

/// Espejo de `ApplicantsViewModel.kt`: postulantes de una convocatoria (vista
/// del organizador).
@MainActor
@Observable
final class ApplicantsViewModel {
    private(set) var applicants: [Postulation] = []
    private(set) var isLoading = false
    private(set) var error: String?
    /// IDs de postulaciones con una decisión en curso (para deshabilitar botones).
    private(set) var decidingIds: Set<String> = []

    private var currentConvocatoryId: String?

    init() {
        NotificationRefreshBus.shared.subscribe { [weak self] event in
            guard event == .applicants, let convocatoryId = self?.currentConvocatoryId else { return }
            self?.load(convocatoryId: convocatoryId)
        }
    }

    func load(convocatoryId: String) {
        currentConvocatoryId = convocatoryId
        isLoading = true
        error = nil
        Task {
            do {
                applicants = try await IosKoin.shared.getApplicants.invoke(convocatoryId: convocatoryId)
                isLoading = false
            } catch {
                isLoading = false
                self.error = error.kotlinMessage(fallback: "No se pudieron cargar los postulantes")
            }
        }
    }

    func approve(postulationId: String) {
        decide(postulationId: postulationId) {
            try await IosKoin.shared.approveApplicant.invoke(postulationId: postulationId)
        }
    }

    func reject(postulationId: String) {
        decide(postulationId: postulationId) {
            try await IosKoin.shared.rejectApplicant.invoke(postulationId: postulationId)
        }
    }

    private func decide(postulationId: String, action: @escaping () async throws -> Postulation) {
        guard !decidingIds.contains(postulationId) else { return }
        decidingIds.insert(postulationId)
        Task {
            do {
                let updated = try await action()
                applicants = applicants.map { $0.id == updated.id ? updated : $0 }
                decidingIds.remove(postulationId)
            } catch {
                decidingIds.remove(postulationId)
                self.error = error.kotlinMessage(fallback: "No se pudo actualizar la postulación")
            }
        }
    }

    func clearError() { error = nil }
}
