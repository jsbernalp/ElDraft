package com.eldraft.backend.attendance

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Genera y valida tokens QR autocontenidos y firmados (HMAC-SHA256), sin estado
 * en base de datos. El token codifica: convocatoryId + epoch de expiración + firma.
 *
 * Formato: base64url("<convocatoryId>:<expiryEpochSeconds>") + "." + base64url(hmac)
 */
class QrTokenService(secret: String) {

    private val key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")

    /** Genera un token para [convocatoryId] válido por [ttlSeconds] segundos. */
    fun generate(convocatoryId: String, ttlSeconds: Long): String {
        val expiry = System.currentTimeMillis() / 1000 + ttlSeconds
        val payload = "$convocatoryId:$expiry"
        val payloadB64 = b64(payload.toByteArray())
        val sig = b64(hmac(payloadB64))
        return "$payloadB64.$sig"
    }

    /** Resultado de validar un token QR. */
    sealed interface Validation {
        data class Valid(val convocatoryId: String) : Validation
        data object BadSignature : Validation
        data object Expired : Validation
        data object Malformed : Validation
    }

    fun validate(token: String): Validation {
        val parts = token.split(".")
        if (parts.size != 2) return Validation.Malformed
        val (payloadB64, sig) = parts

        // Verificar firma (comparación en tiempo constante).
        val expectedSig = b64(hmac(payloadB64))
        if (!constantTimeEquals(sig, expectedSig)) return Validation.BadSignature

        val payload = try {
            String(Base64.getUrlDecoder().decode(payloadB64))
        } catch (_: Exception) {
            return Validation.Malformed
        }
        val fields = payload.split(":")
        if (fields.size != 2) return Validation.Malformed
        val convocatoryId = fields[0]
        val expiry = fields[1].toLongOrNull() ?: return Validation.Malformed

        if (System.currentTimeMillis() / 1000 > expiry) return Validation.Expired
        return Validation.Valid(convocatoryId)
    }

    private fun hmac(data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return mac.doFinal(data.toByteArray())
    }

    private fun b64(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }
}
