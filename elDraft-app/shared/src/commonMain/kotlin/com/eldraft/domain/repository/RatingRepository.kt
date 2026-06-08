package com.eldraft.domain.repository

import com.eldraft.data.models.Teammate

/** Calificación de compañerismo post-partido. */
interface RatingRepository {

    /** Compañeros (asistentes) que el usuario puede calificar en la convocatoria. */
    suspend fun getTeammates(convocatoryId: String): List<Teammate>

    /** Califica el compañerismo (1-5) de un jugador en la convocatoria. */
    suspend fun submitRating(convocatoryId: String, ratedPlayerId: String, score: Int)
}
