package com.eldraft.backend.service

import com.eldraft.backend.notifications.FcmService
import com.eldraft.backend.repository.ConvocatoryCreate
import com.eldraft.backend.repository.ConvocatoryRecord
import com.eldraft.backend.repository.ConvocatoryRepository
import com.eldraft.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import java.util.UUID

class ConvocatoryService(
    private val repository: ConvocatoryRepository,
    private val users: UserRepository,
    private val fcm: FcmService,
    /** Radio (en km) para notificar a jugadores cercanos. Configurable. */
    private val nearbyRadiusKm: Double,
) {
    private val log = LoggerFactory.getLogger(ConvocatoryService::class.java)

    /** Crea una convocatoria tras validar los campos y notifica a jugadores cercanos. */
    fun create(data: ConvocatoryCreate): ConvocatoryRecord {
        validate(data)
        val created = repository.create(data)
        notifyNearbyPlayers(
            c = created,
            title = "Nueva convocatoria cerca de ti",
            body = "Partido ${created.format} cerca de tu zona. ¡Postúlate!",
            type = "new_convocatory",
        )
        return created
    }

    fun getById(id: UUID): ConvocatoryRecord? = repository.findById(id)

    fun getMine(organizerId: UUID): List<ConvocatoryRecord> =
        repository.findByOrganizer(organizerId)

    fun getNearby(lat: Double, lng: Double, radiusMeters: Double): List<ConvocatoryRecord> {
        require(lat in -90.0..90.0) { "Latitud fuera de rango" }
        require(lng in -180.0..180.0) { "Longitud fuera de rango" }
        require(radiusMeters in 1.0..50_000.0) { "El radio debe estar entre 1 y 50000 metros" }
        return repository.findNearby(lat, lng, radiusMeters)
    }

    /**
     * Reenvía (best-effort) el push a los jugadores cercanos de cada convocatoria
     * activa que aún no tiene postulantes. Pensado para un scheduler periódico:
     * en cuanto alguien se postula (o la convocatoria vence), deja de aparecer en
     * la consulta y el recordatorio se detiene solo. Devuelve cuántas se recordaron.
     */
    fun sendRemindersForPendingConvocatories(): Int {
        val pending = try {
            repository.findActiveWithoutPostulations()
        } catch (e: Exception) {
            log.warn("No se pudieron obtener convocatorias pendientes para recordatorio: ${e.message}")
            return 0
        }
        if (pending.isEmpty()) return 0
        pending.forEach { c ->
            notifyNearbyPlayers(
                c = c,
                title = "Aún hay cupos cerca de ti",
                body = "El partido ${c.format} sigue buscando jugadores. ¡Postúlate!",
                type = "convocatory_reminder",
            )
        }
        log.info("Recordatorio: reenviado a jugadores cercanos de ${pending.size} convocatorias sin postulantes.")
        return pending.size
    }

    /**
     * Notifica (best-effort) a los jugadores cercanos a una convocatoria. Nunca
     * rompe el flujo: si FCM está deshabilitado o algún envío falla, solo se
     * loguea. El envío es 1-a-1 en bucle; basta para el volumen actual (migrar a
     * multicast/topics si crece mucho).
     */
    private fun notifyNearbyPlayers(
        c: ConvocatoryRecord,
        title: String,
        body: String,
        type: String,
    ) {
        try {
            val recipients = users.findNearbyPlayersToNotify(
                lat = c.lat,
                lng = c.lng,
                radiusMeters = nearbyRadiusKm * 1_000.0,
                excludeUserId = c.organizerId,
            )
            if (recipients.isEmpty()) return
            recipients.forEach { r ->
                fcm.sendToToken(
                    token = r.fcmToken,
                    title = title,
                    body = body,
                    data = mapOf(
                        "type" to type,
                        "convocatoryId" to c.id.toString(),
                    ),
                )
            }
            log.info("Convocatoria ${c.id}: notificados ${recipients.size} jugadores cercanos (radio ${nearbyRadiusKm}km, type=$type).")
        } catch (e: Exception) {
            log.warn("Fallo al notificar jugadores cercanos para convocatoria ${c.id}: ${e.message}")
        }
    }

    private fun validate(data: ConvocatoryCreate) {
        require(data.lat in -90.0..90.0) { "Latitud fuera de rango" }
        require(data.lng in -180.0..180.0) { "Longitud fuera de rango" }
        require(data.slotsNeeded in 1..30) { "Los cupos deben estar entre 1 y 30" }
        require(data.positionRequired.isNotBlank()) { "La posición requerida es obligatoria" }
        require(data.format.isNotBlank()) { "El formato es obligatorio" }
        require(data.ambiente.isNotBlank()) { "El ambiente es obligatorio" }
        require(data.fee >= 0.0) { "La cuota no puede ser negativa" }
    }
}
