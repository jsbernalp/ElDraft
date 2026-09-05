import FirebaseAuth

/// Espejo de `EmailAuthClient.friendlyMessage` (Android): traduce los códigos
/// de error de Firebase Auth a mensajes en español. Compartido entre
/// `EmailAuthClientIOS` y `GoogleSignInClientIOS` porque el canje de token de
/// Google también termina en una llamada a Firebase Auth que puede fallar
/// con los mismos códigos (red, demasiados intentos, etc.).
///
/// A diferencia de Android, acá el código llega tipado (`AuthErrorCode`) en
/// vez de tener que parsear el texto crudo del mensaje.
enum FirebaseAuthErrorMessage {
    static func friendly(_ error: Error, fallback: String = "Error al autenticar. Intenta de nuevo.") -> String {
        guard let code = AuthErrorCode(rawValue: (error as NSError).code) else { return fallback }
        switch code {
        case .emailAlreadyInUse:
            return "Ya existe una cuenta con ese correo."
        case .weakPassword:
            return "La contraseña es muy débil. Usa al menos 6 caracteres."
        case .invalidEmail:
            return "El correo no tiene un formato válido."
        case .userNotFound:
            return "No encontramos una cuenta con ese correo."
        case .wrongPassword, .invalidCredential:
            return "Correo o contraseña incorrectos."
        case .networkError:
            return "Sin conexión. Verifica tu red e intenta de nuevo."
        case .tooManyRequests:
            return "Demasiados intentos. Espera unos minutos e intenta de nuevo."
        default:
            return fallback
        }
    }
}
