package com.eldraft.data.api

import com.eldraft.core.config.ApiConfig
import com.eldraft.data.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/** Error de API con el status HTTP y el cuerpo crudo devuelto por el backend. */
class ApiException(
    val status: Int,
    val body: String,
    cause: Throwable? = null
) : RuntimeException("HTTP $status: ${body.ifBlank { "(sin cuerpo)" }}", cause)

class ElDraftApi(
    config: ApiConfig
) {
    private val baseUrl: String = config.baseUrl
    private val wsBaseUrl: String = config.wsBaseUrl

    private val client = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(WebSockets)
        // Lanzar excepción en respuestas no-2xx en lugar de intentar
        // deserializar un cuerpo de error como si fuera la respuesta esperada.
        expectSuccess = true
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                val clientEx = exception as? ClientRequestException ?: return@handleResponseExceptionWithRequest
                val response = clientEx.response
                val errorBody = runCatching { response.bodyAsText() }.getOrDefault("")
                throw ApiException(
                    status = response.status.value,
                    body = errorBody,
                    cause = exception
                )
            }
        }
    }

    private var authToken: String? = null

    fun setToken(token: String) { authToken = token }

    private fun HttpRequestBuilder.auth() {
        authToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    // Auth
    suspend fun login(firebaseToken: String): LoginResponse =
        client.post("$baseUrl/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("firebaseToken" to firebaseToken))
        }.body()

    suspend fun updatePhone(phone: String) =
        client.put("$baseUrl/api/v1/auth/phone") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(mapOf("phone" to phone))
        }.body<Map<String, String>>()

    // Players
    suspend fun getPlayerProfile(playerId: String): PlayerProfile =
        client.get("$baseUrl/api/v1/players/$playerId/profile") { auth() }.body()

    suspend fun updateProfile(playerId: String, request: UpdateProfileRequest): PlayerProfile =
        client.put("$baseUrl/api/v1/players/$playerId/profile") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    // Convocatories
    suspend fun getNearbyConvocatories(lat: Double, lng: Double, radius: Double = 5000.0): List<Convocatory> =
        client.get("$baseUrl/api/v1/convocatories/nearby") {
            auth()
            parameter("lat", lat)
            parameter("lng", lng)
            parameter("radius", radius)
        }.body()

    suspend fun createConvocatory(request: Convocatory): Convocatory =
        client.post("$baseUrl/api/v1/convocatories") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun getMyConvocatories(): List<Convocatory> =
        client.get("$baseUrl/api/v1/convocatories/mine") { auth() }.body()

    // Postulations
    suspend fun applyToConvocatory(convocatoryId: String): Postulation =
        client.post("$baseUrl/api/v1/convocatories/$convocatoryId/apply") { auth() }.body()

    suspend fun getApplicants(convocatoryId: String): List<Postulation> =
        client.get("$baseUrl/api/v1/convocatories/$convocatoryId/applicants") { auth() }.body()

    suspend fun approvePostulation(postulationId: String) =
        client.put("$baseUrl/api/v1/postulations/$postulationId/approve") { auth() }.body<Map<String, String>>()

    suspend fun rejectPostulation(postulationId: String) =
        client.put("$baseUrl/api/v1/postulations/$postulationId/reject") { auth() }.body<Map<String, String>>()

    // Attendance
    suspend fun generateQr(convocatoryId: String): AttendanceQr =
        client.post("$baseUrl/api/v1/attendance/generate-qr") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(mapOf("convocatoryId" to convocatoryId))
        }.body()

    // WebSocket - mapa en tiempo real
    fun observeMapEvents(lat: Double, lng: Double, radius: Double = 5000.0): Flow<MapEvent> = flow {
        client.webSocket("$wsBaseUrl/ws/map?lat=$lat&lng=$lng&radius=$radius") {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    runCatching {
                        Json.decodeFromString<MapEvent>(frame.readText())
                    }.onSuccess { emit(it) }
                }
            }
        }
    }
}
