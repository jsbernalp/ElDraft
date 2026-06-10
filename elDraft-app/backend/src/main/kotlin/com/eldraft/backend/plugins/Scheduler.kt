package com.eldraft.backend.plugins

import com.eldraft.backend.service.ConvocatoryService
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.ktor.ext.get
import kotlin.time.Duration.Companion.minutes

/**
 * Tareas periódicas en segundo plano (recordatorios de convocatorias sin
 * postulantes). Vive dentro del backend Ktor: arranca al iniciar la aplicación
 * y se detiene con ella (usa el scope de la aplicación, que se cancela en el
 * shutdown). El intervalo se lee de `notifications.reminderIntervalMinutes`;
 * un valor <= 0 deshabilita el recordatorio.
 */
fun Application.configureScheduler() {
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
