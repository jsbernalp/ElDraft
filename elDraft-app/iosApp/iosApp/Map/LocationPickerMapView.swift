import CoreLocation
import GoogleMaps
import SwiftUI

/// Mapa para seleccionar una ubicación tocándolo. Coloca un marcador donde el
/// usuario toque y notifica vía `onLocationSelected`. La cámara se controla
/// desde fuera (p. ej. el buscador de direcciones la recentra), igual que
/// `GoogleMapView` — reutiliza el mismo `MapCameraController`. Espejo de
/// `LocationPickerMap.kt`.
struct LocationPickerMapView: UIViewRepresentable {
    let selectedCoordinate: CLLocationCoordinate2D?
    let cameraController: MapCameraController
    let initialCamera: GMSCameraPosition
    let onLocationSelected: (CLLocationCoordinate2D) -> Void

    func makeUIView(context: Context) -> GMSMapView {
        let options = GMSMapViewOptions()
        options.camera = initialCamera
        let mapView = GMSMapView(options: options)
        mapView.delegate = context.coordinator
        mapView.settings.zoomGestures = true
        mapView.settings.compassButton = false
        cameraController.attach(mapView)
        context.coordinator.updateMarker(at: selectedCoordinate, on: mapView)
        return mapView
    }

    func updateUIView(_ mapView: GMSMapView, context: Context) {
        context.coordinator.updateMarker(at: selectedCoordinate, on: mapView)
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(onLocationSelected: onLocationSelected)
    }

    final class Coordinator: NSObject, GMSMapViewDelegate {
        private let onLocationSelected: (CLLocationCoordinate2D) -> Void
        private var marker: GMSMarker?

        init(onLocationSelected: @escaping (CLLocationCoordinate2D) -> Void) {
            self.onLocationSelected = onLocationSelected
        }

        func updateMarker(at coordinate: CLLocationCoordinate2D?, on mapView: GMSMapView) {
            guard let coordinate else {
                marker?.map = nil
                marker = nil
                return
            }
            if let marker {
                marker.position = coordinate
            } else {
                let newMarker = GMSMarker(position: coordinate)
                newMarker.title = String(localized: "location_picker_marker")
                newMarker.map = mapView
                marker = newMarker
            }
        }

        func mapView(_ mapView: GMSMapView, didTapAt coordinate: CLLocationCoordinate2D) {
            onLocationSelected(coordinate)
        }
    }
}
