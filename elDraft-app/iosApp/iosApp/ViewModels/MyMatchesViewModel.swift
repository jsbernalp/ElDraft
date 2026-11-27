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
    /// Convocatoria que se está abriendo al público (bloquea el diálogo).
    private(set) var openingId: String?
    /// Fallo de "Abrir ya": va aparte de `error` porque se muestra dentro del
    /// diálogo (que está encima y taparía el toast).
    private(set) var openError: String?
    private(set) var openSuccess = false

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
                self.error = error.kotlinMessage(fallback: "No se pudieron cargar tus convocatorias")
            }
        }
    }

    /// "Abrir ya al público": salta lo que falta de la ventana exclusiva del equipo.
    func openNow(convocatoryId: String) {
        guard openingId == nil else { return }
        openingId = convocatoryId
        openError = nil
        Task {
            do {
                _ = try await IosKoin.shared.openConvocatoryToPublic.invoke(convocatoryId: convocatoryId)
                openingId = nil
                openSuccess = true
                load()
                // Si se publicó, el pin aparece en el mapa de los demás.
                NotificationRefreshBus.shared.emit(.map)
            } catch {
                openingId = nil
                openError = error.kotlinMessage(fallback: "No se pudo abrir la convocatoria")
                // Típico: la ventana ya venció y se abrió sola. Se recarga para mostrarlo.
                load()
            }
        }
    }

    func clearOpenError() { openError = nil }
    func clearOpenSuccess() { openSuccess = false }
}
