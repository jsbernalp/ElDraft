package com.eldraft.backend.service

import com.eldraft.backend.repository.AttendanceDeclarationRepository
import com.eldraft.backend.repository.NoShowContext
import com.eldraft.backend.repository.NoShowRepository
import com.eldraft.backend.repository.PlayerAttendanceRow
import com.eldraft.backend.repository.RatingRepository
import com.eldraft.backend.repository.TeammateRecord
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reglas de qué partido terminado sigue apareciendo en las listas, con repos
 * falsos (sin base de datos).
 */
class MatchLifecycleTest {

    private val convocatoryId = UUID.randomUUID()
    private val organizerId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()
    private val start: LocalDateTime = LocalDateTime.of(2026, 8, 16, 20, 0)

    private class FakeRatings(
        private val attendees: Set<UUID> = emptySet(),
        private val mates: List<TeammateRecord> = emptyList(),
    ) : RatingRepository() {
        override fun attended(convocatoryId: UUID, userId: UUID) = userId in attendees
        override fun teammates(convocatoryId: UUID, requesterId: UUID) = mates
    }

    private class FakeDeclarations(
        private val approved: List<PlayerAttendanceRow> = emptyList(),
    ) : AttendanceDeclarationRepository() {
        override fun approvedPlayers(convocatoryId: UUID) = approved
    }

    private class FakeNoShow(
        private val ctx: NoShowContext?,
        private val reporters: Set<UUID> = emptySet(),
    ) : NoShowRepository() {
        override fun context(convocatoryId: UUID) = ctx
        override fun hasReported(convocatoryId: UUID, userId: UUID) = userId in reporters
    }

    private fun context(
        confirmed: Boolean = false,
        noShow: Boolean = false,
    ) = NoShowContext(
        organizerId = organizerId,
        scheduledAt = start,
        organizerNoShow = noShow,
        organizerConfirmed = confirmed,
    )

    private fun approvedPlayer() = PlayerAttendanceRow(
        playerId = playerId,
        name = "Jugador",
        avatarUrl = null,
        positionPrimary = null,
        scanned = false,
        markedNoShow = false,
    )

    private fun mate(rated: Boolean) = TeammateRecord(
        userId = UUID.randomUUID(),
        name = "Compañero",
        avatarUrl = null,
        positionPrimary = null,
        alreadyRated = rated,
    )

    private fun lifecycle(
        ratings: RatingRepository = FakeRatings(),
        declarations: AttendanceDeclarationRepository = FakeDeclarations(),
        noShow: NoShowRepository = FakeNoShow(context()),
    ) = MatchLifecycle(ratings, declarations, noShow)

    @Test
    fun el_partido_termina_45_minutos_despues_del_inicio() {
        val l = lifecycle()
        assertFalse(l.isOver(start, start.plusMinutes(44)))
        assertTrue(l.isOver(start, start.plusMinutes(45)))
    }

    @Test
    fun sin_jugadores_aprobados_el_organizador_no_tiene_nada_pendiente() {
        // El partido que nadie llegó a jugar: no hay asistencia que declarar ni
        // a quién calificar, así que desaparece en cuanto termina.
        val l = lifecycle(declarations = FakeDeclarations(emptyList()))
        assertFalse(l.organizerHasPending(convocatoryId, organizerId))
    }

    @Test
    fun con_aprobados_y_sin_declarar_el_organizador_tiene_pendiente() {
        val l = lifecycle(
            declarations = FakeDeclarations(listOf(approvedPlayer())),
            noShow = FakeNoShow(context(confirmed = false)),
        )
        assertTrue(l.organizerHasPending(convocatoryId, organizerId))
    }

    @Test
    fun organizador_que_no_asistio_y_ya_declaro_no_queda_pegado() {
        // El callejón sin salida que motivó usar "¿te queda algo por hacer?" en
        // vez de "¿ya calificaste a todos?": sin asistencia no puede calificar a
        // nadie NUNCA, así que con el otro criterio la tarjeta sería eterna.
        val l = lifecycle(
            ratings = FakeRatings(attendees = emptySet()),
            declarations = FakeDeclarations(listOf(approvedPlayer())),
            noShow = FakeNoShow(context(confirmed = true)),
        )
        assertFalse(l.organizerHasPending(convocatoryId, organizerId))
    }

    @Test
    fun organizador_con_calificaciones_pendientes_sigue_visible() {
        val l = lifecycle(
            ratings = FakeRatings(attendees = setOf(organizerId), mates = listOf(mate(rated = false))),
            declarations = FakeDeclarations(listOf(approvedPlayer())),
            noShow = FakeNoShow(context(confirmed = true)),
        )
        assertTrue(l.organizerHasPending(convocatoryId, organizerId))
    }

    @Test
    fun organizador_que_ya_califico_a_todos_desaparece() {
        val l = lifecycle(
            ratings = FakeRatings(attendees = setOf(organizerId), mates = listOf(mate(rated = true))),
            declarations = FakeDeclarations(listOf(approvedPlayer())),
            noShow = FakeNoShow(context(confirmed = true)),
        )
        assertFalse(l.organizerHasPending(convocatoryId, organizerId))
    }

    @Test
    fun el_jugador_conserva_el_partido_mientras_pueda_reportar_el_no_show() {
        // Si el organizador no llegó nadie escaneó el QR, así que el jugador no
        // puede calificar: sin esta condición la tarjeta se iría justo cuando
        // más falta hace.
        val l = lifecycle(noShow = FakeNoShow(context(confirmed = false)))
        assertTrue(l.playerHasPending(convocatoryId, playerId, start.plusHours(2)))
    }

    @Test
    fun cerrada_la_ventana_y_sin_asistencia_el_jugador_no_tiene_nada() {
        val l = lifecycle(noShow = FakeNoShow(context(confirmed = false)))
        assertFalse(l.playerHasPending(convocatoryId, playerId, start.plusHours(49)))
    }

    @Test
    fun ya_reportado_el_no_show_no_cuenta_como_pendiente() {
        val l = lifecycle(noShow = FakeNoShow(context(), reporters = setOf(playerId)))
        assertFalse(l.playerHasPending(convocatoryId, playerId, start.plusHours(2)))
    }

    @Test
    fun calificar_mantiene_el_partido_aunque_la_ventana_de_no_show_cierre() {
        val l = lifecycle(
            ratings = FakeRatings(attendees = setOf(playerId), mates = listOf(mate(rated = false))),
            noShow = FakeNoShow(context(confirmed = true)),
        )
        assertTrue(l.playerHasPending(convocatoryId, playerId, start.plusHours(49)))
    }
}

/**
 * MatchLifecycle que nunca reporta pendientes. Para los tests de otros servicios
 * que no ejercitan las listas: sus repos reales abrirían transacciones contra
 * una base de datos que no existe en un test unitario.
 */
internal fun inertMatchLifecycle() = MatchLifecycle(
    ratings = object : RatingRepository() {
        override fun attended(convocatoryId: UUID, userId: UUID) = false
    },
    declarations = object : AttendanceDeclarationRepository() {
        override fun approvedPlayers(convocatoryId: UUID) = emptyList<PlayerAttendanceRow>()
    },
    noShow = object : NoShowRepository() {
        override fun context(convocatoryId: UUID): NoShowContext? = null
    },
)
