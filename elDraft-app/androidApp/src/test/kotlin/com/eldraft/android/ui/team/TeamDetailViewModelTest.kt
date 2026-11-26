package com.eldraft.android.ui.team

import com.eldraft.android.MainDispatcherRule
import com.eldraft.android.notifications.NotificationRefreshBus
import com.eldraft.core.network.ApiException
import com.eldraft.data.models.TeamDetail
import com.eldraft.data.models.TeamMember
import com.eldraft.data.models.TeamSuggestion
import com.eldraft.domain.usecase.team.DeleteTeamUseCase
import com.eldraft.domain.usecase.team.GetTeamDetailUseCase
import com.eldraft.domain.usecase.team.GetTeamSuggestionsUseCase
import com.eldraft.domain.usecase.team.InviteToTeamUseCase
import com.eldraft.domain.usecase.team.RemoveTeamMemberUseCase
import com.eldraft.domain.usecase.team.RenameTeamUseCase
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeamDetailViewModelTest {

    private val main = MainDispatcherRule()

    @BeforeTest fun setUp() = main.start()
    @AfterTest fun tearDown() = main.stop()

    private fun viewModel(repo: FakeTeamRepository, userId: String? = "me") = TeamDetailViewModel(
        getTeamDetail = GetTeamDetailUseCase(repo),
        renameTeam = RenameTeamUseCase(repo),
        deleteTeam = DeleteTeamUseCase(repo),
        removeMember = RemoveTeamMemberUseCase(repo),
        getSuggestions = GetTeamSuggestionsUseCase(repo),
        inviteToTeam = InviteToTeamUseCase(repo),
        authRepository = FakeAuthRepository(userId),
        refreshBus = NotificationRefreshBus(),
    )

    private fun member(id: String, status: String = "active") =
        TeamMember(playerId = id, name = "Jugador $id", status = status)

    private fun suggestion(id: String) = TeamSuggestion(playerId = id, name = "Jugador $id", matchesTogether = 3)

    /** Carga el detalle y abre el sheet, que es el punto de partida de los tests de invitar. */
    private fun TestScope.loadedWithSheet(repo: FakeTeamRepository): TeamDetailViewModel {
        val vm = viewModel(repo)
        vm.load("t1")
        advanceUntilIdle()
        vm.openAddPlayer()
        advanceUntilIdle()
        return vm
    }

    @Test
    fun load_publica_el_detalle() = runTest {
        val repo = FakeTeamRepository(
            detail = TeamDetail(id = "t1", name = "Los de siempre", isOwner = true, members = listOf(member("p1"))),
        )
        val vm = viewModel(repo)

        vm.load("t1")
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()

        assertEquals("Los de siempre", vm.state.value.team?.name)
        assertTrue(vm.state.value.isOwner)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun las_sugerencias_se_piden_con_el_id_del_equipo() = runTest {
        val repo = FakeTeamRepository(suggestions = listOf(suggestion("s1")))
        val vm = loadedWithSheet(repo)

        assertEquals(listOf<String?>("t1"), repo.suggestionRequests)
        assertEquals(1, vm.state.value.suggestions.size)
    }

    @Test
    fun invitar_un_sugerido_lo_saca_de_la_lista_y_aparece_como_invitado() = runTest {
        val repo = FakeTeamRepository(suggestions = listOf(suggestion("s1"), suggestion("s2")))
        val vm = loadedWithSheet(repo)

        vm.inviteSuggested("s1")
        assertTrue("s1" in vm.state.value.invitingIds)
        vm.inviteSuggested("s1") // reenvío mientras la primera sigue en curso: se ignora
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(listOf("s2"), s.suggestions.map { it.playerId })
        val invited = s.members.single { it.playerId == "s1" }
        assertTrue(invited.isInvited)
        assertTrue(s.invitingIds.isEmpty())
    }

    @Test
    fun invitar_por_correo_con_exito_limpia_el_campo_y_confirma() = runTest {
        val repo = FakeTeamRepository()
        val vm = loadedWithSheet(repo)

        vm.onEmailChange("  Ana@Correo.com ")
        assertTrue(vm.state.value.canSendEmail)
        vm.inviteByEmail()
        assertTrue(vm.state.value.isInvitingByEmail)
        assertFalse(vm.state.value.canSendEmail)
        vm.inviteByEmail() // doble tap: no sale una segunda invitación
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(listOf("ana@correo.com"), repo.invitesByEmail) // normalizado por el caso de uso
        assertEquals("", s.email)
        assertNull(s.emailError)
        assertEquals("Jugador by-mail", s.emailInvitedName)
        assertTrue(s.members.any { it.playerId == "by-mail" && it.isInvited })
        assertFalse(s.isInvitingByEmail)
    }

    @Test
    fun invitar_por_correo_inexistente_muestra_el_404_en_la_pestana_correo() = runTest {
        val repo = FakeTeamRepository(
            failInviteWith = ApiException(
                status = 404,
                body = """{"code":"PLAYER_NOT_FOUND","message":"No encontramos un jugador con ese correo"}""",
            ),
        )
        val vm = loadedWithSheet(repo)

        vm.onEmailChange("nadie@correo.com")
        vm.inviteByEmail()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("No encontramos un jugador con ese correo", s.emailError)
        assertEquals("nadie@correo.com", s.email) // se conserva para corregirlo
        assertNull(s.error) // no sale como snackbar de pantalla
        assertFalse(s.isInvitingByEmail)
    }

    @Test
    fun invitar_por_correo_con_rate_limit_muestra_el_mensaje_del_429() = runTest {
        val repo = FakeTeamRepository(
            failInviteWith = ApiException(
                status = 429,
                body = """{"code":"RATE_LIMITED","message":"Hiciste muchas búsquedas. Intenta en un rato"}""",
            ),
        )
        val vm = loadedWithSheet(repo)

        vm.onEmailChange("ana@correo.com")
        vm.inviteByEmail()
        advanceUntilIdle()

        assertEquals("Hiciste muchas búsquedas. Intenta en un rato", vm.state.value.emailError)
    }

    @Test
    fun quitar_un_miembro_lo_saca_de_la_lista() = runTest {
        val repo = FakeTeamRepository(
            detail = TeamDetail(id = "t1", isOwner = true, members = listOf(member("p1"), member("p2", "invited"))),
        )
        val vm = viewModel(repo)
        vm.load("t1")
        advanceUntilIdle()

        vm.remove("p2")
        assertTrue("p2" in vm.state.value.removingIds)
        advanceUntilIdle()

        assertEquals(listOf("t1" to "p2"), repo.removed)
        assertEquals(listOf("p1"), vm.state.value.members.map { it.playerId })
        assertTrue(vm.state.value.removingIds.isEmpty())
    }

    @Test
    fun salirse_usa_el_id_del_usuario_actual_y_navega_atras() = runTest {
        val repo = FakeTeamRepository(
            detail = TeamDetail(id = "t1", isOwner = false, members = listOf(member("me"))),
        )
        val vm = viewModel(repo, userId = "me")
        vm.load("t1")
        advanceUntilIdle()

        vm.leave()
        advanceUntilIdle()

        assertEquals(listOf("t1" to "me"), repo.removed)
        assertTrue(vm.state.value.closed)
    }

    @Test
    fun borrar_el_equipo_navega_atras() = runTest {
        val repo = FakeTeamRepository()
        val vm = viewModel(repo)
        vm.load("t1")
        advanceUntilIdle()

        vm.delete()
        assertTrue(vm.state.value.isDeleting)
        advanceUntilIdle()

        assertEquals(listOf("t1"), repo.deleted)
        assertTrue(vm.state.value.closed)
        assertFalse(vm.state.value.isDeleting)
    }

    @Test
    fun renombrar_actualiza_el_nombre_y_avisa_para_cerrar_el_dialogo() = runTest {
        val repo = FakeTeamRepository()
        val vm = viewModel(repo)
        vm.load("t1")
        advanceUntilIdle()

        vm.rename("  Nuevo nombre ")
        advanceUntilIdle()

        assertEquals("Nuevo nombre", vm.state.value.team?.name)
        assertTrue(vm.state.value.renamed)
        vm.consumeRenamed()
        assertFalse(vm.state.value.renamed)
    }
}
