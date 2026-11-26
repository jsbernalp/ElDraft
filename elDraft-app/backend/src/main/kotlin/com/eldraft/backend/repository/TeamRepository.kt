package com.eldraft.backend.repository

import com.eldraft.backend.db.tables.ConvocatoriesTable
import com.eldraft.backend.db.tables.PlayerNoShowMarksTable
import com.eldraft.backend.db.tables.PlayerProfilesTable
import com.eldraft.backend.db.tables.PostulationsTable
import com.eldraft.backend.db.tables.TeamMembersTable
import com.eldraft.backend.db.tables.TeamsTable
import com.eldraft.backend.db.tables.UsersTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

/** Estados de una fila de [TeamMembersTable]. */
object TeamMemberStatus {
    const val INVITED = "invited"
    const val ACTIVE = "active"
}

/** Un equipo con el nombre de su dueño ya resuelto. */
data class TeamRecord(
    val id: UUID,
    val ownerId: UUID,
    val ownerName: String,
    val name: String,
    val createdAt: LocalDateTime,
)

/** Un equipo con sus conteos, para las listas. */
data class TeamSummaryRecord(
    val team: TeamRecord,
    val activeCount: Int,
    val invitedCount: Int,
)

/** Un miembro (invitado o activo) con lo necesario para pintarlo. */
data class TeamMemberRecord(
    val teamId: UUID,
    val playerId: UUID,
    val name: String,
    val avatarUrl: String?,
    val positionPrimary: String?,
    val status: String,
    val invitedAt: LocalDateTime,
    val joinedAt: LocalDateTime?,
)

/** Una invitación pendiente que recibió el usuario. */
data class TeamInvitationRecord(
    val teamId: UUID,
    val teamName: String,
    val ownerId: UUID,
    val ownerName: String,
    val ownerAvatarUrl: String?,
    val invitedAt: LocalDateTime,
)

/**
 * Una postulación de una convocatoria en la que participó el usuario, con los
 * datos de la convocatoria que hacen falta para decidir si "jugaron juntos". La
 * decisión vive en `coPlayCounts` (función pura); el repositorio solo trae filas.
 */
data class CoPlayRow(
    val convocatoryId: UUID,
    val scheduledAt: LocalDateTime,
    val convocatoryStatus: String,
    val organizerId: UUID,
    val organizerNoShow: Boolean,
    val playerId: UUID,
    val postulationStatus: String,
    /** True si el organizador marcó a [playerId] como ausente en esa convocatoria. */
    val playerMarkedNoShow: Boolean,
)

/** Candidato a sugerencia, con lo necesario para filtrarlo y pintarlo. */
data class SuggestionCandidate(
    val playerId: UUID,
    val name: String,
    val avatarUrl: String?,
    val positionPrimary: String?,
    val hasProfile: Boolean,
    /** True si la cuenta fue borrada (firebase_uid "deleted:…", ver UserRepository.anonymize). */
    val anonymized: Boolean,
)

open class TeamRepository {

    open fun countOwned(ownerId: UUID): Int = transaction {
        TeamsTable.selectAll().where { TeamsTable.ownerId eq ownerId }.count().toInt()
    }

    open fun create(ownerId: UUID, name: String, now: LocalDateTime): TeamRecord = transaction {
        val id = TeamsTable.insertAndGetId {
            it[TeamsTable.ownerId] = ownerId
            it[TeamsTable.name] = name
            it[createdAt] = now
        }.value
        findById(id) ?: error("Equipo no encontrado tras crearlo")
    }

    open fun findById(teamId: UUID): TeamRecord? = transaction {
        teamsWithOwner().selectAll()
            .where { TeamsTable.id eq teamId }
            .singleOrNull()
            ?.toTeamRecord()
    }

    open fun rename(teamId: UUID, name: String): Boolean = transaction {
        TeamsTable.update({ TeamsTable.id eq teamId }) { it[TeamsTable.name] = name } > 0
    }

    /** Borra el equipo y todas sus filas de miembros (primero los miembros, por la FK). */
    open fun delete(teamId: UUID): Boolean = transaction {
        TeamMembersTable.deleteWhere { TeamMembersTable.teamId eq teamId }
        TeamsTable.deleteWhere { TeamsTable.id eq teamId } > 0
    }

    /** Equipos propios con conteo de activos e invitados, del más nuevo al más viejo. */
    open fun ownedBy(ownerId: UUID): List<TeamSummaryRecord> = transaction {
        val teams = teamsWithOwner().selectAll()
            .where { TeamsTable.ownerId eq ownerId }
            .orderBy(TeamsTable.createdAt, SortOrder.DESC)
            .map { it.toTeamRecord() }
        withCounts(teams)
    }

    /** Equipos ajenos en los que [userId] es miembro activo. */
    open fun memberships(userId: UUID): List<TeamSummaryRecord> = transaction {
        val teamIds = TeamMembersTable.select(TeamMembersTable.teamId)
            .where {
                (TeamMembersTable.playerId eq userId) and
                    (TeamMembersTable.status eq TeamMemberStatus.ACTIVE)
            }
            .map { it[TeamMembersTable.teamId].value }
        if (teamIds.isEmpty()) return@transaction emptyList()
        val teams = teamsWithOwner().selectAll()
            .where { TeamsTable.id inList teamIds }
            .orderBy(TeamsTable.name)
            .map { it.toTeamRecord() }
        withCounts(teams)
    }

    /** Todos los miembros del equipo (invitados y activos). */
    open fun members(teamId: UUID): List<TeamMemberRecord> = transaction {
        memberQuery().selectAll()
            .where { TeamMembersTable.teamId eq teamId }
            .orderBy(TeamMembersTable.invitedAt)
            .map { it.toMemberRecord() }
    }

    /** La fila de [playerId] en el equipo, o null si no está ni invitado. */
    open fun membership(teamId: UUID, playerId: UUID): TeamMemberRecord? = transaction {
        memberQuery().selectAll()
            .where { (TeamMembersTable.teamId eq teamId) and (TeamMembersTable.playerId eq playerId) }
            .singleOrNull()
            ?.toMemberRecord()
    }

    /** Invitados + activos (el dueño no cuenta: no es fila de team_members). */
    open fun countMembers(teamId: UUID): Int = transaction {
        TeamMembersTable.selectAll().where { TeamMembersTable.teamId eq teamId }.count().toInt()
    }

    /**
     * Crea la invitación. Devuelve null si ya existía una fila para ese jugador
     * (la unicidad la garantiza el índice, por si dos invitaciones llegan a la vez).
     */
    open fun addInvite(teamId: UUID, playerId: UUID, now: LocalDateTime): TeamMemberRecord? {
        val inserted = try {
            transaction {
                TeamMembersTable.insert {
                    it[TeamMembersTable.teamId] = teamId
                    it[TeamMembersTable.playerId] = playerId
                    it[status] = TeamMemberStatus.INVITED
                    it[invitedAt] = now
                }
            }
            true
        } catch (_: ExposedSQLException) {
            false
        }
        return if (inserted) membership(teamId, playerId) else null
    }

    /** Pasa la invitación a activa. False si no había invitación pendiente. */
    open fun accept(teamId: UUID, playerId: UUID, now: LocalDateTime): Boolean = transaction {
        TeamMembersTable.update({
            (TeamMembersTable.teamId eq teamId) and
                (TeamMembersTable.playerId eq playerId) and
                (TeamMembersTable.status eq TeamMemberStatus.INVITED)
        }) {
            it[status] = TeamMemberStatus.ACTIVE
            it[joinedAt] = now
        } > 0
    }

    /** Borra la fila (rechazar, salirse o ser quitado). */
    open fun removeMember(teamId: UUID, playerId: UUID): Boolean = transaction {
        TeamMembersTable.deleteWhere {
            (TeamMembersTable.teamId eq teamId) and (TeamMembersTable.playerId eq playerId)
        } > 0
    }

    /** Invitaciones pendientes que recibió [userId]. */
    open fun invitationsFor(userId: UUID): List<TeamInvitationRecord> = transaction {
        TeamMembersTable
            .join(TeamsTable, JoinType.INNER, onColumn = TeamMembersTable.teamId, otherColumn = TeamsTable.id)
            .join(UsersTable, JoinType.INNER, onColumn = TeamsTable.ownerId, otherColumn = UsersTable.id)
            .selectAll()
            .where {
                (TeamMembersTable.playerId eq userId) and
                    (TeamMembersTable.status eq TeamMemberStatus.INVITED)
            }
            .orderBy(TeamMembersTable.invitedAt, SortOrder.DESC)
            .map {
                TeamInvitationRecord(
                    teamId = it[TeamsTable.id].value,
                    teamName = it[TeamsTable.name],
                    ownerId = it[TeamsTable.ownerId].value,
                    ownerName = it[UsersTable.name],
                    ownerAvatarUrl = it[UsersTable.avatarUrl],
                    invitedAt = it[TeamMembersTable.invitedAt],
                )
            }
    }

    /**
     * Postulaciones aprobadas de las convocatorias no canceladas en las que
     * [userId] participó (como organizador o aprobado). Trae filas crudas; qué
     * cuenta como "jugamos juntos" lo decide `coPlayCounts`.
     */
    open fun coPlayRows(userId: UUID): List<CoPlayRow> = transaction {
        val organized = ConvocatoriesTable.select(ConvocatoriesTable.id)
            .where {
                (ConvocatoriesTable.organizerId eq userId) and
                    (ConvocatoriesTable.status neq "cancelled")
            }
            .map { it[ConvocatoriesTable.id].value }
        val played = PostulationsTable.select(PostulationsTable.convocatoryId)
            .where { (PostulationsTable.playerId eq userId) and (PostulationsTable.status eq "approved") }
            .map { it[PostulationsTable.convocatoryId].value }
        val ids = (organized + played).toSet()
        if (ids.isEmpty()) return@transaction emptyList()

        val marks = PlayerNoShowMarksTable.selectAll()
            .where { PlayerNoShowMarksTable.convocatoryId inList ids }
            .map { it[PlayerNoShowMarksTable.convocatoryId].value to it[PlayerNoShowMarksTable.playerId].value }
            .toSet()

        PostulationsTable
            .join(ConvocatoriesTable, JoinType.INNER, onColumn = PostulationsTable.convocatoryId, otherColumn = ConvocatoriesTable.id)
            .selectAll()
            .where {
                (PostulationsTable.convocatoryId inList ids) and
                    (PostulationsTable.status eq "approved") and
                    (ConvocatoriesTable.status neq "cancelled")
            }
            .map {
                val convocatoryId = it[ConvocatoriesTable.id].value
                val playerId = it[PostulationsTable.playerId].value
                CoPlayRow(
                    convocatoryId = convocatoryId,
                    scheduledAt = it[ConvocatoriesTable.scheduledAt],
                    convocatoryStatus = it[ConvocatoriesTable.status],
                    organizerId = it[ConvocatoriesTable.organizerId].value,
                    organizerNoShow = it[ConvocatoriesTable.organizerNoShow],
                    playerId = playerId,
                    postulationStatus = it[PostulationsTable.status],
                    playerMarkedNoShow = (convocatoryId to playerId) in marks,
                )
            }
    }

    /** Datos de presentación de los candidatos, con las banderas para filtrarlos. */
    open fun suggestionCandidates(ids: Collection<UUID>): List<SuggestionCandidate> = transaction {
        if (ids.isEmpty()) return@transaction emptyList()
        UsersTable
            .join(PlayerProfilesTable, JoinType.LEFT, onColumn = UsersTable.id, otherColumn = PlayerProfilesTable.userId)
            .selectAll()
            .where { UsersTable.id inList ids }
            .map {
                SuggestionCandidate(
                    playerId = it[UsersTable.id].value,
                    name = it[UsersTable.name],
                    avatarUrl = it[UsersTable.avatarUrl],
                    positionPrimary = it.getOrNull(PlayerProfilesTable.positionPrimary),
                    hasProfile = it.getOrNull(PlayerProfilesTable.id) != null,
                    anonymized = it[UsersTable.firebaseUid].startsWith(ANONYMIZED_UID_PREFIX),
                )
            }
    }

    private fun teamsWithOwner() =
        TeamsTable.join(UsersTable, JoinType.INNER, onColumn = TeamsTable.ownerId, otherColumn = UsersTable.id)

    private fun memberQuery() = TeamMembersTable
        .join(UsersTable, JoinType.INNER, onColumn = TeamMembersTable.playerId, otherColumn = UsersTable.id)
        .join(PlayerProfilesTable, JoinType.LEFT, onColumn = UsersTable.id, otherColumn = PlayerProfilesTable.userId)

    /** Añade conteos activos/invitados con una sola consulta sobre team_members. */
    private fun withCounts(teams: List<TeamRecord>): List<TeamSummaryRecord> {
        if (teams.isEmpty()) return emptyList()
        val rows = TeamMembersTable.select(TeamMembersTable.teamId, TeamMembersTable.status)
            .where { TeamMembersTable.teamId inList teams.map { it.id } }
            .map { it[TeamMembersTable.teamId].value to it[TeamMembersTable.status] }
        return teams.map { team ->
            val mine = rows.filter { it.first == team.id }
            TeamSummaryRecord(
                team = team,
                activeCount = mine.count { it.second == TeamMemberStatus.ACTIVE },
                invitedCount = mine.count { it.second == TeamMemberStatus.INVITED },
            )
        }
    }

    private fun ResultRow.toTeamRecord() = TeamRecord(
        id = this[TeamsTable.id].value,
        ownerId = this[TeamsTable.ownerId].value,
        ownerName = this[UsersTable.name],
        name = this[TeamsTable.name],
        createdAt = this[TeamsTable.createdAt],
    )

    private fun ResultRow.toMemberRecord() = TeamMemberRecord(
        teamId = this[TeamMembersTable.teamId].value,
        playerId = this[TeamMembersTable.playerId].value,
        name = this[UsersTable.name],
        avatarUrl = this[UsersTable.avatarUrl],
        positionPrimary = this.getOrNull(PlayerProfilesTable.positionPrimary),
        status = this[TeamMembersTable.status],
        invitedAt = this[TeamMembersTable.invitedAt],
        joinedAt = this[TeamMembersTable.joinedAt],
    )
}
