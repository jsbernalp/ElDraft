package com.eldraft.backend.service

import com.eldraft.backend.repository.ConvocatoryRepository
import com.eldraft.backend.repository.PostulationRecord
import com.eldraft.backend.repository.PostulationRepository
import java.util.UUID

/** Errores de dominio de postulaciones (las rutas los mapean a HTTP). */
class PostulationConflict(message: String) : RuntimeException(message)
class PostulationForbidden(message: String) : RuntimeException(message)
class PostulationNotFound(message: String) : RuntimeException(message)

/**
 * Lógica de postulaciones (El Draft, lado jugador y organizador):
 * postularse, listar postulantes y aprobar/rechazar.
 */
class PostulationService(
    private val postulations: PostulationRepository,
    private val convocatories: ConvocatoryRepository,
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
        return postulations.create(convocatoryId, playerId)
            ?: throw PostulationConflict("Ya te postulaste a esta convocatoria")
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
        return postulations.findById(postulationId)
            ?: throw PostulationNotFound("Postulación no encontrada tras actualizar")
    }
}
