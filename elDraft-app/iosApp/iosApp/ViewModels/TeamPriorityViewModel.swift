import Foundation
import Observation
import shared

/// Espejo de `TeamPriorityViewModel.kt`: convocatorias de mis equipos que
/// esperan mi "Voy" / "No puedo" (pestaña Juego).
@MainActor
@Observable
final class TeamPriorityViewModel {
    private(set) var invites: [TeamPriorityInvite] = []
    private(set) var isLoading = false
    /// Convocatoria cuya respuesta está en vuelo (deshabilita sus botones).
    private(set) var respondingId: String?
    private(set) var error: String?
    /// True un instante tras responder, para avisar con un toast.
    private(set) var responded = false

    init() {
        // El push team_priority y cualquier cambio de postulaciones refrescan la card.
        NotificationRefreshBus.shared.subscribe { [weak self] event in
            guard event == .myPostulations else { return }
            self?.load()
        }
    }

    func load() {
        isLoading = true
        Task {
            do {
                invites = try await IosKoin.shared.getTeamPriorityInvites.invoke()
                isLoading = false
            } catch {
                isLoading = false
                self.error = error.kotlinMessage(fallback: "No se pudieron cargar las convocatorias de tu equipo")
            }
        }
    }

    /// "Voy" (`accept` = true, con `position`) o "No puedo".
    func respond(convocatoryId: String, accept: Bool, position: String?) {
        guard respondingId == nil else { return }
        respondingId = convocatoryId
        error = nil
        Task {
            do {
                try await IosKoin.shared.respondTeamPriority.invoke(
                    convocatoryId: convocatoryId, accept: accept, position: position
                )
                respondingId = nil
                responded = true
                invites.removeAll { $0.convocatory.id == convocatoryId }
                // Un "Voy" crea una postulación: la lista de Juego debe recargarse.
                NotificationRefreshBus.shared.emit(.myPostulations)
            } catch {
                respondingId = nil
                self.error = error.kotlinMessage(fallback: "No se pudo enviar tu respuesta")
                // Un 409 (sin cupo, ventana vencida) deja la card desactualizada.
                load()
            }
        }
    }

    func clearError() { error = nil }
    func clearResponded() { responded = false }
}
