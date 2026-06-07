package com.eldraft.data.remote

import com.eldraft.core.config.ApiConfig
import com.eldraft.core.network.AuthTokenProvider
import com.eldraft.core.network.BaseApi
import com.eldraft.core.network.eldraftJson
import com.eldraft.data.models.Convocatory
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

    suspend fun create(request: Convocatory): Convocatory =
        client.post("$baseUrl/api/v1/convocatories") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun getMine(): List<Convocatory> =
        client.get("$baseUrl/api/v1/convocatories/mine") { auth() }.body()

    /** WebSocket — pines del mapa en tiempo real. */
    fun observeMapEvents(lat: Double, lng: Double, radius: Double = 5000.0): Flow<MapEvent> = flow {
        client.webSocket("$wsBaseUrl/ws/map?lat=$lat&lng=$lng&radius=$radius") {
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
