package com.eldraft.backend.service

import com.eldraft.backend.notifications.FcmService
import com.eldraft.backend.repository.ConvocatoryCreate
import com.eldraft.backend.repository.ConvocatoryRecord
import com.eldraft.backend.repository.ConvocatoryRepository
import com.eldraft.backend.repository.MyPostulationRecord
import com.eldraft.backend.repository.NearbyPlayer
import com.eldraft.backend.repository.PositionSlot
import com.eldraft.backend.repository.PostulationRecord
import com.eldraft.backend.repository.PostulationRepository
import com.eldraft.backend.repository.PriorityResponse
import com.eldraft.backend.repository.TeamMemberRecord
import com.eldraft.backend.repository.TeamMemberStatus
import com.eldraft.backend.repository.TeamPriorityInviteRecord
import com.eldraft.backend.repository.TeamPriorityInviteRepository
import com.eldraft.backend.repository.TeamRecord
import com.eldraft.backend.repository.TeamRepository
import com.eldraft.backend.repository.UserRecord
import com.eldraft.backend.repository.UserRepository
import com.eldraft.backend.websocket.PinBroadcaster
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Convocatoria con prioridad para un equipo, con repos en memoria (sin BD). Los
 * repos fingen las mismas garantías que el SQL real: `markOpened` y
 * `markPublished` son transiciones condicionales, así que contar sus efectos
 * (pushes, pines) comprueba la idempotencia.
 */
class TeamPriorityServiceTest {

    private val bogota = ZoneId.of("America/Bogota")
    // Hora real: PostulationService/ConvocatoryService miran LocalDateTime.now() para
    // 'ya comenzó', así que el partido (now0 + 5 h) debe quedar en el futuro de verdad.
    private val now0 = LocalDateTime.now().withNano(0)
    private var now = now0
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = bogota
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now.atZone(bogota).toInstant()
    }

    private val organizerId = UUID.randomUUID()
    private val teamId = UUID.randomUUID()
    private val members = List(3) { UUID.randomUUID() }

    // ── Fakes ────────────────────────────────────────────────────────────────

    private class MemConvocatories : ConvocatoryRepository() {
        val rows = mutableMapOf<UUID, ConvocatoryRecord>()
        override fun create(data: ConvocatoryCreate): ConvocatoryRecord {
            val c = ConvocatoryRecord(
                id = UUID.randomUUID(), organizerId = data.organizerId, lat = 6.2, lng = -75.5,
                addressText = null, slotsNeeded = data.positionSlots.sumOf { it.slots },
                positionRequired = "Varias", positionSlots = data.positionSlots, fee = 0.0,
                format = data.format, ambiente = data.ambiente,
                status = if (data.teamId != null) "team_only" else "active",
                scheduledAt = data.scheduledAt, teamId = data.teamId, teamName = "Los del martes",
                teamWindowEndsAt = data.teamWindowEndsAt?.toString(),
            )
            rows[c.id] = c
            return c
        }
        override fun findById(id: UUID) = rows[id]
        override fun findByOrganizer(organizerId: UUID) =
            rows.values.filter { it.organizerId == organizerId && it.status in listOf("active", "team_only") }
        override fun markOpened(id: UUID, now: LocalDateTime): Boolean {
            val c = rows[id] ?: return false
            if (c.status != "team_only") return false
            rows[id] = c.copy(status = "active", openedAt = now.toString())
            return true
        }
        override fun markPublished(id: UUID, now: LocalDateTime): Boolean {
            val c = rows[id] ?: return false
            if (c.status != "active" || c.publishedAt != null) return false
            rows[id] = c.copy(publishedAt = now.toString())
            return true
        }
        override fun findTeamOnlyDue(now: LocalDateTime) = rows.values.filter {
            it.status == "team_only" && it.teamWindowEndsAt?.let(LocalDateTime::parse)?.let { e -> !e.isAfter(now) } == true
        }
        override fun findTeamOnlyByTeam(teamId: UUID) =
            rows.values.filter { it.status == "team_only" && it.teamId == teamId }
        override fun cancel(id: UUID, reason: String): Boolean {
            val c = rows[id] ?: return false
            rows[id] = c.copy(status = "cancelled")
            return true
        }
        override fun findNearby(lat: Double, lng: Double, radiusMeters: Double, excludeOrganizerId: UUID?) =
            rows.values.filter { it.status == "active" && (it.teamId == null || it.publishedAt != null) }
    }

    private class MemPostulations : PostulationRepository() {
        val rows = mutableMapOf<UUID, PostulationRecord>()
        override fun create(convocatoryId: UUID, playerId: UUID, position: String): PostulationRecord? {
            if (rows.values.any { it.convocatoryId == convocatoryId && it.playerId == playerId }) return null
            val p = PostulationRecord(UUID.randomUUID(), convocatoryId, playerId, position, "pending", "now", null)
            rows[p.id] = p
            return p
        }
        override fun findById(id: UUID) = rows[id]
        override fun findByConvocatory(convocatoryId: UUID) =
            rows.values.filter { it.convocatoryId == convocatoryId && it.status != "cancelled" }
        override fun findByPlayer(playerId: UUID): List<MyPostulationRecord> = emptyList()
        override fun updateStatus(id: UUID, status: String): Boolean {
            val p = rows[id] ?: return false
            rows[id] = p.copy(status = status)
            return true
        }
    }

    private class MemInvites : TeamPriorityInviteRepository() {
        val rows = mutableListOf<TeamPriorityInviteRecord>()
        private fun replace(old: TeamPriorityInviteRecord, new: TeamPriorityInviteRecord) {
            rows[rows.indexOf(old)] = new
        }
        override fun createAll(convocatoryId: UUID, playerIds: Collection<UUID>) {
            playerIds.forEach { rows += TeamPriorityInviteRecord(UUID.randomUUID(), convocatoryId, it, PriorityResponse.PENDING, null, null) }
        }
        override fun find(convocatoryId: UUID, playerId: UUID) =
            rows.firstOrNull { it.convocatoryId == convocatoryId && it.playerId == playerId }
        override fun findByConvocatory(convocatoryId: UUID) = rows.filter { it.convocatoryId == convocatoryId }
        override fun countPending(convocatoryId: UUID) =
            rows.count { it.convocatoryId == convocatoryId && it.response == PriorityResponse.PENDING }
        override fun pendingFor(playerId: UUID) =
            rows.filter { it.playerId == playerId && it.response == PriorityResponse.PENDING }
        override fun respond(convocatoryId: UUID, playerId: UUID, response: String, now: LocalDateTime, postulationId: UUID?): Boolean {
            val i = find(convocatoryId, playerId)?.takeIf { it.response == PriorityResponse.PENDING } ?: return false
            replace(i, i.copy(response = response, respondedAt = now, postulationId = postulationId))
            return true
        }
        override fun declineByPostulation(postulationId: UUID, now: LocalDateTime): Boolean {
            val i = rows.firstOrNull { it.postulationId == postulationId && it.response == PriorityResponse.ACCEPTED } ?: return false
            replace(i, i.copy(response = PriorityResponse.DECLINED))
            return true
        }
        override fun expirePending(convocatoryId: UUID, now: LocalDateTime): Int {
            val pending = rows.filter { it.convocatoryId == convocatoryId && it.response == PriorityResponse.PENDING }
            pending.forEach { replace(it, it.copy(response = PriorityResponse.EXPIRED)) }
            return pending.size
        }
    }

    private inner class MemTeams(var activeMembers: List<UUID>) : TeamRepository() {
        override fun findById(teamId: UUID) = if (teamId == this@TeamPriorityServiceTest.teamId)
            TeamRecord(teamId, organizerId, "Org", "Los del martes", now0) else null
        override fun members(teamId: UUID) = activeMembers.map {
            TeamMemberRecord(teamId, it, "M", null, null, TeamMemberStatus.ACTIVE, now0, now0)
        }
    }

    private class Pushes : FcmService(serviceAccountPath = null) {
        val sent = mutableListOf<Pair<String?, Map<String, String>>>()
        override fun sendToToken(token: String?, title: String, body: String, data: Map<String, String>): Boolean {
            sent += token to data
            return true
        }
        fun count(type: String) = sent.count { it.second["type"] == type }
    }

    private class Users : UserRepository() {
        override fun findById(userId: UUID) =
            UserRecord(userId, "uid", "Jugador", null, null, null)
        override fun getFcmToken(userId: UUID): String? = "tok-$userId"
        override fun findNearbyPlayersToNotify(lat: Double, lng: Double, radiusMeters: Double, excludeUserId: UUID) =
            listOf(NearbyPlayer(UUID.randomUUID(), "near"))
    }

    private class Pins : PinBroadcaster {
        val opened = mutableListOf<Int>()
        var closed = 0
        override fun newPin(c: ConvocatoryRecord, slots: Int, excludeUserId: String?) { opened += slots }
        override fun pinClosed(c: ConvocatoryRecord, excludeUserId: String?) { closed++ }
    }

    private val convocatories = MemConvocatories()
    private val postulations = MemPostulations()
    private val invites = MemInvites()
    private val fcm = Pushes()
    private val users = Users()
    private val pins = Pins()
    private val teamsRepo = MemTeams(members)
    private val notifier = object : NearbyNotifier(users, fcm, 50.0) {
        var calls = 0
        override fun notify(c: ConvocatoryRecord, title: String, body: String, type: String) { calls++ }
    }

    private val priority = TeamPriorityService(
        convocatories = convocatories, invites = invites, postulations = postulations,
        teams = teamsRepo, users = users, fcm = fcm, notifier = notifier, broadcaster = pins,
        creator = PostulationCreator(postulations, convocatories, users, fcm), clock = clock,
    )
    private val convocatoryService = ConvocatoryService(
        repository = convocatories, postulations = postulations, users = users, fcm = fcm,
        nearbyRadiusKm = 50.0, lifecycle = inertMatchLifecycle(), notifier = notifier,
        broadcaster = pins, teamPriority = priority,
    )
    private val postulationService = PostulationService(
        postulations, convocatories, users, fcm, inertMatchLifecycle(), priority,
    )

    private fun create(
        teamId: UUID? = this.teamId,
        window: Int? = 60,
        scheduledAt: LocalDateTime = now0.plusHours(5),
        slots: List<PositionSlot> = listOf(PositionSlot("Delantero", 2), PositionSlot("Defensa", 1)),
        organizer: UUID = organizerId,
    ) = convocatoryService.create(
        ConvocatoryCreate(
            organizerId = organizer, lat = 6.2, lng = -75.5, addressText = null,
            positionSlots = slots, fee = 0.0, format = "Fútbol 5", ambiente = "Recocha",
            scheduledAt = scheduledAt.toString(), teamId = teamId, teamWindowMinutes = window,
        )
    )

    // ── Crear ────────────────────────────────────────────────────────────────

    @Test
    fun crear_con_equipo_queda_team_only_sin_pin_ni_aviso_a_cercanos_e_invita_solo_a_activos() {
        val c = create()

        assertEquals("team_only", c.status)
        assertEquals(now0.plusMinutes(60).toString(), c.teamWindowEndsAt)
        assertEquals(0, notifier.calls, "No debe avisar a los cercanos")
        assertTrue(pins.opened.isEmpty(), "No debe haber new_pin en la fase privada")
        assertEquals(members.toSet(), invites.findByConvocatory(c.id).map { it.playerId }.toSet())
        assertEquals(members.size, fcm.count("team_priority"))
    }

    @Test
    fun el_organizador_se_excluye_de_la_foto_de_invitaciones() {
        teamsRepo.activeMembers = members + organizerId
        val c = create()
        assertEquals(members.size, invites.findByConvocatory(c.id).size)
    }

    @Test
    fun crear_sin_equipo_sigue_siendo_publica_al_instante() {
        val c = create(teamId = null, window = null)

        assertEquals("active", c.status)
        assertEquals(1, notifier.calls)
        assertEquals(listOf(3), pins.opened)
        assertEquals(0, fcm.count("team_priority"))
    }

    @Test
    fun ventana_que_no_cierra_una_hora_antes_del_partido_es_400() {
        // Partido en 1 h 30; ventana de 60 min cierra solo 30 min antes.
        assertFailsWith<IllegalArgumentException> { create(scheduledAt = now0.plusMinutes(90), window = 60) }
        // Con 30 min sí cabe (cierra 60 min antes: justo en el límite).
        create(scheduledAt = now0.plusMinutes(90), window = 30)
    }

    @Test
    fun ventana_fuera_de_las_opciones_es_400() {
        assertFailsWith<IllegalArgumentException> { create(window = 45) }
        assertFailsWith<IllegalArgumentException> { create(window = null) }
    }

    @Test
    fun equipo_sin_miembros_activos_es_400() {
        teamsRepo.activeMembers = emptyList()
        assertFailsWith<IllegalArgumentException> { create() }
        assertTrue(convocatories.rows.isEmpty(), "No debe crearse nada")
    }

    @Test
    fun equipo_ajeno_es_403() {
        assertFailsWith<TeamForbidden> { create(organizer = UUID.randomUUID()) }
    }

    // ── Responder ────────────────────────────────────────────────────────────

    @Test
    fun voy_crea_una_postulacion_pending_en_team_only() {
        val c = create()
        priority.respond(c.id, members[0], accept = true, position = "Delantero")

        val p = postulations.rows.values.single()
        assertEquals("pending", p.status)
        assertEquals(members[0], p.playerId)
        assertEquals(PriorityResponse.ACCEPTED, invites.find(c.id, members[0])?.response)
        assertEquals(p.id, invites.find(c.id, members[0])?.postulationId)
        assertEquals("team_only", convocatories.findById(c.id)?.status, "Aún faltan respuestas")
    }

    @Test
    fun voy_sin_cupo_libre_en_esa_posicion_es_conflicto() {
        val c = create() // Defensa tiene 1 cupo
        priority.respond(c.id, members[0], accept = true, position = "Defensa")
        assertFailsWith<PostulationConflict> {
            priority.respond(c.id, members[1], accept = true, position = "Defensa")
        }
    }

    @Test
    fun responder_dos_veces_es_conflicto() {
        val c = create()
        priority.respond(c.id, members[0], accept = false, position = null)
        assertFailsWith<PostulationConflict> {
            priority.respond(c.id, members[0], accept = false, position = null)
        }
    }

    @Test
    fun quien_no_fue_invitado_no_puede_responder() {
        val c = create()
        assertFailsWith<PostulationForbidden> {
            priority.respond(c.id, UUID.randomUUID(), accept = false, position = null)
        }
    }

    @Test
    fun la_postulacion_publica_sigue_rechazando_team_only() {
        val c = create()
        assertFailsWith<PostulationConflict> {
            postulationService.apply(c.id, UUID.randomUUID(), "Delantero")
        }
    }

    // ── Abrir ────────────────────────────────────────────────────────────────

    @Test
    fun cuando_todos_responden_se_abre_antes_de_la_ventana_y_publica_con_los_cupos_libres() {
        val c = create() // 3 cupos
        priority.respond(c.id, members[0], true, "Delantero")
        priority.respond(c.id, members[1], false, null)
        assertEquals("team_only", convocatories.findById(c.id)?.status)
        priority.respond(c.id, members[2], false, null)

        val opened = convocatories.findById(c.id)!!
        assertEquals("active", opened.status)
        assertNotNull(opened.publishedAt)
        assertEquals(listOf(2), pins.opened, "Publica con los cupos libres: 3 − 1 pendiente del equipo")
        assertEquals(1, notifier.calls)
        assertEquals(1, fcm.count("team_window_opened"))
    }

    @Test
    fun al_vencer_la_ventana_abre_y_expira_las_pendientes() {
        val c = create()
        priority.respond(c.id, members[0], false, null)

        now = now0.plusMinutes(61)
        assertEquals(1, priority.openDue())

        assertEquals("active", convocatories.findById(c.id)?.status)
        assertEquals(PriorityResponse.DECLINED, invites.find(c.id, members[0])?.response)
        assertEquals(PriorityResponse.EXPIRED, invites.find(c.id, members[1])?.response)
        assertEquals(PriorityResponse.EXPIRED, invites.find(c.id, members[2])?.response)
    }

    @Test
    fun responder_con_la_ventana_vencida_abre_y_responde_conflicto() {
        val c = create()
        now = now0.plusMinutes(61)
        assertFailsWith<PostulationConflict> { priority.respond(c.id, members[0], false, null) }
        assertEquals("active", convocatories.findById(c.id)?.status, "Se abre aunque el scheduler se atrasó")
    }

    @Test
    fun si_el_equipo_llena_todos_los_cupos_abre_pero_no_publica() {
        val c = create(slots = listOf(PositionSlot("Delantero", 2)))
        priority.respond(c.id, members[0], true, "Delantero")
        priority.respond(c.id, members[1], true, "Delantero")
        priority.respond(c.id, members[2], false, null)

        val opened = convocatories.findById(c.id)!!
        assertEquals("active", opened.status)
        assertNull(opened.publishedAt)
        assertTrue(pins.opened.isEmpty())
        assertEquals(0, notifier.calls)
        assertEquals(1, fcm.count("team_window_opened"), "Avisa al organizador: el equipo llenó los cupos")
        assertTrue(convocatoryService.getNearby(6.2, -75.5, 1000.0).none { it.id == c.id }, "No debe salir en el mapa")
    }

    @Test
    fun rechazar_a_un_miembro_despues_de_abierta_publica_en_ese_momento() {
        val c = create(slots = listOf(PositionSlot("Delantero", 2)))
        priority.respond(c.id, members[0], true, "Delantero")
        priority.respond(c.id, members[1], true, "Delantero")
        priority.respond(c.id, members[2], false, null)
        assertTrue(pins.opened.isEmpty())

        val rejected = postulations.rows.values.first { it.playerId == members[0] }
        postulationService.reject(rejected.id, organizerId)

        assertEquals(listOf(1), pins.opened)
        assertEquals(1, notifier.calls)
    }

    @Test
    fun maybeOpen_y_maybePublish_son_idempotentes() {
        val c = create()
        members.forEach { priority.respond(c.id, it, false, null) }
        val pushes = fcm.count("team_window_opened")

        repeat(3) {
            assertEquals(false, priority.maybeOpen(c.id))
            assertEquals(false, priority.maybePublish(c.id))
        }
        priority.openDue()

        assertEquals(1, pins.opened.size, "2 llamadas = 1 pin")
        assertEquals(1, notifier.calls)
        assertEquals(pushes, fcm.count("team_window_opened"), "2 llamadas = 1 push")
    }

    @Test
    fun el_retiro_de_un_voy_en_team_only_cuenta_como_declined_y_puede_abrir() {
        val c = create()
        priority.respond(c.id, members[0], true, "Delantero")
        priority.respond(c.id, members[1], false, null)
        priority.respond(c.id, members[2], false, null)
        // Todos respondieron → ya abrió. Probamos el retiro en un caso aún privado:
        val c2 = create(scheduledAt = now0.plusHours(9))
        priority.respond(c2.id, members[0], true, "Delantero")
        val p = postulations.rows.values.first { it.convocatoryId == c2.id }

        postulationService.withdraw(p.id, members[0])

        assertEquals(PriorityResponse.DECLINED, invites.find(c2.id, members[0])?.response)
    }

    // ── Abrir ya / cancelar / equipo ─────────────────────────────────────────

    @Test
    fun abrir_ya_solo_el_organizador_y_solo_en_team_only() {
        val c = create()
        assertFailsWith<PostulationForbidden> { priority.openNow(c.id, UUID.randomUUID()) }

        val opened = priority.openNow(c.id, organizerId)
        assertEquals("active", opened.status)
        assertEquals(0, fcm.count("team_window_opened"), "El organizador ya ve el resultado: sin push")
        assertEquals(listOf(3), pins.opened)

        assertFailsWith<PostulationConflict> { priority.openNow(c.id, organizerId) }
    }

    @Test
    fun cancelar_en_team_only_expira_las_invitaciones_y_no_emite_pin_closed() {
        val c = create()
        convocatoryService.cancel(c.id, organizerId, "Lluvia")

        assertEquals("cancelled", convocatories.findById(c.id)?.status)
        assertTrue(invites.findByConvocatory(c.id).all { it.response == PriorityResponse.EXPIRED })
        assertEquals(0, pins.closed, "Nunca hubo pin")
    }

    @Test
    fun cancelar_una_publicada_si_emite_pin_closed() {
        val c = create()
        priority.openNow(c.id, organizerId)
        convocatoryService.cancel(c.id, organizerId, "Lluvia")
        assertEquals(1, pins.closed)
    }

    @Test
    fun borrar_el_equipo_abre_sus_team_only() {
        val c = create()
        priority.onTeamDeleted(teamId)

        val opened = convocatories.findById(c.id)!!
        assertEquals("active", opened.status)
        assertEquals(listOf(3), pins.opened)
    }

    @Test
    fun quitar_a_un_miembro_expira_su_invitacion_y_conserva_su_postulacion() {
        val c = create()
        priority.respond(c.id, members[0], true, "Delantero")
        priority.respond(c.id, members[1], false, null)

        priority.onMemberRemoved(teamId, members[2])

        assertEquals(PriorityResponse.EXPIRED, invites.find(c.id, members[2])?.response)
        assertEquals("pending", postulations.rows.values.single().status)
        assertEquals("active", convocatories.findById(c.id)?.status, "Era la última pendiente: se abre")
    }

    // ── Lecturas ─────────────────────────────────────────────────────────────

    @Test
    fun getMine_incluye_team_only_con_el_conteo_de_respuestas_y_getNearby_no_la_filtra() {
        val c = create()
        priority.respond(c.id, members[0], false, null)

        val mine = convocatoryService.getMine(organizerId).single()
        assertEquals("team_only", mine.status)
        assertEquals(1, mine.teamResponded)
        assertEquals(3, mine.teamInvited)
        assertTrue(convocatoryService.getNearby(6.2, -75.5, 1000.0).isEmpty())
    }

    @Test
    fun getById_abre_la_ventana_vencida_al_leer() {
        val c = create()
        now = now0.plusMinutes(61)
        assertEquals("active", convocatoryService.getById(c.id)?.status)
    }

    @Test
    fun pendingFor_lista_solo_lo_que_espera_mi_respuesta_con_las_posiciones_libres() {
        val c = create()
        priority.respond(c.id, members[0], true, "Defensa")

        val view = priority.pendingFor(members[1]).single()
        assertEquals(c.id, view.convocatory.id)
        assertEquals(listOf(PositionSlot("Delantero", 2)), view.freePositions, "Defensa ya tiene su cupo ocupado")
        assertTrue(priority.pendingFor(members[0]).isEmpty(), "Ya respondió")
    }
}
