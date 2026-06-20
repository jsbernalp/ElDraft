package com.eldraft.data.remote

import com.eldraft.core.config.ApiConfig
import com.eldraft.core.network.AuthTokenProvider
import com.eldraft.core.network.BaseApi
import com.eldraft.core.network.eldraftJson
import com.eldraft.data.models.CancelConvocatoryRequest
import com.eldraft.data.models.Convocatory
import com.eldraft.data.models.CreateConvocatoryRequest
import com.eldraft.data.models.MapEvent
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ConvocatoryApi(
    client: HttpClient,
    config: ApiConfig,
    tokenProvider: AuthTokenProvider,
) : BaseApi(client, config, tokenProvider) {

    suspend fun getNearby(lat: Double, lng: Double, radius: Double = 5000.0): List<Convocatory> =
        client.get("$baseUrl/api/v1/convocatories/nearby") {
            auth()
            parameter("lat", lat)
            parameter("lng", lng)
            parameter("radius", radius)
        }.body()

    suspend fun create(request: CreateConvocatoryRequest): Convocatory =
        client.post("$baseUrl/api/v1/convocatories") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun getById(id: String): Convocatory =
        client.get("$baseUrl/api/v1/convocatories/$id") { auth() }.body()

    suspend fun getMine(): List<Convocatory> =
        client.get("$baseUrl/api/v1/convocatories/mine") { auth() }.body()

    suspend fun cancel(id: String, reason: String) {
        client.delete("$baseUrl/api/v1/convocatories/$id") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(CancelConvocatoryRequest(reason))
        }
    }

    /**
     * WebSocket — pines del mapa en tiempo real. Si se pasa [userId], el backend
     * omite los pines de las convocatorias del propio usuario (no las verá).
     */
    fun observeMapEvents(lat: Double, lng: Double, radius: Double = 5000.0, userId: String? = null): Flow<MapEvent> = flow {
        val userParam = userId?.let { "&userId=$it" } ?: ""
        client.webSocket("$wsBaseUrl/ws/map?lat=$lat&lng=$lng&radius=$radius$userParam") {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    runCatching {
                        eldraftJson.decodeFromString<MapEvent>(frame.readText())
                    }.onSuccess { emit(it) }
                }
            }
        }
    }
}
