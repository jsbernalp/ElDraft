package com.eldraft.android.data

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/** Resultado del Sign-In con Google: el ID token de Firebase a enviar al backend. */
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
    private val firebaseAuth = FirebaseAuth.getInstance()

    private companion object {
        const val TAG = "GoogleAuthClient"
    }

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
            // Log completo para diagnóstico (subtipo + mensaje interno del framework)
            Log.e(TAG, "GetCredential falló: type=${e.type} class=${e::class.java.simpleName} msg=${e.message}", e)
            val friendly = when (e) {
                is NoCredentialException ->
                    "No hay cuentas de Google disponibles en este dispositivo. " +
                        "Agrega una cuenta de Google en Ajustes y vuelve a intentar."
                is GetCredentialCancellationException ->
                    "Inicio de sesión cancelado."
                else ->
                    "No se pudo iniciar sesión con Google (${e::class.java.simpleName}). " +
                        "Verifica que el emulador tenga Google Play Services y una cuenta de Google."
            }
            throw GoogleAuthException(friendly, e)
        }

        val credential = response.credential
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw GoogleAuthException("Tipo de credencial inesperado: ${credential.type}")
        }

        val googleCredential = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (e: GoogleIdTokenParsingException) {
            throw GoogleAuthException("Error al parsear el token de Google", e)
        }

        // Credential Manager entrega un ID token de GOOGLE: lo emite accounts.google.com
        // y su `aud` es el web client id. El backend lo verifica con
        // FirebaseAuth.verifyIdToken, que solo acepta tokens de FIREBASE (emisor
        // securetoken.google.com/<project>, `aud` = project id). Son tokens distintos,
        // así que hay que canjear uno por otro aquí.
        //
        // Sin este canje TODO login con Google falla con "Token de Firebase inválido o
        // vencido". No se detectó antes porque en desarrollo el backend corría en modo
        // mock, que acepta cualquier string como token. Ver EmailAuthClient: esa ruta
        // siempre devolvió un token de Firebase, y por eso sí funcionaba.
        val firebaseIdToken = try {
            val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
            val user = firebaseAuth.signInWithCredential(firebaseCredential).await().user
                ?: throw GoogleAuthException("Firebase no devolvió un usuario tras el Sign-In con Google")
            user.getIdToken(false).await()?.token
                ?: throw GoogleAuthException("No se pudo obtener el token de sesión")
        } catch (e: GoogleAuthException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Falló el canje del token de Google por uno de Firebase", e)
            throw GoogleAuthException("No se pudo completar el inicio de sesión con Google", e)
        }

        return GoogleSignInResult(
            idToken = firebaseIdToken,
            displayName = googleCredential.displayName,
            email = googleCredential.id,
            photoUrl = googleCredential.profilePictureUri?.toString()
        )
    }

    suspend fun signOut() {
        // signIn() ahora abre sesión en Firebase, así que cerrarla es parte de salir:
        // si no, el usuario queda autenticado en Firebase tras el logout.
        runCatching { firebaseAuth.signOut() }
        runCatching {
            androidx.credentials.ClearCredentialStateRequest().let {
                credentialManager.clearCredentialState(it)
            }
        }
    }
}
