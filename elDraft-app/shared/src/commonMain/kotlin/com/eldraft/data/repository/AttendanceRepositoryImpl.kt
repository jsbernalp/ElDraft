package com.eldraft.data.repository

import com.eldraft.data.models.AttendanceQr
import com.eldraft.data.models.AttendanceScanResult
import com.eldraft.data.remote.AttendanceApi
import com.eldraft.domain.repository.AttendanceRepository

class AttendanceRepositoryImpl(
    private val attendanceApi: AttendanceApi,
) : AttendanceRepository {

    override suspend fun generateQr(convocatoryId: String): AttendanceQr =
        attendanceApi.generateQr(convocatoryId)

    override suspend fun scan(qrCode: String): AttendanceScanResult =
        attendanceApi.scan(qrCode)
}
