package com.eldraft.backend.service

import com.eldraft.backend.notifications.FcmService
import com.eldraft.backend.repository.ConvocatoryRecord
import com.eldraft.backend.repository.UserRepository
import org.slf4j.LoggerFactory

/**
 * Notifica (best-effort) a los jugadores cercanos a una convocatoria. Nunca
 * rompe el flujo: si FCM está deshabilitado o algún envío falla, solo se
 * loguea. El envío es 1-a-1 en bucle; basta para el volumen actual (migrar a
 * multicast/topics si crece mucho).
 *
 * Separado de [ConvocatoryService] para que [TeamPriorityService] pueda avisar a
 * los cercanos al publicar sin depender de él.
 */
open class NearbyNotifier(
    private val users: UserRepository,
    private val fcm: FcmService,
    /** Radio (en km) para notificar a jugadores cercanos. Configurable. */
    private val nearbyRadiusKm: Double,
) {
    private val log = LoggerFactory.getLogger(NearbyNotifier::class.java)

    open fun notify(c: ConvocatoryRecord, title: String, body: String, type: String) {
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
}
