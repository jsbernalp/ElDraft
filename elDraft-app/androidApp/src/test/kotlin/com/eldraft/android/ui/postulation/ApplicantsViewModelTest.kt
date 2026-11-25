package com.eldraft.android.ui.postulation

import com.eldraft.android.MainDispatcherRule
import com.eldraft.android.notifications.NotificationRefreshBus
import com.eldraft.core.network.ApiException
import com.eldraft.data.models.MyPostulation
import com.eldraft.data.models.Postulation
import com.eldraft.domain.repository.PostulationRepository
import com.eldraft.domain.usecase.postulation.ApproveApplicantUseCase
import com.eldraft.domain.usecase.postulation.GetApplicantsUseCase
import com.eldraft.domain.usecase.postulation.RejectApplicantUseCase
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fake del repositorio de dominio (mismo patrón que los tests de :shared): los
 * casos de uso son clases concretas, así que se construyen de verdad sobre este
 * fake en lugar de simularlos.
 */
private class FakePostulationRepository(
    private val applicants: List<Postulation> = emptyList(),
    private val failWith: Exception? = null,
) : PostulationRepository {
    override suspend fun apply(convocatoryId: String, position: String) = error("no usado")
    override suspend fun getMine(): List<MyPostulation> = error("no usado")

    override suspend fun getApplicants(convocatoryId: String): List<Postulation> {
        failWith?.let { throw it }
        return applicants
    }

    override suspend fun approve(postulationId: String) =
        Postulation(id = postulationId, convocatoryId = "c1", playerId = "u1", status = "approved")

    override suspend fun reject(postulationId: String) =
        Postulation(id = postulationId, convocatoryId = "c1", playerId = "u1", status = "rejected")

    override suspend fun withdraw(postulationId: String) = error("no usado")
}

class ApplicantsViewModelTest {

    private val main = MainDispatcherRule()

    @BeforeTest fun setUp() = main.start()
    @AfterTest fun tearDown() = main.stop()

    private fun viewModel(repo: FakePostulationRepository) = ApplicantsViewModel(
        getApplicants = GetApplicantsUseCase(repo),
        approveApplicant = ApproveApplicantUseCase(repo),
        rejectApplicant = RejectApplicantUseCase(repo),
        refreshBus = NotificationRefreshBus(),
    )

    private fun postulante(id: String, status: String = "pending") =
        Postulation(id = id, convocatoryId = "c1", playerId = "u$id", status = status)

    @Test
    fun load_publica_los_postulantes() = runTest {
        val vm = viewModel(FakePostulationRepository(applicants = listOf(postulante("p1"))))

        vm.load("c1")
        advanceUntilIdle()

        assertEquals(1, vm.state.value.applicants.size)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun load_marca_cargando_antes_de_responder() = runTest {
        val vm = viewModel(FakePostulationRepository(applicants = listOf(postulante("p1"))))

        vm.load("c1")

        // Sin advanceUntilIdle la corrutina sigue encolada: este es el estado que
        // ve la pantalla mientras espera al backend.
        assertTrue(vm.state.value.isLoading)
    }

    @Test
    fun load_con_error_del_backend_muestra_el_mensaje_del_servidor() = runTest {
        val repo = FakePostulationRepository(
            failWith = ApiException(status = 403, body = """{"code":"FORBIDDEN","message":"No eres el organizador"}"""),
        )
        val vm = viewModel(repo)

        vm.load("c1")
        advanceUntilIdle()

        // El texto sale del backend, no del ViewModel: el fallback solo aplica
        // cuando la respuesta no trae un cuerpo legible.
        assertEquals("No eres el organizador", vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun aprobar_reemplaza_al_postulante_por_su_version_actualizada() = runTest {
        val vm = viewModel(FakePostulationRepository(applicants = listOf(postulante("p1"))))
        vm.load("c1")
        advanceUntilIdle()

        vm.approve("p1")
        advanceUntilIdle()

        assertEquals("approved", vm.state.value.applicants.single().status)
        assertTrue(vm.state.value.decidingIds.isEmpty())
    }

    @Test
    fun mientras_decide_bloquea_el_boton_de_ese_postulante() = runTest {
        val vm = viewModel(FakePostulationRepository(applicants = listOf(postulante("p1"))))
        vm.load("c1")
        advanceUntilIdle()

        vm.approve("p1")

        // La decisión está encolada: la card debe mostrar el botón deshabilitado.
        assertTrue("p1" in vm.state.value.decidingIds)
    }
}
