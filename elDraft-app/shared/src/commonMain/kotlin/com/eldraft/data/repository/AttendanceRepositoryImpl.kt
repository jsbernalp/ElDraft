package com.eldraft.data.repository

import com.eldraft.data.models.AttendanceQr
import com.eldraft.data.models.AttendanceScanResult
import com.eldraft.data.models.NoShowStatus
import com.eldraft.data.models.PlayerAttendanceRow
import com.eldraft.data.remote.AttendanceApi
import com.eldraft.domain.repository.AttendanceRepository

class AttendanceRepositoryImpl(
    private val attendanceApi: AttendanceApi,
) : AttendanceRepository {

    override suspend fun generateQr(convocatoryId: String): AttendanceQr =
        attendanceApi.generateQr(convocatoryId)

    override suspend fun scan(qrCode: String): AttendanceScanResult =
        attendanceApi.scan(qrCode)

    override suspend fun reportNoShow(convocatoryId: String): NoShowStatus =
        attendanceApi.reportNoShow(convocatoryId)

    override suspend fun noShowStatus(convocatoryId: String): NoShowStatus =
        attendanceApi.noShowStatus(convocatoryId)

    override suspend fun attendanceList(convocatoryId: String): List<PlayerAttendanceRow> =
        attendanceApi.attendanceList(convocatoryId)

    override suspend fun declareAttendance(
        convocatoryId: String,
        absentPlayerIds: List<String>,
    ): List<PlayerAttendanceRow> =
        attendanceApi.declareAttendance(convocatoryId, absentPlayerIds)
}
