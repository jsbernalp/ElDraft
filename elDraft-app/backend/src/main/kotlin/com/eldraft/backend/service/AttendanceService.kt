package com.eldraft.backend.service

import com.eldraft.backend.attendance.QrTokenService
import com.eldraft.backend.repository.AttendanceRepository
import com.eldraft.backend.repository.ConvocatoryRepository
import java.time.LocalDateTime
import java.util.UUID

/** Errores de dominio de asistencia (las rutas los mapean a HTTP). */
class AttendanceForbidden(message: String) : RuntimeException(message)
class AttendanceConflict(message: String) : RuntimeException(message)
class AttendanceNotFound(message: String) : RuntimeException(message)
class AttendanceInvalidQr(message: String) : RuntimeException(message)

/** QR generado para una convocatoria. */
data class GeneratedQr(val token: String, val expiresInSeconds: Long)

/** Resultado de un escaneo validado. */
data class ScanResult(val convocatoryId: String, val playerId: String, val validated: Boolean)

/**
 * Lógica de asistencia por QR:
 *  - Cualquier participante (jugador aprobado u organizador) genera el QR de la
 *    convocatoria (con expiración).
 *  - Un participante lo escanea para registrar asistencia, lo que recalcula su
 *    attendance_pct. El organizador también escanea (un aprobado le genera el QR),
 *    para que su presencia deje de asumirse.
 */
class AttendanceService(
    private val convocatories: ConvocatoryRepository,
    private val attendance: AttendanceRepository,
    private val qrTokens: QrTokenService,
) {
    /**
     * Genera el QR de la convocatoria. Lo puede hacer cualquier participante:
     * el organizador o un jugador aprobado (así un aprobado puede mostrarle el
     * QR al organizador para que marque su asistencia).
     */
    fun generateQr(convocatoryId: UUID, requesterId: UUID): GeneratedQr {
        convocatories.findById(convocatoryId)
            ?: throw AttendanceNotFound("Convocatoria no encontrada")
        if (!attendance.canAttend(convocatoryId, requesterId)) {
            throw AttendanceForbidden("Solo los participantes pueden generar el QR")
        }
        val token = qrTokens.generate(convocatoryId.toString(), QR_TTL_SECONDS)
        return GeneratedQr(token = token, expiresInSeconds = QR_TTL_SECONDS)
    }

    /**
     * Valida el QR escaneado por un participante y registra su asistencia.
     * Reglas: token válido y vigente, participante (aprobado u organizador) en la
     * convocatoria, sin asistencia previa.
     */
    fun scan(token: String, playerId: UUID): ScanResult {
        val convocatoryId = when (val v = qrTokens.validate(token)) {
            is QrTokenService.Validation.Valid -> UUID.fromString(v.convocatoryId)
            QrTokenService.Validation.Expired -> throw AttendanceInvalidQr("El código QR expiró. Pide al organizador uno nuevo.")
            QrTokenService.Validation.BadSignature,
            QrTokenService.Validation.Malformed -> throw AttendanceInvalidQr("Código QR inválido")
        }

        convocatories.findById(convocatoryId)
            ?: throw AttendanceNotFound("Convocatoria no encontrada")

        if (!attendance.canAttend(convocatoryId, playerId)) {
            throw AttendanceForbidden("No participas en esta convocatoria")
        }
        if (attendance.hasAttendance(convocatoryId, playerId)) {
            throw AttendanceConflict("Ya registraste tu asistencia a este partido")
        }

        // qrCode único por jugador (respeta el índice único de la columna).
        attendance.recordAttendance(
            convocatoryId = convocatoryId,
            playerId = playerId,
            qrCode = "$token#$playerId",
            expiresAt = LocalDateTime.now().plusSeconds(QR_TTL_SECONDS),
        )
        attendance.recomputeAttendancePct(playerId)

        return ScanResult(
            convocatoryId = convocatoryId.toString(),
            playerId = playerId.toString(),
            validated = true,
        )
    }

    private companion object {
        const val QR_TTL_SECONDS = 600L // 10 minutos
    }
}
