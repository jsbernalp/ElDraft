import SwiftUI

/// Las 5 pestañas del tab bar, mismo orden que el `Tab` sealed class de
/// `MainScaffold.kt`. Existe (en vez de dejar que `TabView` maneje su propia
/// selección interna) para que un deep link `eldraft://tab/...` pueda fijar
/// la pestaña activa desde `RootView`, igual que `initialTab` en
/// `MainScaffold`.
enum MainTab: Hashable {
    case organizo, juego, equipos, buscarCupo, perfil
}

/// Las 5 secciones de la barra de pestañas de Android (`MainScaffold.kt`), todas
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
    @Binding var teamsTabRequest: TeamsTab?
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
    let onOpenTeam: (String) -> Void
    let onEditProfile: () -> Void
    let onLoggedOut: () -> Void

    private var tabItems: [FloatingTabBarItem<MainTab>] {
        [
            .init(key: .organizo, label: String(localized: "tab_organizo"), systemImage: "sportscourt.fill", accessibilityId: "tab_organizo"),
            .init(key: .juego, label: String(localized: "tab_juego"), systemImage: "figure.run", accessibilityId: "tab_juego"),
            .init(key: .equipos, label: String(localized: "tab_equipos"), systemImage: "person.3.fill", accessibilityId: "tab_equipos"),
            .init(key: .buscarCupo, label: String(localized: "tab_buscar_cupo"), systemImage: "map.fill", accessibilityId: "tab_buscar_cupo"),
            .init(key: .perfil, label: String(localized: "tab_perfil"), systemImage: "person.fill", accessibilityId: "tab_perfil"),
        ]
    }

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
            .reservingFloatingTabBar()
            .toolbar(.hidden, for: .tabBar)
            .tag(MainTab.organizo)

            MyPostulationsView(
                onOpenQrScanner: { id in onOpenQrScanner(id, false) },
                onOpenQrGenerator: onOpenQrGenerator,
                onOpenRating: onOpenRating
            )
            .reservingFloatingTabBar()
            .toolbar(.hidden, for: .tabBar)
            .tag(MainTab.juego)

            TeamsView(tabRequest: $teamsTabRequest, onOpenTeam: onOpenTeam)
                .reservingFloatingTabBar()
                .toolbar(.hidden, for: .tabBar)
                .tag(MainTab.equipos)

            BuscarCupoView()
                .toolbar(.hidden, for: .tabBar)
                .tag(MainTab.buscarCupo)

            ProfileTabView(onEditProfile: onEditProfile, onLoggedOut: onLoggedOut)
                .reservingFloatingTabBar()
                .toolbar(.hidden, for: .tabBar)
                .tag(MainTab.perfil)
        }
        // La barra nativa muestra el título de las 5 pestañas; Android solo el
        // de la seleccionada. Se oculta (en cada pestaña: aplicarlo al TabView
        // no basta) y se dibuja `FloatingTabBar`.
        .overlay(alignment: .bottom) {
            FloatingTabBar(items: tabItems, selected: selection) { selection = $0 }
        }
        .tint(ElDraftColors.primary)
        .accessibilityIdentifier("main_tab_view")
    }
}
