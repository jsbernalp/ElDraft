import SwiftUI
import shared

/// Ficha técnica inicial (teléfono, posición, pierna hábil, altura,
/// contextura). Espejo de `OnboardingProfileScreen.kt`. Posición principal y
/// pierna hábil son obligatorias; el resto es opcional — igual que Android.
struct OnboardingProfileView: View {
    let onProfileComplete: () -> Void

    @State private var viewModel = OnboardingViewModel()
    @State private var phone = ""
    @State private var positionPrimary = ""
    @State private var positionSecondary = ""
    @State private var dominantFoot = ""
    @State private var height = ""
    @State private var build = ""
    @State private var errorMessage: String?

    private let positions = DomainCatalogs.shared.POSITIONS
    private let feet = DomainCatalogs.shared.FEET
    private let builds = DomainCatalogs.shared.BUILDS

    private var isSaving: Bool { viewModel.state == .saving }
    private var canSave: Bool { !positionPrimary.isEmpty && !dominantFoot.isEmpty && !isSaving }

    private var heightBinding: Binding<String> {
        Binding(
            get: { height },
            set: { height = String($0.filter(\.isNumber).prefix(3)) }
        )
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ElDraftSpacing.lg) {
                ScreenHeader(
                    title: String(localized: "profile_header_title"),
                    subtitle: String(localized: "profile_header_subtitle")
                )

                ElDraftTextField(
                    label: String(localized: "profile_phone_label"),
                    text: $phone,
                    keyboardType: .phonePad
                )

                DropdownField(
                    label: String(localized: "profile_position_primary_label"),
                    options: positions,
                    selected: $positionPrimary,
                    accessibilityId: "onboarding_position_primary_field"
                )

                DropdownField(
                    label: String(localized: "profile_position_secondary_label"),
                    options: positions,
                    selected: $positionSecondary,
                    accessibilityId: "onboarding_position_secondary_field"
                )

                DropdownField(
                    label: String(localized: "profile_dominant_foot_label"),
                    options: feet,
                    selected: $dominantFoot,
                    accessibilityId: "onboarding_dominant_foot_field"
                )

                ElDraftTextField(
                    label: String(localized: "profile_height_label"),
                    text: heightBinding,
                    keyboardType: .numberPad
                )

                DropdownField(
                    label: String(localized: "profile_build_label"),
                    options: builds,
                    selected: $build
                )

                if let errorMessage {
                    Text(errorMessage)
                        .font(ElDraftTypography.labelMedium)
                        .foregroundStyle(ElDraftColors.error)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                saveButton
            }
            .padding(ElDraftSpacing.xl)
        }
        .background(ElDraftColors.background)
        .scrollDismissesKeyboard(.interactively)
        .onChange(of: viewModel.state) { _, newValue in
            switch newValue {
            case .saving:
                errorMessage = nil
            case .saved:
                onProfileComplete()
            case .error(let message):
                errorMessage = message
                viewModel.resetError()
            case .idle:
                break
            }
        }
    }

    private var saveButton: some View {
        Button {
            viewModel.saveProfile(
                positionPrimary: positionPrimary,
                positionSecondary: positionSecondary,
                dominantFoot: dominantFoot,
                height: Int(height),
                build: build,
                phone: phone
            )
        } label: {
            Group {
                if isSaving {
                    ProgressView().tint(ElDraftColors.onPrimary)
                } else {
                    Text("profile_save_continue")
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, ElDraftSpacing.md)
        }
        .background(
            canSave ? ElDraftColors.primary : ElDraftColors.primary.opacity(ElDraftAlpha.disabled),
            in: ElDraftShapes.pill
        )
        .foregroundStyle(ElDraftColors.onPrimary)
        .disabled(!canSave)
        .accessibilityIdentifier("onboarding_save_button")
    }
}
