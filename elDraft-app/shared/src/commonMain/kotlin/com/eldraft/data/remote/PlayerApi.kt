package com.eldraft.data.remote

import com.eldraft.core.config.ApiConfig
import com.eldraft.core.network.AuthTokenProvider
import com.eldraft.core.network.BaseApi
import com.eldraft.data.models.PlayerProfile
import com.eldraft.data.models.UpdateProfileRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class PlayerApi(
    client: HttpClient,
    config: ApiConfig,
    tokenProvider: AuthTokenProvider,
) : BaseApi(client, config, tokenProvider) {

    suspend fun getProfile(playerId: String): PlayerProfile =
        client.get("$baseUrl/api/v1/players/$playerId/profile") { auth() }.body()

    suspend fun updateProfile(playerId: String, request: UpdateProfileRequest): PlayerProfile =
        client.put("$baseUrl/api/v1/players/$playerId/profile") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
}
