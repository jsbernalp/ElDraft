package com.eldraft.backend.service

import com.eldraft.backend.repository.NoShowContext
import com.eldraft.backend.repository.NoShowRepository
import com.eldraft.backend.repository.NoShowTally
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoShowServiceTest {

    private val convocatoryId = UUID.randomUUID()
    private val organizerId = UUID.randomUUID()
    private val scheduledAt = LocalDateTime.of(2026, 6, 10, 19, 0, 0)

    /**
     * Repo falso en memoria. [attendees] son los asistentes validados (votantes
     * potenciales). [reporters] acumula quién ya reportó. Registra los efectos
     * aplicados (marca + penalización) para verificarlos.
     */
    private class FakeNoShowRepo(
        val organizer: UUID,
        val scheduledAt: LocalDateTime,
        val attendees: MutableSet<UUID>,
        var noShowFlag: Boolean = false,
    ) : NoShowRepository() {
        val reporters = mutableSetOf<UUID>()
        var marked = false
        var penalized = false

        override fun context(convocatoryId: UUID) =
            NoShowContext(organizerId = organizer, scheduledAt = scheduledAt, organizerNoShow = noShowFlag)
        override fun attended(convocatoryId: UUID, userId: UUID) = userId in attendees
        override fun hasReported(convocatoryId: UUID, userId: UUID) = userId in reporters
        override fun insertReport(convocatoryId: UUID, reporterId: UUID) { reporters.add(reporterId) }
        override fun tally(convocatoryId: UUID) =
            NoShowTally(reports = reporters.size, attendees = attendees.size)
        override fun markNoShow(convocatoryId: UUID) { marked = true; noShowFlag = true }
        override fun penalizeOrganizer(organizerId: UUID) { penalized = true }
    }

    /** Reloj fijo en [at] para controlar la ventana de reporte. */
    private fun clockAt(at: LocalDateTime): Clock =
        Clock.fixed(at.toInstant(ZoneOffset.UTC), ZoneId.of("UTC"))

    private fun repo(attendees: Set<UUID>) =
        FakeNoShowRepo(organizerId, scheduledAt, attendees.toMutableSet())

    // Dentro de la ventana: 1h tras el inicio (>15min, <48h).
    private val withinWindow = scheduledAt.plusHours(1)

    // --- Ventana ---

    @Test
    fun reporte_antes_de_la_apertura_falla() {
        val reporter = UUID.randomUUID()
        val r = repo(setOf(reporter))
        // 10 min tras el inicio: aún no abre (apertura a +15min).
        val svc = NoShowService(r, clockAt(scheduledAt.plusMinutes(10)))
        assertFailsWith<NoShowWindowClosed> { svc.report(convocatoryId, reporter) }
    }

    @Test
    fun reporte_tras_el_cierre_falla() {
        val reporter = UUID.randomUUID()
        val r = repo(setOf(reporter))
        // 49h tras el inicio: ventana cerrada (cierre a +48h).
        val svc = NoShowService(r, clockAt(scheduledAt.plusHours(49)))
        assertFailsWith<NoShowWindowClosed> { svc.report(convocatoryId, reporter) }
    }

    // --- Permisos ---

    @Test
    fun no_asistente_no_puede_reportar() {
        val outsider = UUID.randomUUID()
        val r = repo(attendees = emptySet()) // outsider no asistió
        val svc = NoShowService(r, clockAt(withinWindow))
        assertFailsWith<NoShowForbidden> { svc.report(convocatoryId, outsider) }
    }

    @Test
    fun organizador_no_se_reporta_a_si_mismo() {
        val r = repo(attendees = setOf(organizerId))
        val svc = NoShowService(r, clockAt(withinWindow))
        assertFailsWith<NoShowForbidden> { svc.report(convocatoryId, organizerId) }
    }

    @Test
    fun reporte_duplicado_falla() {
        val reporter = UUID.randomUUID()
        val r = repo(setOf(reporter))
        val svc = NoShowService(r, clockAt(withinWindow))
        svc.report(convocatoryId, reporter)
        assertFailsWith<NoShowConflict> { svc.report(convocatoryId, reporter) }
    }

    // --- Consenso ---

    @Test
    fun un_solo_voto_no_alcanza_consenso() {
        // 3 asistentes; 1 voto no es mayoría (>1.5).
        val r1 = UUID.randomUUID()
        val r = repo(setOf(r1, UUID.randomUUID(), UUID.randomUUID()))
        val svc = NoShowService(r, clockAt(withinWindow))
        val status = svc.report(convocatoryId, r1)
        assertFalse(status.consensusReached)
        assertFalse(r.marked)
        assertFalse(r.penalized)
    }

    @Test
    fun mayoria_alcanza_consenso_y_aplica_efectos() {
        // 3 asistentes; 2 votos superan la mayoría (>1.5) → consenso.
        val r1 = UUID.randomUUID()
        val r2 = UUID.randomUUID()
        val r3 = UUID.randomUUID()
        val r = repo(setOf(r1, r2, r3))
        val svc = NoShowService(r, clockAt(withinWindow))

        val s1 = svc.report(convocatoryId, r1)
        assertFalse(s1.consensusReached)

        val s2 = svc.report(convocatoryId, r2)
        assertTrue(s2.consensusReached)
        assertTrue(r.marked)
        assertTrue(r.penalized)
    }

    // --- status ---

    @Test
    fun status_refleja_si_puede_reportar() {
        val reporter = UUID.randomUUID()
        val r = repo(setOf(reporter))
        val svc = NoShowService(r, clockAt(withinWindow))
        val status = svc.status(convocatoryId, reporter)
        assertTrue(status.canReport)
        assertFalse(status.alreadyReported)
        assertEquals(1, status.attendees)
    }
}
