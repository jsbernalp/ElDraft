package com.eldraft.domain.convocatory

import com.eldraft.data.models.Convocatory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConvocatoryPhaseTest {
    private fun conv(status: String, teamId: String? = null, freeSlots: Int? = null) = Convocatory(
        id = "c", organizerId = "o", lat = 0.0, lng = 0.0, slotsNeeded = 3,
        positionRequired = "Delantero", format = "Fútbol 5", ambiente = "Recocha",
        status = status, scheduledAt = "2026-06-10T19:00:00", teamId = teamId, freeSlots = freeSlots,
    )

    @Test
    fun team_only_es_prioridad_del_equipo() {
        assertEquals(ConvocatoryPhase.TEAM_PRIORITY, conv("team_only", "t1").phase())
    }

    @Test
    fun abierta_con_cupos_libres_es_abierta_al_publico() {
        assertEquals(ConvocatoryPhase.OPEN_TO_PUBLIC, conv("active", "t1", freeSlots = 2).phase())
    }

    @Test
    fun abierta_sin_cupos_libres_es_equipo_lleno() {
        assertEquals(ConvocatoryPhase.TEAM_FILLED, conv("active", "t1", freeSlots = 0).phase())
        assertEquals(ConvocatoryPhase.TEAM_FILLED, conv("active", "t1", freeSlots = null).phase())
    }

    @Test
    fun sin_equipo_o_cancelada_no_tiene_fase_especial() {
        assertEquals(ConvocatoryPhase.STANDARD, conv("active").phase())
        assertEquals(ConvocatoryPhase.STANDARD, conv("cancelled", "t1").phase())
    }

    @Test
    fun cancelar_se_puede_en_las_fases_vivas_incluida_team_only() {
        assertTrue(conv("active").canCancel())
        assertTrue(conv("full").canCancel())
        assertTrue(conv("team_only", "t1").canCancel())
        assertFalse(conv("cancelled").canCancel())
    }

    @Test
    fun abrir_ya_solo_en_team_only() {
        assertTrue(conv("team_only", "t1").canOpenNow())
        assertFalse(conv("active", "t1").canOpenNow())
    }
}
