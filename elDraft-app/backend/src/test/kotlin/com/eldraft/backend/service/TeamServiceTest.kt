package com.eldraft.backend.service

import com.eldraft.backend.notifications.FcmService
import com.eldraft.backend.repository.CoPlayRow
import com.eldraft.backend.repository.SuggestionCandidate
import com.eldraft.backend.repository.TeamInvitationRecord
import com.eldraft.backend.repository.TeamMemberRecord
import com.eldraft.backend.repository.TeamMemberStatus
import com.eldraft.backend.repository.TeamRecord
import com.eldraft.backend.repository.TeamRepository
import com.eldraft.backend.repository.TeamSummaryRecord
import com.eldraft.backend.repository.UserRecord
import com.eldraft.backend.repository.UserRepository
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reglas de equipos con repos en memoria: topes, perfil requerido, duplicados,
 * consentimiento, quién administra, qué ve cada uno y el rate-limit por correo.
 * El push usa un FcmService deshabilitado; se verifica que se consulte el token.
 */
class TeamServiceTest {

    private val now = LocalDateTime.of(2026, 6, 10, 12, 0)
    private fun clockAt(at: LocalDateTime): Clock =
        Clock.fixed(at.toInstant(ZoneOffset.UTC), ZoneId.of("UTC"))

    private val owner = UUID.randomUUID()
    private val ana = UUID.randomUUID()
    private val beto = UUID.randomUUID()
    private val sinFicha = UUID.randomUUID()

    /** Equipos y miembros en memoria. */
    private class FakeTeamRepo : TeamRepository() {
        val teamsById = linkedMapOf<UUID, TeamRecord>()
        val rows = mutableListOf<TeamMemberRecord>()
        var coPlay: List<CoPlayRow> = emptyList()
        var candidates: List<SuggestionCandidate> = emptyList()

        override fun countOwned(ownerId: UUID) = teamsById.values.count { it.ownerId == ownerId }
        override fun create(ownerId: UUID, name: String, now: LocalDateTime): TeamRecord {
            val t = TeamRecord(UUID.randomUUID(), ownerId, "Dueño", name, now)
            teamsById[t.id] = t
            return t
        }
        override fun findById(teamId: UUID) = teamsById[teamId]
        override fun rename(teamId: UUID, name: String): Boolean {
            val t = teamsById[teamId] ?: return false
            teamsById[teamId] = t.copy(name = name)
            return true
        }
        override fun delete(teamId: UUID): Boolean {
            rows.removeAll { it.teamId == teamId }
            return teamsById.remove(teamId) != null
        }
        override fun ownedBy(ownerId: UUID) = teamsById.values.filter { it.ownerId == ownerId }.map { summary(it) }
        override fun memberships(userId: UUID) = rows
            .filter { it.playerId == userId && it.status == TeamMemberStatus.ACTIVE }
            .map { summary(teamsById.getValue(it.teamId)) }
        override fun members(teamId: UUID) = rows.filter { it.teamId == teamId }
        override fun membership(teamId: UUID, playerId: UUID) =
            rows.firstOrNull { it.teamId == teamId && it.playerId == playerId }
        override fun countMembers(teamId: UUID) = rows.count { it.teamId == teamId }
        override fun addInvite(teamId: UUID, playerId: UUID, now: LocalDateTime): TeamMemberRecord? {
            if (membership(teamId, playerId) != null) return null
            val r = TeamMemberRecord(teamId, playerId, "Jugador", null, "Defensa", TeamMemberStatus.INVITED, now, null)
            rows += r
            return r
        }
        override fun accept(teamId: UUID, playerId: UUID, now: LocalDateTime): Boolean {
            val i = rows.indexOfFirst { it.teamId == teamId && it.playerId == playerId && it.status == TeamMemberStatus.INVITED }
            if (i < 0) return false
            rows[i] = rows[i].copy(status = TeamMemberStatus.ACTIVE, joinedAt = now)
            return true
        }
        override fun removeMember(teamId: UUID, playerId: UUID) =
            rows.removeAll { it.teamId == teamId && it.playerId == playerId }
        override fun invitationsFor(userId: UUID) = rows
            .filter { it.playerId == userId && it.status == TeamMemberStatus.INVITED }
            .map {
                val t = teamsById.getValue(it.teamId)
                TeamInvitationRecord(t.id, t.name, t.ownerId, t.ownerName, null, it.invitedAt)
            }
        override fun coPlayRows(userId: UUID) = coPlay
        override fun suggestionCandidates(ids: Collection<UUID>) = candidates.filter { it.playerId in ids }

        private fun summary(t: TeamRecord) = TeamSummaryRecord(
            t,
            activeCount = rows.count { it.teamId == t.id && it.status == TeamMemberStatus.ACTIVE },
            invitedCount = rows.count { it.teamId == t.id && it.status == TeamMemberStatus.INVITED },
        )

        /** Atajo para sembrar un miembro en un estado dado. */
        fun seed(teamId: UUID, playerId: UUID, status: String) {
            rows += TeamMemberRecord(teamId, playerId, "Jugador", null, null, status, LocalDateTime.MIN, null)
        }
    }

    /** Usuarios en memoria; anota las búsquedas por correo y las consultas de token FCM. */
    private class CapturingUserRepo(
        val known: Map<UUID, UserRecord>,
        val withProfile: Set<UUID>,
    ) : UserRepository() {
        val emailLookups = mutableListOf<String>()
        val tokenLookups = mutableListOf<UUID>()
        override fun findById(userId: UUID) = known[userId]
        override fun findByEmail(normalized: String): UserRecord? {
            emailLookups += normalized
            return known.values.firstOrNull { it.email?.lowercase() == normalized }
        }
        override fun hasProfile(userId: UUID) = userId in withProfile
        override fun getFcmToken(userId: UUID): String? {
            tokenLookups += userId
            return "token-$userId"
        }
    }

    private fun user(id: UUID, email: String?, uid: String = "uid-$id") =
        UserRecord(id, uid, "Usuario", email, null, null)

    private val fcmDisabled = FcmService(serviceAccountPath = null)

    private val repo = FakeTeamRepo()
    private val users = CapturingUserRepo(
        known = mapOf(
            owner to user(owner, "dueno@x.com"),
            ana to user(ana, "ana@x.com"),
            beto to user(beto, "beto@x.com"),
            sinFicha to user(sinFicha, "nueva@x.com"),
        ),
        withProfile = setOf(owner, ana, beto),
    )

    private fun service(limiter: TeamInviteRateLimiter = TeamInviteRateLimiter(clockAt(now))) =
        TeamService(repo, users, fcmDisabled, limiter, clockAt(now))

    private fun teamOf(svc: TeamService, by: UUID = owner) = svc.create(by, "Los del martes").team.id

    // --- Crear ---

    @Test
    fun crear_recorta_el_nombre() {
        val created = service().create(owner, "  Los del martes  ")
        assertEquals("Los del martes", created.team.name)
    }

    @Test
    fun crear_con_nombre_vacio_falla() {
        assertFailsWith<IllegalArgumentException> { service().create(owner, "   ") }
    }

    @Test
    fun crear_con_41_caracteres_falla_y_40_pasa() {
        val svc = service()
        assertFailsWith<IllegalArgumentException> { svc.create(owner, "a".repeat(41)) }
        assertEquals(40, svc.create(owner, "a".repeat(40)).team.name.length)
    }

    @Test
    fun crear_sin_ficha_esta_prohibido() {
        assertFailsWith<TeamForbidden> { service().create(sinFicha, "Equipo") }
    }

    @Test
    fun el_sexto_equipo_supera_el_tope() {
        val svc = service()
        repeat(5) { svc.create(owner, "Equipo $it") }
        assertFailsWith<TeamLimitReached> { svc.create(owner, "Equipo 6") }
    }

    @Test
    fun ser_miembro_de_otros_equipos_no_cuenta_para_el_tope() {
        val svc = service()
        repeat(5) { repo.seed(svc.create(ana, "Ajeno $it").team.id, owner, TeamMemberStatus.ACTIVE) }
        repeat(5) { svc.create(owner, "Mío $it") }
        assertEquals(5, repo.countOwned(owner))
    }

    // --- Invitar ---

    @Test
    fun invitar_por_id_crea_invitacion_y_consulta_el_token() {
        val svc = service()
        val team = teamOf(svc)
        val created = svc.addMember(team, owner, ana, null)
        assertEquals(TeamMemberStatus.INVITED, created.status)
        assertEquals(listOf(ana), users.tokenLookups)
    }

    @Test
    fun invitar_por_correo_normaliza_antes_de_buscar() {
        val svc = service()
        val team = teamOf(svc)
        val created = svc.addMember(team, owner, null, " ANA@x.com ")
        assertEquals(listOf("ana@x.com"), users.emailLookups)
        assertEquals(ana, created.playerId)
    }

    @Test
    fun correo_inexistente_no_se_encuentra() {
        val svc = service()
        val team = teamOf(svc)
        assertFailsWith<PlayerNotFoundByEmail> { svc.addMember(team, owner, null, "nadie@x.com") }
    }

    @Test
    fun id_inexistente_o_cuenta_borrada_no_se_encuentra() {
        val borrada = UUID.randomUUID()
        val localUsers = CapturingUserRepo(
            known = users.known + (borrada to user(borrada, null, uid = "deleted:$borrada")),
            withProfile = users.withProfile + borrada,
        )
        val svc = TeamService(repo, localUsers, fcmDisabled, TeamInviteRateLimiter(clockAt(now)), clockAt(now))
        val team = teamOf(svc)
        assertFailsWith<TeamNotFound> { svc.addMember(team, owner, UUID.randomUUID(), null) }
        assertFailsWith<TeamNotFound> { svc.addMember(team, owner, borrada, null) }
    }

    @Test
    fun invitar_a_quien_no_tiene_ficha_es_inelegible() {
        val svc = service()
        val team = teamOf(svc)
        assertFailsWith<TeamMemberIneligible> { svc.addMember(team, owner, sinFicha, null) }
        assertFailsWith<TeamMemberIneligible> { svc.addMember(team, owner, null, "nueva@x.com") }
    }

    @Test
    fun invitarse_a_si_mismo_es_conflicto() {
        val svc = service()
        val team = teamOf(svc)
        assertFailsWith<TeamConflict> { svc.addMember(team, owner, owner, null) }
        assertFailsWith<TeamConflict> { svc.addMember(team, owner, null, "dueno@x.com") }
    }

    @Test
    fun duplicado_invitado_o_activo_es_conflicto_y_no_notifica() {
        val svc = service()
        val team = teamOf(svc)
        svc.addMember(team, owner, ana, null)
        repo.seed(team, beto, TeamMemberStatus.ACTIVE)
        users.tokenLookups.clear()

        assertFailsWith<TeamConflict> { svc.addMember(team, owner, ana, null) }
        assertFailsWith<TeamConflict> { svc.addMember(team, owner, null, "beto@x.com") }
        assertTrue(users.tokenLookups.isEmpty())
    }

    @Test
    fun el_miembro_31_supera_el_tope() {
        val svc = service()
        val team = teamOf(svc)
        repeat(30) { repo.seed(team, UUID.randomUUID(), if (it % 2 == 0) TeamMemberStatus.ACTIVE else TeamMemberStatus.INVITED) }
        assertFailsWith<TeamLimitReached> { svc.addMember(team, owner, ana, null) }
    }

    @Test
    fun solo_el_duenio_invita() {
        val svc = service()
        val team = teamOf(svc)
        repo.seed(team, ana, TeamMemberStatus.ACTIVE)
        assertFailsWith<TeamForbidden> { svc.addMember(team, ana, beto, null) }
    }

    @Test
    fun el_intento_21_por_correo_se_limita_y_por_id_no_consume() {
        val svc = service()
        val team = teamOf(svc)
        // 20 intentos fallidos por correo consumen el cupo igual.
        repeat(20) {
            assertFailsWith<PlayerNotFoundByEmail> { svc.addMember(team, owner, null, "nadie$it@x.com") }
        }
        assertFailsWith<TeamRateLimited> { svc.addMember(team, owner, null, "ana@x.com") }
        // Por id sigue funcionando: no consume cupo.
        assertNotNull(svc.addMember(team, owner, ana, null))
    }

    @Test
    fun invitar_por_id_no_consume_cupo_del_correo() {
        val svc = service()
        val team = teamOf(svc)
        repeat(25) { runCatching { svc.addMember(team, owner, UUID.randomUUID(), null) } }
        // 25 llamadas por id (aunque fallen) no tocan el rate-limit.
        assertNotNull(svc.addMember(team, owner, null, "ana@x.com"))
    }

    // --- Responder invitación ---

    @Test
    fun aceptar_vuelve_activo_con_fecha() {
        val svc = service()
        val team = teamOf(svc)
        svc.addMember(team, owner, ana, null)
        svc.respondInvitation(team, ana, accept = true)
        val row = repo.membership(team, ana)!!
        assertEquals(TeamMemberStatus.ACTIVE, row.status)
        assertEquals(now, row.joinedAt)
    }

    @Test
    fun rechazar_borra_la_fila_y_se_puede_reinvitar() {
        val svc = service()
        val team = teamOf(svc)
        svc.addMember(team, owner, ana, null)
        svc.respondInvitation(team, ana, accept = false)
        assertNull(repo.membership(team, ana))
        assertEquals(TeamMemberStatus.INVITED, svc.addMember(team, owner, ana, null).status)
    }

    @Test
    fun aceptar_sin_invitacion_no_existe() {
        val svc = service()
        val team = teamOf(svc)
        assertFailsWith<TeamNotFound> { svc.respondInvitation(team, ana, accept = true) }
    }

    @Test
    fun aceptar_ya_siendo_activo_es_conflicto() {
        val svc = service()
        val team = teamOf(svc)
        repo.seed(team, ana, TeamMemberStatus.ACTIVE)
        assertFailsWith<TeamConflict> { svc.respondInvitation(team, ana, accept = true) }
    }

    // --- Quitar y salirse ---

    @Test
    fun el_duenio_quita_a_invitados_y_a_activos() {
        val svc = service()
        val team = teamOf(svc)
        repo.seed(team, ana, TeamMemberStatus.INVITED)
        repo.seed(team, beto, TeamMemberStatus.ACTIVE)
        svc.removeMember(team, owner, ana)
        svc.removeMember(team, owner, beto)
        assertTrue(repo.members(team).isEmpty())
    }

    @Test
    fun un_miembro_se_sale() {
        val svc = service()
        val team = teamOf(svc)
        repo.seed(team, ana, TeamMemberStatus.ACTIVE)
        svc.removeMember(team, ana, ana)
        assertNull(repo.membership(team, ana))
    }

    @Test
    fun un_tercero_no_puede_quitar_a_nadie() {
        val svc = service()
        val team = teamOf(svc)
        repo.seed(team, ana, TeamMemberStatus.ACTIVE)
        repo.seed(team, beto, TeamMemberStatus.ACTIVE)
        assertFailsWith<TeamForbidden> { svc.removeMember(team, beto, ana) }
        assertNotNull(repo.membership(team, ana))
    }

    @Test
    fun el_duenio_no_puede_quitarse_a_si_mismo() {
        val svc = service()
        val team = teamOf(svc)
        assertFailsWith<TeamConflict> { svc.removeMember(team, owner, owner) }
    }

    @Test
    fun quitar_a_quien_no_esta_no_existe() {
        val svc = service()
        val team = teamOf(svc)
        assertFailsWith<TeamNotFound> { svc.removeMember(team, owner, ana) }
    }

    // --- Administración ---

    @Test
    fun solo_el_duenio_renombra_y_borra() {
        val svc = service()
        val team = teamOf(svc)
        repo.seed(team, ana, TeamMemberStatus.ACTIVE)
        assertFailsWith<TeamForbidden> { svc.rename(team, ana, "Otro") }
        assertFailsWith<TeamForbidden> { svc.delete(team, ana) }

        assertEquals("Nuevo", svc.rename(team, owner, " Nuevo ").team.name)
        svc.delete(team, owner)
        assertNull(repo.findById(team))
        assertTrue(repo.members(team).isEmpty())
    }

    @Test
    fun renombrar_valida_el_nombre() {
        val svc = service()
        val team = teamOf(svc)
        assertFailsWith<IllegalArgumentException> { svc.rename(team, owner, "") }
        assertFailsWith<IllegalArgumentException> { svc.rename(team, owner, "b".repeat(41)) }
    }

    @Test
    fun equipo_inexistente_no_existe() {
        assertFailsWith<TeamNotFound> { service().rename(UUID.randomUUID(), owner, "X") }
        assertFailsWith<TeamNotFound> { service().addMember(UUID.randomUUID(), owner, ana, null) }
    }

    // --- Detalle según quién mira ---

    @Test
    fun el_duenio_ve_a_todos_con_su_estado() {
        val svc = service()
        val team = teamOf(svc)
        repo.seed(team, ana, TeamMemberStatus.ACTIVE)
        repo.seed(team, beto, TeamMemberStatus.INVITED)
        val detail = svc.detail(team, owner)
        assertTrue(detail.isOwner)
        assertEquals(setOf(ana, beto), detail.members.map { it.playerId }.toSet())
    }

    @Test
    fun un_miembro_activo_ve_solo_a_los_activos() {
        val svc = service()
        val team = teamOf(svc)
        repo.seed(team, ana, TeamMemberStatus.ACTIVE)
        repo.seed(team, beto, TeamMemberStatus.INVITED)
        val detail = svc.detail(team, ana)
        assertEquals(false, detail.isOwner)
        assertEquals(listOf(ana), detail.members.map { it.playerId })
    }

    @Test
    fun un_invitado_o_un_ajeno_no_ven_el_equipo() {
        val svc = service()
        val team = teamOf(svc)
        repo.seed(team, beto, TeamMemberStatus.INVITED)
        assertFailsWith<TeamNotFound> { svc.detail(team, beto) }
        assertFailsWith<TeamNotFound> { svc.detail(team, ana) }
    }

    @Test
    fun las_invitaciones_y_membresias_se_listan_por_usuario() {
        val svc = service()
        val team = teamOf(svc)
        svc.addMember(team, owner, ana, null)
        assertEquals(listOf(team), svc.invitations(ana).map { it.teamId })
        assertTrue(svc.memberships(ana).isEmpty())
        svc.respondInvitation(team, ana, accept = true)
        assertTrue(svc.invitations(ana).isEmpty())
        assertEquals(listOf(team), svc.memberships(ana).map { it.team.id })
    }

    // --- Sugerencias ---

    private fun coPlayWith(vararg players: UUID): List<CoPlayRow> {
        val conv = UUID.randomUUID()
        return players.map {
            CoPlayRow(conv, now.minusDays(1), "active", owner, false, it, "approved", false)
        }
    }

    private fun candidate(id: UUID, hasProfile: Boolean = true, anonymized: Boolean = false) =
        SuggestionCandidate(id, "Jugador", null, "Defensa", hasProfile, anonymized)

    @Test
    fun sugerencias_excluyen_anonimizados_y_sin_ficha() {
        val borrado = UUID.randomUUID()
        repo.coPlay = coPlayWith(ana, beto, borrado, sinFicha)
        repo.candidates = listOf(
            candidate(ana), candidate(beto),
            candidate(borrado, anonymized = true), candidate(sinFicha, hasProfile = false),
        )
        val ids = service().suggestions(owner, teamId = null).map { it.playerId }.toSet()
        assertEquals(setOf(ana, beto), ids)
    }

    @Test
    fun sugerencias_con_mi_equipo_excluyen_a_sus_miembros() {
        val svc = service()
        val team = teamOf(svc)
        repo.seed(team, ana, TeamMemberStatus.INVITED)
        val carl = UUID.randomUUID()
        repo.seed(team, carl, TeamMemberStatus.ACTIVE)
        repo.coPlay = coPlayWith(ana, beto, carl)
        repo.candidates = listOf(candidate(ana), candidate(beto), candidate(carl))
        assertEquals(listOf(beto), svc.suggestions(owner, team).map { it.playerId })
        assertEquals(1, svc.suggestions(owner, team).single().matchesTogether)
    }

    @Test
    fun sugerencias_con_equipo_ajeno_no_excluyen_nada() {
        val svc = service()
        val ajeno = teamOf(svc, by = beto)
        repo.seed(ajeno, ana, TeamMemberStatus.ACTIVE)
        repo.coPlay = coPlayWith(ana)
        repo.candidates = listOf(candidate(ana))
        assertEquals(listOf(ana), svc.suggestions(owner, ajeno).map { it.playerId })
    }

    // --- Sugerencias: el corte de 50 en el camino real ---

    /**
     * [matches] partidos de [player] con el dueño como organizador: el más
     * reciente hace [lastDaysAgo] días y los demás, bastante antes.
     */
    private fun playedWith(player: UUID, matches: Int, lastDaysAgo: Long): List<CoPlayRow> =
        (0 until matches).map { k ->
            val at = if (k == 0) now.minusDays(lastDaysAgo) else now.minusDays(500L + k)
            CoPlayRow(UUID.randomUUID(), at, "active", owner, false, player, "approved", false)
        }

    @Test
    fun sugerencias_con_mas_de_50_validos_devuelven_50_en_orden() {
        // 60 válidos: 0..19 con 3 partidos, 20..39 con 2, 40..59 con 1. Dentro de
        // cada grupo, el índice menor jugó más recientemente.
        val players = (0 until 60).map { UUID.randomUUID() }
        val rows = players.flatMapIndexed { i, p ->
            playedWith(p, matches = 3 - i / 20, lastDaysAgo = i + 1L)
        }
        repo.coPlay = rows.shuffled(kotlin.random.Random(7))
        repo.candidates = players.map { candidate(it) }.shuffled(kotlin.random.Random(11))

        val result = service().suggestions(owner, teamId = null)

        assertEquals(TEAM_SUGGESTIONS_LIMIT, result.size)
        assertEquals(players.take(50), result.map { it.playerId })
        assertEquals(3, result.first().matchesTogether)
        assertEquals(1, result.last().matchesTogether)
        assertEquals(now.minusDays(1), result.first().lastPlayedAt)
    }

    @Test
    fun sugerencias_descartados_en_la_cabeza_no_restan_cupo() {
        val svc = service()
        val team = teamOf(svc)

        // 15 descartados con más partidos que cualquier válido: quedarían primeros.
        val sinFichaIds = (0 until 5).map { UUID.randomUUID() }
        val anonimizados = (0 until 5).map { UUID.randomUUID() }
        val miembros = (0 until 5).map { UUID.randomUUID() }
        miembros.forEachIndexed { i, m ->
            repo.seed(team, m, if (i % 2 == 0) TeamMemberStatus.ACTIVE else TeamMemberStatus.INVITED)
        }
        val descartados = sinFichaIds + anonimizados + miembros
        val validos = (0 until 55).map { UUID.randomUUID() }

        val rows = descartados.flatMapIndexed { i, p -> playedWith(p, matches = 5, lastDaysAgo = i + 1L) } +
            validos.flatMapIndexed { i, p -> playedWith(p, matches = 2 - i / 30, lastDaysAgo = i + 1L) }
        repo.coPlay = rows.shuffled(kotlin.random.Random(3))
        repo.candidates = validos.map { candidate(it) } +
            sinFichaIds.map { candidate(it, hasProfile = false) } +
            anonimizados.map { candidate(it, anonymized = true) } +
            miembros.map { candidate(it) }

        val result = svc.suggestions(owner, team)

        assertEquals(TEAM_SUGGESTIONS_LIMIT, result.size)
        assertEquals(validos.take(50), result.map { it.playerId })
        assertTrue(result.none { it.playerId in descartados })
    }
}
