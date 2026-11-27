package com.eldraft.backend.repository

import com.eldraft.backend.db.tables.TeamPriorityInvitesTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

/** Respuestas posibles de una invitación prioritaria. */
object PriorityResponse {
    const val PENDING = "pending"
    const val ACCEPTED = "accepted"
    const val DECLINED = "declined"
    const val EXPIRED = "expired"
}

/** Invitación prioritaria de un miembro del equipo a una convocatoria. */
data class TeamPriorityInviteRecord(
    val id: UUID,
    val convocatoryId: UUID,
    val playerId: UUID,
    val response: String,
    val respondedAt: LocalDateTime?,
    val postulationId: UUID?,
)

open class TeamPriorityInviteRepository {

    /** Foto de los jugadores invitados a esta convocatoria (todos `pending`). */
    open fun createAll(convocatoryId: UUID, playerIds: Collection<UUID>) {
        if (playerIds.isEmpty()) return
        transaction {
            TeamPriorityInvitesTable.batchInsert(playerIds) { playerId ->
                this[TeamPriorityInvitesTable.convocatoryId] = convocatoryId
                this[TeamPriorityInvitesTable.playerId] = playerId
                this[TeamPriorityInvitesTable.response] = PriorityResponse.PENDING
            }
        }
    }

    open fun find(convocatoryId: UUID, playerId: UUID): TeamPriorityInviteRecord? = transaction {
        TeamPriorityInvitesTable.selectAll()
            .where {
                (TeamPriorityInvitesTable.convocatoryId eq convocatoryId) and
                    (TeamPriorityInvitesTable.playerId eq playerId)
            }
            .singleOrNull()?.toRecord()
    }

    open fun findByConvocatory(convocatoryId: UUID): List<TeamPriorityInviteRecord> = transaction {
        TeamPriorityInvitesTable.selectAll()
            .where { TeamPriorityInvitesTable.convocatoryId eq convocatoryId }
            .map { it.toRecord() }
    }

    open fun countPending(convocatoryId: UUID): Int = transaction {
        TeamPriorityInvitesTable.selectAll()
            .where {
                (TeamPriorityInvitesTable.convocatoryId eq convocatoryId) and
                    (TeamPriorityInvitesTable.response eq PriorityResponse.PENDING)
            }
            .count().toInt()
    }

    /** Invitaciones `pending` de un jugador (las convocatorias se filtran en el service). */
    open fun pendingFor(playerId: UUID): List<TeamPriorityInviteRecord> = transaction {
        TeamPriorityInvitesTable.selectAll()
            .where {
                (TeamPriorityInvitesTable.playerId eq playerId) and
                    (TeamPriorityInvitesTable.response eq PriorityResponse.PENDING)
            }
            .map { it.toRecord() }
    }

    /** pending → [response]. Condicional: devuelve false si ya no estaba pendiente. */
    open fun respond(
        convocatoryId: UUID,
        playerId: UUID,
        response: String,
        now: LocalDateTime,
        postulationId: UUID? = null,
    ): Boolean = transaction {
        TeamPriorityInvitesTable.update({
            (TeamPriorityInvitesTable.convocatoryId eq convocatoryId) and
                (TeamPriorityInvitesTable.playerId eq playerId) and
                (TeamPriorityInvitesTable.response eq PriorityResponse.PENDING)
        }) {
            it[TeamPriorityInvitesTable.response] = response
            it[respondedAt] = now
            it[TeamPriorityInvitesTable.postulationId] = postulationId
        } > 0
    }

    /** Un "Voy" que se retira mientras sigue la prioridad vuelve a `declined`. */
    open fun declineByPostulation(postulationId: UUID, now: LocalDateTime): Boolean = transaction {
        TeamPriorityInvitesTable.update({
            (TeamPriorityInvitesTable.postulationId eq postulationId) and
                (TeamPriorityInvitesTable.response eq PriorityResponse.ACCEPTED)
        }) {
            it[response] = PriorityResponse.DECLINED
            it[respondedAt] = now
        } > 0
    }

    /** Las `pending` de una convocatoria pasan a `expired`. */
    open fun expirePending(convocatoryId: UUID, now: LocalDateTime): Int = transaction {
        TeamPriorityInvitesTable.update({
            (TeamPriorityInvitesTable.convocatoryId eq convocatoryId) and
                (TeamPriorityInvitesTable.response eq PriorityResponse.PENDING)
        }) {
            it[response] = PriorityResponse.EXPIRED
            it[respondedAt] = now
        }
    }

    private fun ResultRow.toRecord() = TeamPriorityInviteRecord(
        id = this[TeamPriorityInvitesTable.id].value,
        convocatoryId = this[TeamPriorityInvitesTable.convocatoryId].value,
        playerId = this[TeamPriorityInvitesTable.playerId].value,
        response = this[TeamPriorityInvitesTable.response],
        respondedAt = this[TeamPriorityInvitesTable.respondedAt],
        postulationId = this[TeamPriorityInvitesTable.postulationId],
    )
}
