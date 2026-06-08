package com.eldraft.domain.repository

import com.eldraft.data.models.AttendanceQr
import com.eldraft.data.models.AttendanceScanResult

/** Asistencia por QR (El Cromo / día del partido). */
interface AttendanceRepository {

    /** El organizador genera el QR de su convocatoria (con expiración). */
    suspend fun generateQr(convocatoryId: String): AttendanceQr

    /** El jugador escanea un QR para registrar su asistencia. */
    suspend fun scan(qrCode: String): AttendanceScanResult
}
