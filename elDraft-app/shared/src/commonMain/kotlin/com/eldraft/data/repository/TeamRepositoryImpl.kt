package com.eldraft.data.repository

import com.eldraft.data.models.Team
import com.eldraft.data.models.TeamDetail
import com.eldraft.data.models.TeamInvitation
import com.eldraft.data.models.TeamMember
import com.eldraft.data.models.TeamSuggestion
import com.eldraft.data.remote.TeamApi
import com.eldraft.domain.repository.TeamRepository

class TeamRepositoryImpl(
    private val teamApi: TeamApi,
) : TeamRepository {

    override suspend fun getMyTeams(): List<Team> = teamApi.getMyTeams()

    override suspend fun createTeam(name: String): Team = teamApi.createTeam(name)

    override suspend fun renameTeam(teamId: String, name: String): Team = teamApi.renameTeam(teamId, name)

    override suspend fun deleteTeam(teamId: String) = teamApi.deleteTeam(teamId)

    override suspend fun getTeamDetail(teamId: String): TeamDetail = teamApi.getTeamDetail(teamId)

    override suspend fun getSuggestions(teamId: String?): List<TeamSuggestion> = teamApi.getSuggestions(teamId)

    override suspend fun addMember(teamId: String, playerId: String?, email: String?): TeamMember =
        teamApi.addMember(teamId, playerId, email)

    override suspend fun removeMember(teamId: String, playerId: String) = teamApi.removeMember(teamId, playerId)

    override suspend fun getInvitations(): List<TeamInvitation> = teamApi.getInvitations()

    override suspend fun respondInvitation(teamId: String, accept: Boolean) =
        teamApi.respondInvitation(teamId, accept)

    override suspend fun getMemberships(): List<Team> = teamApi.getMemberships()
}
