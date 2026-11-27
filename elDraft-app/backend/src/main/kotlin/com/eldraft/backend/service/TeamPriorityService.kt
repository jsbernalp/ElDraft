package com.eldraft.backend.service

import com.eldraft.backend.notifications.FcmService
import com.eldraft.backend.repository.ConvocatoryCreate
import com.eldraft.backend.repository.ConvocatoryRecord
import com.eldraft.backend.repository.ConvocatoryRepository
import com.eldraft.backend.repository.PositionSlot
import com.eldraft.backend.repository.PostulationRepository
import com.eldraft.backend.repository.PriorityResponse
import com.eldraft.backend.repository.TeamMemberStatus
import com.eldraft.backend.repository.TeamPriorityInviteRepository
import com.eldraft.backend.repository.TeamRepository
import com.eldraft.backend.repository.UserRepository
import com.eldraft.backend.websocket.PinBroadcaster
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Lo que el resto de services necesita de la convocatoria con prioridad. Es una
 * interfaz para que [ConvocatoryService], [PostulationService] y [TeamService]
 * sigan construyéndose sin ella (default [NoTeamPriorityHooks]) y para romper el
 * ciclo de dependencias con [TeamPriorityService].
 */
interface TeamPriorityHooks {
    /** Valida equipo y ventana y devuelve `data` con `teamWindowEndsAt` resuelta. */
    fun prepareCreate(data: ConvocatoryCreate): ConvocatoryCreate

    /** Tras crear en `team_only`: foto de invitaciones y push `team_priority`. */
    fun startPriority(c: ConvocatoryRecord)

    /** Una postulación dejó de ocupar cupo (retiro, rechazo, cancelación por choque). */
    fun onPostulationReleased(convocatoryId: UUID, postulationId: UUID, declineInvite: Boolean)

    /** El organizador canceló: las invitaciones vivas quedan `expired`. */
    fun onCancel(convocatoryId: UUID)

    fun onMemberRemoved(teamId: UUID, playerId: UUID)

    /** Se llama ANTES de borrar el equipo. */
    fun onTeamDeleted(teamId: UUID)

    /** Abre la convocatoria si ya toca. True si esta llamada fue la que la abrió. */
    fun maybeOpen(convocatoryId: UUID): Boolean

    /** Completa freeSlots (y, para el organizador, el conteo de respuestas). */
    fun decorate(c: ConvocatoryRecord, forOrganizer: Boolean): ConvocatoryRecord
}

object NoTeamPriorityHooks : TeamPriorityHooks {
    override fun prepareCreate(data: ConvocatoryCreate) = data
    override fun startPriority(c: ConvocatoryRecord) = Unit
    override fun onPostulationReleased(convocatoryId: UUID, postulationId: UUID, declineInvite: Boolean) = Unit
    override fun onCancel(convocatoryId: UUID) = Unit
    override fun onMemberRemoved(teamId: UUID, playerId: UUID) = Unit
    override fun onTeamDeleted(teamId: UUID) = Unit
    override fun maybeOpen(convocatoryId: UUID) = false
    override fun decorate(c: ConvocatoryRecord, forOrganizer: Boolean) = c
}

/** Una convocatoria de mis equipos que espera mi respuesta. */
data class TeamPriorityInviteView(
    val convocatory: ConvocatoryRecord,
    val teamName: String,
    val windowEndsAt: String,
    /** Posiciones con cupo libre (las que se ofrecen en "Voy"). */
    val freePositions: List<PositionSlot>,
)

/**
 * Convocatoria con prioridad para un equipo (Parte 2): la convocatoria nace en
 * `team_only`, solo los miembros activos la ven, y se abre al público cuando
 * vence la ventana o todos respondieron (lo primero). Solo se publica (pin +
 * aviso a cercanos) si quedan cupos libres.
 *
 * Las transiciones son idempotentes: `markOpened` y `markPublished` son UPDATE
 * condicionales, así que el scheduler, una lectura y una respuesta pueden
 * coincidir sin duplicar pushes ni pines.
 */
class TeamPriorityService(
    private val convocatories: ConvocatoryRepository,
    private val invites: TeamPriorityInviteRepository,
    private val postulations: PostulationRepository,
    private val teams: TeamRepository,
    private val users: UserRepository,
    private val fcm: FcmService,
    private val notifier: NearbyNotifier,
    private val broadcaster: PinBroadcaster,
    private val creator: PostulationCreator,
    private val clock: Clock = Clock.systemDefaultZone(),
) : TeamPriorityHooks {
    private val log = LoggerFactory.getLogger(TeamPriorityService::class.java)

    private fun now(): LocalDateTime = LocalDateTime.now(clock)

    // ── Crear ────────────────────────────────────────────────────────────────

    override fun prepareCreate(data: ConvocatoryCreate): ConvocatoryCreate {
        val teamId = data.teamId ?: return data
        val team = teams.findById(teamId) ?: throw TeamNotFound("Equipo no encontrado")
        if (team.ownerId != data.organizerId) {
            throw TeamForbidden("Solo puedes avisar a un equipo tuyo")
        }
        val minutes = data.teamWindowMinutes
        require(minutes != null && minutes in WINDOW_OPTIONS) {
            "La ventana debe ser de 30 min, 1 h, 2 h o 6 h"
        }
        val scheduled = parseSchedule(data.scheduledAt)
            ?: throw IllegalArgumentException("Fecha de la convocatoria inválida")
        val endsAt = now().plusMinutes(minutes.toLong())
        require(!endsAt.isAfter(scheduled.minusMinutes(MIN_LEAD_MINUTES))) {
            "La ventana debe cerrar al menos 1 h antes del partido"
        }
        require(activeInvitees(teamId, data.organizerId).isNotEmpty()) {
            "Ese equipo no tiene miembros activos a quienes avisar"
        }
        return data.copy(teamWindowEndsAt = endsAt)
    }

    override fun startPriority(c: ConvocatoryRecord) {
        val teamId = c.teamId ?: return
        val invitees = activeInvitees(teamId, c.organizerId)
        invites.createAll(c.id, invitees)

        val team = teams.findById(teamId)
        val endsAt = c.teamWindowEndsAt?.let(::parseSchedule)
        val body = "Tu equipo «${team?.name ?: ""}» juega ${c.format} el ${formatWhen(c.scheduledAt)}. " +
            (endsAt?.let { "Tienes hasta las ${HOUR.format(it)} para apartar tu cupo" } ?: "Apártalo ya")
        invitees.forEach { playerId ->
            fcm.sendToToken(
                token = users.getFcmToken(playerId),
                title = "Tu equipo juega",
                body = body,
                data = mapOf(
                    "type" to "team_priority",
                    "convocatoryId" to c.id.toString(),
                ),
            )
        }
    }

    // ── Responder ────────────────────────────────────────────────────────────

    /** "Voy" ([accept] = true, con [position]) o "No puedo". */
    fun respond(convocatoryId: UUID, playerId: UUID, accept: Boolean, position: String?) {
        // Si la ventana ya venció y el scheduler se atrasó, se abre aquí.
        maybeOpen(convocatoryId)
        val c = convocatories.findById(convocatoryId)
            ?: throw PostulationNotFound("Convocatoria no encontrada")
        if (c.status != "team_only") {
            throw PostulationConflict("La prioridad del equipo ya terminó")
        }
        val invite = invites.find(convocatoryId, playerId)
            ?: throw PostulationForbidden("Esta convocatoria no es para ti")
        if (invite.response != PriorityResponse.PENDING) {
            throw PostulationConflict("Ya respondiste a esta convocatoria")
        }

        if (accept) {
            if (position.isNullOrBlank()) {
                throw PostulationConflict("Elige una de las posiciones que pide la convocatoria")
            }
            val free = freeByPosition(c)
            val offered = c.positionSlots.any { it.position == position }
            if (offered && (free[position] ?: 0) <= 0) {
                throw PostulationConflict("Ya no quedan cupos para esa posición")
            }
            val created = creator.create(convocatoryId, playerId, position, allowTeamOnly = true)
            if (!invites.respond(convocatoryId, playerId, PriorityResponse.ACCEPTED, now(), created.id)) {
                throw PostulationConflict("Ya respondiste a esta convocatoria")
            }
        } else {
            if (!invites.respond(convocatoryId, playerId, PriorityResponse.DECLINED, now())) {
                throw PostulationConflict("Ya respondiste a esta convocatoria")
            }
        }
        maybeOpen(convocatoryId)
    }

    /** Convocatorias de mis equipos que esperan mi respuesta, con la ventana aún abierta. */
    fun pendingFor(playerId: UUID): List<TeamPriorityInviteView> {
        val now = now()
        return invites.pendingFor(playerId).mapNotNull { invite ->
            val c = convocatories.findById(invite.convocatoryId) ?: return@mapNotNull null
            val endsAt = c.teamWindowEndsAt?.let(::parseSchedule) ?: return@mapNotNull null
            if (c.status != "team_only" || !endsAt.isAfter(now)) return@mapNotNull null
            val free = freeByPosition(c)
            TeamPriorityInviteView(
                convocatory = c.copy(freeSlots = free.values.sum()),
                teamName = c.teamName ?: "",
                windowEndsAt = c.teamWindowEndsAt!!,
                freePositions = c.positionSlots
                    .map { PositionSlot(it.position, free[it.position] ?: 0) }
                    .filter { it.slots > 0 },
            )
        }
    }

    // ── Abrir / publicar ─────────────────────────────────────────────────────

    override fun maybeOpen(convocatoryId: UUID): Boolean {
        val c = convocatories.findById(convocatoryId) ?: return false
        if (c.status != "team_only") return false
        val endsAt = c.teamWindowEndsAt?.let(::parseSchedule)
        val windowOver = endsAt == null || !now().isBefore(endsAt)
        if (!windowOver && invites.countPending(convocatoryId) > 0) return false
        return open(c, notifyOrganizer = true)
    }

    /** "Abrir ya al público": solo el organizador y solo en `team_only`. */
    fun openNow(convocatoryId: UUID, organizerId: UUID): ConvocatoryRecord {
        val c = convocatories.findById(convocatoryId)
            ?: throw PostulationNotFound("Convocatoria no encontrada")
        if (c.organizerId != organizerId) {
            throw PostulationForbidden("Solo el organizador puede abrir la convocatoria")
        }
        if (c.status != "team_only") {
            throw PostulationConflict("La convocatoria ya está abierta")
        }
        // El organizador ya ve el resultado en la respuesta: sin push.
        open(c, notifyOrganizer = false)
        return decorate(convocatories.findById(convocatoryId) ?: c, forOrganizer = true)
    }

    private fun open(c: ConvocatoryRecord, notifyOrganizer: Boolean): Boolean {
        val now = now()
        if (!convocatories.markOpened(c.id, now)) return false
        invites.expirePending(c.id, now)
        val published = maybePublish(c.id)
        if (notifyOrganizer) {
            val free = freeSlots(c)
            fcm.sendToToken(
                token = users.getFcmToken(c.organizerId),
                title = "Se abrió tu convocatoria",
                body = if (published) "Se abrió al público: faltan $free" else "Tu equipo llenó los cupos",
                data = mapOf(
                    "type" to "team_window_opened",
                    "convocatoryId" to c.id.toString(),
                ),
            )
        }
        return true
    }

    /** Publica (pin + aviso a cercanos) si ya está abierta, no se publicó y hay cupos libres. */
    fun maybePublish(convocatoryId: UUID): Boolean {
        val c = convocatories.findById(convocatoryId) ?: return false
        if (c.teamId == null || c.status != "active" || c.publishedAt != null) return false
        val free = freeSlots(c)
        if (free <= 0) return false
        if (!convocatories.markPublished(c.id, now())) return false

        notifier.notify(
            c = c,
            title = "Nueva convocatoria cerca de ti",
            body = "Partido ${c.format} cerca de tu zona. Faltan $free. ¡Postúlate!",
            type = "new_convocatory",
        )
        broadcaster.newPin(c, slots = free, excludeUserId = c.organizerId.toString())
        return true
    }

    /** Scheduler: abre las `team_only` con la ventana vencida. Devuelve cuántas abrió. */
    fun openDue(): Int {
        val due = try {
            convocatories.findTeamOnlyDue(now())
        } catch (e: Exception) {
            log.warn("No se pudieron buscar ventanas vencidas: ${e.message}")
            return 0
        }
        return due.count { c ->
            try {
                maybeOpen(c.id)
            } catch (e: Exception) {
                log.warn("No se pudo abrir la convocatoria ${c.id}: ${e.message}")
                false
            }
        }
    }

    // ── Ganchos de otros flujos ──────────────────────────────────────────────

    override fun onPostulationReleased(convocatoryId: UUID, postulationId: UUID, declineInvite: Boolean) {
        val c = convocatories.findById(convocatoryId) ?: return
        if (c.teamId == null) return
        when (c.status) {
            "team_only" -> if (declineInvite) {
                // Cuenta como respondida: puede ser la que faltaba para abrir.
                invites.declineByPostulation(postulationId, now())
                maybeOpen(convocatoryId)
            }
            // Ya abierta: se liberó un cupo, quizá toque publicar.
            "active" -> maybePublish(convocatoryId)
        }
    }

    override fun onCancel(convocatoryId: UUID) {
        invites.expirePending(convocatoryId, now())
    }

    override fun onMemberRemoved(teamId: UUID, playerId: UUID) {
        convocatories.findTeamOnlyByTeam(teamId).forEach { c ->
            val invite = invites.find(c.id, playerId) ?: return@forEach
            if (invite.response == PriorityResponse.PENDING) {
                invites.respond(c.id, playerId, PriorityResponse.EXPIRED, now())
                maybeOpen(c.id)
            }
        }
    }

    override fun onTeamDeleted(teamId: UUID) {
        convocatories.findTeamOnlyByTeam(teamId).forEach { c ->
            val now = now()
            if (convocatories.markOpened(c.id, now)) {
                invites.expirePending(c.id, now)
                maybePublish(c.id)
            }
        }
    }

    // ── Cupos libres ─────────────────────────────────────────────────────────

    /**
     * libres(pos) = cupos − aprobados − pendientes de miembros del equipo. Los
     * pendientes del público (tras abrir) no cuentan: el organizador aún no los
     * aceptó. Rechazar a uno del equipo libera su cupo.
     */
    fun freeByPosition(c: ConvocatoryRecord): Map<String, Int> {
        val teamPlayers = invites.findByConvocatory(c.id).map { it.playerId }.toSet()
        val taken = postulations.findByConvocatory(c.id)
            .filter { it.status == "approved" || (it.status == "pending" && it.playerId in teamPlayers) }
            .groupingBy { it.position }
            .eachCount()
        return c.positionSlots.associate { it.position to maxOf(0, it.slots - (taken[it.position] ?: 0)) }
    }

    fun freeSlots(c: ConvocatoryRecord): Int = freeByPosition(c).values.sum()

    override fun decorate(c: ConvocatoryRecord, forOrganizer: Boolean): ConvocatoryRecord {
        if (c.teamId == null) return c
        val withFree = c.copy(freeSlots = freeSlots(c))
        if (!forOrganizer) return withFree
        val all = invites.findByConvocatory(c.id)
        return withFree.copy(
            teamInvited = all.size,
            teamResponded = all.count {
                it.response == PriorityResponse.ACCEPTED || it.response == PriorityResponse.DECLINED
            },
        )
    }

    private fun activeInvitees(teamId: UUID, organizerId: UUID): List<UUID> =
        teams.members(teamId)
            .filter { it.status == TeamMemberStatus.ACTIVE && it.playerId != organizerId }
            .map { it.playerId }

    private fun formatWhen(scheduledAt: String): String =
        parseSchedule(scheduledAt)?.let { WHEN.format(it) } ?: scheduledAt

    companion object {
        val WINDOW_OPTIONS = listOf(30, 60, 120, 360)

        /** La ventana debe cerrar al menos esto antes del partido. */
        const val MIN_LEAD_MINUTES = 60L

        private val ES = Locale.forLanguageTag("es-CO")
        private val WHEN = DateTimeFormatter.ofPattern("EEE d 'a las' h:mm a", ES)
        private val HOUR = DateTimeFormatter.ofPattern("h:mm a", ES)
    }
}
