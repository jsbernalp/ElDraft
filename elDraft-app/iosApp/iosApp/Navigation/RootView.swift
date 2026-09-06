import SwiftUI
import shared

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
            // bottomLeading (no bottomTrailing): desde la Fase 4, Organizo
            // tiene su propio FAB de producción en esa esquina y quedaba tapado.
            .overlay(alignment: .bottomLeading) {
                VStack(spacing: ElDraftSpacing.sm) {
                    if phase == .home {
                        // Sin esto no hay forma de cerrar sesión hasta la Fase 7
                        // (Perfil real): necesario para verificar logout de la
                        // Fase 2 sin adelantar esa pantalla.
                        Button {
                            Task {
                                try? await IosKoin.shared.logout.invoke()
                                phase = .needsLogin
                            }
                        } label: {
                            Image(systemName: "rectangle.portrait.and.arrow.right")
                                .font(.title2)
                                .foregroundStyle(.white)
                                .padding(ElDraftSpacing.md)
                                .background(ElDraftColors.secondary, in: Circle())
                        }
                        .accessibilityIdentifier("debug_logout_button")
                    }
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
                    }
                }
                .padding(ElDraftSpacing.lg)
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
                LoginView(onLoginSuccess: { needsOnboarding in
                    phase = needsOnboarding ? .needsOnboarding : .home
                })
                .navigationDestination(for: AppRoute.self, destination: destinationView)
            }

        case .needsOnboarding:
            NavigationStack(path: $path) {
                OnboardingProfileView(onProfileComplete: {
                    phase = .home
                })
                .navigationDestination(for: AppRoute.self, destination: destinationView)
            }

        case .home:
            NavigationStack(path: $path) {
                MainTabView(
                    onCreateDraft: { path.append(.createDraft) },
                    onOpenApplicants: { id in path.append(.applicants(convocatoryId: id)) },
                    onOpenQrGenerator: { id in path.append(.qrGenerator(convocatoryId: id)) },
                    // El organizador también escanea para marcar su propia presencia.
                    onOpenQrScanner: { id in path.append(.qrScanner(convocatoryId: id, isOrganizer: true)) },
                    onOpenRating: { id in path.append(.postMatchRating(convocatoryId: id)) },
                    onOpenAttendance: { id in path.append(.attendance(convocatoryId: id)) }
                )
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
            CreateDraftView(onDraftCreated: { path.removeLast() })
        case .applicants(let id):
            ApplicantsView(
                convocatoryId: id,
                onOpenPlayerCromo: { playerId in path.append(.playerCromo(playerId: playerId)) }
            )
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
