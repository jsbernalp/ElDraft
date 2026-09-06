import Foundation
import Observation
import shared

/// Espejo de `ProfileEditUiState` (`ProfileEditViewModel.kt`, Android).
struct ProfileEditUiState {
    var isLoading = true
    var isSaving = false
    /// Datos de cuenta (nombre, avatar, email, teléfono).
    var user: User? = nil
    /// Ficha técnica actual (puede ser `nil` si no hizo onboarding aún).
    var profile: PlayerProfile? = nil
    /// `true` una vez guardado OK — la vista debe volver atrás.
    var saved = false
    /// `true` cuando el logout fue exitoso — navegar a Login.
    var loggedOut = false
    /// `true` mientras se borra la cuenta; bloquea el diálogo para no repetir la llamada.
    var isDeleting = false
    var error: String? = nil
}

/// Espejo de `ProfileEditViewModel.kt`: carga y guarda la cuenta (nombre,
/// avatar, teléfono) + la ficha técnica del usuario autenticado, y orquesta
/// logout/borrado de cuenta. Sirve tanto a `ProfileTabView` (solo lectura +
/// acciones) como a `ProfileEditView` (formulario).
@MainActor
@Observable
final class ProfileEditViewModel {
    private(set) var state = ProfileEditUiState()

    func load() {
        state.isLoading = true
        state.error = nil
        Task {
            do {
                // `getMyAccount` corre en paralelo; un fallo al cargar la ficha
                // técnica (usuario sin onboarding) no debe tumbar la cuenta ya
                // cargada, por eso va con `try?` por separado — mismo motivo que
                // el `runCatching` de cada `async` en Android.
                async let accountTask = IosKoin.shared.getMyAccount.invoke()
                let userId = try? await IosKoin.shared.authRepository.currentUserId()
                var profile: PlayerProfile?
                if let userId {
                    profile = try? await IosKoin.shared.profileRepository.getProfile(playerId: userId)
                }
                let user = try await accountTask
                state.user = user
                state.profile = profile
                state.isLoading = false
            } catch {
                state.isLoading = false
                state.error = (error as? KotlinThrowable)?.userMessage(fallback: "No se pudo cargar tu perfil")
                    ?? "No se pudo cargar tu perfil"
            }
        }
    }

    /// Guarda nombre + avatar (cuenta) Y ficha técnica. Si la ficha es `nil`
    /// (usuario sin onboarding) solo guarda la cuenta.
    func save(
        name: String,
        avatarUrl: String?,
        phone: String?,
        positionPrimary: String,
        positionSecondary: String?,
        dominantFoot: String,
        height: Int?,
        build: String?
    ) {
        guard !state.isSaving else { return }
        state.isSaving = true
        state.error = nil
        Task {
            do {
                let trimmedAvatar = avatarUrl?.trimmingCharacters(in: .whitespacesAndNewlines)
                let updatedUser = try await IosKoin.shared.updateAccount.invoke(
                    name: name.trimmingCharacters(in: .whitespacesAndNewlines),
                    avatarUrl: (trimmedAvatar?.isEmpty ?? true) ? nil : trimmedAvatar
                )

                if let phone, !phone.trimmingCharacters(in: .whitespaces).isEmpty {
                    try? await IosKoin.shared.authRepository.updatePhone(phone: phone.trimmingCharacters(in: .whitespaces))
                }

                let userId = try? await IosKoin.shared.authRepository.currentUserId()
                var updatedProfile: PlayerProfile?
                if let userId {
                    let trimmedSecondary = positionSecondary?.isEmpty ?? true ? nil : positionSecondary
                    let trimmedBuild = build?.isEmpty ?? true ? nil : build
                    updatedProfile = try? await IosKoin.shared.profileRepository.updateProfile(
                        playerId: userId,
                        request: UpdateProfileRequest(
                            positionPrimary: positionPrimary,
                            positionSecondary: trimmedSecondary,
                            dominantFoot: dominantFoot,
                            height: height.map { KotlinInt(int: Int32($0)) },
                            build: trimmedBuild
                        )
                    )
                }

                state.isSaving = false
                state.saved = true
                state.user = updatedUser
                if let updatedProfile {
                    state.profile = updatedProfile
                }
            } catch {
                state.isSaving = false
                state.error = (error as? KotlinThrowable)?.userMessage(fallback: "No se pudo guardar el perfil")
                    ?? "No se pudo guardar el perfil"
            }
        }
    }

    func logout() {
        Task {
            do {
                try await IosKoin.shared.logout.invoke()
                state.loggedOut = true
            } catch {
                state.error = (error as? KotlinThrowable)?.userMessage(fallback: "No se pudo cerrar la sesión")
                    ?? "No se pudo cerrar la sesión"
            }
        }
    }

    /// Borra la cuenta. Irreversible: la vista debe pedir confirmación antes.
    /// Reutiliza `loggedOut` para navegar porque el destino es el mismo
    /// (Login) y el usuario ya no tiene sesión en ninguno de los dos casos.
    func deleteAccount() {
        state.isDeleting = true
        state.error = nil
        Task {
            do {
                try await IosKoin.shared.deleteAccount.invoke()
                state.isDeleting = false
                state.loggedOut = true
            } catch {
                // Si falla, la cuenta sigue viva y la sesión intacta.
                state.isDeleting = false
                state.error = (error as? KotlinThrowable)?.userMessage(fallback: "No se pudo borrar la cuenta. Tu cuenta sigue activa.")
                    ?? "No se pudo borrar la cuenta. Tu cuenta sigue activa."
            }
        }
    }

    func clearError() { state.error = nil }
    func clearSaved() { state.saved = false }
}
