package com.eldraft.android.ui.screens

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Fecha del formulario de nueva convocatoria.
 *
 * El caso que importa es el nocturno: con la conversión anterior (zona local en
 * vez de UTC) un partido de las 7 p.m. en Colombia se iba al día siguiente en el
 * calendario, y al elegir un día se guardaba el anterior. Como casi todos los
 * partidos son de noche, aquí se barren todas las horas y varias zonas.
 */
class CreateDraftScheduleTest {

    private val defaultZone = TimeZone.getDefault()

    @AfterTest
    fun restoreZone() {
        TimeZone.setDefault(defaultZone)
    }

    /** Zonas con desfase negativo (donde aparecía el bug), UTC y una positiva. */
    private val zones = listOf(
        "America/Bogota",   // UTC-5: la de los usuarios reales
        "America/Los_Angeles", // UTC-8/-7
        "UTC",
        "Europe/Madrid",    // UTC+1/+2
        "Pacific/Kiritimati", // UTC+14, el extremo opuesto
    )

    @Test
    fun el_calendario_marca_el_mismo_dia_a_cualquier_hora_y_en_cualquier_zona() {
        val date = LocalDate.of(2026, 8, 19)
        for (zone in zones) {
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zone)))
            for (hour in 0..23) {
                // La hora del partido no debe influir: solo viaja la fecha. Se llama
                // igual que la pantalla, con la fecha/hora completa.
                val scheduledAt = date.atTime(hour, 30)
                val millis = scheduledAt.toPickerMillis()
                assertEquals(
                    date,
                    pickerMillisToLocalDate(millis),
                    "El calendario corrió el día en $zone a las $hour:30",
                )
            }
        }
    }

    @Test
    fun elegir_un_dia_en_el_calendario_guarda_ese_dia() {
        // Ida y vuelta completa: el usuario abre el calendario con un partido a las
        // 11 p.m. y toca otro día. Antes esto devolvía el día anterior al tocado.
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("America/Bogota")))
        val abierto = LocalDateTime.of(2026, 8, 19, 23, 0)
        val tocado = LocalDate.of(2026, 8, 25)

        // El diálogo abre marcando el día del partido...
        assertEquals(abierto.toLocalDate(), pickerMillisToLocalDate(abierto.toPickerMillis()))
        // ...y al aceptar otra celda se conserva la hora y se toma el día tocado.
        val guardado = pickerMillisToLocalDate(tocado.toPickerMillis()).atTime(abierto.toLocalTime())
        assertEquals(LocalDateTime.of(2026, 8, 25, 23, 0), guardado)
    }

    @Test
    fun de_dia_propone_hoy_a_las_siete() {
        // 8:17 a.m.: quedan más de las 24 h... y desde luego más de 1 hora.
        val now = LocalDateTime.of(2026, 8, 19, 8, 17)
        assertEquals(LocalDateTime.of(2026, 8, 19, 19, 0), defaultScheduledAt(now))
    }

    @Test
    fun ya_entrada_la_tarde_propone_esta_noche_mas_tarde() {
        // 6:30 p.m.: hoy a las 7 ya no cumple la anticipación, pero la noche sigue
        // sirviendo. Se propone la siguiente hora en punto que sí cabe.
        val now = LocalDateTime.of(2026, 8, 19, 18, 30)
        assertEquals(LocalDateTime.of(2026, 8, 19, 20, 0), defaultScheduledAt(now))
    }

    @Test
    fun en_el_limite_exacto_no_propone_la_hora_del_borde() {
        // 6:00 p.m. en punto: hoy a las 7 empata con el mínimo, y el formulario
        // exige estrictamente más. Proponerlo dejaría la sección inválida al abrir.
        val now = LocalDateTime.of(2026, 8, 19, 18, 0)
        assertEquals(LocalDateTime.of(2026, 8, 19, 20, 0), defaultScheduledAt(now))
    }

    @Test
    fun pasadas_las_siete_propone_esta_misma_noche() {
        // 8:34 p.m. (el caso reportado): antes saltaba a mañana; ahora propone las
        // 10 de esta noche, que es lo que todavía se puede armar.
        val now = LocalDateTime.of(2026, 8, 19, 20, 34)
        assertEquals(LocalDateTime.of(2026, 8, 19, 22, 0), defaultScheduledAt(now))
    }

    @Test
    fun muy_de_noche_ya_propone_manana() {
        // 11:10 p.m.: la siguiente hora que cumpliría el mínimo cae de madrugada,
        // fuera de este día. Ahí sí toca mañana.
        val now = LocalDateTime.of(2026, 8, 19, 23, 10)
        assertEquals(LocalDateTime.of(2026, 8, 20, 19, 0), defaultScheduledAt(now))
    }

    @Test
    fun en_el_borde_de_medianoche_propone_manana() {
        // 10:34 p.m.: el mínimo cae a las 11:34 y la siguiente hora en punto es la
        // medianoche, que ya es otro día.
        val now = LocalDateTime.of(2026, 8, 19, 22, 34)
        assertEquals(LocalDateTime.of(2026, 8, 20, 19, 0), defaultScheduledAt(now))
    }

    @Test
    fun la_ultima_hora_que_todavia_cabe_hoy() {
        // 9:59 p.m.: alcanza para las 11 de esta noche, el último horario del día.
        val now = LocalDateTime.of(2026, 8, 19, 21, 59)
        assertEquals(LocalDateTime.of(2026, 8, 19, 23, 0), defaultScheduledAt(now))
    }

    @Test
    fun de_madrugada_propone_hoy() {
        // 1:00 a.m. del 19: el partido de esta noche sigue siendo hoy, no mañana.
        val now = LocalDateTime.of(2026, 8, 19, 1, 0)
        assertEquals(LocalDateTime.of(2026, 8, 19, 19, 0), defaultScheduledAt(now))
    }
}
