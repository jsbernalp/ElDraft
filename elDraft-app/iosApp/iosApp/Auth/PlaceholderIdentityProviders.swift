import shared

/// `startKoinIos` necesita un Google/Email/IdentityCleaner reales desde el
/// arranque de la app, pero Firebase y Google Sign-In llegan en la Fase 2.
/// Estos placeholders satisfacen la firma mientras tanto: solo fallan si algo
/// los invoca antes de tiempo (todavía no hay ninguna pantalla de login).
///
/// Los callbacks `onSuccess`/`onError` que Kotlin expone aquí devuelven
/// `KotlinUnit` (no `Void`): son closures corrientes, no el completion
/// handler que Kotlin/Native genera para un suspend fun. `_ =` descarta ese
/// valor a propósito.
enum PlaceholderIdentity {
    private static func notImplemented(_ what: String) -> KotlinException {
        KotlinException(message: "\(what) llega en la Fase 2 (Autenticación)")
    }

    static func googleSignInProvider() -> IosGoogleSignInProvider {
        IosGoogleSignInProvider(signInBlock: { _, onError in
            _ = onError(notImplemented("Google Sign-In"))
        })
    }

    static func emailAuthProvider() -> IosEmailAuthProvider {
        IosEmailAuthProvider(
            signInBlock: { _, _, _, onError in
                _ = onError(notImplemented("El login por email"))
            },
            signUpBlock: { _, _, _, _, onError in
                _ = onError(notImplemented("El registro por email"))
            }
        )
    }

    static func identitySessionCleaner() -> IosIdentitySessionCleaner {
        IosIdentitySessionCleaner(clearBlock: { onComplete, _ in
            // No hay sesión de Firebase que cerrar todavía: no es un error.
            _ = onComplete()
        })
    }
}
