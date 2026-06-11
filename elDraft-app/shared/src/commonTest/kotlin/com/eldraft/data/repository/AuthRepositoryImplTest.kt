package com.eldraft.data.repository

import com.eldraft.core.config.ApiConfig
import com.eldraft.core.network.ApiException
import com.eldraft.core.network.AuthTokenProvider
import com.eldraft.data.local.SessionStore
import com.eldraft.data.remote.AuthApi
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class FakeSessionStore : SessionStore {
    var token: String? = null
    var userId: String? = null
    override suspend fun currentToken(): String? = token
    override suspend fun currentUserId(): String? = userId
    override suspend fun save(token: String, userId: String) {
        this.token = token; this.userId = userId
    }
    override suspend fun clear() { token = null; userId = null }
}

/** Construye un cliente con MockEngine pero la MISMA config (json + ApiException). */
private fun mockClient(handler: MockRequestHandler): HttpClient =
    HttpClient(MockEngine(handler)) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        expectSuccess = true
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                val clientEx = exception as? ResponseException ?: return@handleResponseExceptionWithRequest
                val response = clientEx.response
                val body = runCatching { response.bodyAsText() }.getOrDefault("")
                throw ApiException(response.status.value, body, exception)
            }
        }
    }

private val config = ApiConfig(baseUrl = "https://test.local", wsBaseUrl = "wss://test.local")
private val noToken = AuthTokenProvider { null }

class AuthRepositoryImplTest {

    @Test
    fun login_exitoso_persiste_sesion() = runTest {
        val client = mockClient {
            respond(
                content = """{"token":"jwt-abc","user":{"id":"u-7","name":"Tester"},"needsOnboarding":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val session = FakeSessionStore()
        val repo = AuthRepositoryImpl(AuthApi(client, config, noToken), session)

        val response = repo.login("firebase-token")

        assertEquals("jwt-abc", response.token)
        assertEquals("u-7", response.user.id)
        assertTrue(response.needsOnboarding)
        // El repo debe haber persistido la sesión
        assertEquals("jwt-abc", session.token)
        assertEquals("u-7", session.userId)
    }

    @Test
    fun login_con_error_http_lanza_ApiException_y_no_persiste() = runTest {
        val client = mockClient {
            respond(
                content = """{"code":"INVALID_TOKEN","message":"Token inválido"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val session = FakeSessionStore()
        val repo = AuthRepositoryImpl(AuthApi(client, config, noToken), session)

        val ex = assertFailsWith<ApiException> { repo.login("bad-token") }
        assertEquals(401, ex.status)
        assertEquals(null, session.token)
    }

    @Test
    fun hasSession_refleja_el_store() = runTest {
        val client = mockClient { respond("", HttpStatusCode.OK) }
        val session = FakeSessionStore()
        val repo = AuthRepositoryImpl(AuthApi(client, config, noToken), session)

        assertEquals(false, repo.hasSession())
        session.save("t", "u")
        assertEquals(true, repo.hasSession())
        repo.logout()
        assertEquals(false, repo.hasSession())
    }
}
