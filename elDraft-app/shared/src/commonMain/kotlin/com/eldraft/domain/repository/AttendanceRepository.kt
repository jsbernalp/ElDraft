package com.eldraft.domain.repository

import com.eldraft.data.models.AttendanceQr
import com.eldraft.data.models.AttendanceScanResult
import com.eldraft.data.models.NoShowStatus

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
}
