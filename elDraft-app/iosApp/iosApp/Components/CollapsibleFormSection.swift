import SwiftUI

/// Sección plegable de un formulario por pasos. Muestra un check verde cuando
/// la sección es válida (`isComplete`); al colapsar, deja a la vista un
/// `summary` del dato ya ingresado. El plegado lo coordina la pantalla (solo
/// una abierta a la vez) vía `expanded`/`onHeaderClick`. Espejo de
/// `CollapsibleFormSection.kt`.
struct CollapsibleFormSection<Content: View>: View {
    let title: String
    let expanded: Bool
    let isComplete: Bool
    var summary: String? = nil
    let onHeaderClick: () -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(spacing: 0) {
            Button(action: onHeaderClick) {
                HStack(alignment: .center, spacing: ElDraftSpacing.md) {
                    statusBadge
                    VStack(alignment: .leading, spacing: ElDraftSpacing.xxs) {
                        Text(title)
                            .font(ElDraftTypography.titleSmall)
                            .fontWeight(.semibold)
                            .foregroundStyle(ElDraftColors.onSurface)
                        // El resumen solo se ve cuando la sección está colapsada.
                        if !expanded, let summary, !summary.isEmpty {
                            Text(summary)
                                .font(ElDraftTypography.bodySmall)
                                .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textTertiary))
                                .lineLimit(1)
                                .truncationMode(.tail)
                        }
                    }
                    Spacer()
                    Image(systemName: "chevron.down")
                        .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textMuted))
                        .rotationEffect(.degrees(expanded ? 180 : 0))
                }
                .padding(ElDraftSpacing.lg)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if expanded {
                VStack(alignment: .leading, spacing: 0) {
                    content()
                }
                .padding(.horizontal, ElDraftSpacing.lg)
                .padding(.bottom, ElDraftSpacing.lg)
            }
        }
        .background(ElDraftColors.surface, in: ElDraftShapes.md)
        .shadow(radius: ElDraftElevation.cardRaised)
        .animation(.easeInOut(duration: 0.2), value: expanded)
    }

    /// Círculo de estado: check verde si está completa, punto neutro si no.
    /// 24pt no corresponde a un token de tamaño de ícono (es el diámetro del
    /// círculo, no de un ícono suelto); espejo del `24.dp` literal de
    /// `StatusBadge` en Android.
    private var statusBadge: some View {
        ZStack {
            Circle()
                .fill(isComplete ? ElDraftColors.success : ElDraftColors.surfaceVariant)
                .frame(width: 24, height: 24)
            if isComplete {
                Image(systemName: "checkmark")
                    .font(.system(size: ElDraftSpacing.lg, weight: .bold))
                    .foregroundStyle(ElDraftColors.onSuccess)
            }
        }
        .accessibilityLabel(isComplete ? String(localized: "section_completed_content_description") : "")
    }
}
