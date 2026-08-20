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
    fun ya_entrada_la_tarde_propone_manana() {
        // 6:30 p.m.: hoy a las 7 ya no cumple la hora de anticipación.
        val now = LocalDateTime.of(2026, 8, 19, 18, 30)
        assertEquals(LocalDateTime.of(2026, 8, 20, 19, 0), defaultScheduledAt(now))
    }

    @Test
    fun en_el_limite_exacto_propone_manana() {
        // 6:00 p.m. en punto: hoy a las 7 empata con el mínimo, y el formulario
        // exige estrictamente más. Proponerlo dejaría la sección inválida al abrir.
        val now = LocalDateTime.of(2026, 8, 19, 18, 0)
        assertEquals(LocalDateTime.of(2026, 8, 20, 19, 0), defaultScheduledAt(now))
    }

    @Test
    fun de_madrugada_propone_hoy() {
        // 1:00 a.m. del 19: el partido de esta noche sigue siendo hoy, no mañana.
        val now = LocalDateTime.of(2026, 8, 19, 1, 0)
        assertEquals(LocalDateTime.of(2026, 8, 19, 19, 0), defaultScheduledAt(now))
    }
}
