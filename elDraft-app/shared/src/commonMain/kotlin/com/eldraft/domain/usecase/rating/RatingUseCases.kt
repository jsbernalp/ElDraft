package com.eldraft.domain.usecase.rating

import com.eldraft.data.models.Teammate
import com.eldraft.domain.repository.RatingRepository

/** Lista los compañeros (asistentes) que el usuario puede calificar. */
class GetTeammatesToRateUseCase(
    private val repository: RatingRepository,
) {
    suspend operator fun invoke(convocatoryId: String): List<Teammate> {
        require(convocatoryId.isNotBlank()) { "Convocatoria inválida" }
        return repository.getTeammates(convocatoryId)
    }
}

/** Califica el compañerismo (1-5) de un jugador. */
class SubmitRatingUseCase(
    private val repository: RatingRepository,
) {
    suspend operator fun invoke(convocatoryId: String, ratedPlayerId: String, score: Int) {
        require(convocatoryId.isNotBlank()) { "Convocatoria inválida" }
        require(ratedPlayerId.isNotBlank()) { "Jugador inválido" }
        require(score in 1..5) { "La calificación debe estar entre 1 y 5" }
        repository.submitRating(convocatoryId, ratedPlayerId, score)
    }
}
