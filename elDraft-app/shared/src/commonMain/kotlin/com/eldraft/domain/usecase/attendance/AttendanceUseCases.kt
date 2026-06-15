package com.eldraft.domain.usecase.attendance

import com.eldraft.data.models.AttendanceQr
import com.eldraft.data.models.AttendanceScanResult
import com.eldraft.data.models.NoShowStatus
import com.eldraft.data.models.PlayerAttendanceRow
import com.eldraft.domain.repository.AttendanceRepository

/**
 * Genera el QR de asistencia de la convocatoria. Lo puede hacer cualquier
 * participante (organizador o jugador aprobado): un aprobado puede mostrarle el
 * QR al organizador para que marque su propia presencia.
 */
class GenerateAttendanceQrUseCase(
    private val repository: AttendanceRepository,
) {
    suspend operator fun invoke(convocatoryId: String): AttendanceQr {
        require(convocatoryId.isNotBlank()) { "Convocatoria inválida" }
        return repository.generateQr(convocatoryId)
    }
}

/** El participante escanea el QR para registrar su asistencia. */
class ScanAttendanceUseCase(
    private val repository: AttendanceRepository,
) {
    suspend operator fun invoke(qrCode: String): AttendanceScanResult {
        require(qrCode.isNotBlank()) { "Código QR vacío" }
        return repository.scan(qrCode)
    }
}

/** Un asistente reporta que el organizador no se presentó al partido. */
class ReportOrganizerNoShowUseCase(
    private val repository: AttendanceRepository,
) {
    suspend operator fun invoke(convocatoryId: String): NoShowStatus {
        require(convocatoryId.isNotBlank()) { "Convocatoria inválida" }
        return repository.reportNoShow(convocatoryId)
    }
}

/** Consulta el estado del reporte de no-show (para pintar el botón). */
class GetNoShowStatusUseCase(
    private val repository: AttendanceRepository,
) {
    suspend operator fun invoke(convocatoryId: String): NoShowStatus {
        require(convocatoryId.isNotBlank()) { "Convocatoria inválida" }
        return repository.noShowStatus(convocatoryId)
    }
}

/** El organizador obtiene la lista de aprobados con su estado de asistencia. */
class GetAttendanceListUseCase(
    private val repository: AttendanceRepository,
) {
    suspend operator fun invoke(convocatoryId: String): List<PlayerAttendanceRow> {
        require(convocatoryId.isNotBlank()) { "Convocatoria inválida" }
        return repository.attendanceList(convocatoryId)
    }
}

/**
 * El organizador declara quién de sus convocados no llegó. La lista de ausentes
 * puede estar vacía (todos presentes). Devuelve la lista actualizada.
 */
class DeclareAttendanceUseCase(
    private val repository: AttendanceRepository,
) {
    suspend operator fun invoke(
        convocatoryId: String,
        absentPlayerIds: List<String>,
    ): List<PlayerAttendanceRow> {
        require(convocatoryId.isNotBlank()) { "Convocatoria inválida" }
        return repository.declareAttendance(convocatoryId, absentPlayerIds)
    }
}
