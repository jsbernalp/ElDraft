import Foundation
import Observation
import shared

/// Espejo de `CromoUiState` en `ProfileViewModel.kt` (Android): estado de
/// carga del Cromo de un jugador ajeno.
enum CromoUiState {
    case loading
    case loaded(PlayerProfile)
    case error(message: String)
}

/// Carga el Cromo de un jugador cualquiera (vista `PlayerCromoView`, abierta
/// al tocar un postulante en `ApplicantsView`). Espejo de la parte "cromo" de
/// `ProfileViewModel.kt` — el onboarding vive aparte, en
/// `OnboardingViewModel.swift`.
@MainActor
@Observable
final class PlayerCromoViewModel {
    private(set) var state: CromoUiState = .loading

    func load(playerId: String) {
        guard !playerId.isEmpty else { return }
        state = .loading
        Task {
            do {
                let profile = try await IosKoin.shared.profileRepository.getProfile(playerId: playerId)
                state = .loaded(profile)
            } catch {
                let fallback = "No se pudo cargar la ficha técnica"
                state = .error(message: error.kotlinMessage(fallback: fallback))
            }
        }
    }
}
