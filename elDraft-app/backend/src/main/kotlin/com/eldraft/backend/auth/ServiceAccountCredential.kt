package com.eldraft.backend.auth

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.Base64

/**
 * Credencial de service account de Firebase ya cargada en memoria.
 *
 * Existe porque el JSON puede llegar de dos formas incompatibles según dónde
 * corra el backend:
 *
 *  - **Como archivo** (`FIREBASE_SERVICE_ACCOUNT_PATH`): desarrollo local y
 *    proveedores que montan secretos en el filesystem (Fly.io, Kubernetes).
 *  - **Como variable de entorno** (`FIREBASE_SERVICE_ACCOUNT_JSON`): Railway y
 *    similares, que **solo** exponen secretos como variables de entorno. Sin
 *    esta segunda vía no hay forma de darle la credencial al proceso allí.
 *
 * El contenido inline se acepta tanto en JSON plano como en Base64. El Base64 es
 * lo recomendado: el JSON trae saltos de línea dentro de `private_key` (`\n`) que
 * algunas interfaces y CLIs de variables de entorno alteran al pegarlo.
 */
class ServiceAccountCredential private constructor(
    private val bytes: ByteArray,
    /** De dónde salió, para poder loguearlo sin filtrar el contenido. */
    val origin: String,
) {
    /** Cada llamada devuelve un stream nuevo: Firebase Admin consume el suyo. */
    fun open(): InputStream = ByteArrayInputStream(bytes)

    companion object {
        /**
         * Resuelve la credencial. Devuelve null solo si no se configuró ninguna
         * de las dos fuentes; si se configuró una pero es inválida, lanza
         * [IllegalArgumentException] en vez de devolver null en silencio.
         */
        fun resolve(path: String?, inlineJson: String?): ServiceAccountCredential? = when {
            !inlineJson.isNullOrBlank() -> fromInline(inlineJson)
            !path.isNullOrBlank() -> fromFile(path)
            else -> null
        }

        private fun fromInline(raw: String): ServiceAccountCredential {
            val trimmed = raw.trim()
            val bytes = if (trimmed.startsWith("{")) {
                trimmed.toByteArray()
            } else {
                try {
                    // Mime decoder: tolera los saltos de línea que meten algunas
                    // interfaces al guardar valores largos.
                    Base64.getMimeDecoder().decode(trimmed)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException(
                        "FIREBASE_SERVICE_ACCOUNT_JSON no es JSON (no empieza por '{') ni Base64 " +
                            "válido. Genera el valor con: base64 -w0 serviceAccountKey.json",
                        e,
                    )
                }
            }
            require(bytes.isNotEmpty()) { "FIREBASE_SERVICE_ACCOUNT_JSON está vacío." }
            require(bytes.decodeToString().trimStart().startsWith("{")) {
                "FIREBASE_SERVICE_ACCOUNT_JSON se decodificó pero no contiene un objeto JSON. " +
                    "¿Codificaste el archivo correcto?"
            }
            return ServiceAccountCredential(bytes, origin = "FIREBASE_SERVICE_ACCOUNT_JSON")
        }

        private fun fromFile(path: String): ServiceAccountCredential {
            val file = File(path)
            require(file.isFile) {
                "FIREBASE_SERVICE_ACCOUNT_PATH apunta a '$path', que no existe o no es un archivo."
            }
            return ServiceAccountCredential(file.readBytes(), origin = path)
        }
    }
}
