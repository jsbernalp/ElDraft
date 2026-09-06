import Foundation
import Observation
import shared

/// Estado de la pantalla de declaración de asistencia (vista del organizador).
/// `rows` son los convocados aprobados; `absent` son los ids que el
/// organizador marca como ausentes (toggle local hasta guardar). Quien
/// escaneó no es editable. Espejo de `AttendanceDeclarationUiState` en
/// `AttendanceDeclarationViewModel.kt`.
struct AttendanceDeclarationUiState {
    var rows: [PlayerAttendanceRow] = []
    var absent: Set<String> = []
    var isLoading = false
    var isSaving = false
    var saved = false
    /// True si el backend bloqueó al organizador (403): fue marcado no-show.
    var blocked = false
    var error: String?
}

@MainActor
@Observable
final class AttendanceDeclarationViewModel {
    private(set) var state = AttendanceDeclarationUiState()

    func load(convocatoryId: String) {
        state.isLoading = true
        state.error = nil
        Task {
            do {
                let rows = try await IosKoin.shared.getAttendanceList.invoke(convocatoryId: convocatoryId)
                state.rows = rows
                // Pre-selecciona las ausencias ya declaradas previamente.
                state.absent = Set(rows.filter { $0.markedNoShow }.map(\.playerId))
                state.isLoading = false
            } catch {
                state.isLoading = false
                state.blocked = (error as? ApiException)?.status == 403
                state.error = (error as? KotlinThrowable)?.userMessage(fallback: "No se pudo cargar la asistencia")
                    ?? "No se pudo cargar la asistencia"
            }
        }
    }

    /// Alterna la ausencia de un jugador. Ignora a quien escaneó (presencia firme).
    func toggleAbsent(playerId: String) {
        guard let row = state.rows.first(where: { $0.playerId == playerId }), !row.scanned else { return }
        if state.absent.contains(playerId) {
            state.absent.remove(playerId)
        } else {
            state.absent.insert(playerId)
        }
    }

    func save(convocatoryId: String) {
        guard !state.isSaving else { return }
        state.isSaving = true
        state.error = nil
        Task {
            do {
                let rows = try await IosKoin.shared.declareAttendance.invoke(
                    convocatoryId: convocatoryId,
                    absentPlayerIds: Array(state.absent)
                )
                state.rows = rows
                state.absent = Set(rows.filter { $0.markedNoShow }.map(\.playerId))
                state.isSaving = false
                state.saved = true
            } catch {
                state.isSaving = false
                state.error = (error as? KotlinThrowable)?.userMessage(fallback: "No se pudo guardar la asistencia")
                    ?? "No se pudo guardar la asistencia"
            }
        }
    }

    func clearError() {
        state.error = nil
    }
}
