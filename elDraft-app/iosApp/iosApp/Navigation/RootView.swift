import SwiftUI

/// Fase de arranque del root: splash, y según haya sesión, login/onboarding
/// u home. Equivale a los primeros 4 destinos del `NavHost` raíz de
/// `ElDraftApp.kt` (Splash/Login/OnboardingProfile/Home); el resto de rutas
/// (`AppRoute`) se empujan sobre el `NavigationStack` de la fase activa.
private enum AppPhase {
    case splash
    case needsLogin
    case needsOnboarding
    case home
}

struct RootView: View {
    @State private var phase: AppPhase = .splash
    @State private var path: [AppRoute] = []
    #if DEBUG
    @State private var showDebugHealthCheck = false
    #endif

    var body: some View {
        content
            #if DEBUG
            // El tab de Debug de MainTabView solo se ve con sesión (Home), y en
            // esta fase login/onboarding son placeholders sin forma de conseguir
            // una. Este botón flotante prueba la conexión al backend desde
            // cualquier fase, sin depender de que el login ya exista (Fase 2).
            .overlay(alignment: .bottomTrailing) {
                if phase != .splash {
                    Button {
                        showDebugHealthCheck = true
                    } label: {
                        Image(systemName: "ladybug.fill")
                            .font(.title2)
                            .foregroundStyle(.white)
                            .padding(ElDraftSpacing.md)
                            .background(ElDraftColors.primary, in: Circle())
                    }
                    .padding(ElDraftSpacing.lg)
                }
            }
            .sheet(isPresented: $showDebugHealthCheck) {
                DebugHealthCheckView()
            }
            #endif
    }

    @ViewBuilder
    private var content: some View {
        switch phase {
        case .splash:
            SplashView(onResolved: { hasSession in
                phase = hasSession ? .home : .needsLogin
            })

        case .needsLogin:
            NavigationStack(path: $path) {
                PlaceholderScreenView(title: "Iniciar sesión")
                    .navigationDestination(for: AppRoute.self, destination: destinationView)
            }

        case .needsOnboarding:
            NavigationStack(path: $path) {
                PlaceholderScreenView(title: "Tu Ficha Técnica")
                    .navigationDestination(for: AppRoute.self, destination: destinationView)
            }

        case .home:
            NavigationStack(path: $path) {
                MainTabView()
                    .navigationDestination(for: AppRoute.self, destination: destinationView)
            }
        }
    }

    @ViewBuilder
    private func destinationView(for route: AppRoute) -> some View {
        switch route {
        case .profileEdit:
            PlaceholderScreenView(title: "Editar perfil")
        case .createDraft:
            PlaceholderScreenView(title: "Nueva convocatoria")
        case .applicants(let id):
            PlaceholderScreenView(title: "Postulantes (\(id))")
        case .attendance(let id):
            PlaceholderScreenView(title: "Asistencia (\(id))")
        case .playerCromo(let id):
            PlaceholderScreenView(title: "Cromo (\(id))")
        case .qrGenerator(let id):
            PlaceholderScreenView(title: "Código QR (\(id))")
        case .qrScanner(let id, let isOrganizer):
            PlaceholderScreenView(title: "Escanear QR (\(id), organizador: \(isOrganizer))")
        case .postMatchRating(let id):
            PlaceholderScreenView(title: "Calificar partido (\(id))")
        }
    }
}
