import Foundation
import Observation
import shared

/// Espejo de `AuthUiState` (`AuthViewModel.kt`, Android). `Success` lleva
/// `needsOnboarding` para que la vista decida a qué fase ir.
enum AuthUiState: Equatable {
    case idle
    case loading
    case success(needsOnboarding: Bool)
    case error(message: String)
}

/// Espejo de `AuthViewModel.kt`: orquesta login/registro por email y Google
/// invocando los use cases de `shared` (la lógica de negocio — intercambiar
/// el token de Firebase por la sesión del backend — vive ahí, no aquí).
/// No sincroniza token FCM (push queda diferido a la Fase 8).
@MainActor
@Observable
final class AuthViewModel {
    private(set) var state: AuthUiState = .idle

    private let signInWithGoogleUseCase = IosKoin.shared.signInWithGoogle
    private let signInWithEmailUseCase = IosKoin.shared.signInWithEmail

    func signInWithGoogle() {
        guard state != .loading else { return }
        state = .loading
        Task {
            do {
                let response = try await signInWithGoogleUseCase.invoke()
                state = .success(needsOnboarding: response.needsOnboarding)
            } catch {
                state = .error(message: friendlyMessage(error, fallback: "Error al iniciar sesión con Google"))
            }
        }
    }

    func signInWithEmail(email: String, password: String) {
        guard state != .loading else { return }
        state = .loading
        Task {
            do {
                let response = try await signInWithEmailUseCase.signIn(
                    email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                    password: password
                )
                state = .success(needsOnboarding: response.needsOnboarding)
            } catch {
                state = .error(message: friendlyMessage(error, fallback: "No se pudo iniciar sesión"))
            }
        }
    }

    func registerWithEmail(name: String, email: String, password: String) {
        guard state != .loading else { return }
        state = .loading
        Task {
            do {
                let response = try await signInWithEmailUseCase.signUp(
                    name: name.trimmingCharacters(in: .whitespacesAndNewlines),
                    email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                    password: password
                )
                state = .success(needsOnboarding: response.needsOnboarding)
            } catch {
                state = .error(message: friendlyMessage(error, fallback: "No se pudo crear la cuenta"))
            }
        }
    }

    func resetError() {
        if case .error = state { state = .idle }
    }

    /// `Throwable.userMessage(fallback)` de `shared` (mismo mecanismo que usa
    /// Android): usa el mensaje de `ApiException`/`KotlinException` cuando lo
    /// hay, o el fallback si no.
    private func friendlyMessage(_ error: Error, fallback: String) -> String {
        error.kotlinMessage(fallback: fallback)
    }
}
