package com.eldraft.android.ui.team

import com.eldraft.android.MainDispatcherRule
import com.eldraft.android.notifications.NotificationRefreshBus
import com.eldraft.android.notifications.RefreshEvent
import com.eldraft.core.network.ApiException
import com.eldraft.data.models.Convocatory
import com.eldraft.data.models.PositionSlot
import com.eldraft.data.models.TeamPriorityInvite
import com.eldraft.domain.repository.TeamPriorityRepository
import com.eldraft.domain.usecase.team.GetTeamPriorityInvitesUseCase
import com.eldraft.domain.usecase.team.RespondTeamPriorityUseCase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class FakeTeamPriorityRepository(
    var invites: List<TeamPriorityInvite> = emptyList(),
    var failRespondWith: Exception? = null,
) : TeamPriorityRepository {
    val responses = mutableListOf<Triple<String, Boolean, String?>>()
    val opened = mutableListOf<String>()
    var failOpenWith: Exception? = null

    override suspend fun getPriorityInvites(): List<TeamPriorityInvite> = invites

    override suspend fun respond(convocatoryId: String, accept: Boolean, position: String?) {
        failRespondWith?.let { throw it }
        responses += Triple(convocatoryId, accept, position)
        // Como el backend: una invitación ya respondida deja de estar pendiente.
        invites = invites.filterNot { it.convocatory.id == convocatoryId }
    }

    override suspend fun openToPublic(convocatoryId: String): Convocatory {
        failOpenWith?.let { throw it }
        opened += convocatoryId
        return convocatory(convocatoryId, "active")
    }
}

internal fun convocatory(id: String, status: String = "active") = Convocatory(
    id = id, organizerId = "org", lat = 0.0, lng = 0.0, slotsNeeded = 3,
    positionRequired = "Delantero", format = "Fútbol 5", ambiente = "Recocha",
    status = status, scheduledAt = "2026-06-10T19:00:00",
)

class TeamPriorityViewModelTest {

    private val main = MainDispatcherRule()

    @BeforeTest fun setUp() = main.start()
    @AfterTest fun tearDown() = main.stop()

    private fun invite(id: String) = TeamPriorityInvite(
        convocatory = convocatory(id, "team_only"),
        teamName = "Los del martes",
        windowEndsAt = "2026-06-10T18:00:00",
        freePositions = listOf(PositionSlot("Delantero", 2)),
    )

    private fun viewModel(repo: FakeTeamPriorityRepository, bus: NotificationRefreshBus = NotificationRefreshBus()) =
        TeamPriorityViewModel(
            getInvites = GetTeamPriorityInvitesUseCase(repo),
            respondUseCase = RespondTeamPriorityUseCase(repo),
            refreshBus = bus,
        )

    @Test
    fun load_publica_las_invitaciones() = runTest {
        val vm = viewModel(FakeTeamPriorityRepository(invites = listOf(invite("c1"), invite("c2"))))
        vm.load()
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()
        assertEquals(2, vm.state.value.invites.size)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun voy_manda_la_posicion_quita_la_card_y_avisa_al_resto_de_pestanas() = runTest {
        val repo = FakeTeamPriorityRepository(invites = listOf(invite("c1"), invite("c2")))
        val bus = NotificationRefreshBus()
        val vm = viewModel(repo, bus)
        vm.load(); advanceUntilIdle()

        val events = mutableListOf<RefreshEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { bus.events.toList(events) }

        vm.respond("c1", accept = true, position = "Delantero")
        assertEquals("c1", vm.state.value.respondingId)
        advanceUntilIdle()

        assertEquals(Triple("c1", true, "Delantero"), repo.responses.single())
        assertEquals(listOf("c2"), vm.state.value.invites.map { it.convocatory.id })
        assertNull(vm.state.value.respondingId)
        assertTrue(vm.state.value.responded)
        assertTrue(RefreshEvent.MY_POSTULATIONS in events)
        job.cancel()
    }

    @Test
    fun no_puedo_no_manda_posicion() = runTest {
        val repo = FakeTeamPriorityRepository(invites = listOf(invite("c1")))
        val vm = viewModel(repo)
        vm.load(); advanceUntilIdle()

        vm.respond("c1", accept = false, position = "Delantero")
        advanceUntilIdle()

        assertNull(repo.responses.single().third)
    }

    @Test
    fun voy_sin_posicion_falla_en_el_caso_de_uso_y_deja_la_card() = runTest {
        val repo = FakeTeamPriorityRepository(invites = listOf(invite("c1")))
        val vm = viewModel(repo)
        vm.load(); advanceUntilIdle()

        vm.respond("c1", accept = true, position = null)
        advanceUntilIdle()

        assertTrue(repo.responses.isEmpty())
        assertNotNull(vm.state.value.error)
        assertEquals(1, vm.state.value.invites.size)
    }

    @Test
    fun error_del_backend_muestra_su_mensaje_y_recarga_la_lista() = runTest {
        val repo = FakeTeamPriorityRepository(
            invites = listOf(invite("c1")),
            failRespondWith = ApiException(
                status = 409,
                body = """{"code":"CONFLICT","message":"Ya no quedan cupos para esa posición"}""",
            ),
        )
        val vm = viewModel(repo)
        vm.load(); advanceUntilIdle()

        // El backend ya quitó la invitación (venció la ventana): la recarga lo refleja.
        vm.respond("c1", accept = true, position = "Delantero")
        repo.invites = emptyList()
        advanceUntilIdle()

        assertEquals("Ya no quedan cupos para esa posición", vm.state.value.error)
        assertTrue(vm.state.value.invites.isEmpty())
        assertNull(vm.state.value.respondingId)
        assertFalse(vm.state.value.responded)
    }

    @Test
    fun el_push_de_postulaciones_recarga_las_invitaciones() = runTest {
        val repo = FakeTeamPriorityRepository()
        val bus = NotificationRefreshBus()
        val vm = viewModel(repo, bus)
        advanceUntilIdle()
        assertTrue(vm.state.value.invites.isEmpty())

        repo.invites = listOf(invite("c1"))
        bus.emit(RefreshEvent.MY_POSTULATIONS)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.invites.size)
    }
}
