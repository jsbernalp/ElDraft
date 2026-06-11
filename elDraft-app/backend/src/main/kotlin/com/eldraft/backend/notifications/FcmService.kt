package com.eldraft.backend.notifications

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import java.io.FileInputStream

/**
 * Envío de notificaciones push vía Firebase Admin SDK.
 *
 * Diseño "best-effort": si no hay credencial (service account) configurada, el
 * servicio queda DESHABILITADO y los envíos son no-ops que solo loguean. Así el
 * backend funciona en desarrollo sin Firebase y nunca rompe el flujo principal
 * por un fallo de notificación.
 *
 * Para habilitarlo: definir la variable de entorno
 *   FIREBASE_SERVICE_ACCOUNT_PATH=/ruta/al/serviceAccountKey.json
 */
class FcmService(
    serviceAccountPath: String?,
) {
    private val log = LoggerFactory.getLogger(FcmService::class.java)

    private val messaging: FirebaseMessaging? = initMessaging(serviceAccountPath)

    val enabled: Boolean get() = messaging != null

    private fun initMessaging(path: String?): FirebaseMessaging? {
        if (path.isNullOrBlank()) {
            log.warn("FCM deshabilitado: FIREBASE_SERVICE_ACCOUNT_PATH no está definido. Los push serán no-ops.")
            return null
        }
        return try {
            val app = FirebaseApp.getApps().firstOrNull { it.name == FCM_APP_NAME }
                ?: run {
                    // El project_id se toma SIEMPRE del service account JSON; no lo
                    // sobrescribimos para evitar "Permission denied on resource project"
                    // cuando el projectId configurado no coincide con la credencial.
                    val credentials = FileInputStream(path).use { GoogleCredentials.fromStream(it) }
                    val options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .build()
                    FirebaseApp.initializeApp(options, FCM_APP_NAME)
                }
            log.info("FCM habilitado (Firebase Admin inicializado desde $path, proyecto ${app.options.projectId}).")
            FirebaseMessaging.getInstance(app)
        } catch (e: Exception) {
            log.error("No se pudo inicializar Firebase Admin desde '$path'; FCM queda deshabilitado.", e)
            null
        }
    }

    /**
     * Envía una notificación a un token de dispositivo. Best-effort: nunca lanza.
     * Devuelve true si se envió, false si estaba deshabilitado o falló.
     */
    fun sendToToken(
        token: String?,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
    ): Boolean {
        val fm = messaging ?: return false
        if (token.isNullOrBlank()) {
            log.debug("Push omitido: el destinatario no tiene token FCM registrado.")
            return false
        }
        return try {
            val message = Message.builder()
                .setToken(token)
                .setNotification(
                    Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build(),
                )
                .apply { data.forEach { (k, v) -> putData(k, v) } }
                .setAndroidConfig(
                    AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build(),
                )
                .build()
            val id = fm.send(message)
            log.info("Push enviado (id=$id, título='$title').")
            true
        } catch (e: FirebaseMessagingException) {
            // Token inválido/no registrado u otros errores de FCM: no rompemos el flujo.
            log.warn("Fallo al enviar push (código=${e.messagingErrorCode}): ${e.message}")
            false
        } catch (e: Exception) {
            log.warn("Fallo inesperado al enviar push: ${e.message}")
            false
        }
    }

    private companion object {
        const val FCM_APP_NAME = "eldraft-fcm"
    }
}
