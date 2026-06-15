package com.eldraft.domain.repository

import com.eldraft.data.models.AttendanceQr
import com.eldraft.data.models.AttendanceScanResult
import com.eldraft.data.models.NoShowStatus
import com.eldraft.data.models.PlayerAttendanceRow

/** Asistencia por QR (El Cromo / día del partido). */
interface AttendanceRepository {

    /** Cualquier participante (organizador o aprobado) genera el QR (con expiración). */
    suspend fun generateQr(convocatoryId: String): AttendanceQr

    /** El participante escanea un QR para registrar su asistencia. */
    suspend fun scan(qrCode: String): AttendanceScanResult

    /** Un asistente reporta que el organizador no se presentó. */
    suspend fun reportNoShow(convocatoryId: String): NoShowStatus

    /** Estado actual del reporte de no-show para una convocatoria. */
    suspend fun noShowStatus(convocatoryId: String): NoShowStatus

    /** El organizador obtiene la lista de aprobados con su estado de asistencia. */
    suspend fun attendanceList(convocatoryId: String): List<PlayerAttendanceRow>

    /** El organizador declara quién no llegó; devuelve la lista actualizada. */
    suspend fun declareAttendance(
        convocatoryId: String,
        absentPlayerIds: List<String>,
    ): List<PlayerAttendanceRow>
}
