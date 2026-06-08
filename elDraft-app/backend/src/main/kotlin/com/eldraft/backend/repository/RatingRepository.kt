package com.eldraft.backend.repository

import com.eldraft.backend.db.tables.AttendanceRecordsTable
import com.eldraft.backend.db.tables.PlayerProfilesTable
import com.eldraft.backend.db.tables.RatingsTable
import com.eldraft.backend.db.tables.UsersTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

/** Compañero calificable (asistió al partido). */
data class TeammateRecord(
    val userId: UUID,
    val name: String,
    val avatarUrl: String?,
    val positionPrimary: String?,
    val alreadyRated: Boolean,
)

open class RatingRepository {

    /** True si el usuario registró asistencia validada en la convocatoria. */
    open fun attended(convocatoryId: UUID, userId: UUID): Boolean = transaction {
        AttendanceRecordsTable.selectAll()
            .where {
                (AttendanceRecordsTable.convocatoryId eq convocatoryId) and
                    (AttendanceRecordsTable.playerId eq userId) and
                    (AttendanceRecordsTable.validated eq true)
            }
            .limit(1)
            .any()
    }

    /**
     * Asistentes de la convocatoria distintos de [requesterId], con su ficha y
     * si el solicitante ya los calificó (para esta convocatoria).
     */
    open fun teammates(convocatoryId: UUID, requesterId: UUID): List<TeammateRecord> = transaction {
        val alreadyRatedIds = RatingsTable.selectAll()
            .where {
                (RatingsTable.convocatoryId eq convocatoryId) and
                    (RatingsTable.raterId eq requesterId)
            }
            .map { it[RatingsTable.ratedPlayerId].value }
            .toSet()

        (AttendanceRecordsTable innerJoin UsersTable)
            .join(
                PlayerProfilesTable,
                JoinType.LEFT,
                onColumn = UsersTable.id,
                otherColumn = PlayerProfilesTable.userId,
            )
            .selectAll()
            .where {
                (AttendanceRecordsTable.convocatoryId eq convocatoryId) and
                    (AttendanceRecordsTable.validated eq true) and
                    (AttendanceRecordsTable.playerId neq requesterId)
            }
            .map { it.toTeammate(alreadyRatedIds) }
    }

    /** True si ya existe una calificación de rater→rated en esa convocatoria. */
    open fun hasRated(convocatoryId: UUID, raterId: UUID, ratedPlayerId: UUID): Boolean = transaction {
        RatingsTable.selectAll()
            .where {
                (RatingsTable.convocatoryId eq convocatoryId) and
                    (RatingsTable.raterId eq raterId) and
                    (RatingsTable.ratedPlayerId eq ratedPlayerId)
            }
            .limit(1)
            .any()
    }

    open fun saveRating(convocatoryId: UUID, raterId: UUID, ratedPlayerId: UUID, score: Int) = transaction {
        RatingsTable.insert {
            it[RatingsTable.convocatoryId] = convocatoryId
            it[RatingsTable.raterId] = raterId
            it[RatingsTable.ratedPlayerId] = ratedPlayerId
            it[sportsmanshipScore] = score
            it[createdAt] = LocalDateTime.now()
        }
        Unit
    }

    /**
     * Recalcula sportsmanship_score del jugador = promedio de todas las
     * calificaciones recibidas. Si no tiene ficha, no hace nada.
     */
    open fun recomputeSportsmanship(ratedPlayerId: UUID) = transaction {
        val scores = RatingsTable.selectAll()
            .where { RatingsTable.ratedPlayerId eq ratedPlayerId }
            .map { it[RatingsTable.sportsmanshipScore] }
        if (scores.isEmpty()) return@transaction
        val avg = scores.sum().toDouble() / scores.size

        PlayerProfilesTable.update({ PlayerProfilesTable.userId eq ratedPlayerId }) {
            it[sportsmanshipScore] = avg
        }
        Unit
    }

    private fun ResultRow.toTeammate(alreadyRatedIds: Set<UUID>): TeammateRecord {
        val userId = this[UsersTable.id].value
        val hasProfile = this.getOrNull(PlayerProfilesTable.positionPrimary) != null
        return TeammateRecord(
            userId = userId,
            name = this[UsersTable.name],
            avatarUrl = this[UsersTable.avatarUrl],
            positionPrimary = if (hasProfile) this[PlayerProfilesTable.positionPrimary] else null,
            alreadyRated = userId in alreadyRatedIds,
        )
    }
}
