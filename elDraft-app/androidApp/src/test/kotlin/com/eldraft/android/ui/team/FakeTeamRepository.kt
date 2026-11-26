package com.eldraft.android.ui.team

import com.eldraft.data.models.LoginResponse
import com.eldraft.data.models.Team
import com.eldraft.data.models.TeamDetail
import com.eldraft.data.models.TeamInvitation
import com.eldraft.data.models.TeamMember
import com.eldraft.data.models.TeamSuggestion
import com.eldraft.data.models.User
import com.eldraft.domain.repository.AuthRepository
import com.eldraft.domain.repository.TeamRepository

/**
 * Fake a mano del repositorio de equipos, compartido por los tests de
 * `TeamsViewModel` y `TeamDetailViewModel`. Los casos de uso se construyen de
 * verdad sobre él, así que su validación también queda cubierta.
 */
internal class FakeTeamRepository(
    var myTeams: List<Team> = emptyList(),
    var memberships: List<Team> = emptyList(),
    var invitations: List<TeamInvitation> = emptyList(),
    var detail: TeamDetail = TeamDetail(id = "t1", name = "Los de siempre", isOwner = true),
    var suggestions: List<TeamSuggestion> = emptyList(),
    /** Si no es null, la próxima llamada que lo consulte lanza esta excepción. */
    var failLoadWith: Exception? = null,
    var failCreateWith: Exception? = null,
    var failInviteWith: Exception? = null,
    var failRespondWith: Exception? = null,
) : TeamRepository {

    val created = mutableListOf<String>()
    val responses = mutableListOf<Pair<String, Boolean>>()
    val removed = mutableListOf<Pair<String, String>>()
    val deleted = mutableListOf<String>()
    val suggestionRequests = mutableListOf<String?>()
    val invitesByEmail = mutableListOf<String>()

    override suspend fun getMyTeams(): List<Team> {
        failLoadWith?.let { throw it }
        return myTeams
    }

    override suspend fun createTeam(name: String): Team {
        failCreateWith?.let { throw it }
        created += name
        return Team(id = "new-${created.size}", name = name, ownerId = "me", ownerName = "Yo")
    }

    override suspend fun renameTeam(teamId: String, name: String): Team =
        Team(id = teamId, name = name, ownerId = "me")

    override suspend fun deleteTeam(teamId: String) {
        deleted += teamId
    }

    override suspend fun getTeamDetail(teamId: String): TeamDetail {
        failLoadWith?.let { throw it }
        return detail
    }

    override suspend fun getSuggestions(teamId: String?): List<TeamSuggestion> {
        suggestionRequests += teamId
        return suggestions
    }

    override suspend fun addMember(teamId: String, playerId: String?, email: String?): TeamMember {
        failInviteWith?.let { throw it }
        if (email != null) invitesByEmail += email
        val id = playerId ?: "by-mail"
        return TeamMember(playerId = id, name = "Jugador $id", status = "invited")
    }

    override suspend fun removeMember(teamId: String, playerId: String) {
        removed += teamId to playerId
    }

    override suspend fun getInvitations(): List<TeamInvitation> = invitations

    override suspend fun respondInvitation(teamId: String, accept: Boolean) {
        failRespondWith?.let { throw it }
        responses += teamId to accept
    }

    override suspend fun getMemberships(): List<Team> = memberships
}

/** Solo `currentUserId` participa: es lo que usa "Salir del equipo". */
internal class FakeAuthRepository(private val userId: String? = "me") : AuthRepository {
    override suspend fun currentUserId(): String? = userId
    override suspend fun login(firebaseToken: String): LoginResponse = error("no usado")
    override suspend fun updatePhone(phone: String) = error("no usado")
    override suspend fun registerFcmToken(token: String) = error("no usado")
    override suspend fun updateLocation(lat: Double, lng: Double) = error("no usado")
    override suspend fun hasSession(): Boolean = error("no usado")
    override suspend fun logout() = error("no usado")
    override suspend fun getMe(): User = error("no usado")
    override suspend fun updateAccount(name: String, avatarUrl: String?): User = error("no usado")
    override suspend fun deleteAccount() = error("no usado")
}
