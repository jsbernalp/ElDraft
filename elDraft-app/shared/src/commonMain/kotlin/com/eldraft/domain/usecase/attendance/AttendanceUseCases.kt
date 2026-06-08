package com.eldraft.domain.usecase.attendance

import com.eldraft.data.models.AttendanceQr
import com.eldraft.data.models.AttendanceScanResult
import com.eldraft.domain.repository.AttendanceRepository

/** El organizador genera el QR de asistencia de su convocatoria. */
class GenerateAttendanceQrUseCase(
    private val repository: AttendanceRepository,
) {
    suspend operator fun invoke(convocatoryId: String): AttendanceQr {
        require(convocatoryId.isNotBlank()) { "Convocatoria inválida" }
        return repository.generateQr(convocatoryId)
    }
}

/** El jugador escanea el QR para registrar su asistencia. */
class ScanAttendanceUseCase(
    private val repository: AttendanceRepository,
) {
    suspend operator fun invoke(qrCode: String): AttendanceScanResult {
        require(qrCode.isNotBlank()) { "Código QR vacío" }
        return repository.scan(qrCode)
    }
}
