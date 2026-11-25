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
    @State private var selectedTab: MainTab = .organizo
    #if DEBUG
    @State private var showDebugHealthCheck = false
    #endif

    var body: some View {
        content
            // Deep links de notificación (eldraft://tab/..., eldraft://
            // applicants/{id}) — ver `PushNotificationDelegate`, que reabre
            // la app por este mismo esquema al tocar una notificación en vez
            // de duplicar la navegación. Espejo de `resolveDeepLink` +
            // `LaunchedEffect(deepLinkUri)` en `ElDraftApp.kt`.
            .onOpenURL { handleDeepLink($0) }
            #if DEBUG
            // El tab de Debug de MainTabView solo se ve con sesión (Home), y en
            // esta fase login/onboarding son placeholders sin forma de conseguir
            // una. Este botón flotante prueba la conexión al backend desde
            // cualquier fase, sin depender de que el login ya exista (Fase 2).
            // bottomLeading (no bottomTrailing): desde la Fase 4, Organizo
            // tiene su propio FAB de producción en esa esquina y quedaba tapado.
            // El logout de debug que vivía aquí se quitó en la Fase 7: el tab
            // Perfil ya ofrece un logout real.
            .overlay(alignment: .bottomLeading) {
                VStack(spacing: ElDraftSpacing.sm) {
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
                    selection: $selectedTab,
                    onCreateDraft: { path.append(.createDraft) },
                    onOpenApplicants: { id in path.append(.applicants(convocatoryId: id)) },
                    onOpenQrGenerator: { id in path.append(.qrGenerator(convocatoryId: id)) },
                    onOpenQrScanner: { id, isOrganizer in path.append(.qrScanner(convocatoryId: id, isOrganizer: isOrganizer)) },
                    onOpenRating: { id in path.append(.postMatchRating(convocatoryId: id)) },
                    onOpenAttendance: { id in path.append(.attendance(convocatoryId: id)) },
                    onEditProfile: { path.append(.profileEdit) },
                    onLoggedOut: {
                        path = []
                        phase = .needsLogin
                    }
                )
                .navigationDestination(for: AppRoute.self, destination: destinationView)
            }
        }
    }

    @ViewBuilder
    private func destinationView(for route: AppRoute) -> some View {
        switch route {
        case .profileEdit:
            ProfileEditView(
                onSaved: { path.removeLast() },
                onLoggedOut: {
                    path = []
                    phase = .needsLogin
                }
            )
        case .createDraft:
            CreateDraftView(onDraftCreated: { path.removeLast() })
        case .applicants(let id):
            ApplicantsView(
                convocatoryId: id,
                onOpenPlayerCromo: { playerId in path.append(.playerCromo(playerId: playerId)) }
            )
        case .attendance(let id):
            AttendanceDeclarationView(convocatoryId: id)
        case .playerCromo(let id):
            PlayerCromoView(playerId: id)
        case .qrGenerator(let id):
            QRGeneratorView(convocatoryId: id)
        case .qrScanner(let id, let isOrganizer):
            QRScannerView(
                convocatoryId: id,
                isOrganizer: isOrganizer,
                onScanComplete: { path.removeLast() },
                onBack: { path.removeLast() }
            )
        case .postMatchRating(let id):
            PostMatchRatingView(convocatoryId: id, onRatingComplete: { path.removeLast() })
        }
    }

    /// Convierte un URI `eldraft://…` al destino de navegación interno.
    /// Solo tiene sentido con sesión activa (`.home`): un push nunca llega
    /// antes de haber iniciado sesión (el token FCM se registra recién
    /// entonces), así que un deep link en cualquier otra fase se ignora en
    /// vez de intentar adivinar a dónde llevar — mismo comportamiento
    /// implícito que Android, cuyo `resolveDeepLink` puede competir con la
    /// navegación del propio splash si llega antes de resolverse la sesión.
    private func handleDeepLink(_ url: URL) {
        guard url.scheme == "eldraft", phase == .home else { return }
        switch url.host {
        case "tab":
            switch url.lastPathComponent {
            case "organizo": selectedTab = .organizo
            case "juego": selectedTab = .juego
            case "buscar_cupo": selectedTab = .buscarCupo
            default: break
            }
        case "applicants":
            let id = url.lastPathComponent
            guard !id.isEmpty else { return }
            path.append(.applicants(convocatoryId: id))
        default:
            break
        }
    }
}
