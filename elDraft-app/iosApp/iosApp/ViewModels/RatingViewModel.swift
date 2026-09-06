import Foundation
import Observation
import shared

/// Los 3 criterios calificables. Espejo de `RatingCriterion` en
/// `RatingViewModel.kt` (Android).
enum RatingCriterion {
    case skill, sportsmanship, responsibility
}

/// Notas que el usuario asigna a un compañero, una por criterio (0 = sin
/// elegir, 1..5 = nota). Espejo de `TeammateScores` (Android).
struct TeammateScores {
    var skill = 0
    var sportsmanship = 0
    var responsibility = 0

    /// Las 3 notas están elegidas (listas para enviar).
    var isComplete: Bool { skill > 0 && sportsmanship > 0 && responsibility > 0 }

    func value(for criterion: RatingCriterion) -> Int {
        switch criterion {
        case .skill: return skill
        case .sportsmanship: return sportsmanship
        case .responsibility: return responsibility
        }
    }

    func with(_ criterion: RatingCriterion, value: Int) -> TeammateScores {
        var copy = self
        switch criterion {
        case .skill: copy.skill = value
        case .sportsmanship: copy.sportsmanship = value
        case .responsibility: copy.responsibility = value
        }
        return copy
    }
}

/// Espejo de `RatingViewModel.kt`: calificación post-partido en 3 criterios
/// por compañero de convocatoria.
@MainActor
@Observable
final class RatingViewModel {
    private(set) var teammates: [Teammate] = []
    /// Notas por jugador (userId -> notas de los 3 criterios).
    private(set) var scores: [String: TeammateScores] = [:]
    private(set) var isLoading = false
    private(set) var isSubmitting = false
    private(set) var done = false
    /// `true` cuando el backend rechaza por no haber registrado asistencia (403).
    private(set) var notAttended = false
    private(set) var error: String?

    func load(convocatoryId: String) {
        isLoading = true
        error = nil
        notAttended = false
        Task {
            do {
                teammates = try await IosKoin.shared.getTeammatesToRate.invoke(convocatoryId: convocatoryId)
                isLoading = false
            } catch {
                isLoading = false
                // 403 ⇒ el jugador aún no marcó asistencia: no es un error real.
                if (error as? ApiException)?.status == 403 {
                    notAttended = true
                } else {
                    self.error = (error as? KotlinThrowable)?.userMessage(fallback: "No se pudieron cargar los compañeros")
                        ?? "No se pudieron cargar los compañeros"
                }
            }
        }
    }

    func setCriterion(userId: String, criterion: RatingCriterion, value: Int) {
        let current = scores[userId] ?? TeammateScores()
        scores[userId] = current.with(criterion, value: value)
    }

    /// Envía solo las calificaciones nuevas (no las ya enviadas) y que tengan
    /// los 3 criterios completos. Si no hay ninguna lista, termina sin enviar.
    func submitAll(convocatoryId: String) {
        guard !isSubmitting else { return }
        let pending = scores.filter { userId, teammateScores in
            teammateScores.isComplete && teammates.contains { $0.userId == userId && !$0.alreadyRated }
        }
        guard !pending.isEmpty else {
            done = true
            return
        }
        isSubmitting = true
        error = nil
        Task {
            do {
                for (userId, teammateScores) in pending {
                    try await IosKoin.shared.submitRating.invoke(
                        convocatoryId: convocatoryId,
                        ratedPlayerId: userId,
                        skill: Int32(teammateScores.skill),
                        sportsmanship: Int32(teammateScores.sportsmanship),
                        responsibility: Int32(teammateScores.responsibility)
                    )
                }
                isSubmitting = false
                done = true
            } catch {
                isSubmitting = false
                self.error = (error as? KotlinThrowable)?.userMessage(fallback: "No se pudieron enviar las calificaciones")
                    ?? "No se pudieron enviar las calificaciones"
            }
        }
    }

    func clearError() { error = nil }
}
