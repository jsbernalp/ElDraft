import Foundation

/// Destinos que se empujan sobre el `NavigationStack`, una vez resuelta la
/// fase de arranque (splash → login/onboarding/home vía `AppPhase`). Espejo
/// de los `Screen` de `ElDraftApp.kt`, salvo Splash/Login/OnboardingProfile/
/// Home, que en Android también son rutas de un NavHost pero aquí se
/// modelan como fases del root (ver `RootView`), no como push del stack.
enum AppRoute: Hashable {
    case profileEdit
    case createDraft
    case applicants(convocatoryId: String)
    case attendance(convocatoryId: String)
    case playerCromo(playerId: String)
    case qrGenerator(convocatoryId: String)
    case qrScanner(convocatoryId: String, isOrganizer: Bool)
    case postMatchRating(convocatoryId: String)
    case teamDetail(teamId: String)
}
