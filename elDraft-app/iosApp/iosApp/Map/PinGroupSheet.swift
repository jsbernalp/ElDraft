import SwiftUI
import shared

/// Sheet que aparece al tocar un grupo de pines (varias convocatorias en la
/// misma ubicación). Lista cada convocatoria; al elegir una se abre su
/// detalle vía `onSelect`. Espejo de `PinGroupSheet.kt`.
struct PinGroupSheet: View {
    let convocatories: [Convocatory]
    /// convocatoryId -> estado de mi postulación; ausente si no me postulé.
    let myPostulations: [String: String]
    let onSelect: (Convocatory) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .center, spacing: ElDraftSpacing.sm) {
                    Image(systemName: ConvocatoryIcons.place)
                        .font(.system(size: ElDraftSizes.iconLg))
                        .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))
                    Text(headerText)
                        .font(ElDraftTypography.titleMedium)
                        .foregroundStyle(ElDraftColors.onSurface)
                }
                .padding(.top, ElDraftSpacing.lg)

                VStack(spacing: ElDraftSpacing.sm) {
                    ForEach(convocatories, id: \.id) { convocatory in
                        GroupRow(
                            convocatory: convocatory,
                            postulationStatus: myPostulations[convocatory.id],
                            onTap: { onSelect(convocatory) }
                        )
                    }
                }
                .padding(.top, ElDraftSpacing.lg)
            }
            .padding(.horizontal, ElDraftSpacing.xl)
            .padding(.bottom, ElDraftSpacing.xxl)
        }
    }

    private var headerText: String {
        let address = convocatories.first?.addressText?.isEmpty == false
            ? convocatories.first!.addressText!
            : String(localized: "pin_group_default_address")
        let matches = String(format: String(localized: "matches_count"), convocatories.count)
        return String(format: String(localized: "pin_group_address_count"), address, matches)
    }
}

private struct GroupRow: View {
    let convocatory: Convocatory
    let postulationStatus: String?
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: ElDraftSpacing.xxs) {
                    Text(convocatory.format.isEmpty ? "Convocatoria" : convocatory.format)
                        .font(ElDraftTypography.bodyLarge)
                        .fontWeight(.semibold)
                        .foregroundStyle(ElDraftColors.onSurface)
                    Text(subtitle)
                        .font(ElDraftTypography.bodySmall)
                        .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))
                }
                Spacer()
                if let postulationStatus {
                    PostulationBadge(status: postulationStatus, compact: true)
                }
                Image(systemName: "chevron.right")
                    .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.disabled))
            }
            .padding(.horizontal, ElDraftSpacing.lg)
            .padding(.vertical, ElDraftSpacing.md)
            .background(ElDraftColors.surfaceVariant.opacity(ElDraftAlpha.disabled), in: ElDraftShapes.sm)
        }
        .buttonStyle(.plain)
    }

    private var subtitle: String {
        var parts: [String] = []
        if let schedule = formatSchedule(convocatory.scheduledAt) { parts.append(schedule) }
        parts.append("\(convocatory.slotsNeeded) cupos")
        parts.append(formatFee(convocatory.fee))
        return parts.joined(separator: " · ")
    }
}
