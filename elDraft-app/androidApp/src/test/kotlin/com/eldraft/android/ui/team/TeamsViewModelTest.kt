package com.eldraft.android.ui.team

import com.eldraft.android.MainDispatcherRule
import com.eldraft.android.notifications.NotificationRefreshBus
import com.eldraft.android.notifications.RefreshEvent
import com.eldraft.core.network.ApiException
import com.eldraft.data.models.Team
import com.eldraft.data.models.TeamInvitation
import com.eldraft.domain.team.TeamRules
import com.eldraft.domain.usecase.team.CreateTeamUseCase
import com.eldraft.domain.usecase.team.GetMyTeamMembershipsUseCase
import com.eldraft.domain.usecase.team.GetMyTeamsUseCase
import com.eldraft.domain.usecase.team.GetTeamInvitationsUseCase
import com.eldraft.domain.usecase.team.RespondTeamInvitationUseCase
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeamsViewModelTest {

    private val main = MainDispatcherRule()

    @BeforeTest fun setUp() = main.start()
    @AfterTest fun tearDown() = main.stop()

    private fun viewModel(repo: FakeTeamRepository, bus: NotificationRefreshBus = NotificationRefreshBus()) =
        TeamsViewModel(
            getMyTeams = GetMyTeamsUseCase(repo),
            getMemberships = GetMyTeamMembershipsUseCase(repo),
            getInvitations = GetTeamInvitationsUseCase(repo),
            createTeam = CreateTeamUseCase(repo),
            respondInvitation = RespondTeamInvitationUseCase(repo),
            refreshBus = bus,
        )

    private fun team(id: String) = Team(id = id, name = "Equipo $id", ownerId = "me")
    private fun invitation(teamId: String) = TeamInvitation(teamId = teamId, teamName = "Equipo $teamId", ownerName = "Ana")

    @Test
    fun load_publica_las_tres_secciones() = runTest {
        val repo = FakeTeamRepository(
            myTeams = listOf(team("a")),
            memberships = listOf(team("b"), team("c")),
            invitations = listOf(invitation("d")),
        )
        val vm = viewModel(repo)

        vm.load()
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(1, s.myTeams.size)
        assertEquals(2, s.memberships.size)
        assertEquals(1, s.invitations.size)
        assertFalse(s.isLoading)
        assertNull(s.error)
    }

    @Test
    fun load_con_error_del_backend_muestra_el_mensaje_del_servidor() = runTest {
        val repo = FakeTeamRepository(
            failLoadWith = ApiException(status = 503, body = """{"code":"DOWN","message":"Equipos en mantenimiento"}"""),
        )
        val vm = viewModel(repo)

        vm.load()
        advanceUntilIdle()

        assertEquals("Equipos en mantenimiento", vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun crear_con_exito_agrega_el_equipo_limpia_el_nombre_y_avisa_para_navegar() = runTest {
        val repo = FakeTeamRepository()
        val vm = viewModel(repo)
        vm.load()
        advanceUntilIdle()

        vm.onNewTeamNameChange("  Los del jueves  ")
        vm.create()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(listOf("Los del jueves"), repo.created) // normalizado por el caso de uso
        assertEquals("Los del jueves", s.myTeams.single().name)
        assertEquals("", s.newTeamName)
        assertEquals("new-1", s.createdTeamId)
        assertFalse(s.isCreating)

        vm.consumeCreatedTeam()
        assertNull(vm.state.value.createdTeamId)
    }

    @Test
    fun crear_con_error_del_backend_muestra_el_mensaje_y_conserva_el_nombre() = runTest {
        val repo = FakeTeamRepository(
            failCreateWith = ApiException(status = 409, body = """{"code":"TEAM_LIMIT","message":"Ya tienes 5 equipos"}"""),
        )
        val vm = viewModel(repo)

        vm.onNewTeamNameChange("Los del jueves")
        vm.create()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("Ya tienes 5 equipos", s.error)
        assertEquals("Los del jueves", s.newTeamName)
        assertFalse(s.isCreating)
        assertNull(s.createdTeamId)
    }

    @Test
    fun doble_tap_en_crear_solo_crea_un_equipo() = runTest {
        val repo = FakeTeamRepository()
        val vm = viewModel(repo)

        vm.onNewTeamNameChange("Los del jueves")
        vm.create()
        // La primera creación sigue encolada: el botón queda deshabilitado.
        assertTrue(vm.state.value.isCreating)
        assertFalse(vm.state.value.canSubmitNewTeam)
        vm.create()
        advanceUntilIdle()

        assertEquals(1, repo.created.size)
    }

    @Test
    fun crear_queda_deshabilitado_con_nombre_invalido_o_al_tope_de_equipos() = runTest {
        val full = FakeTeamRepository(myTeams = (1..TeamRules.MAX_TEAMS).map { team("t$it") })
        val vm = viewModel(full)
        vm.load()
        advanceUntilIdle()

        vm.onNewTeamNameChange("Otro equipo")
        assertFalse(vm.state.value.canCreateAnotherTeam)
        assertFalse(vm.state.value.canSubmitNewTeam)
        vm.create()
        advanceUntilIdle()
        assertTrue(full.created.isEmpty())

        val empty = viewModel(FakeTeamRepository())
        empty.onNewTeamNameChange("   ")
        assertFalse(empty.state.value.canSubmitNewTeam)
        empty.onNewTeamNameChange("x".repeat(TeamRules.NAME_MAX_LENGTH + 1))
        assertFalse(empty.state.value.canSubmitNewTeam)
    }

    @Test
    fun aceptar_quita_la_invitacion_y_recarga_soy_miembro() = runTest {
        val repo = FakeTeamRepository(invitations = listOf(invitation("t9")))
        val vm = viewModel(repo)
        vm.load()
        advanceUntilIdle()

        repo.memberships = listOf(team("t9"))
        vm.accept("t9")
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(listOf("t9" to true), repo.responses)
        assertTrue(s.invitations.isEmpty())
        assertEquals(listOf("t9"), s.memberships.map { it.id })
        assertTrue(s.respondingIds.isEmpty())
    }

    @Test
    fun rechazar_quita_la_invitacion_sin_sumar_membresia() = runTest {
        val repo = FakeTeamRepository(invitations = listOf(invitation("t9")))
        val vm = viewModel(repo)
        vm.load()
        advanceUntilIdle()

        vm.decline("t9")
        advanceUntilIdle()

        assertEquals(listOf("t9" to false), repo.responses)
        assertTrue(vm.state.value.invitations.isEmpty())
        assertTrue(vm.state.value.memberships.isEmpty())
    }

    @Test
    fun mientras_responde_bloquea_la_fila_e_ignora_el_segundo_tap() = runTest {
        val repo = FakeTeamRepository(invitations = listOf(invitation("t9")))
        val vm = viewModel(repo)
        vm.load()
        advanceUntilIdle()

        vm.accept("t9")
        assertTrue("t9" in vm.state.value.respondingIds)
        vm.decline("t9")
        advanceUntilIdle()

        assertEquals(listOf("t9" to true), repo.responses)
    }

    @Test
    fun responder_con_error_desbloquea_la_fila_y_muestra_el_mensaje() = runTest {
        val repo = FakeTeamRepository(
            invitations = listOf(invitation("t9")),
            failRespondWith = ApiException(status = 404, body = """{"code":"NOT_FOUND","message":"La invitación ya no existe"}"""),
        )
        val vm = viewModel(repo)
        vm.load()
        advanceUntilIdle()

        vm.accept("t9")
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("La invitación ya no existe", s.error)
        assertTrue(s.respondingIds.isEmpty())
        assertEquals(1, s.invitations.size)
    }

    @Test
    fun el_evento_teams_del_bus_recarga_las_invitaciones() = runTest {
        val repo = FakeTeamRepository()
        val bus = NotificationRefreshBus()
        val vm = viewModel(repo, bus)
        vm.load()
        advanceUntilIdle()

        repo.invitations = listOf(invitation("t1"))
        bus.emit(RefreshEvent.TEAMS)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.invitations.size)
    }
}
