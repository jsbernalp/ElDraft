package com.eldraft.backend.service

import com.eldraft.backend.notifications.FcmService
import com.eldraft.backend.repository.ConvocatoryCreate
import com.eldraft.backend.repository.ConvocatoryRecord
import com.eldraft.backend.repository.ConvocatoryRepository
import com.eldraft.backend.repository.MyPostulationRecord
import com.eldraft.backend.repository.PostulationRepository
import com.eldraft.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

/**
 * El organizador ya organiza OTRA convocatoria a esa hora: bloqueo duro (no
 * tiene salida razonable cancelar a su propia gente al vuelo).
 */
class ConvocatoryConflict(message: String) : RuntimeException(message)

/** Una postulación del organizador que choca con la convocatoria que quiere crear. */
data class ConflictingPostulation(val format: String, val scheduledAt: String)

/**
 * La convocatoria a crear choca con postulaciones del organizador (como
 * jugador). No es un bloqueo: el cliente puede reintentar con cancelConflicts
 * = true para crearla y cancelar esas postulaciones. Lleva la lista para que la
 * UI muestre qué se cancelará.
 */
class ConvocatoryScheduleConflict(
    val conflicts: List<ConflictingPostulation>,
) : RuntimeException("La convocatoria choca con ${conflicts.size} de tus postulaciones")

class ConvocatoryService(
    private val repository: ConvocatoryRepository,
    private val postulations: PostulationRepository,
    private val users: UserRepository,
    private val fcm: FcmService,
    /** Radio (en km) para notificar a jugadores cercanos. Configurable. */
    private val nearbyRadiusKm: Double,
) {
    private val log = LoggerFactory.getLogger(ConvocatoryService::class.java)

    /** Crea una convocatoria tras validar los campos y notifica a jugadores cercanos. */
    fun create(data: ConvocatoryCreate): ConvocatoryRecord {
        validate(data)

        // Choque con otra convocatoria que ya organiza: bloqueo duro.
        if (organizingConflictExists(data.organizerId, data.scheduledAt)) {
            throw ConvocatoryConflict("Ya tienes otra convocatoria a esa hora")
        }

        // Choque con sus postulaciones (como jugador): requiere confirmación.
        // Si no la dio (cancelConflicts=false), devolvemos la lista para que la
        // UI le avise; si la dio, creamos y cancelamos esas postulaciones.
        val conflicts = conflictingPostulations(data.organizerId, data.scheduledAt)
        if (conflicts.isNotEmpty() && !data.cancelConflicts) {
            throw ConvocatoryScheduleConflict(
                conflicts.map { ConflictingPostulation(it.convocatory.format, it.convocatory.scheduledAt) },
            )
        }

        val created = repository.create(data)

        // Tras crear (con confirmación), cancela las postulaciones en conflicto.
        conflicts.forEach { p ->
            postulations.updateStatus(p.id, "cancelled")
            fcm.sendToToken(
                token = users.getFcmToken(data.organizerId),
                title = "Postulación cancelada",
                body = "Cancelamos tu postulación a ${p.convocatory.format}: " +
                    "creaste una convocatoria que se cruza con ese horario.",
                data = mapOf(
                    "type" to "postulation_cancelled",
                    "convocatoryId" to p.convocatory.id.toString(),
                    "postulationId" to p.id.toString(),
                ),
            )
        }
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

    fun getNearby(
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        excludeOrganizerId: UUID? = null,
    ): List<ConvocatoryRecord> {
        require(lat in -90.0..90.0) { "Latitud fuera de rango" }
        require(lng in -180.0..180.0) { "Longitud fuera de rango" }
        require(radiusMeters in 1.0..50_000.0) { "El radio debe estar entre 1 y 50000 metros" }
        return repository.findNearby(lat, lng, radiusMeters, excludeOrganizerId)
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

    /**
     * True si el organizador ya tiene OTRA convocatoria activa y futura cuyo
     * horario se solapa con [scheduledAt]. Si el horario no parsea, no bloquea.
     */
    private fun organizingConflictExists(organizerId: UUID, scheduledAt: String): Boolean {
        val newStart = parseSchedule(scheduledAt) ?: return false
        val now = LocalDateTime.now()
        return repository.findByOrganizer(organizerId).any { c ->
            parseSchedule(c.scheduledAt)?.let { it.isAfter(now) && overlaps(it, newStart) } == true
        }
    }

    /**
     * Postulaciones VIGENTES del organizador (pendientes o aprobadas como
     * jugador) que se solapan con [scheduledAt]. Son las que se cancelarían al
     * crear la convocatoria (previa confirmación del usuario).
     */
    private fun conflictingPostulations(organizerId: UUID, scheduledAt: String): List<MyPostulationRecord> {
        val newStart = parseSchedule(scheduledAt) ?: return emptyList()
        return postulations.findByPlayer(organizerId).filter { p ->
            (p.status == "pending" || p.status == "approved") &&
                parseSchedule(p.convocatory.scheduledAt)?.let { overlaps(it, newStart) } == true
        }
    }

    private fun validate(data: ConvocatoryCreate) {
        require(data.lat in -90.0..90.0) { "Latitud fuera de rango" }
        require(data.lng in -180.0..180.0) { "Longitud fuera de rango" }
        require(data.positionSlots.isNotEmpty()) { "Debe pedir al menos una posición" }
        require(data.positionSlots.all { it.position.isNotBlank() }) { "Cada posición debe tener nombre" }
        require(data.positionSlots.all { it.slots >= 1 }) { "Cada posición necesita al menos 1 cupo" }
        val positions = data.positionSlots.map { it.position }
        require(positions.size == positions.toSet().size) { "No se permiten posiciones repetidas" }
        require(data.positionSlots.sumOf { it.slots } in 1..30) { "El total de cupos debe estar entre 1 y 30" }
        require(data.format.isNotBlank()) { "El formato es obligatorio" }
        require(data.ambiente.isNotBlank()) { "El ambiente es obligatorio" }
        require(data.fee >= 0.0) { "La cuota no puede ser negativa" }
    }
}
