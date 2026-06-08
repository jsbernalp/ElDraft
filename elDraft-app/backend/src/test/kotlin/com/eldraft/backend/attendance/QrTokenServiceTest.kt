package com.eldraft.backend.attendance

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class QrTokenServiceTest {

    private val service = QrTokenService(secret = "test-secret-1234567890")
    private val convocatoryId = UUID.randomUUID().toString()

    @Test
    fun token_valido_se_valida_y_devuelve_convocatoria() {
        val token = service.generate(convocatoryId, ttlSeconds = 600)
        val result = service.validate(token)
        assertIs<QrTokenService.Validation.Valid>(result)
        assertEquals(convocatoryId, result.convocatoryId)
    }

    @Test
    fun token_expirado_falla() {
        val token = service.generate(convocatoryId, ttlSeconds = -1) // ya expirado
        assertIs<QrTokenService.Validation.Expired>(service.validate(token))
    }

    @Test
    fun firma_alterada_falla() {
        val token = service.generate(convocatoryId, ttlSeconds = 600)
        val tampered = token.dropLast(2) + "xy"
        val result = service.validate(tampered)
        // Puede ser BadSignature (firma no coincide) según el caracter alterado.
        assertIs<QrTokenService.Validation.BadSignature>(result)
    }

    @Test
    fun token_de_otro_secreto_falla() {
        val other = QrTokenService(secret = "otro-secreto-distinto")
        val token = other.generate(convocatoryId, ttlSeconds = 600)
        assertIs<QrTokenService.Validation.BadSignature>(service.validate(token))
    }

    @Test
    fun token_malformado_falla() {
        assertIs<QrTokenService.Validation.Malformed>(service.validate("no-tiene-punto"))
    }
}
