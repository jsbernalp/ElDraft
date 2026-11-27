package com.eldraft.backend.service

import com.eldraft.backend.repository.CoPlayRow
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reglas de "ya jugaron juntos" sobre filas crudas, sin base de datos.
 */
class TeamSuggestionsTest {

    private val me = UUID.randomUUID()
    private val organizer = UUID.randomUUID()
    private val ana = UUID.randomUUID()
    private val now = LocalDateTime.of(2026, 6, 10, 12, 0)
    private val past = now.minusDays(2)

    /** Una convocatoria con sus postulaciones (por defecto, terminada y activa). */
    private fun match(
        vararg players: Pair<UUID, String>,
        at: LocalDateTime = past,
        status: String = "active",
        organizerId: UUID = organizer,
        organizerNoShow: Boolean = false,
        noShows: Set<UUID> = emptySet(),
    ): List<CoPlayRow> {
        val id = UUID.randomUUID()
        return players.map { (player, postStatus) ->
            CoPlayRow(
                convocatoryId = id, scheduledAt = at, convocatoryStatus = status,
                organizerId = organizerId, organizerNoShow = organizerNoShow,
                playerId = player, postulationStatus = postStatus,
                playerMarkedNoShow = player in noShows,
            )
        }
    }

    private fun ids(rows: List<CoPlayRow>) = coPlayCounts(rows, me, now).map { it.playerId }.toSet()

    @Test
    fun cuenta_a_los_co_aprobados_y_al_organizador() {
        val rows = match(me to "approved", ana to "approved")
        assertEquals(setOf(ana, organizer), ids(rows))
    }

    @Test
    fun siendo_organizador_cuenta_a_los_aprobados() {
        val rows = match(ana to "approved", organizerId = me)
        assertEquals(setOf(ana), ids(rows))
    }

    @Test
    fun el_otro_marcado_no_show_no_cuenta() {
        val rows = match(me to "approved", ana to "approved", noShows = setOf(ana))
        assertEquals(setOf(organizer), ids(rows))
    }

    @Test
    fun si_yo_quede_no_show_el_partido_no_cuenta() {
        val rows = match(me to "approved", ana to "approved", noShows = setOf(me))
        assertTrue(ids(rows).isEmpty())
    }

    @Test
    fun organizador_no_show_excluye_al_organizador() {
        val rows = match(me to "approved", ana to "approved", organizerNoShow = true)
        assertEquals(setOf(ana), ids(rows))
    }

    @Test
    fun siendo_yo_el_organizador_no_show_el_partido_no_cuenta() {
        val rows = match(ana to "approved", organizerId = me, organizerNoShow = true)
        assertTrue(ids(rows).isEmpty())
    }

    @Test
    fun partido_futuro_no_cuenta() {
        val rows = match(me to "approved", ana to "approved", at = now.plusDays(1))
        assertTrue(ids(rows).isEmpty())
    }

    @Test
    fun partido_dentro_de_los_45_minutos_no_cuenta() {
        val rows = match(me to "approved", ana to "approved", at = now.minusMinutes(44))
        assertTrue(ids(rows).isEmpty())
    }

    @Test
    fun partido_a_los_45_minutos_justos_ya_cuenta() {
        val rows = match(me to "approved", ana to "approved", at = now.minusMinutes(45))
        assertEquals(setOf(ana, organizer), ids(rows))
    }

    @Test
    fun partido_cancelado_no_cuenta() {
        val rows = match(me to "approved", ana to "approved", status = "cancelled")
        assertTrue(ids(rows).isEmpty())
    }

    @Test
    fun pendientes_y_rechazados_no_cuentan() {
        val rows = match(me to "approved", ana to "pending") +
            match(me to "approved", ana to "rejected", organizerId = me)
        assertEquals(setOf(organizer), ids(rows))
    }

    @Test
    fun si_yo_no_fui_aprobado_el_partido_no_cuenta() {
        val rows = match(me to "pending", ana to "approved")
        assertTrue(ids(rows).isEmpty())
    }

    @Test
    fun nunca_me_incluye_a_mi() {
        val rows = match(me to "approved", ana to "approved")
        assertTrue(me !in ids(rows))
    }

    @Test
    fun dos_partidos_juntos_cuentan_dos_y_guardan_el_ultimo() {
        val older = past.minusDays(5)
        val rows = match(me to "approved", ana to "approved", at = older) +
            match(me to "approved", ana to "approved", at = past)
        val anaCount = coPlayCounts(rows, me, now).single { it.playerId == ana }
        assertEquals(2, anaCount.matches)
        assertEquals(past, anaCount.lastPlayedAt)
    }

    @Test
    fun ordena_por_partidos_juntos_y_luego_por_el_mas_reciente() {
        val beto = UUID.randomUUID()
        val caro = UUID.randomUUID()
        val org2 = UUID.randomUUID()
        // ana: 2 partidos; beto y caro: 1, caro más reciente.
        val rows = match(me to "approved", ana to "approved", beto to "approved", organizerId = me, at = past.minusDays(3)) +
            match(me to "approved", ana to "approved", caro to "approved", organizerId = me, at = past) +
            match(me to "approved", organizerId = org2, at = past.minusDays(10))
        val order = coPlayCounts(rows, me, now).map { it.playerId }
        assertEquals(listOf(ana, caro, beto, org2), order)
    }

    @Test
    fun se_corta_en_50() {
        val players = (1..60).map { UUID.randomUUID() to "approved" }.toTypedArray()
        val rows = match(*players, organizerId = me)
        assertEquals(TEAM_SUGGESTIONS_LIMIT, coPlayCounts(rows, me, now).size)
    }
}
