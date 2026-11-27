package com.eldraft.backend.service

import com.eldraft.backend.notifications.FcmService
import com.eldraft.backend.repository.ANONYMIZED_UID_PREFIX
import com.eldraft.backend.repository.TeamInvitationRecord
import com.eldraft.backend.repository.TeamMemberRecord
import com.eldraft.backend.repository.TeamMemberStatus
import com.eldraft.backend.repository.TeamRecord
import com.eldraft.backend.repository.TeamRepository
import com.eldraft.backend.repository.TeamSummaryRecord
import com.eldraft.backend.repository.UserRepository
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

/** Errores de dominio de equipos (StatusPages los mapea a HTTP). */
class TeamForbidden(message: String) : RuntimeException(message)
class TeamNotFound(message: String) : RuntimeException(message)
class TeamConflict(message: String) : RuntimeException(message)
class TeamLimitReached(message: String) : RuntimeException(message)
class PlayerNotFoundByEmail(message: String) : RuntimeException(message)
class TeamMemberIneligible(message: String) : RuntimeException(message)
class TeamRateLimited(message: String) : RuntimeException(message)

/** Detalle de un equipo tal como lo puede ver quien pregunta. */
data class TeamDetail(
    val team: TeamRecord,
    val isOwner: Boolean,
    val members: List<TeamMemberRecord>,
)

/** Un jugador sugerido para el equipo: alguien con quien ya jugué. */
data class TeamSuggestion(
    val playerId: UUID,
    val name: String,
    val avatarUrl: String?,
    val positionPrimary: String?,
    val matchesTogether: Int,
    val lastPlayedAt: LocalDateTime,
)

/**
 * Equipos (Parte 1): una lista del dueño con nombre y miembros. Los miembros no
 * la administran: solo aceptan la invitación o se salen.
 *
 * - Máximo [MAX_OWNED_TEAMS] equipos propios por usuario y [MAX_MEMBERS]
 *   miembros (invitados + activos; el dueño no es fila de team_members).
 * - Solo se agrega a quien completó su ficha de jugador.
 * - Las invitaciones por correo pasan por [TeamInviteRateLimiter].
 *
 * El push `team_invite` es best-effort, igual que en [PostulationService].
 */
class TeamService(
    private val teams: TeamRepository,
    private val users: UserRepository,
    private val fcm: FcmService,
    private val rateLimiter: TeamInviteRateLimiter,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val teamPriority: TeamPriorityHooks = NoTeamPriorityHooks,
) {
    private fun now(): LocalDateTime = LocalDateTime.now(clock)

    fun create(ownerId: UUID, rawName: String): TeamSummaryRecord {
        val name = validName(rawName)
        if (!users.hasProfile(ownerId)) {
            throw TeamForbidden("Completa tu perfil para crear equipos")
        }
        if (teams.countOwned(ownerId) >= MAX_OWNED_TEAMS) {
            throw TeamLimitReached("Ya tienes $MAX_OWNED_TEAMS equipos, el máximo permitido")
        }
        return TeamSummaryRecord(teams.create(ownerId, name, now()), activeCount = 0, invitedCount = 0)
    }

    fun owned(ownerId: UUID): List<TeamSummaryRecord> = teams.ownedBy(ownerId)

    fun memberships(userId: UUID): List<TeamSummaryRecord> = teams.memberships(userId)

    fun invitations(userId: UUID): List<TeamInvitationRecord> = teams.invitationsFor(userId)

    /**
     * El dueño ve a todos con su estado; un miembro activo ve solo a los activos.
     * Para cualquier otro —incluido un invitado— el equipo "no existe".
     */
    fun detail(teamId: UUID, requesterId: UUID): TeamDetail {
        val team = teams.findById(teamId) ?: throw notFound()
        if (team.ownerId == requesterId) {
            return TeamDetail(team, isOwner = true, members = teams.members(teamId))
        }
        val mine = teams.membership(teamId, requesterId)
        if (mine?.status != TeamMemberStatus.ACTIVE) throw notFound()
        return TeamDetail(
            team,
            isOwner = false,
            members = teams.members(teamId).filter { it.status == TeamMemberStatus.ACTIVE },
        )
    }

    fun rename(teamId: UUID, requesterId: UUID, rawName: String): TeamSummaryRecord {
        val name = validName(rawName)
        ownedTeam(teamId, requesterId)
        teams.rename(teamId, name)
        return teams.ownedBy(requesterId).firstOrNull { it.team.id == teamId } ?: throw notFound()
    }

    fun delete(teamId: UUID, requesterId: UUID) {
        ownedTeam(teamId, requesterId)
        // Las convocatorias de este equipo que sigan en fase privada se abren al
        // público ANTES de borrar (después ya no habría quién las encuentre).
        teamPriority.onTeamDeleted(teamId)
        teams.delete(teamId)
    }

    /**
     * Invita a un jugador por id (desde sugerencias) o por correo. Exactamente
     * uno de los dos. Cada intento por correo consume cupo del rate-limit, sea
     * cual sea el resultado; por id no consume (no sirve para sondear correos).
     */
    fun addMember(teamId: UUID, requesterId: UUID, playerId: UUID?, email: String?): TeamMemberRecord {
        require((playerId == null) != (email == null)) { "Envía playerId o email, solo uno" }

        if (email != null && !rateLimiter.tryAcquire(requesterId)) {
            throw TeamRateLimited("Hiciste demasiadas búsquedas por correo. Intenta más tarde")
        }

        val team = ownedTeam(teamId, requesterId)

        val target = if (email != null) {
            val normalized = email.trim().lowercase()
            require(normalized.isNotEmpty()) { "Escribe un correo" }
            users.findByEmail(normalized)
                ?: throw PlayerNotFoundByEmail("No encontramos un jugador con ese correo")
        } else {
            users.findById(playerId!!)
                ?.takeUnless { it.firebaseUid.startsWith(ANONYMIZED_UID_PREFIX) }
                ?: throw TeamNotFound("No encontramos a ese jugador")
        }

        if (target.id == requesterId) {
            throw TeamConflict("No puedes agregarte a tu propio equipo")
        }
        if (!users.hasProfile(target.id)) {
            throw TeamMemberIneligible("Ese jugador todavía no completó su perfil")
        }
        teams.membership(teamId, target.id)?.let { existing ->
            throw TeamConflict(
                if (existing.status == TeamMemberStatus.ACTIVE) "Ese jugador ya es miembro del equipo"
                else "Ese jugador ya tiene una invitación pendiente"
            )
        }
        if (teams.countMembers(teamId) >= MAX_MEMBERS) {
            throw TeamLimitReached("El equipo ya tiene $MAX_MEMBERS jugadores, el máximo permitido")
        }

        val created = teams.addInvite(teamId, target.id, now())
            ?: throw TeamConflict("Ese jugador ya tiene una invitación pendiente")

        // Solo las invitaciones nuevas notifican (best-effort).
        fcm.sendToToken(
            token = users.getFcmToken(target.id),
            title = "Te invitaron a un equipo",
            body = "${team.ownerName} te agregó a «${team.name}»",
            data = mapOf(
                "type" to "team_invite",
                "teamId" to teamId.toString(),
            ),
        )
        return created
    }

    /** Aceptar → miembro activo. Rechazar → se borra la fila (se puede reinvitar). */
    fun respondInvitation(teamId: UUID, playerId: UUID, accept: Boolean) {
        val mine = teams.membership(teamId, playerId)
            ?: throw TeamNotFound("No tienes una invitación a ese equipo")
        if (mine.status == TeamMemberStatus.ACTIVE) {
            throw TeamConflict("Ya eres miembro de este equipo")
        }
        if (accept) {
            if (!teams.accept(teamId, playerId, now())) {
                throw TeamNotFound("No tienes una invitación a ese equipo")
            }
        } else {
            teams.removeMember(teamId, playerId)
        }
    }

    /**
     * El dueño quita a cualquiera (invitado o activo); un miembro se sale a sí
     * mismo. Nadie más puede. El dueño no puede quitarse: para eso borra el equipo.
     */
    fun removeMember(teamId: UUID, requesterId: UUID, playerId: UUID) {
        val team = teams.findById(teamId) ?: throw notFound()
        val isOwner = team.ownerId == requesterId
        if (isOwner && playerId == requesterId) {
            throw TeamConflict("Eres el dueño del equipo: si ya no lo quieres, bórralo")
        }
        if (!isOwner && playerId != requesterId) {
            throw TeamForbidden("Solo el dueño puede quitar jugadores del equipo")
        }
        if (teams.membership(teamId, playerId) == null) {
            throw TeamNotFound("Ese jugador no está en el equipo")
        }
        teams.removeMember(teamId, playerId)
        // Su invitación prioritaria viva pasa a expired (su postulación, si existe,
        // se mantiene: ya es una postulación normal).
        teamPriority.onMemberRemoved(teamId, playerId)
    }

    /**
     * Jugadores con los que ya jugué, para agregarlos a un equipo. Si llega
     * [teamId] y soy su dueño, se excluye a quienes ya están en él (invitados o
     * activos). Nunca incluye cuentas borradas ni jugadores sin ficha.
     */
    fun suggestions(requesterId: UUID, teamId: UUID?): List<TeamSuggestion> {
        val exclude = teamId
            ?.let { teams.findById(it) }
            ?.takeIf { it.ownerId == requesterId }
            ?.let { team -> teams.members(team.id).map { it.playerId }.toSet() }
            ?: emptySet()

        // Sin tope aquí: el corte se aplica después de filtrar, para no devolver
        // menos de los que caben por culpa de candidatos descartados.
        val counts = coPlayCounts(teams.coPlayRows(requesterId), requesterId, now(), limit = Int.MAX_VALUE)
            .filter { it.playerId !in exclude }
        if (counts.isEmpty()) return emptyList()

        val candidates = teams.suggestionCandidates(counts.map { it.playerId })
            .filter { it.hasProfile && !it.anonymized }
            .associateBy { it.playerId }

        return counts.mapNotNull { count ->
            val c = candidates[count.playerId] ?: return@mapNotNull null
            TeamSuggestion(
                playerId = c.playerId,
                name = c.name,
                avatarUrl = c.avatarUrl,
                positionPrimary = c.positionPrimary,
                matchesTogether = count.matches,
                lastPlayedAt = count.lastPlayedAt,
            )
        }.take(TEAM_SUGGESTIONS_LIMIT)
    }

    /** El equipo si [requesterId] es su dueño; si no, Forbidden (o NotFound si no existe). */
    private fun ownedTeam(teamId: UUID, requesterId: UUID): TeamRecord {
        val team = teams.findById(teamId) ?: throw notFound()
        if (team.ownerId != requesterId) {
            throw TeamForbidden("Solo el dueño puede administrar el equipo")
        }
        return team
    }

    private fun validName(raw: String): String {
        val name = raw.trim()
        require(name.isNotEmpty()) { "Ponle un nombre al equipo" }
        require(name.length <= MAX_NAME_LENGTH) { "El nombre puede tener hasta $MAX_NAME_LENGTH caracteres" }
        return name
    }

    private fun notFound() = TeamNotFound("Equipo no encontrado")

    companion object {
        const val MAX_OWNED_TEAMS = 5
        const val MAX_MEMBERS = 30
        const val MAX_NAME_LENGTH = 40
    }
}
