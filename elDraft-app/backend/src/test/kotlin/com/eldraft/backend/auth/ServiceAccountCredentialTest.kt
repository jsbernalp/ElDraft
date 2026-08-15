package com.eldraft.backend.auth

import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La credencial de Firebase tiene que poder llegar sin un filesystem donde montarla:
 * Railway solo expone secretos como variables de entorno. Estos tests fijan que las
 * dos vías (archivo e inline) funcionen y que un valor mal puesto falle ruidosamente
 * en vez de dejar el backend a medias.
 */
class ServiceAccountCredentialTest {

    private val jsonValido = """{"type":"service_account","project_id":"eldraft-a6d42"}"""

    private fun leer(c: ServiceAccountCredential) = c.open().use { it.readBytes().decodeToString() }

    @Test
    fun sin_ninguna_fuente_configurada_devuelve_null() {
        assertNull(ServiceAccountCredential.resolve(path = null, inlineJson = null))
        assertNull(ServiceAccountCredential.resolve(path = "  ", inlineJson = ""))
    }

    @Test
    fun acepta_json_plano_inline() {
        val credential = ServiceAccountCredential.resolve(path = null, inlineJson = jsonValido)!!

        assertEquals(jsonValido, leer(credential))
        assertEquals("FIREBASE_SERVICE_ACCOUNT_JSON", credential.origin)
    }

    @Test
    fun acepta_json_en_base64() {
        // La vía recomendada: el JSON trae \n dentro de private_key y algunas
        // interfaces de variables de entorno los alteran al pegarlo en plano.
        val base64 = Base64.getEncoder().encodeToString(jsonValido.toByteArray())

        assertEquals(jsonValido, leer(ServiceAccountCredential.resolve(null, base64)!!))
    }

    @Test
    fun tolera_saltos_de_linea_dentro_del_base64() {
        val base64 = Base64.getMimeEncoder().encodeToString(jsonValido.toByteArray())

        assertEquals(jsonValido, leer(ServiceAccountCredential.resolve(null, base64)!!))
    }

    @Test
    fun un_base64_que_no_decodifica_a_json_falla_con_un_mensaje_util() {
        val base64DeOtraCosa = Base64.getEncoder().encodeToString("no soy json".toByteArray())

        val error = assertFailsWith<IllegalArgumentException> {
            ServiceAccountCredential.resolve(null, base64DeOtraCosa)
        }

        assertTrue(
            error.message!!.contains("no contiene un objeto JSON"),
            "Mensaje poco útil: ${error.message}",
        )
    }

    @Test
    fun un_valor_que_no_es_json_ni_base64_falla() {
        assertFailsWith<IllegalArgumentException> {
            ServiceAccountCredential.resolve(null, "esto no es ni json ni base64 %%%")
        }
    }

    @Test
    fun lee_desde_archivo_cuando_solo_hay_ruta() {
        val file = File.createTempFile("service-account", ".json").apply {
            writeText(jsonValido)
            deleteOnExit()
        }

        val credential = ServiceAccountCredential.resolve(path = file.absolutePath, inlineJson = null)!!

        assertEquals(jsonValido, leer(credential))
        assertEquals(file.absolutePath, credential.origin)
    }

    @Test
    fun una_ruta_inexistente_falla_en_vez_de_devolver_null() {
        // Devolver null aquí dejaría a FirebaseTokenVerifier reportando "no hay
        // credencial" cuando el problema real es una ruta mal escrita.
        assertFailsWith<IllegalArgumentException> {
            ServiceAccountCredential.resolve(path = "/ruta/que/no/existe.json", inlineJson = null)
        }
    }

    @Test
    fun el_json_inline_tiene_prioridad_sobre_la_ruta() {
        // En Railway no hay archivo que montar; si alguien deja la variable de la
        // ruta puesta de un despliegue anterior, no debe ganarle al valor real.
        val credential = ServiceAccountCredential.resolve(
            path = "/ruta/que/no/existe.json",
            inlineJson = jsonValido,
        )!!

        assertEquals(jsonValido, leer(credential))
    }
}
