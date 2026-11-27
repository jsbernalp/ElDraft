import Foundation
import Observation
import shared

/// Estado del formulario de creación de convocatoria (El Draft). Espejo de
/// `CreateDraftUiState`/`CreateDraftViewModel.kt`.
enum CreateDraftUiState: Equatable {
    case idle
    case saving
    case created
    case error(message: String)
    /// La convocatoria choca con postulaciones del organizador. Se pide
    /// confirmación: si acepta, se reintenta cancelándolas. `conflicts` son
    /// los partidos que se cancelarían.
    case confirmCancel(conflicts: [ScheduleConflictItem])

    // `ScheduleConflictItem` (Kotlin data class) no conforma `Equatable` en
    // Swift, así que se compara por sus campos nativos (String) en vez de
    // apoyarse en la conformidad automática de `Array`.
    static func == (lhs: CreateDraftUiState, rhs: CreateDraftUiState) -> Bool {
        switch (lhs, rhs) {
        case (.idle, .idle), (.saving, .saving), (.created, .created):
            return true
        case (.error(let a), .error(let b)):
            return a == b
        case (.confirmCancel(let a), .confirmCancel(let b)):
            return a.count == b.count
                && zip(a, b).allSatisfy { $0.format == $1.format && $0.scheduledAt == $1.scheduledAt }
        default:
            return false
        }
    }
}

@MainActor
@Observable
final class CreateDraftViewModel {
    private(set) var state: CreateDraftUiState = .idle

    /// Mis equipos que se pueden avisar primero: solo los que tienen al menos un
    /// miembro activo (si no, no hay a quién avisar). Si falla la carga, la
    /// sección simplemente no aparece: el equipo es opcional, no debe estorbar
    /// la creación.
    private(set) var teams: [Team] = []

    func loadTeams() {
        Task {
            let mine = (try? await IosKoin.shared.getMyTeams.invoke()) ?? []
            teams = TeamRules.shared.prioritizable(teams: mine)
        }
    }

    // Último request, para reintentar con cancelConflicts=true si el usuario confirma.
    private var pendingRequest: CreateConvocatoryRequest?

    func create(_ request: CreateConvocatoryRequest) {
        if state == .saving { return }
        pendingRequest = request
        state = .saving
        Task {
            do {
                _ = try await IosKoin.shared.createConvocatory.invoke(request: request)
                state = .created
            } catch {
                // Conflicto con sus postulaciones: ofrecer cancelar y reintentar.
                if let apiError = error.apiException,
                   apiError.code == "SCHEDULE_CONFLICT",
                   !apiError.scheduleConflicts.isEmpty {
                    state = .confirmCancel(conflicts: apiError.scheduleConflicts)
                } else {
                    let message = error.kotlinMessage(fallback: "No se pudo crear la convocatoria")
                    state = .error(message: message)
                }
            }
        }
    }

    /// El usuario confirmó cancelar sus postulaciones en conflicto: reintenta.
    /// `CreateConvocatoryRequest` no cruza el puente Objective-C con valores
    /// por defecto (Kotlin/Native no expone `copy()` con argumentos
    /// opcionales para Swift), así que se reconstruye entero.
    func confirmCancelConflicts() {
        guard let request = pendingRequest else { return }
        let updated = CreateConvocatoryRequest(
            lat: request.lat,
            lng: request.lng,
            addressText: request.addressText,
            positionSlots: request.positionSlots,
            fee: request.fee,
            format: request.format,
            ambiente: request.ambiente,
            scheduledAt: request.scheduledAt,
            cancelConflicts: true,
            teamId: request.teamId,
            teamWindowMinutes: request.teamWindowMinutes
        )
        create(updated)
    }

    /// El usuario rechazó el diálogo de conflicto: vuelve al formulario.
    func dismissConfirm() {
        if case .confirmCancel = state { state = .idle }
    }

    func resetError() {
        if case .error = state { state = .idle }
    }
}
