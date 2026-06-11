package com.eldraft.data.remote

import com.eldraft.core.config.ApiConfig
import com.eldraft.core.network.AuthTokenProvider
import com.eldraft.core.network.BaseApi
import com.eldraft.data.models.AttendanceQr
import com.eldraft.data.models.AttendanceScanResult
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class AttendanceApi(
    client: HttpClient,
    config: ApiConfig,
    tokenProvider: AuthTokenProvider,
) : BaseApi(client, config, tokenProvider) {

    suspend fun generateQr(convocatoryId: String): AttendanceQr =
        client.post("$baseUrl/api/v1/attendance/generate-qr") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(mapOf("convocatoryId" to convocatoryId))
        }.body()

    suspend fun scan(qrCode: String): AttendanceScanResult =
        client.post("$baseUrl/api/v1/attendance/scan") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(mapOf("qrCode" to qrCode))
        }.body()
}
