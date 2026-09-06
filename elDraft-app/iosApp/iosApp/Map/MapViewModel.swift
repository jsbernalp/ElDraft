import Foundation
import Observation
import shared

/// Espejo de `MapViewModel.kt`: un snapshot REST (`getNearby`) + suscripción
/// al WebSocket (`MapEventsSubscription`, Fase 0.6) para el radio observado.
@MainActor
@Observable
final class MapViewModel {
    private(set) var pins: [Convocatory] = []
    private(set) var isLoading = false
    private(set) var error: String?
    /// convocatoryId -> estado de mi postulación ("pending"/"approved"/"rejected"/"cancelled").
    private(set) var myPostulations: [String: String] = [:]
    /// False hasta que termina la primera carga del área.
    private(set) var hasLoadedOnce = false

    private var lastArea: (lat: Double, lng: Double, radius: Double)?
    // Fuera de tracking de `@Observable` (no es estado de UI, como `wsJob` en
    // Android). `nonisolated(unsafe)`: un `var` en una clase @MainActor no
    // admite `nonisolated` a secas, y es lo único que permite a `deinit`
    // (siempre nonisolated) cancelarla; toda mutación real ocurre en el
    // MainActor igual.
    @ObservationIgnored
    private nonisolated(unsafe) var subscription: Cancellable?

    init() {
        NotificationRefreshBus.shared.subscribe { [weak self] event in
            guard event == .map else { return }
            self?.reload()
        }
    }

    /// Carga los pines cercanos (snapshot REST) y se suscribe al WebSocket
    /// para recibir nuevos pines en tiempo real, en el radio dado.
    func loadArea(lat: Double, lng: Double, radius: Double = 5000) {
        lastArea = (lat, lng, radius)
        isLoading = true
        error = nil
        Task {
            do {
                let nearby = try await IosKoin.shared.convocatoryRepository.getNearby(lat: lat, lng: lng, radius: radius)
                pins = nearby
                isLoading = false
                hasLoadedOnce = true
            } catch {
                isLoading = false
                hasLoadedOnce = true
                self.error = friendlyMessage(error, fallback: "No se pudieron cargar los partidos")
            }
        }
        subscribeRealtime(lat: lat, lng: lng, radius: radius)
        refreshMyPostulations()
    }

    /// Recarga el área actual (pull-to-refresh desde la lista).
    func reload() {
        guard let area = lastArea else { return }
        loadArea(lat: area.lat, lng: area.lng, radius: area.radius)
    }

    /// Reporta la ubicación REAL del usuario al backend. Best-effort: solo
    /// debe llamarse con una ubicación real del dispositivo.
    func reportLocation(lat: Double, lng: Double) {
        Task { try? await IosKoin.shared.reportLocation.invoke(lat: lat, lng: lng) }
    }

    /// Recarga las postulaciones propias para reflejar en la UI a qué
    /// convocatorias ya se postuló. Best-effort.
    func refreshMyPostulations() {
        Task {
            guard let mine = try? await IosKoin.shared.getMyPostulations.invoke() else { return }
            myPostulations = Dictionary(uniqueKeysWithValues: mine.map { ($0.convocatory.id, $0.status) })
        }
    }

    private func subscribeRealtime(lat: Double, lng: Double, radius: Double) {
        subscription?.cancel()
        subscription = nil
        Task {
            // El backend usa el userId para no reenviarnos nuestros propios pines.
            let userId = try? await IosKoin.shared.authRepository.currentUserId()
            let events = MapEventsSubscription(
                observeMapEvents: IosKoin.shared.observeMapEvents,
                lat: lat, lng: lng, radius: radius, userId: userId
            )
            subscription = events.subscribe(
                onEach: { [weak self] event in
                    Task { @MainActor [weak self] in
                        guard let self else { return }
                        switch event.event {
                        case "new_pin": self.addOrUpdatePin(event.data)
                        case "pin_closed": self.removePin(id: event.data.id)
                        default: break
                        }
                    }
                },
                onError: { _ in } // el WS puede cerrarse; se ignora para no romper la UI
            )
        }
    }

    private func addOrUpdatePin(_ pin: MapPin) {
        guard !pins.contains(where: { $0.id == pin.id }) else { return }
        pins.append(pin.toConvocatory())
    }

    private func removePin(id: String) {
        pins.removeAll { $0.id == id }
    }

    /// `Throwable.userMessage(fallback)` de `shared`, mismo mecanismo que Android.
    private func friendlyMessage(_ error: Error, fallback: String) -> String {
        (error as? KotlinThrowable)?.userMessage(fallback: fallback) ?? fallback
    }

    deinit {
        subscription?.cancel()
    }
}

/// Convierte el pin del WebSocket (datos mínimos) a un `Convocatory` para el
/// mapa. Espejo de `MapPin.toConvocatory()` en `MapViewModel.kt`.
private extension MapPin {
    func toConvocatory() -> Convocatory {
        Convocatory(
            id: id, organizerId: "", lat: lat, lng: lng, addressText: nil,
            slotsNeeded: slots, positionRequired: "", positionSlots: [], fee: 0.0,
            format: format, ambiente: ambiente, status: "active", scheduledAt: "",
            organizerNoShow: false, pendingCount: 0, attended: false,
            cancellationReason: nil, cancelledAt: nil
        )
    }
}
