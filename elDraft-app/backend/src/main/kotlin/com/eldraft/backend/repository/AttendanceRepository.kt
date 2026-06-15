package com.eldraft.backend.repository

import com.eldraft.backend.db.tables.AttendanceRecordsTable
import com.eldraft.backend.db.tables.ConvocatoriesTable
import com.eldraft.backend.db.tables.PlayerNoShowMarksTable
import com.eldraft.backend.db.tables.PlayerProfilesTable
import com.eldraft.backend.db.tables.PostulationsTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

open class AttendanceRepository {

    /** True si el jugador tiene una postulación APROBADA en la convocatoria. */
    open fun isApprovedPlayer(convocatoryId: UUID, playerId: UUID): Boolean = transaction {
        PostulationsTable.selectAll()
            .where {
                (PostulationsTable.convocatoryId eq convocatoryId) and
                    (PostulationsTable.playerId eq playerId) and
                    (PostulationsTable.status eq "approved")
            }
            .limit(1)
            .any()
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
     * True si [userId] puede registrar asistencia en la convocatoria: jugador
     * aprobado O el organizador (que ahora también escanea para marcar presencia).
     */
    open fun canAttend(convocatoryId: UUID, userId: UUID): Boolean =
        isApprovedPlayer(convocatoryId, userId) || isOrganizer(convocatoryId, userId)

    /** True si ya existe un registro de asistencia para este jugador y convocatoria. */
    open fun hasAttendance(convocatoryId: UUID, playerId: UUID): Boolean = transaction {
        AttendanceRecordsTable.selectAll()
            .where {
                (AttendanceRecordsTable.convocatoryId eq convocatoryId) and
                    (AttendanceRecordsTable.playerId eq playerId)
            }
            .limit(1)
            .any()
    }

    /**
     * Registra la asistencia validada del jugador. El [qrCode] guardado es único
     * por jugador (token#playerId) para respetar el índice único de la columna.
     */
    open fun recordAttendance(
        convocatoryId: UUID,
        playerId: UUID,
        qrCode: String,
        expiresAt: LocalDateTime,
    ) = transaction {
        AttendanceRecordsTable.insert {
            it[AttendanceRecordsTable.convocatoryId] = convocatoryId
            it[AttendanceRecordsTable.playerId] = playerId
            it[AttendanceRecordsTable.qrCode] = qrCode
            it[qrExpiresAt] = expiresAt
            it[scannedAt] = LocalDateTime.now()
            it[validated] = true
        }
        Unit
    }

    /**
     * Recalcula attendance_pct del jugador bajo el modelo EXPLÍCITO: la asistencia
     * la define el organizador, no el escaneo. Un convocado se considera PRESENTE a
     * menos que el organizador lo haya marcado como ausente (fila en
     * PlayerNoShowMarksTable). El escaneo del QR sigue siendo prueba firme (impide
     * que lo marquen ausente), pero ya no es la fuente de verdad del porcentaje.
     *
     *   attendance_pct = (aprobados − marcas de no-show) / aprobados * 100
     *   total_matches  = aprobados
     *
     * Si no tiene ficha, no hace nada.
     */
    open fun recomputeAttendancePct(playerId: UUID) = transaction {
        val approved = PostulationsTable.selectAll()
            .where { (PostulationsTable.playerId eq playerId) and (PostulationsTable.status eq "approved") }
            .count()
        val noShows = PlayerNoShowMarksTable.selectAll()
            .where { PlayerNoShowMarksTable.playerId eq playerId }
            .count()
        val present = (approved - noShows).coerceAtLeast(0)

        val pct = if (approved > 0) (present.toDouble() / approved.toDouble()) * 100.0 else 100.0

        PlayerProfilesTable.update({ PlayerProfilesTable.userId eq playerId }) {
            it[attendancePct] = pct
            it[totalMatches] = approved.toInt()
        }
        Unit
    }
}
