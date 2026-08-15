package com.eldraft.backend.auth

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream

/**
 * Verificación REAL del ID token contra Firebase, para producción.
 *
 * A diferencia de [MockTokenVerifier], valida la firma criptográfica del token
 * contra las claves públicas de Google y comprueba emisor, audiencia y
 * expiración. Un token forjado o vencido se rechaza.
 *
 * Diseño deliberadamente FAIL-HARD, al revés que [com.eldraft.backend.notifications.FcmService]:
 * si falta la credencial o es inválida, el constructor lanza y el backend no
 * arranca. Un push que no sale es un inconveniente; una verificación de identidad
 * que se degrada en silencio deja entrar a cualquiera, que es exactamente el bug
 * que esta clase viene a cerrar.
 */
class FirebaseTokenVerifier(serviceAccountPath: String?) : TokenVerifier {

    private val log = LoggerFactory.getLogger(FirebaseTokenVerifier::class.java)

    private val auth: FirebaseAuth = initAuth(serviceAccountPath)

    private fun initAuth(path: String?): FirebaseAuth {
        require(!path.isNullOrBlank()) {
            "authMode='firebase' requiere FIREBASE_SERVICE_ACCOUNT_PATH: sin la service " +
                "account no se puede verificar ningún token contra Google."
        }
        val file = File(path)
        require(file.isFile) {
            "FIREBASE_SERVICE_ACCOUNT_PATH apunta a '$path', que no existe o no es un archivo."
        }

        val app = FirebaseApp.getApps().firstOrNull { it.name == AUTH_APP_NAME }
            ?: run {
                // El project_id sale del JSON de la service account; es el que se usa
                // para validar el claim `aud` de los tokens entrantes.
                val credentials = FileInputStream(file).use { GoogleCredentials.fromStream(it) }
                val options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build()
                FirebaseApp.initializeApp(options, AUTH_APP_NAME)
            }

        log.info("Verificación de tokens REAL habilitada (proyecto ${app.options.projectId}).")
        return FirebaseAuth.getInstance(app)
    }

    override fun verify(idToken: String): VerifiedIdentity {
        if (idToken.isBlank()) {
            throw TokenVerificationException("Token vacío")
        }

        val decoded = try {
            auth.verifyIdToken(idToken)
        } catch (e: FirebaseAuthException) {
            // No filtramos el detalle de Firebase al cliente: para quien llama, un
            // token inválido es indistinguible de uno vencido o de otro proyecto.
            log.debug("Token rechazado por Firebase: {}", e.authErrorCode)
            throw TokenVerificationException("Token de Firebase inválido o vencido", e)
        } catch (e: IllegalArgumentException) {
            // verifyIdToken lanza esto cuando el string ni siquiera tiene forma de JWT.
            throw TokenVerificationException("Token de Firebase malformado", e)
        }

        val uid = decoded.uid
        if (uid.isNullOrBlank()) {
            throw TokenVerificationException("El token verificado no trae uid")
        }

        return VerifiedIdentity(
            firebaseUid = uid,
            name = decoded.name?.takeIf { it.isNotBlank() }
                ?: decoded.email?.substringBefore("@")
                ?: "Jugador ${uid.take(6)}",
            email = decoded.email,
            avatarUrl = decoded.picture,
        )
    }

    private companion object {
        // Nombre propio: FcmService inicializa su propia FirebaseApp ("eldraft-fcm")
        // y no queremos que el ciclo de vida de una dependa de la otra.
        const val AUTH_APP_NAME = "eldraft-auth"
    }
}
