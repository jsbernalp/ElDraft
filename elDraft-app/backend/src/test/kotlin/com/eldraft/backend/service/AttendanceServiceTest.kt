package com.eldraft.backend.service

import com.eldraft.backend.attendance.QrTokenService
import com.eldraft.backend.repository.AttendanceRepository
import com.eldraft.backend.repository.ConvocatoryRecord
import com.eldraft.backend.repository.ConvocatoryRepository
import com.eldraft.backend.repository.PositionSlot
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AttendanceServiceTest {

    private val organizerId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()
    private val convocatoryId = UUID.randomUUID()
    private val qrTokens = QrTokenService(secret = "test-secret-1234567890")

    private fun convocatory(organizer: UUID = organizerId) = ConvocatoryRecord(
        id = convocatoryId, organizerId = organizer, lat = 6.2, lng = -75.5,
        addressText = null, slotsNeeded = 3, positionRequired = "Delantero",
        positionSlots = listOf(PositionSlot("Delantero", 3)),
        fee = 0.0, format = "Fútbol 5", ambiente = "Recocha", status = "active",
        scheduledAt = "2026-06-10T19:00:00",
    )

    private class FakeConvocatoryRepo(val record: ConvocatoryRecord?) : ConvocatoryRepository() {
        override fun findById(id: UUID): ConvocatoryRecord? = record
    }

    private class FakeAttendanceRepo(
        val approved: Boolean = true,
        val organizerOf: UUID? = null,
        var alreadyAttended: Boolean = false,
    ) : AttendanceRepository() {
        var recorded = false
        var recomputed = false
        override fun isApprovedPlayer(convocatoryId: UUID, playerId: UUID) = approved
        override fun isOrganizer(convocatoryId: UUID, userId: UUID) = userId == organizerOf
        override fun canAttend(convocatoryId: UUID, userId: UUID) =
            isApprovedPlayer(convocatoryId, userId) || isOrganizer(convocatoryId, userId)
        override fun hasAttendance(convocatoryId: UUID, playerId: UUID) = alreadyAttended
        override fun recordAttendance(convocatoryId: UUID, playerId: UUID, qrCode: String, expiresAt: LocalDateTime) {
            recorded = true
        }
        override fun recomputeAttendancePct(playerId: UUID) { recomputed = true }
    }

    private fun service(conv: ConvocatoryRepository, att: AttendanceRepository) =
        AttendanceService(conv, att, qrTokens)

    // --- generateQr ---

    @Test
    fun organizador_genera_qr() {
        val att = FakeAttendanceRepo(approved = false, organizerOf = organizerId)
        val svc = service(FakeConvocatoryRepo(convocatory()), att)
        val qr = svc.generateQr(convocatoryId, organizerId)
        assertTrue(qr.token.isNotBlank())
        assertEquals(600, qr.expiresInSeconds)
    }

    @Test
    fun jugador_aprobado_tambien_genera_qr() {
        // Ahora cualquier participante aprobado puede generar el QR (para que el
        // organizador lo escane y marque su presencia).
        val svc = service(FakeConvocatoryRepo(convocatory()), FakeAttendanceRepo(approved = true))
        val qr = svc.generateQr(convocatoryId, playerId)
        assertTrue(qr.token.isNotBlank())
    }

    @Test
    fun no_participante_no_genera_qr() {
        // Ni aprobado ni organizador → no puede generar.
        val att = FakeAttendanceRepo(approved = false, organizerOf = null)
        val svc = service(FakeConvocatoryRepo(convocatory()), att)
        assertFailsWith<AttendanceForbidden> { svc.generateQr(convocatoryId, playerId) }
    }

    @Test
    fun generar_qr_convocatoria_inexistente_falla() {
        val svc = service(FakeConvocatoryRepo(null), FakeAttendanceRepo())
        assertFailsWith<AttendanceNotFound> { svc.generateQr(convocatoryId, organizerId) }
    }

    // --- scan ---

    @Test
    fun jugador_aprobado_escanea_y_registra() {
        val att = FakeAttendanceRepo(approved = true)
        val svc = service(FakeConvocatoryRepo(convocatory()), att)
        val token = qrTokens.generate(convocatoryId.toString(), 600)
        val result = svc.scan(token, playerId)
        assertTrue(result.validated)
        assertTrue(att.recorded)
        assertTrue(att.recomputed)
    }

    @Test
    fun qr_invalido_falla() {
        val svc = service(FakeConvocatoryRepo(convocatory()), FakeAttendanceRepo())
        assertFailsWith<AttendanceInvalidQr> { svc.scan("token-basura", playerId) }
    }

    @Test
    fun qr_expirado_falla() {
        val svc = service(FakeConvocatoryRepo(convocatory()), FakeAttendanceRepo())
        val expired = qrTokens.generate(convocatoryId.toString(), -1)
        assertFailsWith<AttendanceInvalidQr> { svc.scan(expired, playerId) }
    }

    @Test
    fun organizador_escanea_y_registra_su_presencia() {
        // El organizador ya no se asume presente: escanea (un aprobado le generó
        // el QR) para registrar su asistencia.
        val att = FakeAttendanceRepo(approved = false, organizerOf = organizerId)
        val svc = service(FakeConvocatoryRepo(convocatory()), att)
        val token = qrTokens.generate(convocatoryId.toString(), 600)
        val result = svc.scan(token, organizerId)
        assertTrue(result.validated)
        assertTrue(att.recorded)
    }

    @Test
    fun no_participante_no_registra() {
        // Ni aprobado ni organizador → no puede escanear.
        val att = FakeAttendanceRepo(approved = false, organizerOf = null)
        val svc = service(FakeConvocatoryRepo(convocatory()), att)
        val token = qrTokens.generate(convocatoryId.toString(), 600)
        assertFailsWith<AttendanceForbidden> { svc.scan(token, playerId) }
    }

    @Test
    fun asistencia_duplicada_falla() {
        val att = FakeAttendanceRepo(approved = true, alreadyAttended = true)
        val svc = service(FakeConvocatoryRepo(convocatory()), att)
        val token = qrTokens.generate(convocatoryId.toString(), 600)
        assertFailsWith<AttendanceConflict> { svc.scan(token, playerId) }
    }
}
