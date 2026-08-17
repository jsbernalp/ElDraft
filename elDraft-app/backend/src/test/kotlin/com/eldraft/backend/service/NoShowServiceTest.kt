package com.eldraft.backend.service

import com.eldraft.backend.repository.AttendanceDeclarationRepository
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
     * Repo falso en memoria. [approved] son los jugadores convocados (base del
     * consenso y votantes potenciales). [reporters] acumula quién ya reportó.
     * Registra los efectos aplicados (marca + penalización) para verificarlos.
     */
    private class FakeNoShowRepo(
        val organizer: UUID,
        val scheduledAt: LocalDateTime,
        val approved: MutableSet<UUID>,
        var noShowFlag: Boolean = false,
        val confirmed: Boolean = false,
    ) : NoShowRepository() {
        val reporters = mutableSetOf<UUID>()
        val marksNoShow = mutableSetOf<UUID>()
        var marked = false
        var penalized = false

        override fun context(convocatoryId: UUID) =
            NoShowContext(
                organizerId = organizer,
                scheduledAt = scheduledAt,
                organizerNoShow = noShowFlag,
                organizerConfirmed = confirmed,
            )
        override fun isApprovedPlayer(convocatoryId: UUID, userId: UUID) = userId in approved
        override fun hasReported(convocatoryId: UUID, userId: UUID) = userId in reporters
        override fun isMarkedNoShow(convocatoryId: UUID, userId: UUID) = userId in marksNoShow
        override fun insertReport(convocatoryId: UUID, reporterId: UUID) { reporters.add(reporterId) }
        override fun tally(convocatoryId: UUID) =
            NoShowTally(reports = reporters.size, approved = approved.size)
        override fun markNoShow(convocatoryId: UUID) { marked = true; noShowFlag = true }
        override fun penalizeOrganizer(organizerId: UUID) { penalized = true }
    }

    /**
     * Fake de declaraciones para verificar que al alcanzar consenso se revierten
     * las marcas de no-show de convocados (el organizador no estuvo en el partido).
     */
    private class FakeDeclarationRepo : AttendanceDeclarationRepository() {
        var cleared = false
        override fun clearMarksAndRecompute(convocatoryId: UUID) { cleared = true }
    }

    /** Reloj fijo en [at] para controlar la ventana de reporte. */
    private fun clockAt(at: LocalDateTime): Clock =
        Clock.fixed(at.toInstant(ZoneOffset.UTC), ZoneId.of("UTC"))

    /** Crea el servicio con un fake de declaraciones fresco (accesible vía retorno). */
    private fun service(
        repo: NoShowRepository,
        at: LocalDateTime,
        decl: FakeDeclarationRepo = FakeDeclarationRepo(),
        // Por defecto el organizador NO tiene asistencia registrada: es el caso
        // que ejercitan casi todos los tests (si estuvo, no hay nada que reportar).
        lifecycle: MatchLifecycle = inertMatchLifecycle(),
    ) = NoShowService(repo, decl, lifecycle, clockAt(at))

    /** MatchLifecycle que da por asistidos a los usuarios indicados. */
    private fun lifecycleWithAttendees(vararg attendees: UUID) = MatchLifecycle(
        ratings = object : com.eldraft.backend.repository.RatingRepository() {
            override fun attended(convocatoryId: UUID, userId: UUID) = userId in attendees
        },
        declarations = FakeDeclarationRepo(),
        noShow = FakeNoShowRepo(organizerId, scheduledAt, mutableSetOf()),
    )

    private fun repo(approved: Set<UUID>) =
        FakeNoShowRepo(organizerId, scheduledAt, approved.toMutableSet())

    // Dentro de la ventana: 1h tras el inicio (>15min, <48h).
    private val withinWindow = scheduledAt.plusHours(1)

    // --- Ventana ---

    @Test
    fun reporte_antes_de_la_apertura_falla() {
        val reporter = UUID.randomUUID()
        val r = repo(setOf(reporter))
        // 2 min tras el inicio: aún no abre (apertura a +15min).
        val svc = service(r, scheduledAt.plusMinutes(2))
        assertFailsWith<NoShowWindowClosed> { svc.report(convocatoryId, reporter) }
    }

    @Test
    fun reporte_tras_el_cierre_falla() {
        val reporter = UUID.randomUUID()
        val r = repo(setOf(reporter))
        // 49h tras el inicio: ventana cerrada (cierre a +48h).
        val svc = service(r, scheduledAt.plusHours(49))
        assertFailsWith<NoShowWindowClosed> { svc.report(convocatoryId, reporter) }
    }

    // --- Permisos ---

    @Test
    fun no_aprobado_no_puede_reportar() {
        val outsider = UUID.randomUUID()
        val r = repo(approved = emptySet()) // outsider no es jugador convocado
        val svc = service(r, withinWindow)
        assertFailsWith<NoShowForbidden> { svc.report(convocatoryId, outsider) }
    }

    @Test
    fun organizador_no_se_reporta_a_si_mismo() {
        val r = repo(approved = emptySet())
        val svc = service(r, withinWindow)
        assertFailsWith<NoShowForbidden> { svc.report(convocatoryId, organizerId) }
    }

    @Test
    fun no_se_reporta_si_el_organizador_ya_confirmo_asistencia() {
        // El organizador ya declaró la asistencia (prueba de que estuvo): nadie
        // puede reportar que no llegó.
        val reporter = UUID.randomUUID()
        val r = FakeNoShowRepo(
            organizerId, scheduledAt, mutableSetOf(reporter), confirmed = true,
        )
        val svc = service(r, withinWindow)
        assertFailsWith<NoShowConflict> { svc.report(convocatoryId, reporter) }
        // Y el status no ofrece el botón.
        assertFalse(svc.status(convocatoryId, reporter).canReport)
    }

    @Test
    fun no_se_reporta_si_el_organizador_registro_su_asistencia() {
        // Escaneó un QR del partido: estuvo allí. Prueba distinta de declarar la
        // asistencia de los demás, y hasta ahora no se usaba.
        val reporter = UUID.randomUUID()
        val r = repo(setOf(reporter))
        val svc = service(r, withinWindow, lifecycle = lifecycleWithAttendees(organizerId))
        assertFailsWith<NoShowConflict> { svc.report(convocatoryId, reporter) }
        assertFalse(svc.status(convocatoryId, reporter).canReport)
    }

    @Test
    fun la_asistencia_del_jugador_no_bloquea_el_reporte() {
        // Asimetría deliberada: el QR solo lleva el id del partido y cualquier
        // participante puede generarlo, así que dos jugadores pueden validarse
        // entre ellos sin que el organizador aparezca. Ese es justo el caso que
        // este reporte existe para cubrir.
        val reporter = UUID.randomUUID()
        val r = repo(setOf(reporter))
        val svc = service(r, withinWindow, lifecycle = lifecycleWithAttendees(reporter))
        assertTrue(svc.status(convocatoryId, reporter).canReport)
    }

    @Test
    fun reporte_duplicado_falla() {
        val reporter = UUID.randomUUID()
        val r = repo(setOf(reporter))
        val svc = service(r, withinWindow)
        svc.report(convocatoryId, reporter)
        assertFailsWith<NoShowConflict> { svc.report(convocatoryId, reporter) }
    }

    // --- Consenso ---

    @Test
    fun un_solo_voto_no_alcanza_consenso() {
        // 3 aprobados; 1 voto no es mayoría (>1.5).
        val r1 = UUID.randomUUID()
        val r = repo(setOf(r1, UUID.randomUUID(), UUID.randomUUID()))
        val svc = service(r, withinWindow)
        val status = svc.report(convocatoryId, r1)
        assertFalse(status.consensusReached)
        assertFalse(r.marked)
        assertFalse(r.penalized)
    }

    @Test
    fun mayoria_alcanza_consenso_y_aplica_efectos() {
        // 3 aprobados; 2 votos superan la mayoría (>1.5) → consenso.
        val r1 = UUID.randomUUID()
        val r2 = UUID.randomUUID()
        val r3 = UUID.randomUUID()
        val r = repo(setOf(r1, r2, r3))
        val decl = FakeDeclarationRepo()
        val svc = service(r, withinWindow, decl)

        val s1 = svc.report(convocatoryId, r1)
        assertFalse(s1.consensusReached)
        assertFalse(decl.cleared)

        val s2 = svc.report(convocatoryId, r2)
        assertTrue(s2.consensusReached)
        assertTrue(r.marked)
        assertTrue(r.penalized)
        // Al marcar al organizador como ausente, se revierten las marcas que él
        // hubiera puesto sobre los convocados (no estuvo en el partido).
        assertTrue(decl.cleared)
    }

    // --- status ---

    @Test
    fun status_refleja_si_puede_reportar() {
        val reporter = UUID.randomUUID()
        val r = repo(setOf(reporter))
        val svc = service(r, withinWindow)
        val status = svc.status(convocatoryId, reporter)
        assertTrue(status.canReport)
        assertFalse(status.alreadyReported)
        assertEquals(1, status.attendees)
        assertFalse(status.markedNoShow)
    }

    @Test
    fun status_avisa_si_el_convocado_fue_marcado_ausente() {
        val reporter = UUID.randomUUID()
        val r = repo(setOf(reporter))
        r.marksNoShow.add(reporter)
        val svc = service(r, withinWindow)
        assertTrue(svc.status(convocatoryId, reporter).markedNoShow)
    }
}
