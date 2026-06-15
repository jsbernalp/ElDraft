package com.eldraft.backend.repository

import com.eldraft.backend.db.tables.AttendanceRecordsTable
import com.eldraft.backend.db.tables.ConvocatoriesTable
import com.eldraft.backend.db.tables.PlayerNoShowMarksTable
import com.eldraft.backend.db.tables.PlayerProfilesTable
import com.eldraft.backend.db.tables.PostulationsTable
import com.eldraft.backend.db.tables.RatingsTable
import com.eldraft.backend.db.tables.UsersTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

/** Datos de la convocatoria necesarios para validar la declaración. */
data class DeclarationContext(
    val organizerId: UUID,
    val scheduledAt: LocalDateTime,
    /**
     * True si el consenso marcó al organizador como ausente. En ese caso no puede
     * declarar la asistencia de los demás: no estuvo en el partido, no puede dar
     * fe de quién llegó.
     */
    val organizerNoShow: Boolean,
)

/**
 * Un convocado aprobado con su estado de asistencia para la lista del organizador.
 * [scanned] = registró asistencia por QR (presencia firme, no marcable como ausente).
 * [markedNoShow] = el organizador ya lo declaró ausente.
 */
data class PlayerAttendanceRow(
    val playerId: UUID,
    val name: String,
    val avatarUrl: String?,
    val positionPrimary: String?,
    val scanned: Boolean,
    val markedNoShow: Boolean,
)

open class AttendanceDeclarationRepository {

    /** Organizador y hora de la convocatoria, o null si no existe. */
    open fun context(convocatoryId: UUID): DeclarationContext? = transaction {
        ConvocatoriesTable.selectAll()
            .where { ConvocatoriesTable.id eq convocatoryId }
            .singleOrNull()
            ?.let {
                DeclarationContext(
                    organizerId = it[ConvocatoriesTable.organizerId].value,
                    scheduledAt = it[ConvocatoriesTable.scheduledAt],
                    organizerNoShow = it[ConvocatoriesTable.organizerNoShow],
                )
            }
    }

    /**
     * Lista de jugadores aprobados de la convocatoria con su ficha y estado de
     * asistencia (escaneó / marcado ausente). Es la base de la pantalla del
     * organizador y de las validaciones del servicio.
     */
    open fun approvedPlayers(convocatoryId: UUID): List<PlayerAttendanceRow> = transaction {
        val scannedIds = AttendanceRecordsTable.selectAll()
            .where {
                (AttendanceRecordsTable.convocatoryId eq convocatoryId) and
                    (AttendanceRecordsTable.validated eq true)
            }
            .map { it[AttendanceRecordsTable.playerId].value }
            .toSet()

        val markedIds = PlayerNoShowMarksTable.selectAll()
            .where { PlayerNoShowMarksTable.convocatoryId eq convocatoryId }
            .map { it[PlayerNoShowMarksTable.playerId].value }
            .toSet()

        val approvedIds = PostulationsTable.selectAll()
            .where {
                (PostulationsTable.convocatoryId eq convocatoryId) and
                    (PostulationsTable.status eq "approved")
            }
            .map { it[PostulationsTable.playerId].value }
            .toSet()

        if (approvedIds.isEmpty()) return@transaction emptyList()

        UsersTable.join(
            PlayerProfilesTable,
            JoinType.LEFT,
            onColumn = UsersTable.id,
            otherColumn = PlayerProfilesTable.userId,
        )
            .selectAll()
            .where { UsersTable.id inList approvedIds }
            .map { row ->
                val id = row[UsersTable.id].value
                val hasProfile = row.getOrNull(PlayerProfilesTable.positionPrimary) != null
                PlayerAttendanceRow(
                    playerId = id,
                    name = row[UsersTable.name],
                    avatarUrl = row[UsersTable.avatarUrl],
                    positionPrimary = if (hasProfile) row[PlayerProfilesTable.positionPrimary] else null,
                    scanned = id in scannedIds,
                    markedNoShow = id in markedIds,
                )
            }
    }

    /**
     * Reemplaza por completo las marcas de no-show de la convocatoria por
     * [absentPlayerIds]. Al ser un reemplazo, quitar a alguien de la lista revierte
     * su ausencia. Devuelve los ids de jugadores afectados (los que estaban o pasan
     * a estar marcados) para recomputar solo sus métricas.
     */
    open fun replaceMarks(convocatoryId: UUID, absentPlayerIds: Set<UUID>): Set<UUID> = transaction {
        val previous = PlayerNoShowMarksTable.selectAll()
            .where { PlayerNoShowMarksTable.convocatoryId eq convocatoryId }
            .map { it[PlayerNoShowMarksTable.playerId].value }
            .toSet()

        PlayerNoShowMarksTable.deleteWhere { PlayerNoShowMarksTable.convocatoryId eq convocatoryId }

        absentPlayerIds.forEach { pid ->
            PlayerNoShowMarksTable.insert {
                it[PlayerNoShowMarksTable.convocatoryId] = convocatoryId
                it[playerId] = pid
                it[createdAt] = LocalDateTime.now()
            }
        }

        // Afectados = antes marcados ∪ ahora marcados: a unos se les revierte, a
        // otros se les aplica; ambos necesitan recálculo de métricas.
        previous + absentPlayerIds
    }

    /**
     * Recalcula la responsabilidad del jugador de forma REVERSIBLE: parte del
     * promedio limpio de sus calificaciones recibidas (default 5.0 si no tiene) y
     * le resta una penalización por cada marca de no-show activa (piso 1.0). Como
     * siempre se reconstruye desde el promedio limpio, quitar una marca revierte su
     * efecto en la siguiente declaración (no se acumula sobre un valor ya penalizado).
     */
    open fun recomputeResponsibility(playerId: UUID) = transaction {
        PlayerProfilesTable.selectAll()
            .where { PlayerProfilesTable.userId eq playerId }
            .singleOrNull() ?: return@transaction

        // Promedio limpio de responsabilidad recibida en calificaciones.
        val ratings = RatingsTable.selectAll()
            .where { RatingsTable.ratedPlayerId eq playerId }
            .map { it[RatingsTable.responsibilityScore] }
        val baseResponsibility =
            if (ratings.isEmpty()) DEFAULT_RESPONSIBILITY
            else ratings.average()

        val activeMarks = PlayerNoShowMarksTable.selectAll()
            .where { PlayerNoShowMarksTable.playerId eq playerId }
            .count()

        val penalized = (baseResponsibility - NO_SHOW_PENALTY * activeMarks).coerceAtLeast(1.0)

        PlayerProfilesTable.update({ PlayerProfilesTable.userId eq playerId }) {
            it[responsibilityScore] = penalized
        }
        Unit
    }

    /** Recalcula attendance_pct del jugador bajo el modelo explícito. */
    open fun recomputeAttendance(playerId: UUID) = transaction {
        AttendanceRepository().recomputeAttendancePct(playerId)
    }

    /**
     * Marca la convocatoria como "asistencia confirmada por el organizador". Es
     * prueba de que estuvo presente: bloquea reportes de no-show en su contra.
     */
    open fun markOrganizerConfirmed(convocatoryId: UUID) = transaction {
        ConvocatoriesTable.update({ ConvocatoriesTable.id eq convocatoryId }) {
            it[organizerConfirmed] = true
        }
        Unit
    }

    /**
     * Borra TODAS las marcas de no-show de convocados de esta convocatoria y
     * recalcula las métricas de los afectados. Se invoca cuando el consenso marca
     * al organizador como ausente: si él no estuvo en el partido, las ausencias que
     * declaró dejan de tener validez. Idempotente (sin marcas, no hace nada).
     */
    open fun clearMarksAndRecompute(convocatoryId: UUID) = transaction {
        val affected = PlayerNoShowMarksTable.selectAll()
            .where { PlayerNoShowMarksTable.convocatoryId eq convocatoryId }
            .map { it[PlayerNoShowMarksTable.playerId].value }
            .toSet()

        if (affected.isEmpty()) return@transaction

        PlayerNoShowMarksTable.deleteWhere { PlayerNoShowMarksTable.convocatoryId eq convocatoryId }

        affected.forEach { pid ->
            AttendanceRepository().recomputeAttendancePct(pid)
            recomputeResponsibility(pid)
        }
    }

    private companion object {
        /** Cuánto baja la responsabilidad por cada ausencia confirmada. */
        const val NO_SHOW_PENALTY = 1.0

        /** Responsabilidad base si el jugador aún no tiene calificaciones. */
        const val DEFAULT_RESPONSIBILITY = 5.0
    }
}
