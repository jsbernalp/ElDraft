import SwiftUI
import shared

/// Vista de lista de "Buscar Cupo": las mismas convocatorias del mapa, como
/// tarjetas escaneables verticalmente, ordenadas por hora del partido.
/// Espejo de `ConvocatoryListContent.kt`.
struct ConvocatoryListView: View {
    let pins: [Convocatory]
    let isLoading: Bool
    let hasLoadedOnce: Bool
    let myPostulations: [String: String]
    let onSelect: (Convocatory) -> Void
    let onRefresh: () async -> Void

    var body: some View {
        if !hasLoadedOnce && pins.isEmpty {
            LoadingState()
        } else if pins.isEmpty {
            ScrollView {
                EmptyState(
                    title: String(localized: "search_empty_title"),
                    message: String(localized: "search_empty_message"),
                    icon: "⚽"
                )
                .frame(minHeight: 400)
            }
            .refreshable { await onRefresh() }
        } else {
            ScrollView {
                LazyVStack(spacing: ElDraftSpacing.md) {
                    ForEach(pins.sorted(by: { $0.scheduledAt < $1.scheduledAt }), id: \.id) { convocatory in
                        ConvocatoryListCard(
                            convocatory: convocatory,
                            postulationStatus: myPostulations[convocatory.id],
                            onTap: { onSelect(convocatory) }
                        )
                    }
                }
                .padding(ElDraftSpacing.lg)
            }
            .refreshable { await onRefresh() }
        }
    }
}

/// Tarjeta completa de una convocatoria: hora, formato, cupos, cuota,
/// dirección, ambiente y posiciones. Espejo de `ConvocatoryListCard`.
private struct ConvocatoryListCard: View {
    let convocatory: Convocatory
    let postulationStatus: String?
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .center) {
                    if let schedule = formatSchedule(convocatory.scheduledAt) {
                        HStack(spacing: ElDraftSpacing.xs2) {
                            Image(systemName: "calendar")
                                .font(.system(size: ElDraftSpacing.lg))
                                .foregroundStyle(ElDraftColors.primary)
                            Text(schedule)
                                .font(ElDraftTypography.labelLarge)
                                .fontWeight(.semibold)
                                .foregroundStyle(ElDraftColors.primary)
                        }
                    }
                    Spacer()
                    if let postulationStatus {
                        PostulationBadge(status: postulationStatus)
                    }
                }

                Spacer().frame(height: ElDraftSpacing.sm)

                Text(convocatory.format.isEmpty ? "Convocatoria" : convocatory.format)
                    .font(ElDraftTypography.titleMedium)
                    .fontWeight(.bold)
                    .foregroundStyle(ElDraftColors.onSurface)

                if let address = convocatory.addressText, !address.isEmpty {
                    Spacer().frame(height: ElDraftSpacing.xs2)
                    MetaItem(icon: ConvocatoryIcons.place, text: address)
                }

                Spacer().frame(height: ElDraftSpacing.md2)
                HStack(spacing: ElDraftSpacing.lg) {
                    MetaItem(icon: ConvocatoryIcons.groups, text: "\(convocatory.displaySlots()) cupos")
                    MetaItem(icon: ConvocatoryIcons.fee, text: formatFee(convocatory.fee))
                    if !convocatory.ambiente.isEmpty {
                        MetaItem(icon: ConvocatoryIcons.groups, text: convocatory.ambiente)
                    }
                }

                if !positionChips.isEmpty {
                    Spacer().frame(height: ElDraftSpacing.md)
                    FlowLayout(hSpacing: ElDraftSpacing.sm, vSpacing: ElDraftSpacing.sm) {
                        ForEach(positionChips, id: \.self) { PositionChip(text: $0) }
                    }
                }
            }
            .padding(ElDraftSpacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(ElDraftColors.surface, in: ElDraftShapes.md)
            .shadow(color: .black.opacity(0.08), radius: ElDraftElevation.card) // design-tokens-ignore: sombra de card (M3 no tiene equivalente directo en SwiftUI)
        }
        .buttonStyle(.plain)
    }

    private var positionChips: [String] {
        if !convocatory.positionSlots.isEmpty {
            return convocatory.positionSlots.map { "\($0.position) ×\($0.slots)" }
        }
        if !convocatory.positionRequired.isEmpty {
            return [convocatory.positionRequired]
        }
        return []
    }
}

private struct PositionChip: View {
    let text: String

    var body: some View {
        Text(text)
            .font(ElDraftTypography.labelMedium)
            .fontWeight(.semibold)
            .foregroundStyle(ElDraftColors.primary)
            .padding(.horizontal, ElDraftSpacing.md2)
            .padding(.vertical, ElDraftSpacing.xs)
            .background(ElDraftColors.primary.opacity(ElDraftAlpha.containerSoft), in: ElDraftShapes.pill)
    }
}
