import FirebaseAuth
import GoogleSignIn
import UIKit
import shared

/// Google Sign-In con GoogleSignIn-iOS, canjeado por una sesión de Firebase
/// Auth. Espejo de `GoogleAuthClient` (Android), que hace el mismo canje
/// pero partiendo de un ID token de Google obtenido vía Credential Manager
/// en vez del SDK de Google Sign-In: en ambos casos el backend recibe un ID
/// token de FIREBASE, nunca el de Google directamente, porque
/// `FirebaseAuth.verifyIdToken` solo acepta tokens propios.
enum GoogleSignInClientIOS {

    private enum SignInError: LocalizedError {
        case noPresentingViewController
        case missingIdToken

        var errorDescription: String? {
            switch self {
            case .noPresentingViewController:
                return "No se pudo iniciar sesión con Google."
            case .missingIdToken:
                return "Google no devolvió un token válido."
            }
        }
    }

    @MainActor
    static func signIn() async throws -> GoogleIdentity {
        guard let presenter = topViewController() else { throw SignInError.noPresentingViewController }

        let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
        guard let googleIdToken = result.user.idToken?.tokenString else { throw SignInError.missingIdToken }

        let credential = GoogleAuthProvider.credential(
            withIDToken: googleIdToken,
            accessToken: result.user.accessToken.tokenString
        )
        let authResult = try await Auth.auth().signIn(with: credential)
        let firebaseIdToken = try await authResult.user.getIDToken()

        return GoogleIdentity(
            idToken: firebaseIdToken,
            displayName: result.user.profile?.name,
            email: result.user.profile?.email,
            photoUrl: result.user.profile?.imageURL(withDimension: 200)?.absoluteString
        )
    }

    static func signOut() {
        GIDSignIn.sharedInstance.signOut()
    }

    static func friendlyMessage(_ error: Error) -> String {
        // kGIDSignInErrorCodeCanceled = -5 (GIDSignIn.h). El wrapper Swift de
        // este NS_ERROR_ENUM anida el código en GIDSignInError.Code, no como
        // un GIDSignInErrorCode de nivel superior; comparar el NSError crudo
        // evita depender del nombre exacto que genera el bridging.
        let nsError = error as NSError
        if nsError.domain == kGIDSignInErrorDomain, nsError.code == -5 {
            return "Inicio de sesión cancelado."
        }
        if let signInError = error as? SignInError {
            return signInError.errorDescription ?? "No se pudo iniciar sesión con Google."
        }
        return FirebaseAuthErrorMessage.friendly(error, fallback: "No se pudo iniciar sesión con Google.")
    }

    @MainActor
    private static func topViewController() -> UIViewController? {
        guard
            let scene = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .first(where: { $0.activationState == .foregroundActive }),
            var top = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController
        else { return nil }
        while let presented = top.presentedViewController { top = presented }
        return top
    }
}
