package com.eldraft.domain.team

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeamWindowTest {
    private val now = LocalDateTime(2026, 6, 10, 12, 0)

    private fun enabled(scheduledAt: LocalDateTime) =
        TeamWindow.options(scheduledAt, now).filter { it.enabled }.map { it.minutes }

    @Test
    fun partido_en_cinco_horas_habilita_hasta_dos_horas() {
        // 5 h = 300 min; 360 + 60 > 300, 120 + 60 <= 300.
        assertEquals(listOf(30, 60, 120), enabled(LocalDateTime(2026, 6, 10, 17, 0)))
    }

    @Test
    fun el_limite_exacto_es_valido() {
        // Partido en 90 min: 30 + 60 = 90 justo cabe; 60 + 60 no.
        assertEquals(listOf(30), enabled(LocalDateTime(2026, 6, 10, 13, 30)))
    }

    @Test
    fun a_un_minuto_del_limite_ya_no_cabe() {
        assertEquals(emptyList(), enabled(LocalDateTime(2026, 6, 10, 13, 29)))
    }

    @Test
    fun partido_en_una_hora_o_ya_pasado_no_habilita_nada() {
        assertEquals(emptyList(), enabled(LocalDateTime(2026, 6, 10, 13, 0)))
        assertEquals(emptyList(), enabled(LocalDateTime(2026, 6, 10, 11, 0)))
    }

    @Test
    fun partido_la_semana_siguiente_habilita_todo_y_siempre_ofrece_las_cuatro_opciones() {
        val options = TeamWindow.options(LocalDateTime(2026, 6, 17, 19, 0), now)
        assertEquals(listOf(30, 60, 120, 360), options.map { it.minutes })
        assertTrue(options.all { it.enabled })
    }

    @Test
    fun isValid_detecta_la_ventana_que_deja_de_caber_al_cambiar_la_fecha() {
        assertTrue(TeamWindow.isValid(120, LocalDateTime(2026, 6, 10, 17, 0), now))
        assertFalse(TeamWindow.isValid(120, LocalDateTime(2026, 6, 10, 14, 0), now))
        assertFalse(TeamWindow.isValid(null, LocalDateTime(2026, 6, 10, 17, 0), now))
        assertFalse(TeamWindow.isValid(45, LocalDateTime(2026, 6, 10, 17, 0), now))
    }

    @Test
    fun minutesLeft_cuenta_regresiva_sin_bajar_de_cero() {
        assertEquals(42, TeamWindow.minutesLeft(LocalDateTime(2026, 6, 10, 12, 42), now))
        assertEquals(0, TeamWindow.minutesLeft(LocalDateTime(2026, 6, 10, 11, 0), now))
    }
}
