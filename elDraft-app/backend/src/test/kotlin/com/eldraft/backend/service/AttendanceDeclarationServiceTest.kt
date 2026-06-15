package com.eldraft.backend.service

import com.eldraft.backend.repository.AttendanceDeclarationRepository
import com.eldraft.backend.repository.DeclarationContext
import com.eldraft.backend.repository.PlayerAttendanceRow
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

class AttendanceDeclarationServiceTest {

    private val convocatoryId = UUID.randomUUID()
    private val organizerId = UUID.randomUUID()
    private val scheduledAt = LocalDateTime.of(2026, 6, 10, 19, 0, 0)

    /**
     * Repo falso en memoria. [players] son los aprobados con su estado de escaneo.
     * Las marcas de no-show se guardan en [marks]; registra qué jugadores fueron
     * recomputados para verificar que la penalización (reversible) se aplica.
     */
    private class FakeRepo(
        val organizer: UUID,
        val scheduledAt: LocalDateTime,
        var players: List<PlayerAttendanceRow>,
        val organizerNoShow: Boolean = false,
    ) : AttendanceDeclarationRepository() {
        var marks: Set<UUID> = players.filter { it.markedNoShow }.map { it.playerId }.toSet()
        val recomputedAttendance = mutableSetOf<UUID>()
        val recomputedResponsibility = mutableSetOf<UUID>()
        var confirmed = false

        override fun context(convocatoryId: UUID) =
            DeclarationContext(
                organizerId = organizer,
                scheduledAt = scheduledAt,
                organizerNoShow = organizerNoShow,
            )

        override fun approvedPlayers(convocatoryId: UUID): List<PlayerAttendanceRow> =
            players.map { it.copy(markedNoShow = it.playerId in marks) }

        override fun replaceMarks(convocatoryId: UUID, absentPlayerIds: Set<UUID>): Set<UUID> {
            val affected = marks + absentPlayerIds
            marks = absentPlayerIds
            return affected
        }

        override fun recomputeAttendance(playerId: UUID) { recomputedAttendance.add(playerId) }
        override fun recomputeResponsibility(playerId: UUID) { recomputedResponsibility.add(playerId) }
        override fun markOrganizerConfirmed(convocatoryId: UUID) { confirmed = true }
    }

    private fun clockAt(at: LocalDateTime): Clock =
        Clock.fixed(at.toInstant(ZoneOffset.UTC), ZoneId.of("UTC"))

    private fun row(scanned: Boolean = false, markedNoShow: Boolean = false) = PlayerAttendanceRow(
        playerId = UUID.randomUUID(),
        name = "Jugador",
        avatarUrl = null,
        positionPrimary = null,
        scanned = scanned,
        markedNoShow = markedNoShow,
    )

    // Tras el cierre del partido (cierre a +45min).
    private val afterClose = scheduledAt.plusMinutes(50)

    // --- Permisos ---

    @Test
    fun solo_el_organizador_declara() {
        val p = row()
        val repo = FakeRepo(organizerId, scheduledAt, listOf(p))
        val svc = AttendanceDeclarationService(repo, clockAt(afterClose))
        val intruso = UUID.randomUUID()
        assertFailsWith<DeclarationForbidden> { svc.declare(convocatoryId, intruso, setOf(p.playerId)) }
    }

    @Test
    fun convocatoria_inexistente_falla() {
        val repo = object : AttendanceDeclarationRepository() {
            override fun context(convocatoryId: UUID): DeclarationContext? = null
        }
        val svc = AttendanceDeclarationService(repo, clockAt(afterClose))
        assertFailsWith<DeclarationNotFound> { svc.declare(convocatoryId, organizerId, emptySet()) }
    }

    @Test
    fun organizador_marcado_no_show_no_puede_declarar() {
        // El consenso lo marcó a él como ausente: no estuvo, no da fe de nadie.
        val p = row()
        val repo = FakeRepo(organizerId, scheduledAt, listOf(p), organizerNoShow = true)
        val svc = AttendanceDeclarationService(repo, clockAt(afterClose))
        assertFailsWith<DeclarationForbidden> {
            svc.declare(convocatoryId, organizerId, setOf(p.playerId))
        }
    }

    @Test
    fun organizador_marcado_no_show_no_ve_la_lista() {
        val repo = FakeRepo(organizerId, scheduledAt, listOf(row()), organizerNoShow = true)
        val svc = AttendanceDeclarationService(repo, clockAt(afterClose))
        assertFailsWith<DeclarationForbidden> {
            svc.attendanceList(convocatoryId, organizerId)
        }
    }

    // --- Ventana ---

    @Test
    fun declarar_antes_del_cierre_falla() {
        val p = row()
        val repo = FakeRepo(organizerId, scheduledAt, listOf(p))
        // 5 min tras el inicio: el partido aún no cierra (cierre a +45min).
        val svc = AttendanceDeclarationService(repo, clockAt(scheduledAt.plusMinutes(5)))
        assertFailsWith<DeclarationWindowClosed> { svc.declare(convocatoryId, organizerId, setOf(p.playerId)) }
    }

    // --- Validación de la lista ---

    @Test
    fun no_se_puede_marcar_a_quien_escaneo() {
        val presente = row(scanned = true)
        val repo = FakeRepo(organizerId, scheduledAt, listOf(presente))
        val svc = AttendanceDeclarationService(repo, clockAt(afterClose))
        assertFailsWith<DeclarationForbidden> {
            svc.declare(convocatoryId, organizerId, setOf(presente.playerId))
        }
    }

    @Test
    fun no_se_puede_marcar_a_un_no_aprobado() {
        val p = row()
        val repo = FakeRepo(organizerId, scheduledAt, listOf(p))
        val svc = AttendanceDeclarationService(repo, clockAt(afterClose))
        val ajeno = UUID.randomUUID()
        assertFailsWith<DeclarationForbidden> { svc.declare(convocatoryId, organizerId, setOf(ajeno)) }
    }

    // --- Declaración válida ---

    @Test
    fun declarar_marca_y_recalcula_a_los_afectados() {
        val ausente = row()
        val presente = row()
        val repo = FakeRepo(organizerId, scheduledAt, listOf(ausente, presente))
        val svc = AttendanceDeclarationService(repo, clockAt(afterClose))

        val result = svc.declare(convocatoryId, organizerId, setOf(ausente.playerId))

        assertEquals(setOf(ausente.playerId), repo.marks)
        assertTrue(ausente.playerId in repo.recomputedAttendance)
        assertTrue(ausente.playerId in repo.recomputedResponsibility)
        assertTrue(result.first { it.playerId == ausente.playerId }.markedNoShow)
        assertFalse(result.first { it.playerId == presente.playerId }.markedNoShow)
        // Declarar confirma la presencia del organizador (bloquea reportes en su contra).
        assertTrue(repo.confirmed)
    }

    @Test
    fun lista_vacia_marca_a_todos_presentes() {
        val a = row(markedNoShow = true)
        val repo = FakeRepo(organizerId, scheduledAt, listOf(a))
        val svc = AttendanceDeclarationService(repo, clockAt(afterClose))

        val result = svc.declare(convocatoryId, organizerId, emptySet())

        assertTrue(repo.marks.isEmpty())
        assertFalse(result.first().markedNoShow)
        // Aun con lista vacía, declarar confirma la presencia del organizador.
        assertTrue(repo.confirmed)
    }

    // --- Reversibilidad ---

    @Test
    fun quitar_a_alguien_de_la_lista_lo_recalcula() {
        // Parte con un ausente ya marcado; al re-declarar con lista vacía, ese
        // jugador debe recalcularse (para revertir su penalización).
        val previo = row(markedNoShow = true)
        val repo = FakeRepo(organizerId, scheduledAt, listOf(previo))
        val svc = AttendanceDeclarationService(repo, clockAt(afterClose))

        svc.declare(convocatoryId, organizerId, emptySet())

        assertTrue(repo.marks.isEmpty())
        assertTrue(previo.playerId in repo.recomputedAttendance)
        assertTrue(previo.playerId in repo.recomputedResponsibility)
    }

    // --- attendanceList ---

    @Test
    fun lista_solo_la_ve_el_organizador() {
        val repo = FakeRepo(organizerId, scheduledAt, listOf(row()))
        val svc = AttendanceDeclarationService(repo, clockAt(afterClose))
        assertFailsWith<DeclarationForbidden> {
            svc.attendanceList(convocatoryId, UUID.randomUUID())
        }
        // El organizador sí.
        assertEquals(1, svc.attendanceList(convocatoryId, organizerId).size)
    }
}
