package com.eldraft.domain.usecase.team

import com.eldraft.data.models.Team
import com.eldraft.data.models.TeamDetail
import com.eldraft.data.models.TeamInvitation
import com.eldraft.data.models.TeamMember
import com.eldraft.data.models.TeamSuggestion
import com.eldraft.domain.repository.AuthRepository
import com.eldraft.domain.repository.TeamRepository
import com.eldraft.domain.team.TeamRules

private const val INVALID_TEAM = "Equipo inválido"
private const val INVALID_PLAYER = "Jugador inválido"
private const val NO_SESSION = "No hay sesión activa. Inicia sesión de nuevo."

private fun requireValidName(raw: String): String {
    val name = TeamRules.normalizeName(raw)
    require(name.isNotEmpty()) { "Ponle un nombre al equipo" }
    require(name.length <= TeamRules.NAME_MAX_LENGTH) {
        "El nombre puede tener hasta ${TeamRules.NAME_MAX_LENGTH} caracteres"
    }
    return name
}

/** Equipos de los que soy dueño. */
class GetMyTeamsUseCase(private val repository: TeamRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<Team> = repository.getMyTeams()
}

/** Crea un equipo. El nombre llega al backend sin espacios sobrantes. */
class CreateTeamUseCase(private val repository: TeamRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(name: String): Team = repository.createTeam(requireValidName(name))
}

class RenameTeamUseCase(private val repository: TeamRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(teamId: String, name: String): Team {
        require(teamId.isNotBlank()) { INVALID_TEAM }
        return repository.renameTeam(teamId, requireValidName(name))
    }
}

class DeleteTeamUseCase(private val repository: TeamRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(teamId: String) {
        require(teamId.isNotBlank()) { INVALID_TEAM }
        repository.deleteTeam(teamId)
    }
}

class GetTeamDetailUseCase(private val repository: TeamRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(teamId: String): TeamDetail {
        require(teamId.isNotBlank()) { INVALID_TEAM }
        return repository.getTeamDetail(teamId)
    }
}

/**
 * Jugadores con los que ya jugué. Con [teamId] se excluye a quienes ya están en
 * ese equipo; un id en blanco se trata como "sin equipo".
 */
class GetTeamSuggestionsUseCase(private val repository: TeamRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(teamId: String? = null): List<TeamSuggestion> =
        repository.getSuggestions(teamId?.takeIf { it.isNotBlank() })
}

/** A quién invitar: un jugador sugerido (por id) o alguien por su correo. */
sealed class TeamInviteTarget {
    data class ById(val playerId: String) : TeamInviteTarget()
    data class ByEmail(val email: String) : TeamInviteTarget()
}

/**
 * Invita a un jugador al equipo. El correo se normaliza (trim + minúsculas) y se
 * valida su forma antes de gastar un intento del rate-limit del backend.
 */
class InviteToTeamUseCase(private val repository: TeamRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(teamId: String, target: TeamInviteTarget): TeamMember {
        require(teamId.isNotBlank()) { INVALID_TEAM }
        return when (target) {
            is TeamInviteTarget.ById -> {
                require(target.playerId.isNotBlank()) { INVALID_PLAYER }
                repository.addMember(teamId, playerId = target.playerId, email = null)
            }
            is TeamInviteTarget.ByEmail -> {
                val email = TeamRules.normalizeEmail(target.email)
                require(email.isNotEmpty()) { "Escribe un correo" }
                require(TeamRules.isPlausibleEmail(email)) { "Ese correo no parece válido" }
                repository.addMember(teamId, playerId = null, email = email)
            }
        }
    }

    /** Atajo para Swift, que no construye cómodamente la sealed class. */
    @Throws(Exception::class)
    suspend fun byId(teamId: String, playerId: String): TeamMember =
        invoke(teamId, TeamInviteTarget.ById(playerId))

    /** Atajo para Swift, que no construye cómodamente la sealed class. */
    @Throws(Exception::class)
    suspend fun byEmail(teamId: String, email: String): TeamMember =
        invoke(teamId, TeamInviteTarget.ByEmail(email))
}

/**
 * Quita a un jugador del equipo. Sirve para los dos casos: el dueño quitando a
 * alguien, o el propio miembro saliéndose (pasa su propio id).
 */
class RemoveTeamMemberUseCase(private val repository: TeamRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(teamId: String, playerId: String) {
        require(teamId.isNotBlank()) { INVALID_TEAM }
        require(playerId.isNotBlank()) { INVALID_PLAYER }
        repository.removeMember(teamId, playerId)
    }
}

/**
 * El usuario actual se sale de un equipo ajeno. El id propio se resuelve aquí
 * desde la sesión (igual que [com.eldraft.domain.usecase.profile.SaveProfileUseCase]),
 * no en la UI: así ninguna plataforma puede mandar un id vacío por no tener sesión.
 */
class LeaveTeamUseCase(
    private val authRepository: AuthRepository,
    private val repository: TeamRepository,
) {
    @Throws(Exception::class)
    suspend operator fun invoke(teamId: String) {
        require(teamId.isNotBlank()) { INVALID_TEAM }
        val me = authRepository.currentUserId()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(NO_SESSION)
        repository.removeMember(teamId, me)
    }
}

class GetTeamInvitationsUseCase(private val repository: TeamRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<TeamInvitation> = repository.getInvitations()
}

/** Acepta (pasa a miembro activo) o rechaza (se borra) una invitación. */
class RespondTeamInvitationUseCase(private val repository: TeamRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(teamId: String, accept: Boolean) {
        require(teamId.isNotBlank()) { INVALID_TEAM }
        repository.respondInvitation(teamId, accept)
    }
}

/** Equipos ajenos en los que soy miembro activo. */
class GetMyTeamMembershipsUseCase(private val repository: TeamRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<Team> = repository.getMemberships()
}
