package com.eldraft.android.ui.draft

import com.eldraft.android.MainDispatcherRule
import com.eldraft.android.ui.team.FakeTeamRepository
import com.eldraft.core.network.ApiException
import com.eldraft.data.models.Team
import com.eldraft.domain.usecase.convocatory.CreateConvocatoryUseCase
import com.eldraft.domain.usecase.team.GetMyTeamsUseCase
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CreateDraftViewModelTest {

    private val main = MainDispatcherRule()

    @BeforeTest fun setUp() = main.start()
    @AfterTest fun tearDown() = main.stop()

    private fun viewModel(teams: FakeTeamRepository) = CreateDraftViewModel(
        createConvocatory = CreateConvocatoryUseCase(FakeConvocatoryRepository()),
        getMyTeams = GetMyTeamsUseCase(teams),
    )

    @Test
    fun solo_ofrece_los_equipos_con_miembros_activos() = runTest {
        val vm = viewModel(
            FakeTeamRepository(
                myTeams = listOf(
                    Team(id = "a", name = "Con gente", activeCount = 3),
                    Team(id = "b", name = "Vacío", activeCount = 0, invitedCount = 2),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("a"), vm.teams.value.map { it.id })
    }

    @Test
    fun si_falla_la_carga_la_lista_queda_vacia_y_no_estorba_la_creacion() = runTest {
        val vm = viewModel(
            FakeTeamRepository(failLoadWith = ApiException(status = 503, body = "")),
        )
        advanceUntilIdle()

        assertTrue(vm.teams.value.isEmpty())
    }
}
