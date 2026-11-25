package com.eldraft.domain.auth

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Adaptador de Google Sign-In (GoogleSignIn-iOS, implementado en Swift) a la
 * abstracción de dominio [GoogleSignInProvider].
 *
 * Implementar un `suspend fun` de Kotlin *desde* Swift es incómodo (llega como
 * `completionHandler:`). En su lugar, Swift entrega un closure plano de
 * callbacks (éxito/error) y esta clase lo envuelve con
 * [suspendCancellableCoroutine] para exponerlo como suspend fun a
 * [SignInWithGoogleUseCase][com.eldraft.domain.usecase.auth.SignInWithGoogleUseCase].
 */
class IosGoogleSignInProvider(
    private val signInBlock: (onSuccess: (GoogleIdentity) -> Unit, onError: (Throwable) -> Unit) -> Unit,
) : GoogleSignInProvider {

    override suspend fun signIn(): GoogleIdentity = suspendCancellableCoroutine { continuation ->
        signInBlock(
            { identity -> continuation.resume(identity) },
            { error -> continuation.resumeWithException(error) },
        )
    }
}
