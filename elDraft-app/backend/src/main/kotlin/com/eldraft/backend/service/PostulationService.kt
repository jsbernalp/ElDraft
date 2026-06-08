package com.eldraft.backend.service

import com.eldraft.backend.notifications.FcmService
import com.eldraft.backend.repository.ConvocatoryRepository
import com.eldraft.backend.repository.PostulationRecord
import com.eldraft.backend.repository.PostulationRepository
import com.eldraft.backend.repository.UserRepository
import java.util.UUID

/** Errores de dominio de postulaciones (las rutas los mapean a HTTP). */
class PostulationConflict(message: String) : RuntimeException(message)
class PostulationForbidden(message: String) : RuntimeException(message)
class PostulationNotFound(message: String) : RuntimeException(message)

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
) {
    /**
     * Un jugador se postula a una convocatoria.
     * Reglas: la convocatoria debe existir y estar activa, el jugador no puede
     * ser el organizador, y no puede postularse dos veces.
     */
    fun apply(convocatoryId: UUID, playerId: UUID): PostulationRecord {
        val convocatory = convocatories.findById(convocatoryId)
            ?: throw PostulationNotFound("Convocatoria no encontrada")
        if (convocatory.status != "active") {
            throw PostulationConflict("La convocatoria ya no está abierta")
        }
        if (convocatory.organizerId == playerId) {
            throw PostulationConflict("No puedes postularte a tu propia convocatoria")
        }
        val created = postulations.create(convocatoryId, playerId)
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
}
