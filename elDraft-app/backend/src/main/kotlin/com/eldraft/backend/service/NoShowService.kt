package com.eldraft.backend.service

import com.eldraft.backend.repository.NoShowRepository
import com.eldraft.backend.repository.NoShowTally
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

/** Errores de dominio del reporte de no-show (las rutas los mapean a HTTP). */
class NoShowForbidden(message: String) : RuntimeException(message)
class NoShowConflict(message: String) : RuntimeException(message)
class NoShowNotFound(message: String) : RuntimeException(message)
class NoShowWindowClosed(message: String) : RuntimeException(message)

/** Estado del reporte de no-show para que la UI pinte el botón. */
data class NoShowStatus(
    val canReport: Boolean,
    val alreadyReported: Boolean,
    val windowOpen: Boolean,
    val reports: Int,
    val attendees: Int,
    val consensusReached: Boolean,
)

/**
 * Reporte por consenso de "el organizador no se presentó".
 *
 * Ventana de reporte:
 *  - Apertura: scheduled_at + [OPEN_AFTER_MINUTES] (margen de tolerancia).
 *  - Cierre:   scheduled_at + [CLOSE_AFTER_HOURS].
 *
 * Solo los asistentes validados pueden reportar (1 voto c/u). Cuando los reportes
 * superan la mayoría de asistentes, se marca el no-show y se penaliza al
 * organizador (attendance_pct + responsabilidad + flag visible).
 */
class NoShowService(
    private val repository: NoShowRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    /** Registra el voto del solicitante y, si se alcanza consenso, aplica efectos. */
    fun report(convocatoryId: UUID, reporterId: UUID): NoShowStatus {
        val ctx = repository.context(convocatoryId)
            ?: throw NoShowNotFound("Convocatoria no encontrada")

        if (reporterId == ctx.organizerId) {
            throw NoShowForbidden("El organizador no puede reportarse a sí mismo")
        }
        if (!repository.attended(convocatoryId, reporterId)) {
            throw NoShowForbidden("Solo quienes asistieron pueden reportar")
        }

        val now = LocalDateTime.now(clock)
        when (windowState(ctx.scheduledAt, now)) {
            WindowState.NOT_YET -> throw NoShowWindowClosed(
                "Aún no puedes reportar: disponible tras el inicio del partido"
            )
            WindowState.CLOSED -> throw NoShowWindowClosed(
                "El plazo para reportar ya cerró"
            )
            WindowState.OPEN -> { /* continúa */ }
        }

        if (repository.hasReported(convocatoryId, reporterId)) {
            throw NoShowConflict("Ya reportaste este partido")
        }

        repository.insertReport(convocatoryId, reporterId)

        val tally = repository.tally(convocatoryId)
        // Aplica efectos solo en la transición a consenso (la convocatoria aún no
        // estaba marcada). markNoShow es idempotente; penalizeOrganizer no.
        if (tally.consensusReached && !ctx.organizerNoShow) {
            repository.markNoShow(convocatoryId)
            repository.penalizeOrganizer(ctx.organizerId)
        }

        return buildStatus(ctx.scheduledAt, now, tally, alreadyReported = true)
    }

    /** Estado actual para la UI, sin emitir voto. */
    fun status(convocatoryId: UUID, requesterId: UUID): NoShowStatus {
        val ctx = repository.context(convocatoryId)
            ?: throw NoShowNotFound("Convocatoria no encontrada")

        val now = LocalDateTime.now(clock)
        val tally = repository.tally(convocatoryId)
        val alreadyReported = repository.hasReported(convocatoryId, requesterId)
        return buildStatus(ctx.scheduledAt, now, tally, alreadyReported).copy(
            // Solo asistentes (no organizador) pueden reportar.
            canReport = requesterId != ctx.organizerId &&
                repository.attended(convocatoryId, requesterId) &&
                !alreadyReported &&
                windowState(ctx.scheduledAt, now) == WindowState.OPEN,
        )
    }

    private fun buildStatus(
        scheduledAt: LocalDateTime,
        now: LocalDateTime,
        tally: NoShowTally,
        alreadyReported: Boolean,
    ): NoShowStatus {
        val open = windowState(scheduledAt, now) == WindowState.OPEN
        return NoShowStatus(
            canReport = open && !alreadyReported,
            alreadyReported = alreadyReported,
            windowOpen = open,
            reports = tally.reports,
            attendees = tally.attendees,
            consensusReached = tally.consensusReached,
        )
    }

    private enum class WindowState { NOT_YET, OPEN, CLOSED }

    private fun windowState(scheduledAt: LocalDateTime, now: LocalDateTime): WindowState {
        val opensAt = scheduledAt.plusMinutes(OPEN_AFTER_MINUTES)
        val closesAt = scheduledAt.plusHours(CLOSE_AFTER_HOURS)
        return when {
            now.isBefore(opensAt) -> WindowState.NOT_YET
            now.isAfter(closesAt) -> WindowState.CLOSED
            else -> WindowState.OPEN
        }
    }

    private companion object {
        const val OPEN_AFTER_MINUTES = 15L
        const val CLOSE_AFTER_HOURS = 48L
    }
}
