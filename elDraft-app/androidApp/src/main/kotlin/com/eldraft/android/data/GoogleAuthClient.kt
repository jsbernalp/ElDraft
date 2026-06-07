package com.eldraft.android.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** Resultado del Sign-In con Google: el ID token de Firebase/Google a enviar al backend. */
data class GoogleSignInResult(
    val idToken: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?
)

class GoogleAuthException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Encapsula el flujo de Google Sign-In usando Credential Manager (API moderna).
 *
 * El [serverClientId] debe ser el "web client id" (client_type 3) del google-services.json,
 * disponible como recurso R.string.default_web_client_id que genera el plugin google-services.
 */
class GoogleAuthClient(
    private val context: Context,
    private val serverClientId: String
) {
    private val credentialManager = CredentialManager.create(context)

    /**
     * Lanza el selector de cuentas de Google y devuelve el ID token.
     * @throws GoogleAuthException si el usuario cancela o falla la credencial.
     */
    suspend fun signIn(): GoogleSignInResult {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            // false = permite elegir cualquier cuenta, no solo las ya autorizadas
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val response = try {
            credentialManager.getCredential(context, request)
        } catch (e: GetCredentialException) {
            throw GoogleAuthException(e.message ?: "No se pudo obtener la credencial de Google", e)
        }

        val credential = response.credential
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw GoogleAuthException("Tipo de credencial inesperado: ${credential.type}")
        }

        return try {
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            GoogleSignInResult(
                idToken = googleCredential.idToken,
                displayName = googleCredential.displayName,
                email = googleCredential.id,
                photoUrl = googleCredential.profilePictureUri?.toString()
            )
        } catch (e: GoogleIdTokenParsingException) {
            throw GoogleAuthException("Error al parsear el token de Google", e)
        }
    }

    suspend fun signOut() {
        runCatching {
            androidx.credentials.ClearCredentialStateRequest().let {
                credentialManager.clearCredentialState(it)
            }
        }
    }
}
