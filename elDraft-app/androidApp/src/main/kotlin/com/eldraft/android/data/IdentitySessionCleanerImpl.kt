package com.eldraft.android.data

import com.eldraft.domain.auth.IdentitySessionCleaner

/**
 * Implementación Android de [IdentitySessionCleaner].
 *
 * Delega en [GoogleAuthClient.signOut], que cierra la sesión de Firebase Auth y
 * limpia el estado de Credential Manager. Sirve para las dos rutas de login, no
 * solo la de Google: `FirebaseAuth.signOut()` es global a la app, así que también
 * cierra las sesiones abiertas por [EmailAuthClient].
 */
class IdentitySessionCleanerImpl(
    private val googleAuthClient: GoogleAuthClient,
) : IdentitySessionCleaner {

    override suspend fun clear() = googleAuthClient.signOut()
}
