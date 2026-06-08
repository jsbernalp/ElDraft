package com.eldraft.data.remote

import com.eldraft.core.config.ApiConfig
import com.eldraft.core.network.AuthTokenProvider
import com.eldraft.core.network.BaseApi
import com.eldraft.data.models.LoginResponse
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
}
