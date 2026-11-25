import CoreLocation
import Observation

/// Obtiene la ubicación actual del dispositivo vía CoreLocation y expone el
/// estado de autorización para que la UI decida qué mostrar. Espejo de
/// `util/LocationProvider.kt` (Android usa FusedLocationProvider con
/// fallback a la última ubicación cacheada; acá `requestLocation()` ya hace
/// ese mismo intento con fallback a `manager.location` si falla).
@Observable
final class LocationProvider: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private(set) var authorizationStatus: CLAuthorizationStatus
    private var continuation: CheckedContinuation<CLLocationCoordinate2D?, Never>?

    override init() {
        authorizationStatus = manager.authorizationStatus
        super.init()
        manager.delegate = self
    }

    func requestPermission() {
        manager.requestWhenInUseAuthorization()
    }

    /// Vuelve a leer el estado del sistema (el usuario pudo cambiarlo desde
    /// Ajustes mientras la app estaba en segundo plano).
    func refreshAuthorizationStatus() {
        authorizationStatus = manager.authorizationStatus
    }

    /// El permiso debe estar concedido antes de llamar. Devuelve `nil` solo
    /// si no hay ninguna ubicación disponible.
    func current() async -> CLLocationCoordinate2D? {
        await withCheckedContinuation { continuation in
            self.continuation = continuation
            manager.requestLocation()
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        continuation?.resume(returning: locations.last?.coordinate)
        continuation = nil
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        continuation?.resume(returning: manager.location?.coordinate)
        continuation = nil
    }
}

/// Permiso de ubicación que la app solicita. Espejo de `LOCATION_PERMISSIONS`.
extension LocationProvider {
    var hasPermission: Bool {
        authorizationStatus == .authorizedWhenInUse || authorizationStatus == .authorizedAlways
    }
}
