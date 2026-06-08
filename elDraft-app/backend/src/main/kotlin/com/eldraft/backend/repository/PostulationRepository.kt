package com.eldraft.backend.repository

import com.eldraft.backend.db.tables.PlayerProfilesTable
import com.eldraft.backend.db.tables.PostulationsTable
import com.eldraft.backend.db.tables.UsersTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

/** Vista de dominio de una postulación, con la ficha del jugador embebida. */
data class PostulationRecord(
    val id: UUID,
    val convocatoryId: UUID,
    val playerId: UUID,
    val status: String,
    val createdAt: String,
    val player: PostulantPlayer?,
)

/** Postulación del jugador con la convocatoria embebida (vista "mis partidos"). */
data class MyPostulationRecord(
    val id: UUID,
    val status: String,
    val convocatory: ConvocatoryRecord,
)

/** Resumen del postulante (datos del usuario + ficha técnica) para el organizador. */
data class PostulantPlayer(
    val userId: UUID,
    val name: String,
    val avatarUrl: String?,
    val positionPrimary: String?,
    val dominantFoot: String?,
    val speedRating: Int?,
    val precisionRating: Int?,
    val attendancePct: Double?,
    val sportsmanshipScore: Double?,
    val totalMatches: Int?,
)

open class PostulationRepository {

    /**
     * Registra una postulación. Devuelve `null` si el jugador ya tiene una
     * postulación previa para esta convocatoria (evita duplicados).
     */
    open fun create(convocatoryId: UUID, playerId: UUID): PostulationRecord? = transaction {
        val exists = PostulationsTable.selectAll()
            .where {
                (PostulationsTable.convocatoryId eq convocatoryId) and
                    (PostulationsTable.playerId eq playerId)
            }
            .limit(1)
            .any()
        if (exists) return@transaction null

        val now = LocalDateTime.now()
        val newId = PostulationsTable.insertAndGetId {
            it[PostulationsTable.convocatoryId] = convocatoryId
            it[PostulationsTable.playerId] = playerId
            it[status] = "pending"
            it[createdAt] = now
        }.value

        findById(newId)
    }

    open fun findById(id: UUID): PostulationRecord? = transaction {
        joinedQuery()
            .where { PostulationsTable.id eq id }
            .singleOrNull()
            ?.toRecord()
    }

    /** Postulaciones de una convocatoria, con la ficha de cada postulante. */
    open fun findByConvocatory(convocatoryId: UUID): List<PostulationRecord> = transaction {
        joinedQuery()
            .where { PostulationsTable.convocatoryId eq convocatoryId }
            .orderBy(PostulationsTable.createdAt)
            .map { it.toRecord() }
    }

    /** Postulaciones del jugador, con la convocatoria embebida (más recientes primero). */
    open fun findByPlayer(playerId: UUID): List<MyPostulationRecord> = transaction {
        (PostulationsTable innerJoin com.eldraft.backend.db.tables.ConvocatoriesTable)
            .selectAll()
            .where { PostulationsTable.playerId eq playerId }
            .orderBy(PostulationsTable.createdAt to org.jetbrains.exposed.sql.SortOrder.DESC)
            .map { row ->
                val c = com.eldraft.backend.db.tables.ConvocatoriesTable
                MyPostulationRecord(
                    id = row[PostulationsTable.id].value,
                    status = row[PostulationsTable.status],
                    convocatory = ConvocatoryRecord(
                        id = row[c.id].value,
                        organizerId = row[c.organizerId].value,
                        lat = row[c.locationLat],
                        lng = row[c.locationLng],
                        addressText = row[c.addressText],
                        slotsNeeded = row[c.slotsNeeded],
                        positionRequired = row[c.positionRequired],
                        fee = row[c.fee].toDouble(),
                        format = row[c.format],
                        ambiente = row[c.ambiente],
                        status = row[c.status],
                        scheduledAt = row[c.scheduledAt].toString(),
                    ),
                )
            }
    }

    /** Cambia el estado (approved/rejected). Devuelve true si actualizó una fila. */
    open fun updateStatus(id: UUID, status: String): Boolean = transaction {
        PostulationsTable.update({ PostulationsTable.id eq id }) {
            it[PostulationsTable.status] = status
        } > 0
    }

    /**
     * Query con LEFT JOIN a usuarios y ficha técnica. PlayerProfiles puede no
     * existir todavía para un jugador, por eso es un outer join manual.
     */
    private fun joinedQuery() =
        (PostulationsTable innerJoin UsersTable)
            .join(
                PlayerProfilesTable,
                org.jetbrains.exposed.sql.JoinType.LEFT,
                onColumn = UsersTable.id,
                otherColumn = PlayerProfilesTable.userId,
            )
            .selectAll()

    private fun ResultRow.toRecord(): PostulationRecord {
        val hasProfile = this.getOrNull(PlayerProfilesTable.positionPrimary) != null
        return PostulationRecord(
            id = this[PostulationsTable.id].value,
            convocatoryId = this[PostulationsTable.convocatoryId].value,
            playerId = this[PostulationsTable.playerId].value,
            status = this[PostulationsTable.status],
            createdAt = this[PostulationsTable.createdAt].toString(),
            player = PostulantPlayer(
                userId = this[UsersTable.id].value,
                name = this[UsersTable.name],
                avatarUrl = this[UsersTable.avatarUrl],
                positionPrimary = if (hasProfile) this[PlayerProfilesTable.positionPrimary] else null,
                dominantFoot = if (hasProfile) this[PlayerProfilesTable.dominantFoot] else null,
                speedRating = if (hasProfile) this[PlayerProfilesTable.speedRating] else null,
                precisionRating = if (hasProfile) this[PlayerProfilesTable.precisionRating] else null,
                attendancePct = if (hasProfile) this[PlayerProfilesTable.attendancePct] else null,
                sportsmanshipScore = if (hasProfile) this[PlayerProfilesTable.sportsmanshipScore] else null,
                totalMatches = if (hasProfile) this[PlayerProfilesTable.totalMatches] else null,
            ),
        )
    }
}
