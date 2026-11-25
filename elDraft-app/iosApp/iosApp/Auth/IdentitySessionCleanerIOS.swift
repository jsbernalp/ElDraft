import FirebaseAuth
import GoogleSignIn

/// Cierra la sesión de Firebase Auth y de GoogleSignIn-iOS. Espejo de
/// `IdentitySessionCleanerImpl` (Android): `Auth.auth().signOut()` es global
/// a la app, así que también cierra las sesiones abiertas por
/// `EmailAuthClientIOS`, no solo las de Google.
enum IdentitySessionCleanerIOS {
    static func clear() throws {
        GoogleSignInClientIOS.signOut()
        try Auth.auth().signOut()
    }
}
