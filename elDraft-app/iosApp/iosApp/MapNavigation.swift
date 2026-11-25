import MapKit

/// Abre Apple Maps con ruta al destino. Espejo funcional de `openDirections`
/// en `util/MapNavigation.kt`: Android intenta Google Maps y cae a un
/// `geo:` genérico si no hay ninguna app de mapas instalada; en iOS, Apple
/// Maps siempre está presente, así que un único camino basta.
func openDirections(lat: Double, lng: Double, label: String?) {
    let placemark = MKPlacemark(coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lng))
    let item = MKMapItem(placemark: placemark)
    item.name = label
    item.openInMaps(launchOptions: [MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeDriving])
}
