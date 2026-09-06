import SwiftUI

/// Las 4 secciones del `NavigationBar` de Android (`MainScaffold.kt`).
/// Organizo (Fase 4) y Juego (Fase 5) ya son reales; Perfil sigue como
/// placeholder (Fase 7). El acceso a `DebugHealthCheckView` vive en
/// `RootView` (botón flotante en Debug), no aquí: este tab bar solo existe
/// con sesión (Home), y el chequeo de salud del backend tiene que poder
/// probarse antes de que exista login (Fase 2).
///
/// Las rutas de detalle (`AppRoute`) se empujan sobre el `NavigationStack`
/// raíz que envuelve este tab bar (ver `RootView`) — de ahí que Organizo
/// reciba callbacks en vez de un `path` propio, igual que `OrganizoScreen` en
/// Android recibe los suyos desde `MainScaffold`.
struct MainTabView: View {
    let onCreateDraft: () -> Void
    let onOpenApplicants: (String) -> Void
    let onOpenQrGenerator: (String) -> Void
    let onOpenQrScanner: (String) -> Void
    let onOpenRating: (String) -> Void
    let onOpenAttendance: (String) -> Void

    var body: some View {
        TabView {
            MyMatchesView(
                onCreateDraft: onCreateDraft,
                onOpenApplicants: onOpenApplicants,
                onOpenQrGenerator: onOpenQrGenerator,
                onOpenQrScanner: onOpenQrScanner,
                onOpenRating: onOpenRating,
                onOpenAttendance: onOpenAttendance
            )
            .tabItem { Label("Organizo", systemImage: "sportscourt.fill") }

            MyPostulationsView(
                onOpenQrScanner: onOpenQrScanner,
                onOpenQrGenerator: onOpenQrGenerator,
                onOpenRating: onOpenRating
            )
            .tabItem { Label("Juego", systemImage: "figure.run") }

            BuscarCupoView()
                .tabItem { Label("Buscar Cupo", systemImage: "map.fill") }

            PlaceholderScreenView(title: "Perfil")
                .tabItem { Label("Perfil", systemImage: "person.fill") }
        }
        .tint(ElDraftColors.primary)
        .accessibilityIdentifier("main_tab_view")
    }
}
