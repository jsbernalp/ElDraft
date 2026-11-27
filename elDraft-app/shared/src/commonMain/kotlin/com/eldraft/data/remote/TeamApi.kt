package com.eldraft.data.remote

import com.eldraft.core.config.ApiConfig
import com.eldraft.core.network.AuthTokenProvider
import com.eldraft.core.network.BaseApi
import com.eldraft.data.models.AddTeamMemberRequest
import com.eldraft.data.models.CreateTeamRequest
import com.eldraft.data.models.RenameTeamRequest
import com.eldraft.data.models.Team
import com.eldraft.data.models.TeamDetail
import com.eldraft.data.models.TeamInvitation
import com.eldraft.data.models.TeamInvitationResponseRequest
import com.eldraft.data.models.TeamMember
import com.eldraft.data.models.TeamSuggestion
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class TeamApi(
    client: HttpClient,
    config: ApiConfig,
    tokenProvider: AuthTokenProvider,
) : BaseApi(client, config, tokenProvider) {

    private val teamsUrl get() = "$baseUrl/api/v1/teams"

    suspend fun getMyTeams(): List<Team> =
        client.get(teamsUrl) { auth() }.body()

    suspend fun createTeam(name: String): Team =
        client.post(teamsUrl) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest(name))
        }.body()

    suspend fun getSuggestions(teamId: String?): List<TeamSuggestion> =
        client.get("$teamsUrl/suggestions") {
            auth()
            if (!teamId.isNullOrBlank()) parameter("teamId", teamId)
        }.body()

    suspend fun getInvitations(): List<TeamInvitation> =
        client.get("$teamsUrl/invitations") { auth() }.body()

    suspend fun getMemberships(): List<Team> =
        client.get("$teamsUrl/memberships") { auth() }.body()

    suspend fun getTeamDetail(teamId: String): TeamDetail =
        client.get("$teamsUrl/$teamId") { auth() }.body()

    suspend fun renameTeam(teamId: String, name: String): Team =
        client.patch("$teamsUrl/$teamId") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(RenameTeamRequest(name))
        }.body()

    suspend fun deleteTeam(teamId: String) {
        client.delete("$teamsUrl/$teamId") { auth() }
    }

    suspend fun addMember(teamId: String, playerId: String?, email: String?): TeamMember =
        client.post("$teamsUrl/$teamId/members") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(AddTeamMemberRequest(playerId = playerId, email = email))
        }.body()

    suspend fun removeMember(teamId: String, playerId: String) {
        client.delete("$teamsUrl/$teamId/members/$playerId") { auth() }
    }

    suspend fun respondInvitation(teamId: String, accept: Boolean) {
        client.post("$teamsUrl/$teamId/invitation") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(TeamInvitationResponseRequest(accept))
        }
    }
}
