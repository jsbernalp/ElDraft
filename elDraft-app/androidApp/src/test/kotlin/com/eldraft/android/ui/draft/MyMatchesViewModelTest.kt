package com.eldraft.android.ui.draft

import com.eldraft.android.MainDispatcherRule
import com.eldraft.android.notifications.NotificationRefreshBus
import com.eldraft.android.ui.team.FakeTeamPriorityRepository
import com.eldraft.android.ui.team.convocatory
import com.eldraft.core.network.ApiException
import com.eldraft.data.models.Convocatory
import com.eldraft.data.models.CreateConvocatoryRequest
import com.eldraft.data.models.MapEvent
import com.eldraft.domain.repository.ConvocatoryRepository
import com.eldraft.domain.usecase.team.OpenConvocatoryToPublicUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class FakeConvocatoryRepository(var mine: List<Convocatory> = emptyList()) : ConvocatoryRepository {
    override suspend fun create(request: CreateConvocatoryRequest): Convocatory = error("no usado")
    override suspend fun getNearby(lat: Double, lng: Double, radius: Double) = emptyList<Convocatory>()
    override suspend fun getMine(): List<Convocatory> = mine
    override suspend fun getById(id: String): Convocatory = error("no usado")
    override fun observeMapEvents(lat: Double, lng: Double, radius: Double, userId: String?): Flow<MapEvent> = emptyFlow()
    override suspend fun cancel(id: String, reason: String) = Unit
}

class MyMatchesViewModelTest {

    private val main = MainDispatcherRule()

    @BeforeTest fun setUp() = main.start()
    @AfterTest fun tearDown() = main.stop()

    private fun viewModel(convocatories: FakeConvocatoryRepository, priority: FakeTeamPriorityRepository) =
        MyMatchesViewModel(convocatories, NotificationRefreshBus(), OpenConvocatoryToPublicUseCase(priority))

    @Test
    fun openNow_abre_y_recarga_la_lista() = runTest {
        val convocatories = FakeConvocatoryRepository(mine = listOf(convocatory("c1", "team_only")))
        val priority = FakeTeamPriorityRepository()
        val vm = viewModel(convocatories, priority)
        vm.load(); advanceUntilIdle()
        assertEquals("team_only", vm.state.value.matches.single().status)

        // Tras abrir, el backend la devuelve ya en active.
        convocatories.mine = listOf(convocatory("c1", "active"))
        vm.openNow("c1")
        assertEquals("c1", vm.state.value.openingId)
        advanceUntilIdle()

        assertEquals(listOf("c1"), priority.opened)
        assertTrue(vm.state.value.openSuccess)
        assertNull(vm.state.value.openingId)
        assertEquals("active", vm.state.value.matches.single().status)
    }

    @Test
    fun openNow_con_error_lo_muestra_para_el_dialogo_y_recarga() = runTest {
        val convocatories = FakeConvocatoryRepository(mine = listOf(convocatory("c1", "team_only")))
        val priority = FakeTeamPriorityRepository().apply {
            failOpenWith = ApiException(
                status = 409,
                body = """{"code":"CONFLICT","message":"La convocatoria ya está abierta"}""",
            )
        }
        val vm = viewModel(convocatories, priority)
        vm.load(); advanceUntilIdle()

        convocatories.mine = listOf(convocatory("c1", "active")) // se abrió sola
        vm.openNow("c1")
        advanceUntilIdle()

        assertEquals("La convocatoria ya está abierta", vm.state.value.openError)
        assertEquals(false, vm.state.value.openSuccess)
        assertEquals("active", vm.state.value.matches.single().status)

        vm.clearOpenError()
        assertNull(vm.state.value.openError)
    }

    @Test
    fun openNow_ignora_un_segundo_toque_mientras_abre() = runTest {
        val priority = FakeTeamPriorityRepository()
        val vm = viewModel(FakeConvocatoryRepository(), priority)

        vm.openNow("c1")
        vm.openNow("c1")
        advanceUntilIdle()

        assertEquals(1, priority.opened.size)
    }
}
