import CoreLocation
import GoogleMaps
import SwiftUI
import shared

/// Mapa de convocatorias: agrupa pines por coordenada exacta y notifica la
/// selección de un pin único o de un grupo al contenedor
/// ([BuscarCupoView]). Espejo de `MapTabContent.kt`.
struct MapTabView: View {
    let pins: [Convocatory]
    let isLoading: Bool
    let error: String?
    let hasLocationPermission: Bool
    let cameraController: MapCameraController
    let initialCamera: GMSCameraPosition
    let onPinTap: (Convocatory) -> Void
    let onGroupTap: ([Convocatory]) -> Void

    private var groups: [ConvocatoryPinGroup] {
        Dictionary(grouping: pins) { "\($0.lat),\($0.lng)" }
            .values
            .map { convocatories in
                ConvocatoryPinGroup(
                    coordinate: CLLocationCoordinate2D(latitude: convocatories[0].lat, longitude: convocatories[0].lng),
                    convocatories: convocatories
                )
            }
    }

    var body: some View {
        ZStack {
            // `GMSMapView` lanza `GMSServicesException` al construirse si
            // `GMSServices.provideAPIKey` nunca recibió una key no vacía — ni
            // siquiera "muestra tiles en blanco". Sin `MAPS_API_KEY_IOS`
            // (Config/*.xcconfig) evitamos construirlo del todo: la Fase 3
            // sigue siendo usable desde la Lista, y el mapa no tumba la app.
            if AppConfig.mapsApiKey.isEmpty {
                mapUnavailable
            } else {
                GoogleMapView(
                    groups: groups,
                    hasLocationPermission: hasLocationPermission,
                    cameraController: cameraController,
                    initialCamera: initialCamera,
                    onPinTap: onPinTap,
                    onGroupTap: onGroupTap
                )
                .ignoresSafeArea(edges: .bottom)
            }

            if isLoading {
                ProgressView()
                    .tint(ElDraftColors.primary)
                    .padding(.top, ElDraftSpacing.lg)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    .allowsHitTesting(false)
            }

            if let error {
                Text(error)
                    .font(ElDraftTypography.bodySmall)
                    .foregroundStyle(ElDraftColors.onErrorContainer)
                    .padding(ElDraftSpacing.md)
                    .background(ElDraftColors.errorContainer, in: ElDraftShapes.md)
                    .padding(ElDraftSpacing.lg)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .allowsHitTesting(false)
            }
        }
    }

    private var mapUnavailable: some View {
        VStack(spacing: ElDraftSpacing.sm) {
            Image(systemName: "map.fill")
                .font(.system(size: 40))
                .foregroundStyle(ElDraftColors.onBackground.opacity(ElDraftAlpha.textTertiary))
            Text("Mapa no disponible")
                .font(ElDraftTypography.titleMedium)
                .foregroundStyle(ElDraftColors.onBackground)
            Text("Falta configurar MAPS_API_KEY_IOS. Mientras tanto, usa la vista de Lista.")
                .font(ElDraftTypography.bodyMedium)
                .foregroundStyle(ElDraftColors.onBackground.opacity(ElDraftAlpha.textSecondary))
                .multilineTextAlignment(.center)
                .padding(.horizontal, ElDraftSpacing.xxl)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(ElDraftColors.background)
    }
}
