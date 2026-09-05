package com.eldraft.domain.auth

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Adaptador del cierre de sesión de Firebase Auth (Swift) a la abstracción de
 * dominio [IdentitySessionCleaner]. Mismo patrón que [IosGoogleSignInProvider].
 */
class IosIdentitySessionCleaner(
    private val clearBlock: (onComplete: () -> Unit, onError: (Throwable) -> Unit) -> Unit,
) : IdentitySessionCleaner {

    override suspend fun clear(): Unit = suspendCancellableCoroutine { continuation ->
        clearBlock(
            { continuation.resume(Unit) },
            { error -> continuation.resumeWithException(error) },
        )
    }
}
