import shared

/// Implementaciones reales de los bridges de autenticación que
/// `startKoinIos` necesita desde el arranque (ver Fase 0.6). Reemplaza a
/// `PlaceholderIdentityProviders` ahora que Firebase Auth y GoogleSignIn-iOS
/// están integrados (Fase 2): estos closures son el único punto donde el
/// código real de Firebase/Google toca los bridges de Kotlin.
enum LiveIdentity {
    static func googleSignInProvider() -> IosGoogleSignInProvider {
        IosGoogleSignInProvider(signInBlock: { onSuccess, onError in
            Task {
                do {
                    onSuccess(try await GoogleSignInClientIOS.signIn())
                } catch {
                    onError(KotlinException(message: GoogleSignInClientIOS.friendlyMessage(error)))
                }
            }
        })
    }

    static func emailAuthProvider() -> IosEmailAuthProvider {
        IosEmailAuthProvider(
            signInBlock: { email, password, onSuccess, onError in
                Task {
                    do {
                        onSuccess(try await EmailAuthClientIOS.signIn(email: email, password: password))
                    } catch {
                        onError(KotlinException(
                            message: FirebaseAuthErrorMessage.friendly(error, fallback: "No se pudo iniciar sesión.")
                        ))
                    }
                }
            },
            signUpBlock: { name, email, password, onSuccess, onError in
                Task {
                    do {
                        onSuccess(try await EmailAuthClientIOS.signUp(name: name, email: email, password: password))
                    } catch {
                        onError(KotlinException(
                            message: FirebaseAuthErrorMessage.friendly(error, fallback: "No se pudo crear la cuenta.")
                        ))
                    }
                }
            }
        )
    }

    static func identitySessionCleaner() -> IosIdentitySessionCleaner {
        IosIdentitySessionCleaner(clearBlock: { onComplete, onError in
            do {
                try IdentitySessionCleanerIOS.clear()
                onComplete()
            } catch {
                onError(KotlinException(message: "No se pudo cerrar la sesión."))
            }
        })
    }
}
