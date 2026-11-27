import SwiftUI

/// Una pestaña del `FloatingTabBar`. `key` identifica la sección; `label` ya
/// llega resuelto. Espejo de `FloatingTabBarItem` en `FloatingTabBar.kt`.
struct FloatingTabBarItem<Key: Hashable>: Identifiable {
    let key: Key
    let label: String
    let systemImage: String
    var accessibilityId: String = ""

    var id: Key { key }
}

/// Barra de pestañas flotante: un pill de cristal (superficie translúcida con
/// borde de luz) despegado de los bordes, con sombra. Solo la pestaña
/// seleccionada es más ancha y muestra su título; las demás llevan solo el
/// ícono, para que quepan cinco sin apretarse. Espejo de `FloatingTabBar` en
/// `FloatingTabBar.kt`.
///
/// Reemplaza a la barra nativa de `TabView` (que muestra el título de todas):
/// el `TabView` se queda para conservar el estado de cada pestaña y su barra se
/// oculta. La barra se dibuja ENCIMA del contenido (como en Android, donde el
/// mapa pasa por debajo), así que cada pestaña tiene que reservarse el hueco
/// con `reservingFloatingTabBar()` — si no, la última card o el FAB quedan
/// debajo del pill.
struct FloatingTabBar<Key: Hashable>: View {
    let items: [FloatingTabBarItem<Key>]
    let selected: Key
    let onSelect: (Key) -> Void

    /// Cuánto más ancha es la pestaña seleccionada respecto a las demás.
    private let selectedWeight: CGFloat = 1.9

    var body: some View {
        GeometryReader { proxy in
            let inner = proxy.size.width - ElDraftSpacing.xs * 2
            let unit = inner / (CGFloat(items.count - 1) + selectedWeight)
            HStack(spacing: 0) {
                ForEach(items) { item in
                    let isSelected = item.key == selected
                    tab(item, isSelected: isSelected)
                        .frame(width: unit * (isSelected ? selectedWeight : 1))
                }
            }
            .padding(.horizontal, ElDraftSpacing.xs)
            .frame(maxHeight: .infinity)
        }
        .frame(height: ElDraftSizes.tabBarHeight)
        .background(ElDraftColors.surface.opacity(ElDraftAlpha.glass), in: ElDraftShapes.pill)
        // Borde de cristal: el canto de arriba atrapa la luz y el de abajo se
        // funde en una línea tenue.
        .overlay(
            ElDraftShapes.pill.stroke(
                LinearGradient(
                    colors: [ElDraftColors.surface, ElDraftColors.onSurface.opacity(ElDraftAlpha.hairline)],
                    startPoint: .top, endPoint: .bottom
                ),
                lineWidth: ElDraftSizes.borderHairline
            )
        )
        .shadow(radius: ElDraftElevation.overlay)
        .padding(.horizontal, ElDraftSpacing.lg)
        .padding(.bottom, ElDraftSpacing.xs)
        .animation(.easeInOut(duration: 0.2), value: selected)
    }

    private func tab(_ item: FloatingTabBarItem<Key>, isSelected: Bool) -> some View {
        Button {
            onSelect(item.key)
        } label: {
            VStack(spacing: ElDraftSpacing.xxs) {
                Image(systemName: item.systemImage)
                    .font(.system(size: ElDraftSizes.iconLg))
                if isSelected {
                    Text(item.label)
                        .font(ElDraftTypography.labelMedium)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
            }
            .foregroundStyle(isSelected ? ElDraftColors.primary : ElDraftColors.onSurfaceVariant)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(isSelected ? ElDraftColors.primaryContainer : Color.clear, in: ElDraftShapes.pill)
            .padding(.vertical, ElDraftSpacing.sm)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        // Sin título visible el ícono es el único nombre que lee VoiceOver.
        .accessibilityLabel(item.label)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
        .accessibilityIdentifier(item.accessibilityId)
    }
}

extension View {
    /// Deja libre al fondo el espacio que ocupa el `FloatingTabBar` (alto del
    /// pill + su margen inferior). Espejo de `floatingTabBarInset()`.
    func reservingFloatingTabBar() -> some View {
        safeAreaInset(edge: .bottom, spacing: 0) {
            Color.clear.frame(height: ElDraftSizes.tabBarHeight + ElDraftSpacing.xs)
        }
    }
}
