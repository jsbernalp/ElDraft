package com.eldraft.data.repository

import com.eldraft.data.models.Teammate
import com.eldraft.data.remote.RatingApi
import com.eldraft.domain.repository.RatingRepository

class RatingRepositoryImpl(
    private val ratingApi: RatingApi,
) : RatingRepository {

    override suspend fun getTeammates(convocatoryId: String): List<Teammate> =
        ratingApi.getTeammates(convocatoryId)

    override suspend fun submitRating(
        convocatoryId: String,
        ratedPlayerId: String,
        skill: Int,
        sportsmanship: Int,
        responsibility: Int,
    ) {
        ratingApi.submitRating(convocatoryId, ratedPlayerId, skill, sportsmanship, responsibility)
    }
}
