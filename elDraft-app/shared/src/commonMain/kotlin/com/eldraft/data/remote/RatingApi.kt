package com.eldraft.data.remote

import com.eldraft.core.config.ApiConfig
import com.eldraft.core.network.AuthTokenProvider
import com.eldraft.core.network.BaseApi
import com.eldraft.data.models.Teammate
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
private data class RatingRequestBody(
    val convocatoryId: String,
    val ratedPlayerId: String,
    val sportsmanshipScore: Int,
)

class RatingApi(
    client: HttpClient,
    config: ApiConfig,
    tokenProvider: AuthTokenProvider,
) : BaseApi(client, config, tokenProvider) {

    suspend fun getTeammates(convocatoryId: String): List<Teammate> =
        client.get("$baseUrl/api/v1/convocatories/$convocatoryId/teammates") { auth() }.body()

    suspend fun submitRating(convocatoryId: String, ratedPlayerId: String, score: Int): Map<String, String> =
        client.post("$baseUrl/api/v1/ratings") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(RatingRequestBody(convocatoryId, ratedPlayerId, score))
        }.body()
}
