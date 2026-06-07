package com.eldraft.android.data

import com.eldraft.domain.auth.GoogleIdentity
import com.eldraft.domain.auth.GoogleSignInProvider

/**
 * Adaptador de [GoogleAuthClient] (Credential Manager, Android) a la
 * abstracción de dominio [GoogleSignInProvider], para que el caso de uso
 * de login viva en commonMain.
 */
class GoogleSignInProviderImpl(
    private val client: GoogleAuthClient,
) : GoogleSignInProvider {

    override suspend fun signIn(): GoogleIdentity {
        val result = client.signIn()
        return GoogleIdentity(
            idToken = result.idToken,
            displayName = result.displayName,
            email = result.email,
            photoUrl = result.photoUrl,
        )
    }
}
