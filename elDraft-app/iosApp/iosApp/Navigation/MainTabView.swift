import SwiftUI

/// Las 4 pestañas del tab bar, mismo orden que el `Tab` sealed class de
/// `MainScaffold.kt`. Existe (en vez de dejar que `TabView` maneje su propia
/// selección interna) para que un deep link `eldraft://tab/...` pueda fijar
/// la pestaña activa desde `RootView`, igual que `initialTab` en
/// `MainScaffold`.
enum MainTab: Hashable {
    case organizo, juego, buscarCupo, perfil
}

/// Las 4 secciones del `NavigationBar` de Android (`MainScaffold.kt`), todas
/// reales desde la Fase 7. El acceso a `DebugHealthCheckView` vive en
/// `RootView` (botón flotante en Debug), no aquí: este tab bar solo existe
/// con sesión (Home), y el chequeo de salud del backend tiene que poder
/// probarse antes de que exista login (Fase 2).
///
/// Las rutas de detalle (`AppRoute`) se empujan sobre el `NavigationStack`
/// raíz que envuelve este tab bar (ver `RootView`) — de ahí que Organizo
/// reciba callbacks en vez de un `path` propio, igual que `OrganizoScreen` en
/// Android recibe los suyos desde `MainScaffold`.
struct MainTabView: View {
    @Binding var selection: MainTab
    let onCreateDraft: () -> Void
    let onOpenApplicants: (String) -> Void
    let onOpenQrGenerator: (String) -> Void
    /// `isOrganizer` decide el texto de instrucciones del escáner ("pídele a
    /// un jugador convocado" vs. "pídele al organizador"). Se fija aquí, no en
    /// `MyMatchesView`/`MyPostulationsView`, igual que `MainScaffold.kt`
    /// resuelve el booleano según qué pestaña llamó, no cada pantalla.
    let onOpenQrScanner: (String, Bool) -> Void
    let onOpenRating: (String) -> Void
    let onOpenAttendance: (String) -> Void
    let onEditProfile: () -> Void
    let onLoggedOut: () -> Void

    var body: some View {
        TabView(selection: $selection) {
            MyMatchesView(
                onCreateDraft: onCreateDraft,
                onOpenApplicants: onOpenApplicants,
                onOpenQrGenerator: onOpenQrGenerator,
                onOpenQrScanner: { id in onOpenQrScanner(id, true) },
                onOpenRating: onOpenRating,
                onOpenAttendance: onOpenAttendance
            )
            .tabItem { Label("Organizo", systemImage: "sportscourt.fill") }
            .tag(MainTab.organizo)

            MyPostulationsView(
                onOpenQrScanner: { id in onOpenQrScanner(id, false) },
                onOpenQrGenerator: onOpenQrGenerator,
                onOpenRating: onOpenRating
            )
            .tabItem { Label("Juego", systemImage: "figure.run") }
            .tag(MainTab.juego)

            BuscarCupoView()
                .tabItem { Label("Buscar Cupo", systemImage: "map.fill") }
                .tag(MainTab.buscarCupo)

            ProfileTabView(onEditProfile: onEditProfile, onLoggedOut: onLoggedOut)
                .tabItem { Label("Perfil", systemImage: "person.fill") }
                .tag(MainTab.perfil)
        }
        .tint(ElDraftColors.primary)
        .accessibilityIdentifier("main_tab_view")
    }
}
