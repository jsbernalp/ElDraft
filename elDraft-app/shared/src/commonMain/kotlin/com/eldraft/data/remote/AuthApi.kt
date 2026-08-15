package com.eldraft.data.remote

import com.eldraft.core.config.ApiConfig
import com.eldraft.core.network.AuthTokenProvider
import com.eldraft.core.network.BaseApi
import com.eldraft.data.models.LoginResponse
import com.eldraft.data.models.UpdateAccountRequest
import com.eldraft.data.models.User
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class AuthApi(
    client: HttpClient,
    config: ApiConfig,
    tokenProvider: AuthTokenProvider,
) : BaseApi(client, config, tokenProvider) {

    suspend fun login(firebaseToken: String): LoginResponse =
        client.post("$baseUrl/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("firebaseToken" to firebaseToken))
        }.body()

    suspend fun updatePhone(phone: String): Map<String, String> =
        client.put("$baseUrl/api/v1/auth/phone") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(mapOf("phone" to phone))
        }.body()

    /** Registra el token FCM del dispositivo para recibir notificaciones push. */
    suspend fun registerFcmToken(token: String): Map<String, String> =
        client.put("$baseUrl/api/v1/auth/fcm-token") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(mapOf("token" to token))
        }.body()

    /** Reporta la última ubicación conocida (para notificar convocatorias cercanas). */
    suspend fun updateLocation(lat: Double, lng: Double): Map<String, String> =
        client.put("$baseUrl/api/v1/auth/location") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(mapOf("lat" to lat, "lng" to lng))
        }.body()

    /** Devuelve los datos del usuario autenticado. */
    suspend fun getMe(): User =
        client.get("$baseUrl/api/v1/auth/me") {
            auth()
        }.body()

    /** Actualiza el nombre y avatar del usuario autenticado. */
    suspend fun updateAccount(name: String, avatarUrl: String?): User =
        client.patch("$baseUrl/api/v1/auth/me") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(UpdateAccountRequest(name = name, avatarUrl = avatarUrl))
        }.body()

    /**
     * Borra la cuenta del usuario autenticado. Irreversible.
     *
     * El servidor responde 204 sin cuerpo, así que no se llama a `body()`: hacerlo
     * intentaría deserializar una respuesta vacía y fallaría.
     */
    suspend fun deleteAccount() {
        client.delete("$baseUrl/api/v1/auth/me") {
            auth()
        }
    }
}
