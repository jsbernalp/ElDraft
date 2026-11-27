package com.eldraft.domain.repository

import com.eldraft.data.models.Team
import com.eldraft.data.models.TeamDetail
import com.eldraft.data.models.TeamInvitation
import com.eldraft.data.models.TeamMember
import com.eldraft.data.models.TeamSuggestion

/** Equipos del usuario: los que posee, en los que juega y sus invitaciones. */
interface TeamRepository {

    /** Equipos de los que soy dueño, con conteo de activos e invitados. */
    @Throws(Exception::class)
    suspend fun getMyTeams(): List<Team>

    @Throws(Exception::class)
    suspend fun createTeam(name: String): Team

    @Throws(Exception::class)
    suspend fun renameTeam(teamId: String, name: String): Team

    @Throws(Exception::class)
    suspend fun deleteTeam(teamId: String)

    @Throws(Exception::class)
    suspend fun getTeamDetail(teamId: String): TeamDetail

    /** Jugadores con los que ya jugué; si llega [teamId], excluye a quienes ya están en él. */
    @Throws(Exception::class)
    suspend fun getSuggestions(teamId: String?): List<TeamSuggestion>

    /** Invita por id (desde sugerencias) o por correo: exactamente uno de los dos. */
    @Throws(Exception::class)
    suspend fun addMember(teamId: String, playerId: String?, email: String?): TeamMember

    /** El dueño quita a un jugador, o el propio miembro se sale. */
    @Throws(Exception::class)
    suspend fun removeMember(teamId: String, playerId: String)

    @Throws(Exception::class)
    suspend fun getInvitations(): List<TeamInvitation>

    @Throws(Exception::class)
    suspend fun respondInvitation(teamId: String, accept: Boolean)

    /** Equipos ajenos en los que soy miembro activo. */
    @Throws(Exception::class)
    suspend fun getMemberships(): List<Team>
}
