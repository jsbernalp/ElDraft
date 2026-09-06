import Foundation

/// Espejo de `NotificationRefreshBus.kt` (Android): avisa a las pantallas
/// abiertas que deben recargar cuando `NotificationHelperIOS.show` procesa un
/// push relevante. Las suscripciones capturan `[weak self]`, así que un
/// ViewModel liberado simplemente deja de reaccionar — no hace falta darse de
/// baja explícitamente (evita el lío de un `deinit` no-aislado llamando a un
/// bus `@MainActor`, como sí hace falta con `Cancellable` en `MapViewModel`).
enum RefreshEvent {
    case myMatches       // Tab Organizo: llegó una nueva postulación al organizador
    case myPostulations  // Tab Juego: postulación aprobada o rechazada
    case map             // Tab Buscar Cupo: nueva convocatoria o recordatorio cercano
    case applicants      // Pantalla de postulantes (si está abierta)
}

@MainActor
final class NotificationRefreshBus {
    static let shared = NotificationRefreshBus()
    private init() {}

    private var handlers: [(RefreshEvent) -> Void] = []

    func subscribe(_ onEvent: @escaping (RefreshEvent) -> Void) {
        handlers.append(onEvent)
    }

    func emit(_ event: RefreshEvent) {
        handlers.forEach { $0(event) }
    }
}
