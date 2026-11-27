package com.eldraft.data.repository

import com.eldraft.data.models.Convocatory
import com.eldraft.data.models.TeamPriorityInvite
import com.eldraft.data.remote.ConvocatoryApi
import com.eldraft.domain.repository.TeamPriorityRepository

class TeamPriorityRepositoryImpl(
    private val convocatoryApi: ConvocatoryApi,
) : TeamPriorityRepository {

    override suspend fun getPriorityInvites(): List<TeamPriorityInvite> = convocatoryApi.getPriorityInvites()

    override suspend fun respond(convocatoryId: String, accept: Boolean, position: String?) =
        convocatoryApi.respondTeamPriority(convocatoryId, accept, position)

    override suspend fun openToPublic(convocatoryId: String): Convocatory = convocatoryApi.openToPublic(convocatoryId)
}
