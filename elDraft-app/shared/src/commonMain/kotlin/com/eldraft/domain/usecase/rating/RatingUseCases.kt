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

/** Califica a un jugador en 3 criterios (cada uno 1-5). */
class SubmitRatingUseCase(
    private val repository: RatingRepository,
) {
    suspend operator fun invoke(
        convocatoryId: String,
        ratedPlayerId: String,
        skill: Int,
        sportsmanship: Int,
        responsibility: Int,
    ) {
        require(convocatoryId.isNotBlank()) { "Convocatoria inválida" }
        require(ratedPlayerId.isNotBlank()) { "Jugador inválido" }
        require(skill in 1..5 && sportsmanship in 1..5 && responsibility in 1..5) {
            "Cada criterio debe estar entre 1 y 5"
        }
        repository.submitRating(convocatoryId, ratedPlayerId, skill, sportsmanship, responsibility)
    }
}
