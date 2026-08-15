package com.eldraft.backend.auth

import com.eldraft.backend.di.backendModule
import io.ktor.server.config.MapApplicationConfig
import org.koin.dsl.koinApplication
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Regresión del agujero de autenticación: `authMode = "firebase"` estaba soportado
 * en la config pero el módulo de DI caía de vuelta a [MockTokenVerifier] con solo
 * un log.warn, dejando el backend aceptando cualquier identidad.
 *
 * Lo que estos tests fijan es el comportamiento FAIL-HARD: en modo firebase, o hay
 * verificación real o el backend no arranca. Nunca una degradación silenciosa.
 *
 * La verificación real contra Google (firma, emisor, audiencia, expiración) no se
 * puede ejercitar aquí sin una service account; eso se cubre en el smoke test
 * contra producción.
 */
class TokenVerifierWiringTest {

    private fun config(authMode: String, serviceAccountPath: String? = null) =
        MapApplicationConfig(
            *listOfNotNull(
                "jwt.secret" to "test-secret",
                "jwt.issuer" to "eldraft",
                "jwt.audience" to "eldraft-users",
                "jwt.realm" to "test",
                "firebase.authMode" to authMode,
                serviceAccountPath?.let { "firebase.serviceAccountPath" to it },
            ).toTypedArray()
        )

    private fun resolveVerifier(authMode: String, serviceAccountPath: String? = null) =
        koinApplication { modules(backendModule(config(authMode, serviceAccountPath))) }
            .koin.get<TokenVerifier>()

    @Test
    fun modo_firebase_sin_credencial_no_arranca_en_vez_de_caer_a_mock() {
        val error = assertFailsWith<Exception> {
            resolveVerifier(authMode = "firebase")
        }
        // Koin envuelve la excepción del constructor; lo que importa es que falle
        // y que el motivo sea la credencial ausente.
        assertTrue(
            generateSequence<Throwable>(error) { it.cause }.any { it is IllegalArgumentException },
            "Se esperaba IllegalArgumentException en la cadena de causas, fue: $error",
        )
    }

    @Test
    fun modo_firebase_con_ruta_inexistente_no_arranca() {
        assertFailsWith<Exception> {
            resolveVerifier(authMode = "firebase", serviceAccountPath = "/ruta/que/no/existe.json")
        }
    }

    @Test
    fun modo_firebase_nunca_devuelve_el_verificador_mock() {
        val verifier = runCatching { resolveVerifier(authMode = "firebase") }.getOrNull()
        assertTrue(
            verifier !is MockTokenVerifier,
            "authMode='firebase' devolvió MockTokenVerifier: el backend estaría aceptando " +
                "identidades arbitrarias en producción.",
        )
    }

    @Test
    fun modo_mock_sigue_disponible_para_desarrollo() {
        assertIs<MockTokenVerifier>(resolveVerifier(authMode = "mock"))
    }

    @Test
    fun el_verificador_mock_acepta_identidad_arbitraria_por_eso_no_puede_ir_a_produccion() {
        // Documenta POR QUÉ los tests de arriba importan: sin firma que validar,
        // basta un JSON en Base64 para hacerse pasar por cualquier usuario.
        val forjado = Base64.getEncoder()
            .encodeToString("""{"uid":"uid-del-organizador-de-otro"}""".toByteArray())

        val identidad = MockTokenVerifier().verify(forjado)

        assertEquals("uid-del-organizador-de-otro", identidad.firebaseUid)
    }
}
