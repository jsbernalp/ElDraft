package com.eldraft.backend.repository

import com.eldraft.backend.db.tables.AttendanceRecordsTable
import com.eldraft.backend.db.tables.ConvocatoriesTable
import com.eldraft.backend.db.tables.PlayerProfilesTable
import com.eldraft.backend.db.tables.RatingsTable
import com.eldraft.backend.db.tables.UsersTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
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

    /**
     * True si el usuario "asistió" a efectos de PODER CALIFICAR: registró
     * asistencia validada (escaneó el QR). El organizador ya no se asume
     * presente: si no escaneó, no asistió y por tanto no puede calificar.
     */
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
     * True si [userId] PUEDE SER CALIFICADO en la convocatoria: asistió (escaneó)
     * O es el organizador. El organizador siempre es calificable por los
     * asistentes, haya llegado o no (p. ej. para valorar su responsabilidad si
     * no se presentó).
     */
    open fun isRateable(convocatoryId: UUID, userId: UUID): Boolean = transaction {
        isOrganizer(convocatoryId, userId) || attended(convocatoryId, userId)
    }

    /** True si [userId] es el organizador de la convocatoria. */
    open fun isOrganizer(convocatoryId: UUID, userId: UUID): Boolean = transaction {
        ConvocatoriesTable.selectAll()
            .where {
                (ConvocatoriesTable.id eq convocatoryId) and
                    (ConvocatoriesTable.organizerId eq userId)
            }
            .limit(1)
            .any()
    }

    /**
     * Participantes calificables de la convocatoria distintos de [requesterId]:
     * los asistentes con QR validado MÁS el organizador (asistente implícito).
     * Incluye su ficha y si el solicitante ya los calificó en este partido.
     */
    open fun teammates(convocatoryId: UUID, requesterId: UUID): List<TeammateRecord> = transaction {
        val alreadyRatedIds = RatingsTable.selectAll()
            .where {
                (RatingsTable.convocatoryId eq convocatoryId) and
                    (RatingsTable.raterId eq requesterId)
            }
            .map { it[RatingsTable.ratedPlayerId].value }
            .toSet()

        // Ids calificables = asistentes validados ∪ {organizador}, menos el solicitante.
        val attendeeIds = AttendanceRecordsTable.selectAll()
            .where {
                (AttendanceRecordsTable.convocatoryId eq convocatoryId) and
                    (AttendanceRecordsTable.validated eq true)
            }
            .map { it[AttendanceRecordsTable.playerId].value }
            .toMutableSet()

        ConvocatoriesTable.selectAll()
            .where { ConvocatoriesTable.id eq convocatoryId }
            .firstOrNull()
            ?.let { attendeeIds.add(it[ConvocatoriesTable.organizerId].value) }

        val rateableIds = attendeeIds - requesterId
        if (rateableIds.isEmpty()) return@transaction emptyList()

        (UsersTable.join(
            PlayerProfilesTable,
            JoinType.LEFT,
            onColumn = UsersTable.id,
            otherColumn = PlayerProfilesTable.userId,
        ))
            .selectAll()
            .where { UsersTable.id inList rateableIds }
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

    open fun saveRating(
        convocatoryId: UUID,
        raterId: UUID,
        ratedPlayerId: UUID,
        skill: Int,
        sportsmanship: Int,
        responsibility: Int,
    ) = transaction {
        RatingsTable.insert {
            it[RatingsTable.convocatoryId] = convocatoryId
            it[RatingsTable.raterId] = raterId
            it[RatingsTable.ratedPlayerId] = ratedPlayerId
            it[skillScore] = skill
            it[sportsmanshipScore] = sportsmanship
            it[responsibilityScore] = responsibility
            it[createdAt] = LocalDateTime.now()
        }
        Unit
    }

    /**
     * Recalcula los 3 atributos del jugador (habilidad, deportividad,
     * responsabilidad) = promedio de todas las calificaciones recibidas.
     * Si no tiene calificaciones, no hace nada.
     */
    open fun recomputeScores(ratedPlayerId: UUID) = transaction {
        val rows = RatingsTable.selectAll()
            .where { RatingsTable.ratedPlayerId eq ratedPlayerId }
            .toList()
        if (rows.isEmpty()) return@transaction

        val n = rows.size.toDouble()
        val avgSkill = rows.sumOf { it[RatingsTable.skillScore] } / n
        val avgSportsmanship = rows.sumOf { it[RatingsTable.sportsmanshipScore] } / n
        val avgResponsibility = rows.sumOf { it[RatingsTable.responsibilityScore] } / n

        PlayerProfilesTable.update({ PlayerProfilesTable.userId eq ratedPlayerId }) {
            it[skillScore] = avgSkill
            it[sportsmanshipScore] = avgSportsmanship
            it[responsibilityScore] = avgResponsibility
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
