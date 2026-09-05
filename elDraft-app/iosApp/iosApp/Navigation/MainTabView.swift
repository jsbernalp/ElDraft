import SwiftUI

/// Las 4 secciones del `NavigationBar` de Android (`MainScaffold.kt`), como
/// placeholders. El acceso a `DebugHealthCheckView` vive en `RootView` (botón
/// flotante en Debug), no aquí: este tab bar solo existe con sesión (Home), y
/// el chequeo de salud del backend tiene que poder probarse antes de que
/// exista login (Fase 2).
struct MainTabView: View {
    var body: some View {
        TabView {
            PlaceholderScreenView(title: "Organizo")
                .tabItem { Label("Organizo", systemImage: "sportscourt.fill") }

            PlaceholderScreenView(title: "Juego")
                .tabItem { Label("Juego", systemImage: "figure.run") }

            PlaceholderScreenView(title: "Buscar Cupo")
                .tabItem { Label("Buscar Cupo", systemImage: "map.fill") }

            PlaceholderScreenView(title: "Perfil")
                .tabItem { Label("Perfil", systemImage: "person.fill") }
        }
        .tint(ElDraftColors.primary)
    }
}
