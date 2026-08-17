package com.eldraft.backend.service

import com.eldraft.backend.notifications.FcmService
import com.eldraft.backend.repository.ConvocatoryRepository
import com.eldraft.backend.repository.MyPostulationRecord
import com.eldraft.backend.repository.PostulationRecord
import com.eldraft.backend.repository.PostulationRepository
import com.eldraft.backend.repository.UserRepository
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/** Errores de dominio de postulaciones (las rutas los mapean a HTTP). */
class PostulationConflict(message: String) : RuntimeException(message)
class PostulationForbidden(message: String) : RuntimeException(message)
class PostulationNotFound(message: String) : RuntimeException(message)
class PostulationWithdrawForbidden(message: String) : RuntimeException(message)

/**
 * Lógica de postulaciones (El Draft, lado jugador y organizador):
 * postularse, listar postulantes y aprobar/rechazar.
 *
 * Las notificaciones push (FcmService) son best-effort: si fallan o están
 * deshabilitadas, la acción principal se completa igual.
 */
class PostulationService(
    private val postulations: PostulationRepository,
    private val convocatories: ConvocatoryRepository,
    private val users: UserRepository,
    private val fcm: FcmService,
    private val lifecycle: MatchLifecycle,
) {
    /**
     * Un jugador se postula a una convocatoria.
     * Reglas: la convocatoria debe existir y estar activa, el jugador no puede
     * ser el organizador, y no puede postularse dos veces.
     */
    fun apply(convocatoryId: UUID, playerId: UUID, position: String): PostulationRecord {
        val convocatory = convocatories.findById(convocatoryId)
            ?: throw PostulationNotFound("Convocatoria no encontrada")
        if (convocatory.status != "active") {
            throw PostulationConflict("La convocatoria ya no está abierta")
        }
        if (convocatory.organizerId == playerId) {
            throw PostulationConflict("No puedes postularte a tu propia convocatoria")
        }
        // La posición elegida debe ser una de las que pide la convocatoria.
        val offered = convocatory.positionSlots.map { it.position }
        if (position.isBlank() || position !in offered) {
            throw PostulationConflict("Elige una de las posiciones que pide la convocatoria")
        }
        // No puedes postularte si ya tienes un partido APROBADO que se solape:
        // estarías en dos sitios a la vez. (Las pendientes sí se permiten; al
        // aprobarte en uno se cancelan automáticamente las demás en conflicto.)
        val newStart = parseSchedule(convocatory.scheduledAt)
        if (newStart != null) {
            val clash = postulations.findByPlayer(playerId).any { mine ->
                mine.status == "approved" &&
                    parseSchedule(mine.convocatory.scheduledAt)?.let { overlaps(it, newStart) } == true
            }
            if (clash) {
                throw PostulationConflict("Ya tienes un partido aprobado a esa hora")
            }
        }
        val created = postulations.create(convocatoryId, playerId, position)
            ?: throw PostulationConflict("Ya te postulaste a esta convocatoria")

        // Notifica al organizador (best-effort).
        val playerName = users.findById(playerId)?.name ?: "Un jugador"
        fcm.sendToToken(
            token = users.getFcmToken(convocatory.organizerId),
            title = "Nueva postulación",
            body = "$playerName se postuló a tu convocatoria de ${convocatory.format}.",
            data = mapOf(
                "type" to "new_postulation",
                "convocatoryId" to convocatoryId.toString(),
                "postulationId" to created.id.toString(),
            ),
        )
        return created
    }

    /**
     * Postulaciones del jugador: las de partidos que aún no han terminado, más
     * las de partidos terminados en los que todavía le queda algo por hacer
     * (calificar, o reportar que el organizador no llegó).
     *
     * De un partido terminado solo puede quedar algo pendiente si lo aprobaron:
     * una postulación rechazada, retirada o que nunca se resolvió no deja nada
     * que hacer, y se retira de la lista al terminar el partido. Antes no se
     * filtraba nada y se acumulaban todas desde siempre.
     */
    fun getMyPostulations(playerId: UUID): List<MyPostulationRecord> {
        val now = LocalDateTime.now()
        return postulations.findByPlayer(playerId).filter { p ->
            // Una postulación muerta no lleva a ningún sitio, aunque el partido
            // sea mañana: cubre las tres formas de morir —el organizador canceló,
            // el jugador se retiró, o chocaba de horario con otra a la que
            // entró—. Se mira también el estado de la convocatoria por si una
            // cancelación quedó a medias: lo que importa es que el partido no va.
            if (p.status == "cancelled" || p.convocatory.status == "cancelled") return@filter false

            val scheduled = parseSchedule(p.convocatory.scheduledAt) ?: return@filter true
            if (!lifecycle.isOver(scheduled, now)) return@filter true
            p.status == "approved" && lifecycle.playerHasPending(p.convocatory.id, playerId, now)
        }
            // Para que la card deje de ofrecer "Ya llegué" a quien ya llegó.
            .map { it.copy(attended = lifecycle.hasAttended(it.convocatory.id, playerId)) }
    }

    /** Lista los postulantes de una convocatoria. Solo el organizador puede verlos. */
    fun getApplicants(convocatoryId: UUID, requesterId: UUID): List<PostulationRecord> {
        val convocatory = convocatories.findById(convocatoryId)
            ?: throw PostulationNotFound("Convocatoria no encontrada")
        if (convocatory.organizerId != requesterId) {
            throw PostulationForbidden("Solo el organizador puede ver los postulantes")
        }
        return postulations.findByConvocatory(convocatoryId)
    }

    fun approve(postulationId: UUID, requesterId: UUID): PostulationRecord =
        decide(postulationId, requesterId, "approved")

    fun reject(postulationId: UUID, requesterId: UUID): PostulationRecord =
        decide(postulationId, requesterId, "rejected")

    /**
     * El jugador retira su propia postulación. Solo se puede si aún está
     * pendiente o aprobada, y antes de que comience el partido.
     * Penalización si retira con menos de 1 hora de anticipación y estaba aprobado.
     */
    fun withdraw(postulationId: UUID, callerId: UUID) {
        val postulation = postulations.findById(postulationId)
            ?: throw PostulationNotFound("Postulación no encontrada")

        if (postulation.playerId != callerId) {
            throw PostulationWithdrawForbidden("Solo puedes retirar tus propias postulaciones")
        }
        if (postulation.status !in listOf("pending", "approved")) {
            throw PostulationWithdrawForbidden("No puedes retirar una postulación en estado '${postulation.status}'")
        }

        val convocatory = convocatories.findById(postulation.convocatoryId)
            ?: throw PostulationNotFound("Convocatoria no encontrada")

        val scheduledAt = parseSchedule(convocatory.scheduledAt)
            ?: throw IllegalStateException("Fecha del partido inválida")
        val now = LocalDateTime.now()

        if (!scheduledAt.isAfter(now)) {
            throw PostulationWithdrawForbidden("No puedes retirarte de un partido que ya comenzó")
        }
        // Se puede escanear al llegar, antes de la hora de inicio: sin esto,
        // alguien que ya está en la cancha podría retirarse igual y quedaría
        // registrado como asistente de un partido del que se borró.
        if (lifecycle.hasAttended(postulation.convocatoryId, callerId)) {
            throw PostulationWithdrawForbidden("Ya registraste tu asistencia a este partido")
        }

        // Penalización si se retira con menos de 1 hora y estaba aprobado.
        if (postulation.status == "approved") {
            val minutesLeft = Duration.between(now, scheduledAt).toMinutes()
            if (minutesLeft < 60) {
                try {
                    users.incrementCancelPenalty(callerId)
                } catch (e: Exception) {
                    // best-effort
                }
            }
        }

        postulations.updateStatus(postulationId, "cancelled")

        // Notifica al organizador (best-effort).
        val playerName = users.findById(callerId)?.name ?: "Un jugador"
        fcm.sendToToken(
            token = users.getFcmToken(convocatory.organizerId),
            title = "Jugador se retiró",
            body = "$playerName retiró su postulación de ${convocatory.format}.",
            data = mapOf(
                "type" to "postulation_withdrawn",
                "convocatoryId" to convocatory.id.toString(),
                "postulationId" to postulationId.toString(),
            ),
        )
    }

    /**
     * Aprueba o rechaza una postulación. Solo el organizador de la convocatoria
     * asociada puede decidir. Idempotente sobre el mismo estado.
     */
    private fun decide(postulationId: UUID, requesterId: UUID, newStatus: String): PostulationRecord {
        val postulation = postulations.findById(postulationId)
            ?: throw PostulationNotFound("Postulación no encontrada")
        val convocatory = convocatories.findById(postulation.convocatoryId)
            ?: throw PostulationNotFound("Convocatoria no encontrada")
        if (convocatory.organizerId != requesterId) {
            throw PostulationForbidden("Solo el organizador puede decidir la postulación")
        }
        postulations.updateStatus(postulationId, newStatus)
        val updated = postulations.findById(postulationId)
            ?: throw PostulationNotFound("Postulación no encontrada tras actualizar")

        // Al aprobar: el jugador ya no puede estar en otro partido a esa hora,
        // así que se cancelan automáticamente sus demás postulaciones PENDIENTES
        // que se solapen con el horario recién aprobado.
        if (newStatus == "approved") {
            cancelConflictingPending(updated.playerId, convocatory.scheduledAt, exclude = postulationId)
        }

        // Notifica al jugador la decisión (best-effort).
        val (title, body) = when (newStatus) {
            "approved" -> "¡Te aceptaron!" to "Fuiste seleccionado para la convocatoria de ${convocatory.format}."
            "rejected" -> "Postulación no seleccionada" to "Esta vez no quedaste en la convocatoria de ${convocatory.format}."
            else -> "Actualización de tu postulación" to "El estado de tu postulación cambió."
        }
        fcm.sendToToken(
            token = users.getFcmToken(updated.playerId),
            title = title,
            body = body,
            data = mapOf(
                "type" to "postulation_$newStatus",
                "convocatoryId" to updated.convocatoryId.toString(),
                "postulationId" to updated.id.toString(),
            ),
        )
        return updated
    }

    /**
     * Marca como "cancelled" las postulaciones PENDIENTES del jugador que se
     * solapen con [approvedSchedule] (excluyendo [exclude], la recién aprobada).
     * Best-effort en notificaciones.
     */
    private fun cancelConflictingPending(playerId: UUID, approvedSchedule: String, exclude: UUID) {
        val approvedStart = parseSchedule(approvedSchedule) ?: return
        postulations.findByPlayer(playerId)
            .filter { it.id != exclude && it.status == "pending" }
            .filter { parseSchedule(it.convocatory.scheduledAt)?.let { s -> overlaps(s, approvedStart) } == true }
            .forEach { conflicting ->
                postulations.updateStatus(conflicting.id, "cancelled")
                fcm.sendToToken(
                    token = users.getFcmToken(playerId),
                    title = "Postulación cancelada",
                    body = "Cancelamos tu postulación a ${conflicting.convocatory.format}: " +
                        "se cruzaba con otro partido en el que te aceptaron.",
                    data = mapOf(
                        "type" to "postulation_cancelled",
                        "convocatoryId" to conflicting.convocatory.id.toString(),
                        "postulationId" to conflicting.id.toString(),
                    ),
                )
            }
    }
}
