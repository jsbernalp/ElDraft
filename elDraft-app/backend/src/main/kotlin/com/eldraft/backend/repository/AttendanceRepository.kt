package com.eldraft.backend.repository

import com.eldraft.backend.db.tables.AttendanceRecordsTable
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
     * Recalcula attendance_pct del jugador = asistencias validadas / postulaciones
     * aprobadas * 100. Incrementa total_matches. Si no tiene ficha, no hace nada.
     */
    open fun recomputeAttendancePct(playerId: UUID) = transaction {
        val approved = PostulationsTable.selectAll()
            .where { (PostulationsTable.playerId eq playerId) and (PostulationsTable.status eq "approved") }
            .count()
        val attended = AttendanceRecordsTable.selectAll()
            .where { (AttendanceRecordsTable.playerId eq playerId) and (AttendanceRecordsTable.validated eq true) }
            .count()

        val pct = if (approved > 0) (attended.toDouble() / approved.toDouble()) * 100.0 else 100.0

        PlayerProfilesTable.update({ PlayerProfilesTable.userId eq playerId }) {
            it[attendancePct] = pct
            it[totalMatches] = attended.toInt()
        }
        Unit
    }
}
