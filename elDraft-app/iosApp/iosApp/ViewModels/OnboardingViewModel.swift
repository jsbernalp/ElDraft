import Foundation
import Observation
import shared

/// Espejo de `OnboardingUiState` (`ProfileViewModel.kt`, Android).
enum OnboardingUiState: Equatable {
    case idle
    case saving
    case saved
    case error(message: String)
}

/// Espejo de la parte de onboarding de `ProfileViewModel.kt`: guarda la
/// ficha técnica (posición, pierna hábil, altura, contextura) y el teléfono
/// a través de `SaveProfileUseCase`, que ya orquesta ambas llamadas de red.
@MainActor
@Observable
final class OnboardingViewModel {
    private(set) var state: OnboardingUiState = .idle

    private let saveProfileUseCase = IosKoin.shared.saveProfile

    func saveProfile(
        positionPrimary: String,
        positionSecondary: String,
        dominantFoot: String,
        height: Int?,
        build: String,
        phone: String
    ) {
        guard state != .saving else { return }
        state = .saving
        Task {
            do {
                _ = try await saveProfileUseCase.invoke(input: SaveProfileInput(
                    positionPrimary: positionPrimary,
                    positionSecondary: positionSecondary,
                    dominantFoot: dominantFoot,
                    height: height.map { KotlinInt(int: Int32($0)) },
                    build: build,
                    phone: phone
                ))
                state = .saved
            } catch {
                let fallback = "No se pudo guardar tu ficha técnica"
                state = .error(message: (error as? KotlinThrowable)?.userMessage(fallback: fallback) ?? fallback)
            }
        }
    }

    func resetError() {
        if case .error = state { state = .idle }
    }
}
