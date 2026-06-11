package com.eldraft.backend.service

import com.eldraft.backend.repository.RatingRepository
import com.eldraft.backend.repository.TeammateRecord
import java.util.UUID

/** Errores de dominio de calificaciones (las rutas los mapean a HTTP). */
class RatingForbidden(message: String) : RuntimeException(message)
class RatingConflict(message: String) : RuntimeException(message)
class RatingInvalid(message: String) : RuntimeException(message)

/**
 * Calificación post-partido en 3 criterios (habilidad, deportividad,
 * responsabilidad), cada uno 1-5.
 * Reglas: solo quien asistió puede calificar; solo a otros asistentes; sin
 * autocalificarse; sin duplicar. Al guardar, recalcula los 3 atributos
 * (promedio) del jugador calificado.
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
    fun submit(
        convocatoryId: UUID,
        raterId: UUID,
        ratedPlayerId: UUID,
        skill: Int,
        sportsmanship: Int,
        responsibility: Int,
    ) {
        if (skill !in 1..5 || sportsmanship !in 1..5 || responsibility !in 1..5) {
            throw RatingInvalid("Cada criterio debe estar entre 1 y 5")
        }
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
        ratings.saveRating(convocatoryId, raterId, ratedPlayerId, skill, sportsmanship, responsibility)
        ratings.recomputeScores(ratedPlayerId)
    }
}
