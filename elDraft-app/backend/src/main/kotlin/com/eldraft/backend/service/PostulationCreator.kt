package com.eldraft.backend.service

import com.eldraft.backend.notifications.FcmService
import com.eldraft.backend.repository.ConvocatoryRepository
import com.eldraft.backend.repository.PostulationRecord
import com.eldraft.backend.repository.PostulationRepository
import com.eldraft.backend.repository.UserRepository
import java.util.UUID

/**
 * Crea una postulación aplicando las reglas de siempre. Vive aparte de
 * [PostulationService] para que [TeamPriorityService] pueda crear el "Voy" de un
 * miembro del equipo sin depender de él (PostulationService ya depende de
 * TeamPriorityService para los ganchos de liberación de cupo: sería un ciclo).
 *
 * [allowTeamOnly]: solo la vía interna del equipo la pone en true; la
 * postulación pública sigue exigiendo `status == "active"`.
 */
class PostulationCreator(
    private val postulations: PostulationRepository,
    private val convocatories: ConvocatoryRepository,
    private val users: UserRepository,
    private val fcm: FcmService,
) {
    fun create(
        convocatoryId: UUID,
        playerId: UUID,
        position: String,
        allowTeamOnly: Boolean = false,
    ): PostulationRecord {
        val convocatory = convocatories.findById(convocatoryId)
            ?: throw PostulationNotFound("Convocatoria no encontrada")
        val open = convocatory.status == "active" || (allowTeamOnly && convocatory.status == "team_only")
        if (!open) {
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
}
