package com.eldraft.domain.repository

import com.eldraft.data.models.Teammate

/** Calificación de compañerismo post-partido. */
interface RatingRepository {

    /** Compañeros (asistentes) que el usuario puede calificar en la convocatoria. */
    suspend fun getTeammates(convocatoryId: String): List<Teammate>

    /**
     * Califica a un jugador en la convocatoria en 3 criterios (cada uno 1-5):
     * habilidad, deportividad y responsabilidad.
     */
    suspend fun submitRating(
        convocatoryId: String,
        ratedPlayerId: String,
        skill: Int,
        sportsmanship: Int,
        responsibility: Int,
    )
}
