import SwiftUI
import shared

/// Formulario del nombre de un equipo con el contador `x/40`; lo comparten
/// "Crear equipo" y "Renombrar". Espejo de `TeamNameField` + los diálogos de
/// `TeamsScreen.kt` / `TeamDetailScreen.kt`. En iOS va como sheet corto en vez
/// de `AlertDialog`: un `alert` no puede mostrar el contador ni el error.
struct TeamNameSheet: View {
    let title: String
    let confirmLabel: String
    @Binding var name: String
    let isBusy: Bool
    let canSubmit: Bool
    /// Fallo de la última operación. Se muestra aquí y no en el toast de la
    /// pantalla: el sheet va encima y lo taparía.
    var error: String? = nil
    let onConfirm: () -> Void
    let onCancel: () -> Void

    private var length: Int { TeamRules.shared.normalizeName(raw: name).count }
    private var maxLength: Int { Int(TeamRules.shared.NAME_MAX_LENGTH) }
    private var tooLong: Bool { length > maxLength }

    var body: some View {
        VStack(alignment: .leading, spacing: ElDraftSpacing.lg) {
            Text(title)
                .font(ElDraftTypography.titleLarge)
                .foregroundStyle(ElDraftColors.onSurface)

            VStack(alignment: .leading, spacing: ElDraftSpacing.xs) {
                Text("team_name_label")
                    .font(ElDraftTypography.labelMedium)
                    .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))
                TextField("", text: $name)
                    .disabled(isBusy)
                    .submitLabel(.done)
                    .onSubmit { if canSubmit { onConfirm() } }
                    .padding(.horizontal, ElDraftSpacing.md)
                    .padding(.vertical, ElDraftSpacing.sm)
                    .background(ElDraftColors.surface, in: ElDraftShapes.field)
                    .overlay(
                        ElDraftShapes.field.stroke(
                            tooLong ? ElDraftColors.error : ElDraftColors.outline,
                            lineWidth: ElDraftSizes.borderHairline
                        )
                    )
                HStack {
                    if tooLong {
                        Text(String(format: String(localized: "team_name_invalid"), maxLength))
                            .foregroundStyle(ElDraftColors.error)
                    }
                    Spacer()
                    Text(String(format: String(localized: "team_name_counter"), length, maxLength))
                        .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))
                }
                .font(ElDraftTypography.bodySmall)
            }

            if let error {
                Text(error)
                    .font(ElDraftTypography.bodyMedium)
                    .foregroundStyle(ElDraftColors.error)
            }

            HStack(spacing: ElDraftSpacing.md) {
                Button(action: onCancel) {
                    Text("action_cancel").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(isBusy)

                Button(action: onConfirm) {
                    if isBusy {
                        ProgressView().frame(maxWidth: .infinity)
                    } else {
                        Text(confirmLabel).frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(!canSubmit)
            }
            .tint(ElDraftColors.primary)
        }
        .padding(ElDraftSpacing.xl)
        .background(ElDraftColors.background)
        .interactiveDismissDisabled(isBusy)
        .presentationDetents([.height(340)])
    }
}
