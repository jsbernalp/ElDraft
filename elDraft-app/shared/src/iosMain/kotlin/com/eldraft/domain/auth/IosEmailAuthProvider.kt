package com.eldraft.domain.auth

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Adaptador del login por email (Firebase Auth para iOS, implementado en
 * Swift) a la abstracción de dominio [EmailAuthProvider]. Mismo patrón que
 * [IosGoogleSignInProvider]: Swift solo entrega closures de callbacks, esta
 * clase los envuelve con [suspendCancellableCoroutine].
 */
class IosEmailAuthProvider(
    private val signInBlock: (
        email: String,
        password: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) -> Unit,
    private val signUpBlock: (
        name: String,
        email: String,
        password: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) -> Unit,
) : EmailAuthProvider {

    override suspend fun signIn(email: String, password: String): String =
        suspendCancellableCoroutine { continuation ->
            signInBlock(
                email,
                password,
                { idToken -> continuation.resume(idToken) },
                { error -> continuation.resumeWithException(error) },
            )
        }

    override suspend fun signUp(name: String, email: String, password: String): String =
        suspendCancellableCoroutine { continuation ->
            signUpBlock(
                name,
                email,
                password,
                { idToken -> continuation.resume(idToken) },
                { error -> continuation.resumeWithException(error) },
            )
        }
}
