import CoreLocation
import GoogleMaps
import SwiftUI
import shared

/// Controlador liviano de cámara: guarda la referencia al `GMSMapView` para
/// poder centrarlo desde fuera (el contenedor, `BuscarCupoView`), igual que
/// `cameraPositionState` en Android — incluso antes de que el mapa se monte,
/// el primer `move(to:)` queda pendiente y se aplica en cuanto se adjunta.
@MainActor
final class MapCameraController {
    fileprivate weak var mapView: GMSMapView?
    private var pendingMove: (coordinate: CLLocationCoordinate2D, zoom: Float, animated: Bool)?

    func move(to coordinate: CLLocationCoordinate2D, zoom: Float, animated: Bool = true) {
        guard let mapView else {
            pendingMove = (coordinate, zoom, animated)
            return
        }
        apply(coordinate: coordinate, zoom: zoom, animated: animated, on: mapView)
    }

    // Antes `fileprivate`: solo `GoogleMapView` lo usaba. Fase 4 reutiliza
    // este mismo controlador desde `LocationPickerMapView` (otro archivo),
    // así que necesita ser visible a todo el módulo.
    func attach(_ mapView: GMSMapView) {
        self.mapView = mapView
        if let pending = pendingMove {
            pendingMove = nil
            apply(coordinate: pending.coordinate, zoom: pending.zoom, animated: pending.animated, on: mapView)
        }
    }

    private func apply(coordinate: CLLocationCoordinate2D, zoom: Float, animated: Bool, on mapView: GMSMapView) {
        let camera = GMSCameraPosition(latitude: coordinate.latitude, longitude: coordinate.longitude, zoom: zoom)
        if animated {
            mapView.animate(to: camera)
        } else {
            mapView.camera = camera
        }
    }
}

/// Convocatorias que comparten coordenada exacta: comparten un solo marcador
/// (con badge numérico) en vez de solaparse. Espejo del `groupBy { lat to
/// lng }` de `MapTabContent.kt`.
struct ConvocatoryPinGroup: Identifiable {
    let coordinate: CLLocationCoordinate2D
    let convocatories: [Convocatory]
    var id: String { convocatories.map(\.id).sorted().joined(separator: ",") }
}

/// `UIViewRepresentable` sobre `GMSMapView`. Espejo del `GoogleMap`
/// composable dentro de `MapTabContent.kt`; acá el marcador de marca se
/// dibuja con `MapPinImageFactory` en vez de un `Canvas` declarativo.
struct GoogleMapView: UIViewRepresentable {
    let groups: [ConvocatoryPinGroup]
    let hasLocationPermission: Bool
    let cameraController: MapCameraController
    let initialCamera: GMSCameraPosition
    let onPinTap: (Convocatory) -> Void
    let onGroupTap: ([Convocatory]) -> Void

    func makeUIView(context: Context) -> GMSMapView {
        let options = GMSMapViewOptions()
        options.camera = initialCamera
        let mapView = GMSMapView(options: options)
        mapView.delegate = context.coordinator
        mapView.isMyLocationEnabled = hasLocationPermission
        mapView.settings.zoomGestures = true
        cameraController.attach(mapView)
        context.coordinator.updateMarkers(groups: groups, onPinTap: onPinTap, onGroupTap: onGroupTap, on: mapView)
        return mapView
    }

    func updateUIView(_ mapView: GMSMapView, context: Context) {
        mapView.isMyLocationEnabled = hasLocationPermission
        context.coordinator.updateMarkers(groups: groups, onPinTap: onPinTap, onGroupTap: onGroupTap, on: mapView)
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator: NSObject, GMSMapViewDelegate {
        // Evita redibujar (y parpadear) en cada `updateUIView` si el conjunto
        // de pines no cambió realmente.
        private var lastSignature: String?

        func updateMarkers(
            groups: [ConvocatoryPinGroup],
            onPinTap: @escaping (Convocatory) -> Void,
            onGroupTap: @escaping ([Convocatory]) -> Void,
            on mapView: GMSMapView
        ) {
            let signature = groups.map(\.id).sorted().joined(separator: "|")
            guard signature != lastSignature else { return }
            lastSignature = signature

            mapView.clear()
            for group in groups {
                let marker = GMSMarker(position: group.coordinate)
                marker.groundAnchor = CGPoint(x: 0.5, y: 1.0) // la punta de la gota toca la coordenada real
                if let single = group.convocatories.first, group.convocatories.count == 1 {
                    marker.icon = MapPinImageFactory.pin(count: nil)
                    marker.title = single.format.isEmpty ? String(localized: "map_marker_default") : single.format
                    marker.userData = { onPinTap(single) } as () -> Void
                } else {
                    marker.icon = MapPinImageFactory.pin(count: group.convocatories.count)
                    marker.title = String(format: String(localized: "matches_here"), group.convocatories.count)
                    marker.userData = { onGroupTap(group.convocatories) } as () -> Void
                }
                marker.map = mapView
            }
        }

        func mapView(_ mapView: GMSMapView, didTap marker: GMSMarker) -> Bool {
            (marker.userData as? () -> Void)?()
            return true
        }
    }
}
