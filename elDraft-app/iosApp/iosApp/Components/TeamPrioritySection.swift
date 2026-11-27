import SwiftUI
import shared

/// Chip seleccionable (`FilterChip` de Material 3), con estado deshabilitado.
struct TeamChip: View {
    let label: String
    let selected: Bool
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(ElDraftTypography.labelLarge)
                .padding(.horizontal, ElDraftSpacing.md)
                .padding(.vertical, ElDraftSpacing.sm)
                .foregroundStyle(selected ? ElDraftColors.onPrimaryContainer : ElDraftColors.onSurface)
                .background(selected ? ElDraftColors.primaryContainer : ElDraftColors.surface, in: ElDraftShapes.sm)
                .overlay(ElDraftShapes.sm.stroke(
                    selected ? ElDraftColors.primary : ElDraftColors.outline,
                    lineWidth: ElDraftSizes.borderHairline
                ))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : ElDraftAlpha.disabled)
    }
}

/// Sección opcional "Avisar primero a mi equipo" del formulario de
/// convocatoria: interruptor, selector de equipo y chips de ventana. De
/// presentación pura: el estado y la validez de cada ventana los decide quien
/// la usa. Espejo de `TeamPrioritySection.kt`.
struct TeamPrioritySection: View {
    let teams: [Team]
    let enabled: Bool
    let selectedTeamId: String?
    let selectedWindow: Int?
    let windowOptions: [TeamWindowOption]
    let onEnabledChange: (Bool) -> Void
    let onSelectTeam: (String) -> Void
    let onSelectWindow: (Int) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 0) {
                    Text("team_priority_section_title")
                        .font(ElDraftTypography.titleMedium)
                        .fontWeight(.semibold)
                        .foregroundStyle(ElDraftColors.onBackground)
                    Text("team_priority_section_hint")
                        .font(ElDraftTypography.bodySmall)
                        .foregroundStyle(ElDraftColors.onBackground.opacity(ElDraftAlpha.textSecondary))
                }
                Spacer(minLength: ElDraftSpacing.md)
                Toggle("", isOn: Binding(get: { enabled }, set: onEnabledChange))
                    .labelsHidden()
                    .tint(ElDraftColors.primary)
            }

            if enabled {
                Spacer().frame(height: ElDraftSpacing.md)
                label("team_priority_team_label")
                FlowLayout(hSpacing: ElDraftSpacing.xs2, vSpacing: ElDraftSpacing.xs2) {
                    ForEach(teams, id: \.id) { team in
                        TeamChip(label: team.name, selected: team.id == selectedTeamId) { onSelectTeam(team.id) }
                    }
                }

                Spacer().frame(height: ElDraftSpacing.md)
                label("team_priority_window_label")
                FlowLayout(hSpacing: ElDraftSpacing.xs2, vSpacing: ElDraftSpacing.xs2) {
                    ForEach(windowOptions, id: \.minutes) { option in
                        TeamChip(
                            label: TeamWindowTiming.label(minutes: Int(option.minutes)),
                            selected: Int(option.minutes) == selectedWindow,
                            // Deshabilitada: la ventana no cerraría 1 h antes del partido.
                            enabled: option.enabled
                        ) { onSelectWindow(Int(option.minutes)) }
                    }
                }

                Spacer().frame(height: ElDraftSpacing.sm)
                if !windowOptions.contains(where: { $0.enabled }) {
                    Text("team_priority_too_close")
                        .font(ElDraftTypography.bodySmall)
                        .foregroundStyle(ElDraftColors.error)
                } else if let selectedWindow {
                    Text(String(format: String(localized: "team_priority_summary"), TeamWindowTiming.label(minutes: selectedWindow)))
                        .font(ElDraftTypography.bodySmall)
                        .foregroundStyle(ElDraftColors.onBackground.opacity(ElDraftAlpha.textSecondary))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func label(_ key: String.LocalizationValue) -> some View {
        Text(String(localized: key))
            .font(ElDraftTypography.labelLarge)
            .foregroundStyle(ElDraftColors.onBackground)
            .padding(.bottom, ElDraftSpacing.xs)
    }
}
