import CoreLocation
import GoogleMaps
import SwiftUI
import shared

/// Centro por defecto: Medellín (hasta tener la ubicación del usuario).
private let buscarCupoDefaultCenter = CLLocationCoordinate2D(latitude: 6.2442, longitude: -75.5812)

/// Modo de visualización de "Buscar Cupo". La lista es la vista por defecto.
private enum BuscarCupoMode {
    case lista
    case mapa
}

/// Sección "Buscar Cupo": convocatorias abiertas como lista (vista por
/// defecto) o como mapa, alternables con un toggle. Ambas comparten el mismo
/// [MapViewModel] y abren el mismo [PinDetailSheet] al elegir una
/// convocatoria. Espejo de `BuscarCupoScreen` en `TabScreens.kt`.
struct BuscarCupoView: View {
    @State private var viewModel = MapViewModel()
    @State private var locationProvider = LocationProvider()
    @State private var cameraController = MapCameraController()
    @State private var mode: BuscarCupoMode = .lista
    @State private var selectedPin: Convocatory?
    @State private var selectedGroup: [Convocatory]?
    @State private var centeredOnUser = false
    @State private var toastMessage: String?
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            if !locationProvider.hasPermission {
                LocationPermissionRequiredView()
            } else {
                content
            }
        }
        .onAppear {
            if !locationProvider.hasPermission {
                locationProvider.requestPermission()
            }
        }
        // Al volver de Ajustes, re-chequea el permiso (equivalente al
        // ON_RESUME de Android).
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                locationProvider.refreshAuthorizationStatus()
            }
        }
        // Obtiene la ubicación real y carga el área en cuanto hay permiso. Si
        // el GPS está frío, carga con Medellín como fallback y reintenta.
        .task(id: locationProvider.hasPermission) {
            guard locationProvider.hasPermission, !centeredOnUser else { return }
            centeredOnUser = true
            if let location = await locationProvider.current() {
                cameraController.move(to: location, zoom: 14)
                viewModel.loadArea(lat: location.latitude, lng: location.longitude)
                viewModel.reportLocation(lat: location.latitude, lng: location.longitude)
                return
            }
            viewModel.loadArea(lat: buscarCupoDefaultCenter.latitude, lng: buscarCupoDefaultCenter.longitude)
            if let retry = await locationProvider.current() {
                cameraController.move(to: retry, zoom: 14)
                viewModel.loadArea(lat: retry.latitude, lng: retry.longitude)
                viewModel.reportLocation(lat: retry.latitude, lng: retry.longitude)
            }
        }
    }

    private var content: some View {
        VStack(spacing: 0) {
            Picker("", selection: $mode) {
                Text(String(localized: "search_toggle_list")).tag(BuscarCupoMode.lista)
                Text(String(localized: "search_toggle_map")).tag(BuscarCupoMode.mapa)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, ElDraftSpacing.lg)
            .padding(.vertical, ElDraftSpacing.md)

            Group {
                switch mode {
                case .lista:
                    ConvocatoryListView(
                        pins: viewModel.pins,
                        isLoading: viewModel.isLoading,
                        hasLoadedOnce: viewModel.hasLoadedOnce,
                        myPostulations: viewModel.myPostulations,
                        onSelect: { selectedPin = $0 },
                        onRefresh: {
                            viewModel.reload()
                            while viewModel.isLoading {
                                try? await Task.sleep(nanoseconds: 150_000_000)
                            }
                        }
                    )
                case .mapa:
                    // El mapa comparte el viewModel; al estar oculto no
                    // recarga porque `loadArea` solo se dispara desde el
                    // `.task` de arriba, una vez.
                    MapTabView(
                        pins: viewModel.pins,
                        isLoading: viewModel.isLoading,
                        error: viewModel.error,
                        hasLocationPermission: locationProvider.hasPermission,
                        cameraController: cameraController,
                        initialCamera: GMSCameraPosition(
                            latitude: buscarCupoDefaultCenter.latitude,
                            longitude: buscarCupoDefaultCenter.longitude,
                            zoom: 13
                        ),
                        onPinTap: { selectedPin = $0 },
                        onGroupTap: { selectedGroup = $0 }
                    )
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .toast(message: $toastMessage)
        // Lista de convocatorias de una ubicación con varias (oculta si ya se
        // eligió una, para no apilar dos sheets).
        .sheet(isPresented: groupSheetPresented) {
            if let group = selectedGroup {
                PinGroupSheet(convocatories: group, myPostulations: viewModel.myPostulations, onSelect: { selectedPin = $0 })
            }
        }
        .sheet(item: $selectedPin) { pin in
            PinDetailSheet(
                convocatory: pin,
                postulationStatus: viewModel.myPostulations[pin.id],
                onApplied: {
                    selectedPin = nil
                    selectedGroup = nil
                    viewModel.refreshMyPostulations()
                    toastMessage = String(localized: "postulation_sent")
                }
            )
        }
    }

    private var groupSheetPresented: Binding<Bool> {
        Binding(
            get: { selectedGroup != nil && selectedPin == nil },
            set: { presented in if !presented { selectedGroup = nil } }
        )
    }
}
