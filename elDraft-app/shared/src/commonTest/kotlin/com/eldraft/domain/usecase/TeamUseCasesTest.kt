package com.eldraft.domain.usecase

import com.eldraft.data.models.Team
import com.eldraft.data.models.TeamDetail
import com.eldraft.data.models.TeamInvitation
import com.eldraft.data.models.TeamMember
import com.eldraft.data.models.TeamSuggestion
import com.eldraft.domain.repository.TeamRepository
import com.eldraft.domain.usecase.team.CreateTeamUseCase
import com.eldraft.domain.usecase.team.DeleteTeamUseCase
import com.eldraft.domain.usecase.team.GetTeamSuggestionsUseCase
import com.eldraft.domain.usecase.team.InviteToTeamUseCase
import com.eldraft.domain.usecase.team.RemoveTeamMemberUseCase
import com.eldraft.domain.usecase.team.RenameTeamUseCase
import com.eldraft.domain.usecase.team.RespondTeamInvitationUseCase
import com.eldraft.domain.usecase.team.TeamInviteTarget
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeTeamRepository : TeamRepository {
    /** Registro de llamadas: nombre del método + argumentos. */
    val calls = mutableListOf<List<Any?>>()

    override suspend fun getMyTeams(): List<Team> = error("no usado")
    override suspend fun createTeam(name: String): Team {
        calls += listOf("create", name)
        return Team(id = "t1", name = name)
    }
    override suspend fun renameTeam(teamId: String, name: String): Team {
        calls += listOf("rename", teamId, name)
        return Team(id = teamId, name = name)
    }
    override suspend fun deleteTeam(teamId: String) {
        calls += listOf("delete", teamId)
    }
    override suspend fun getTeamDetail(teamId: String): TeamDetail = error("no usado")
    override suspend fun getSuggestions(teamId: String?): List<TeamSuggestion> {
        calls += listOf("suggestions", teamId)
        return emptyList()
    }
    override suspend fun addMember(teamId: String, playerId: String?, email: String?): TeamMember {
        calls += listOf("add", teamId, playerId, email)
        return TeamMember(playerId = playerId ?: "p-email", status = "invited")
    }
    override suspend fun removeMember(teamId: String, playerId: String) {
        calls += listOf("remove", teamId, playerId)
    }
    override suspend fun getInvitations(): List<TeamInvitation> = error("no usado")
    override suspend fun respondInvitation(teamId: String, accept: Boolean) {
        calls += listOf("respond", teamId, accept)
    }
    override suspend fun getMemberships(): List<Team> = error("no usado")
}

class TeamUseCasesTest {

    @Test
    fun crear_equipo_envia_el_nombre_con_trim() = runTest {
        val repo = FakeTeamRepository()
        val team = CreateTeamUseCase(repo)("  Los Pibes  ")
        assertEquals(listOf<Any?>("create", "Los Pibes"), repo.calls.single())
        assertEquals("Los Pibes", team.name)
    }

    @Test
    fun crear_equipo_con_nombre_invalido_falla_sin_llamar_repo() = runTest {
        val repo = FakeTeamRepository()
        assertFailsWith<IllegalArgumentException> { CreateTeamUseCase(repo)("   ") }
        assertFailsWith<IllegalArgumentException> { CreateTeamUseCase(repo)("a".repeat(41)) }
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun renombrar_con_nombre_o_id_invalido_falla_sin_llamar_repo() = runTest {
        val repo = FakeTeamRepository()
        assertFailsWith<IllegalArgumentException> { RenameTeamUseCase(repo)("t1", "") }
        assertFailsWith<IllegalArgumentException> { RenameTeamUseCase(repo)("", "Nuevo") }
        assertTrue(repo.calls.isEmpty())
        RenameTeamUseCase(repo)("t1", " Nuevo ")
        assertEquals(listOf<Any?>("rename", "t1", "Nuevo"), repo.calls.single())
    }

    @Test
    fun invitar_por_correo_lo_envia_normalizado_y_sin_playerId() = runTest {
        val repo = FakeTeamRepository()
        InviteToTeamUseCase(repo)("t1", TeamInviteTarget.ByEmail("  Juan@Correo.COM "))
        assertEquals(listOf<Any?>("add", "t1", null, "juan@correo.com"), repo.calls.single())
    }

    @Test
    fun invitar_por_correo_con_formato_invalido_falla_sin_llamar_repo() = runTest {
        val repo = FakeTeamRepository()
        val invite = InviteToTeamUseCase(repo)
        assertFailsWith<IllegalArgumentException> { invite("t1", TeamInviteTarget.ByEmail("juan")) }
        assertFailsWith<IllegalArgumentException> { invite("t1", TeamInviteTarget.ByEmail("   ")) }
        assertFailsWith<IllegalArgumentException> { invite("t1", TeamInviteTarget.ByEmail("juan perez@x.com")) }
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun invitar_por_id_envia_solo_el_playerId() = runTest {
        val repo = FakeTeamRepository()
        InviteToTeamUseCase(repo).byId("t1", "p9")
        assertEquals(listOf<Any?>("add", "t1", "p9", null), repo.calls.single())
    }

    @Test
    fun invitar_con_id_vacio_falla_sin_llamar_repo() = runTest {
        val repo = FakeTeamRepository()
        val invite = InviteToTeamUseCase(repo)
        assertFailsWith<IllegalArgumentException> { invite("t1", TeamInviteTarget.ById("")) }
        assertFailsWith<IllegalArgumentException> { invite("", TeamInviteTarget.ById("p9")) }
        assertFailsWith<IllegalArgumentException> { invite.byEmail("", "juan@correo.com") }
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun quitar_o_salirse_con_ids_vacios_falla_sin_llamar_repo() = runTest {
        val repo = FakeTeamRepository()
        assertFailsWith<IllegalArgumentException> { RemoveTeamMemberUseCase(repo)("t1", "") }
        assertFailsWith<IllegalArgumentException> { RemoveTeamMemberUseCase(repo)("", "p1") }
        assertFailsWith<IllegalArgumentException> { DeleteTeamUseCase(repo)(" ") }
        assertFailsWith<IllegalArgumentException> { RespondTeamInvitationUseCase(repo)("", true) }
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun responder_invitacion_pasa_la_decision() = runTest {
        val repo = FakeTeamRepository()
        RespondTeamInvitationUseCase(repo)("t1", false)
        assertEquals(listOf<Any?>("respond", "t1", false), repo.calls.single())
    }

    @Test
    fun sugerencias_con_teamId_en_blanco_se_piden_sin_equipo() = runTest {
        val repo = FakeTeamRepository()
        GetTeamSuggestionsUseCase(repo)("  ")
        GetTeamSuggestionsUseCase(repo)("t1")
        assertNull(repo.calls[0][1])
        assertEquals("t1", repo.calls[1][1])
    }
}
