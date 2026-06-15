package com.eldraft.data.remote

import com.eldraft.core.config.ApiConfig
import com.eldraft.core.network.AuthTokenProvider
import com.eldraft.core.network.BaseApi
import com.eldraft.data.models.AttendanceQr
import com.eldraft.data.models.AttendanceScanResult
import com.eldraft.data.models.NoShowStatus
import com.eldraft.data.models.PlayerAttendanceRow
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

    /** Un asistente reporta que el organizador no se presentó. */
    suspend fun reportNoShow(convocatoryId: String): NoShowStatus =
        client.post("$baseUrl/api/v1/convocatories/$convocatoryId/report-no-show") {
            auth()
        }.body()

    /** Estado del reporte de no-show para pintar el botón. */
    suspend fun noShowStatus(convocatoryId: String): NoShowStatus =
        client.get("$baseUrl/api/v1/convocatories/$convocatoryId/no-show-status") {
            auth()
        }.body()

    /** El organizador obtiene la lista de aprobados con su estado de asistencia. */
    suspend fun attendanceList(convocatoryId: String): List<PlayerAttendanceRow> =
        client.get("$baseUrl/api/v1/convocatories/$convocatoryId/attendance-list") {
            auth()
        }.body()

    /** El organizador declara quién no llegó (reemplaza la lista previa). */
    suspend fun declareAttendance(
        convocatoryId: String,
        absentPlayerIds: List<String>,
    ): List<PlayerAttendanceRow> =
        client.post("$baseUrl/api/v1/convocatories/$convocatoryId/declare-attendance") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(mapOf("absentPlayerIds" to absentPlayerIds))
        }.body()
}
