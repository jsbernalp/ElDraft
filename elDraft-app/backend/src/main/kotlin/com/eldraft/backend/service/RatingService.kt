package com.eldraft.backend.service

import com.eldraft.backend.repository.RatingRepository
import com.eldraft.backend.repository.TeammateRecord
import java.util.UUID

/** Errores de dominio de calificaciones (las rutas los mapean a HTTP). */
class RatingForbidden(message: String) : RuntimeException(message)
class RatingConflict(message: String) : RuntimeException(message)
class RatingInvalid(message: String) : RuntimeException(message)

/**
 * Calificación de compañerismo post-partido.
 * Reglas: solo quien asistió puede calificar; solo a otros asistentes; sin
 * autocalificarse; sin duplicar; score 1-5. Al guardar, recalcula el
 * sportsmanship_score (promedio) del jugador calificado.
 */
class RatingService(
    private val ratings: RatingRepository,
) {
    /** Compañeros calificables (asistentes) para el solicitante. */
    fun teammatesToRate(convocatoryId: UUID, requesterId: UUID): List<TeammateRecord> {
        if (!ratings.attended(convocatoryId, requesterId)) {
            throw RatingForbidden("Solo quienes asistieron pueden calificar")
        }
        return ratings.teammates(convocatoryId, requesterId)
    }

    /** Guarda una calificación tras validar todas las reglas. */
    fun submit(convocatoryId: UUID, raterId: UUID, ratedPlayerId: UUID, score: Int) {
        if (score !in 1..5) throw RatingInvalid("La calificación debe estar entre 1 y 5")
        if (raterId == ratedPlayerId) throw RatingForbidden("No puedes calificarte a ti mismo")
        if (!ratings.attended(convocatoryId, raterId)) {
            throw RatingForbidden("Solo quienes asistieron pueden calificar")
        }
        if (!ratings.attended(convocatoryId, ratedPlayerId)) {
            throw RatingForbidden("Solo puedes calificar a quienes asistieron")
        }
        if (ratings.hasRated(convocatoryId, raterId, ratedPlayerId)) {
            throw RatingConflict("Ya calificaste a este jugador en este partido")
        }
        ratings.saveRating(convocatoryId, raterId, ratedPlayerId, score)
        ratings.recomputeSportsmanship(ratedPlayerId)
    }
}
