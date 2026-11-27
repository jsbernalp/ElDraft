package com.eldraft.backend.plugins

import com.eldraft.backend.service.ConvocatoryService
import com.eldraft.backend.service.TeamPriorityService
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.ktor.ext.get
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Tareas periódicas en segundo plano (recordatorios de convocatorias sin
 * postulantes). Vive dentro del backend Ktor: arranca al iniciar la aplicación
 * y se detiene con ella (usa el scope de la aplicación, que se cancela en el
 * shutdown). El intervalo se lee de `notifications.reminderIntervalMinutes`;
 * un valor <= 0 deshabilita el recordatorio.
 */
fun Application.configureScheduler() {
    configureTeamWindowScheduler()
    configureReminderScheduler()
}

/**
 * Abre las convocatorias con prioridad de equipo cuya ventana ya venció. Corre
 * aparte del recordatorio (cada 30 min) porque las ventanas duran desde 30 min.
 * `teams.windowCheckSeconds` <= 0 lo deshabilita. La apertura también se evalúa al
 * leer, así que un retraso aquí no deja la card desfasada.
 */
private fun Application.configureTeamWindowScheduler() {
    val seconds = environment.config
        .propertyOrNull("teams.windowCheckSeconds")
        ?.getString()?.toLongOrNull() ?: 60L
    if (seconds <= 0L) {
        log.info("Revisión de ventanas de equipo DESHABILITADA (windowCheckSeconds=$seconds).")
        return
    }
    val teamPriority = get<TeamPriorityService>()
    log.info("Revisión de ventanas de equipo HABILITADA: cada $seconds s.")
    launch(Dispatchers.IO) {
        while (isActive) {
            delay(seconds.seconds)
            try {
                teamPriority.openDue()
            } catch (e: Exception) {
                log.warn("Fallo en la revisión de ventanas de equipo: ${e.message}")
            }
        }
    }
}

private fun Application.configureReminderScheduler() {
    val intervalMinutes = environment.config
        .propertyOrNull("notifications.reminderIntervalMinutes")
        ?.getString()?.toLongOrNull() ?: 0L

    if (intervalMinutes <= 0L) {
        log.info("Recordatorio de convocatorias DESHABILITADO (reminderIntervalMinutes=$intervalMinutes).")
        return
    }

    val convocatoryService = get<ConvocatoryService>()
    log.info("Recordatorio de convocatorias HABILITADO: cada $intervalMinutes min.")

    // Atamos la corrutina al ciclo de vida de la aplicación: se cancela sola en
    // el shutdown. Cada vuelta es best-effort y nunca propaga excepciones.
    launch(Dispatchers.IO) {
        // Pequeño respiro inicial para no chocar con el arranque (BD, etc.).
        delay(intervalMinutes.minutes)
        while (isActive) {
            try {
                convocatoryService.sendRemindersForPendingConvocatories()
            } catch (e: Exception) {
                log.warn("Fallo en el ciclo de recordatorios: ${e.message}")
            }
            delay(intervalMinutes.minutes)
        }
    }
}
