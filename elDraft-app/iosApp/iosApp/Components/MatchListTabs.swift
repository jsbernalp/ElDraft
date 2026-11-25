import SwiftUI

/// Vista activa en las listas de partidos (Organizo y Juego). Espejo de
/// `MatchListTab` en `MatchListTabs.kt`.
enum MatchListTab: Equatable {
    case proximos
    case cerrados
}

/// Conmutador entre los partidos que siguen en juego y los cerrados. Control
/// segmentado propio (no el `Picker(.segmented)` nativo) para poder mostrar el
/// badge de pendientes dentro del segmento "Cerrados", que UIKit no soporta
/// bien en su control segmentado. Espejo de `MatchListTabs` en
/// `MatchListTabs.kt`.
///
/// [pendingCount] cuenta SOLO lo accionable, nunca los rechazos: un badge que
/// avisa de cosas que no requieren nada se aprende a ignorar.
struct MatchListTabsView: View {
    let selected: MatchListTab
    let pendingCount: Int
    let onSelect: (MatchListTab) -> Void

    var body: some View {
        HStack(spacing: ElDraftSpacing.xxs) {
            segment(String(localized: "section_upcoming"), tab: .proximos, badge: nil)
            segment(String(localized: "section_closed"), tab: .cerrados, badge: pendingCount > 0 ? pendingCount : nil)
        }
        .padding(ElDraftSpacing.xxs)
        .background(ElDraftColors.surfaceVariant, in: ElDraftShapes.field)
    }

    private func segment(_ title: String, tab: MatchListTab, badge: Int?) -> some View {
        let isSelected = selected == tab
        return Button {
            onSelect(tab)
        } label: {
            HStack(spacing: ElDraftSpacing.xs) {
                Text(title)
                    .font(ElDraftTypography.labelLarge)
                if let badge {
                    Text("\(badge)")
                        .font(ElDraftTypography.labelSmall)
                        .fontWeight(.semibold)
                        .foregroundStyle(ElDraftColors.onPrimary)
                        .padding(.horizontal, ElDraftSpacing.xs)
                        .background(ElDraftColors.primary, in: Capsule())
                        .accessibilityHidden(true)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, ElDraftSpacing.sm)
            .foregroundStyle(isSelected ? ElDraftColors.primary : ElDraftColors.onSurfaceVariant)
            .background(isSelected ? ElDraftColors.surface : Color.clear, in: ElDraftShapes.field)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(badge.map { "\(title), \($0)" } ?? title)
    }
}
