package com.eldraft.data.repository

import com.eldraft.data.models.PlayerProfile
import com.eldraft.data.models.UpdateProfileRequest
import com.eldraft.data.remote.PlayerApi
import com.eldraft.domain.repository.ProfileRepository

class ProfileRepositoryImpl(
    private val playerApi: PlayerApi,
) : ProfileRepository {

    override suspend fun getProfile(playerId: String): PlayerProfile =
        playerApi.getProfile(playerId)

    override suspend fun updateProfile(
        playerId: String,
        request: UpdateProfileRequest,
    ): PlayerProfile = playerApi.updateProfile(playerId, request)
}
