package com.eldraft.data.remote

import com.eldraft.core.config.ApiConfig
import com.eldraft.core.network.AuthTokenProvider
import com.eldraft.core.network.BaseApi
import com.eldraft.data.models.Postulation
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class PostulationApi(
    client: HttpClient,
    config: ApiConfig,
    tokenProvider: AuthTokenProvider,
) : BaseApi(client, config, tokenProvider) {

    suspend fun apply(convocatoryId: String): Postulation =
        client.post("$baseUrl/api/v1/convocatories/$convocatoryId/apply") { auth() }.body()

    suspend fun getApplicants(convocatoryId: String): List<Postulation> =
        client.get("$baseUrl/api/v1/convocatories/$convocatoryId/applicants") { auth() }.body()

    suspend fun approve(postulationId: String): Map<String, String> =
        client.put("$baseUrl/api/v1/postulations/$postulationId/approve") { auth() }.body()

    suspend fun reject(postulationId: String): Map<String, String> =
        client.put("$baseUrl/api/v1/postulations/$postulationId/reject") { auth() }.body()
}
