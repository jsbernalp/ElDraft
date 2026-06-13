package com.eldraft.backend.repository

import com.eldraft.backend.db.tables.AttendanceRecordsTable
import com.eldraft.backend.db.tables.ConvocatoriesTable
import com.eldraft.backend.db.tables.OrganizerNoShowReportsTable
import com.eldraft.backend.db.tables.PlayerProfilesTable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

/** Datos de la convocatoria necesarios para evaluar el no-show. */
data class NoShowContext(
    val organizerId: UUID,
    val scheduledAt: LocalDateTime,
    val organizerNoShow: Boolean,
)

/** Conteo de votos vs. asistentes para el consenso de no-show. */
data class NoShowTally(
    val reports: Int,
    val attendees: Int,
) {
    /** Mayoría estricta de los asistentes validados. */
    val consensusReached: Boolean get() = attendees > 0 && reports > attendees / 2
}

open class NoShowRepository {

    /** Contexto de la convocatoria para validar ventana y consenso. */
    open fun context(convocatoryId: UUID): NoShowContext? = transaction {
        ConvocatoriesTable.selectAll()
            .where { ConvocatoriesTable.id eq convocatoryId }
            .singleOrNull()
            ?.let {
                NoShowContext(
                    organizerId = it[ConvocatoriesTable.organizerId].value,
                    scheduledAt = it[ConvocatoriesTable.scheduledAt],
                    organizerNoShow = it[ConvocatoriesTable.organizerNoShow],
                )
            }
    }

    /** True si [userId] registró asistencia validada en la convocatoria. */
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

    /** True si [userId] ya reportó a esta convocatoria. */
    open fun hasReported(convocatoryId: UUID, userId: UUID): Boolean = transaction {
        OrganizerNoShowReportsTable.selectAll()
            .where {
                (OrganizerNoShowReportsTable.convocatoryId eq convocatoryId) and
                    (OrganizerNoShowReportsTable.reporterId eq userId)
            }
            .limit(1)
            .any()
    }

    /** Inserta el voto del reportante (la unicidad la garantiza el índice). */
    open fun insertReport(convocatoryId: UUID, reporterId: UUID) = transaction {
        OrganizerNoShowReportsTable.insert {
            it[OrganizerNoShowReportsTable.convocatoryId] = convocatoryId
            it[OrganizerNoShowReportsTable.reporterId] = reporterId
            it[createdAt] = LocalDateTime.now()
        }
        Unit
    }

    /**
     * Conteo actual: reportes emitidos y asistentes validados. Un organizador en
     * no-show no escaneó, así que no aparece entre los asistentes; solo los
     * jugadores presentes cuentan como votantes potenciales.
     */
    open fun tally(convocatoryId: UUID): NoShowTally = transaction {
        val reports = OrganizerNoShowReportsTable.selectAll()
            .where { OrganizerNoShowReportsTable.convocatoryId eq convocatoryId }
            .count()
            .toInt()
        val attendees = AttendanceRecordsTable.selectAll()
            .where {
                (AttendanceRecordsTable.convocatoryId eq convocatoryId) and
                    (AttendanceRecordsTable.validated eq true)
            }
            .count()
            .toInt()
        NoShowTally(reports = reports, attendees = attendees)
    }

    /** Marca la convocatoria como organizador-no-show (idempotente). */
    open fun markNoShow(convocatoryId: UUID) = transaction {
        ConvocatoriesTable.update({ ConvocatoriesTable.id eq convocatoryId }) {
            it[organizerNoShow] = true
        }
        Unit
    }

    /**
     * Aplica la penalización de no-show al organizador: cuenta la ausencia en su
     * attendance_pct y baja su responsibility_score. Si no tiene ficha, no hace
     * nada. Idempotente a nivel de partido porque solo se invoca una vez (al
     * alcanzar consenso).
     */
    open fun penalizeOrganizer(organizerId: UUID) = transaction {
        val profile = PlayerProfilesTable.selectAll()
            .where { PlayerProfilesTable.userId eq organizerId }
            .singleOrNull() ?: return@transaction

        val currentTotal = profile[PlayerProfilesTable.totalMatches]
        val currentPct = profile[PlayerProfilesTable.attendancePct]
        val currentResponsibility = profile[PlayerProfilesTable.responsibilityScore]

        // Una ausencia más sobre el total de partidos contabilizados. Tomamos el
        // total como base+1 para reflejar este partido como no asistido.
        val newTotal = currentTotal + 1
        val attendedCount = (currentPct / 100.0) * currentTotal
        val newPct = (attendedCount / newTotal) * 100.0

        // Penalización de responsabilidad: un escalón hacia abajo, con piso en 1.
        val newResponsibility = (currentResponsibility - RESPONSIBILITY_PENALTY).coerceAtLeast(1.0)

        PlayerProfilesTable.update({ PlayerProfilesTable.userId eq organizerId }) {
            it[attendancePct] = newPct
            it[totalMatches] = newTotal
            it[responsibilityScore] = newResponsibility
        }
        Unit
    }

    private companion object {
        /** Cuánto baja la responsabilidad del organizador ante un no-show confirmado. */
        const val RESPONSIBILITY_PENALTY = 1.0
    }
}
