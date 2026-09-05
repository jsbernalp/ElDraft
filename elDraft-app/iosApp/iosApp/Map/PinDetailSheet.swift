import SwiftUI
import shared

/// Sheet con el detalle de una convocatoria al tocar su pin o una fila del
/// grupo. El botón "Postularme" envía la postulación y refleja el estado.
/// Espejo de `PinDetailSheet.kt`.
struct PinDetailSheet: View {
    let convocatory: Convocatory
    var postulationStatus: String? = nil
    var onApplied: () -> Void = {}

    @State private var viewModel = ApplyViewModel()
    @State private var selectedPosition: String?
    @State private var errorToast: String?

    private var alreadyApplied: Bool { postulationStatus != nil }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                header
                addressRow
                Spacer().frame(height: ElDraftSpacing.lg2)
                infoPills
                positionsSection
                Spacer().frame(height: ElDraftSpacing.xl)
                applyButton
            }
            .padding(.horizontal, ElDraftSpacing.xl)
            .padding(.top, ElDraftSpacing.lg)
            .padding(.bottom, ElDraftSpacing.xxl)
        }
        .toast(message: $errorToast)
        .onAppear {
            selectedPosition = convocatory.positionSlots.count == 1 ? convocatory.positionSlots.first?.position : nil
            viewModel.reset()
        }
        .onChange(of: viewModel.state) { _, newValue in
            switch newValue {
            case .applied:
                viewModel.reset()
                onApplied()
            case .error(let message):
                errorToast = message
                viewModel.reset()
            default:
                break
            }
        }
    }

    private var header: some View {
        HStack(alignment: .center) {
            Text(convocatory.format.isEmpty ? "Convocatoria" : convocatory.format)
                .font(ElDraftTypography.headlineMedium)
                .foregroundStyle(ElDraftColors.onSurface)
            Spacer()
            if !convocatory.ambiente.isEmpty {
                AmbienteChip(text: convocatory.ambiente)
            }
        }
    }

    @ViewBuilder
    private var addressRow: some View {
        if let address = convocatory.addressText, !address.isEmpty {
            HStack(spacing: ElDraftSpacing.xs2) {
                Image(systemName: ConvocatoryIcons.place)
                    .font(.system(size: ElDraftSizes.iconMd))
                    .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textTertiary))
                Text(address)
                    .font(ElDraftTypography.bodyMedium)
                    .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textSecondary))
            }
            .padding(.top, ElDraftSpacing.sm)
        }
    }

    private var infoPills: some View {
        HStack(spacing: ElDraftSpacing.md2) {
            InfoPill(icon: ConvocatoryIcons.groups, value: "\(convocatory.slotsNeeded)", label: "cupos")
            InfoPill(icon: ConvocatoryIcons.fee, value: formatFee(convocatory.fee), label: "cuota")
            if let schedule = formatSchedule(convocatory.scheduledAt) {
                let parts = schedule.components(separatedBy: " · ")
                InfoPill(icon: "calendar", value: parts.count > 1 ? parts[1] : schedule, label: parts.first ?? schedule)
            }
        }
    }

    @ViewBuilder
    private var positionsSection: some View {
        if !convocatory.positionSlots.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                Text(alreadyApplied ? String(localized: "pin_positions") : String(localized: "pin_which_position"))
                    .font(ElDraftTypography.titleSmall)
                    .fontWeight(.semibold)
                    .foregroundStyle(ElDraftColors.onSurface)
                    .padding(.top, ElDraftSpacing.lg2)
                    .padding(.bottom, ElDraftSpacing.md2)
                FlowLayout(hSpacing: ElDraftSpacing.sm, vSpacing: ElDraftSpacing.sm) {
                    ForEach(convocatory.positionSlots, id: \.position) { slot in
                        SelectableChip(
                            label: String(format: String(localized: "pin_position_slots"), slot.position, slot.slots),
                            selected: !alreadyApplied && selectedPosition == slot.position,
                            enabled: !alreadyApplied,
                            action: { selectedPosition = slot.position }
                        )
                    }
                }
            }
        } else if !convocatory.positionRequired.isEmpty {
            InfoPill(icon: ConvocatoryIcons.groups, value: convocatory.positionRequired, label: String(localized: "pin_required_position"))
                .frame(maxWidth: .infinity)
                .padding(.top, ElDraftSpacing.lg2)
        }
    }

    private var applyButton: some View {
        let isSending = viewModel.state == .sending
        return Button {
            if let position = selectedPosition {
                viewModel.apply(convocatoryId: convocatory.id, position: position)
            }
        } label: {
            Group {
                if alreadyApplied {
                    Text(statusLabel)
                } else if isSending {
                    ProgressView().tint(ElDraftColors.onPrimary)
                } else if selectedPosition == nil {
                    Text(String(localized: "pin_choose_position"))
                } else {
                    Text(String(format: String(localized: "postulation_apply_as"), selectedPosition!))
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, ElDraftSpacing.md)
        }
        .buttonStyle(.borderedProminent)
        .tint(ElDraftColors.primary)
        .disabled(alreadyApplied || isSending || selectedPosition == nil)
    }

    private var statusLabel: String {
        switch postulationStatus {
        case "approved": return String(localized: "postulation_approved")
        case "rejected": return String(localized: "postulation_rejected")
        case "cancelled": return String(localized: "postulation_cancelled")
        default: return String(localized: "postulation_already")
        }
    }
}

private struct InfoPill: View {
    let icon: String
    let value: String
    let label: String

    var body: some View {
        VStack(spacing: ElDraftSpacing.xs) {
            Image(systemName: icon)
                .font(.system(size: ElDraftSizes.iconLg))
                .foregroundStyle(ElDraftColors.primary)
            Text(value)
                .font(ElDraftTypography.titleMedium)
                .fontWeight(.bold)
                .foregroundStyle(ElDraftColors.onSurface)
            Text(label)
                .font(ElDraftTypography.labelSmall)
                .foregroundStyle(ElDraftColors.onSurface.opacity(ElDraftAlpha.textTertiary))
        }
        .padding(.vertical, ElDraftSpacing.md)
        .padding(.horizontal, ElDraftSpacing.sm)
        .frame(maxWidth: .infinity)
        .background(ElDraftColors.surfaceVariant.opacity(ElDraftAlpha.textMuted), in: ElDraftShapes.field)
    }
}

private struct AmbienteChip: View {
    let text: String

    var body: some View {
        Text(text)
            .font(ElDraftTypography.labelMedium)
            .fontWeight(.semibold)
            .foregroundStyle(ElDraftColors.primary)
            .padding(.horizontal, ElDraftSpacing.md)
            .padding(.vertical, ElDraftSpacing.xs2)
            .background(ElDraftColors.primary.opacity(ElDraftAlpha.container), in: ElDraftShapes.pill)
    }
}

/// Chip seleccionable para elegir la posición al postularse. Equivalente al
/// `FilterChip` de Material3 que usa Android.
private struct SelectableChip: View {
    let label: String
    let selected: Bool
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(ElDraftTypography.labelLarge)
                .foregroundStyle(
                    selected ? ElDraftColors.onPrimaryContainer
                        : ElDraftColors.onSurface.opacity(enabled ? 1 : ElDraftAlpha.disabled)
                )
                .padding(.horizontal, ElDraftSpacing.md2)
                .padding(.vertical, ElDraftSpacing.xs)
                .background(
                    selected ? ElDraftColors.primaryContainer : Color.clear,
                    in: ElDraftShapes.pill
                )
                .overlay(
                    ElDraftShapes.pill.stroke(ElDraftColors.outline, lineWidth: selected ? 0 : ElDraftSizes.borderHairline)
                )
        }
        .disabled(!enabled)
    }
}

extension Convocatory: @retroactive Identifiable {}
