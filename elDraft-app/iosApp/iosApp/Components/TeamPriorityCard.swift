import SwiftUI
import shared

/// Card de "Juego" para un miembro del equipo cuyo organizador le dio
/// prioridad: "Voy" (abre el sheet de posición, solo con las que tienen cupo
/// libre) o "No puedo" (con confirmación, porque no se puede deshacer).
/// Espejo de `TeamPriorityCard.kt`.
struct TeamPriorityCard: View {
    let invite: TeamPriorityInvite
    let isResponding: Bool
    let onGo: (String) -> Void
    let onCant: () -> Void

    @State private var showPositions = false
    @State private var showCantConfirm = false

    var body: some View {
        let convocatory = invite.convocatory
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center) {
                ScheduleBanner(scheduledAt: convocatory.scheduledAt)
                Spacer()
                MinutesLeftView(endsAt: invite.windowEndsAt) { minutes in
                    if let minutes {
                        Text(String(format: String(localized: "team_priority_closes_in"), minutes))
                            .font(ElDraftTypography.labelMedium)
                            .fontWeight(.semibold)
                            .foregroundStyle(ElDraftColors.primary)
                    }
                }
            }
            Spacer().frame(height: ElDraftSpacing.md2)
            Text(String(format: String(localized: "team_priority_card_title"), invite.teamName))
                .font(ElDraftTypography.titleMedium)
                .fontWeight(.bold)
                .foregroundStyle(ElDraftColors.onSurface)
            Text(convocatory.format)
                .font(ElDraftTypography.bodyMedium)
                .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))
            Spacer().frame(height: ElDraftSpacing.md)
            HStack(spacing: ElDraftSpacing.sm) {
                Button { showPositions = true } label: {
                    if isResponding {
                        ProgressView().tint(ElDraftColors.onPrimary).frame(maxWidth: .infinity)
                    } else {
                        Text("team_priority_go").frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.borderedProminent)
                .tint(ElDraftColors.primary)
                // Sin cupos libres no hay nada que apartar: solo queda "No puedo".
                .disabled(isResponding || invite.freePositions.isEmpty)

                Button { showCantConfirm = true } label: {
                    Text("team_priority_cant").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .tint(ElDraftColors.primary)
                .disabled(isResponding)
            }
        }
        .padding(ElDraftSpacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ElDraftColors.surface, in: ElDraftShapes.md)
        .shadow(radius: ElDraftElevation.card)
        .sheet(isPresented: $showPositions) {
            PositionPickerSheet(invite: invite) { position in
                showPositions = false
                onGo(position)
            }
        }
        .alert(String(localized: "team_priority_cant_title"), isPresented: $showCantConfirm) {
            Button("action_back_short", role: .cancel) {}
            Button("team_priority_cant_confirm") { onCant() }
        } message: {
            Text("team_priority_cant_message")
        }
    }
}

/// Sheet de posición: solo las que aún tienen cupo libre, con cuántos quedan.
private struct PositionPickerSheet: View {
    let invite: TeamPriorityInvite
    let onConfirm: (String) -> Void

    @State private var selected: String?

    var body: some View {
        VStack(alignment: .leading, spacing: ElDraftSpacing.md) {
            Text("team_priority_position_title")
                .font(ElDraftTypography.titleLarge)
                .foregroundStyle(ElDraftColors.onSurface)
            FlowLayout(hSpacing: ElDraftSpacing.xs2, vSpacing: ElDraftSpacing.xs2) {
                ForEach(invite.freePositions, id: \.position) { slot in
                    TeamChip(
                        label: slot.position + " · "
                            + String(format: String(localized: "team_priority_free_slots"), Int(slot.slots)),
                        selected: selected == slot.position,
                        action: { selected = slot.position }
                    )
                }
            }
            Button {
                if let selected { onConfirm(selected) }
            } label: {
                Text("team_priority_position_confirm").frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(ElDraftColors.primary)
            .disabled(selected == nil)
            Spacer(minLength: 0)
        }
        .padding(ElDraftSpacing.xl)
        .background(ElDraftColors.background)
        .presentationDetents([.medium])
    }
}
