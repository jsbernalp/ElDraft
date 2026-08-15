package com.eldraft.domain.usecase

import com.eldraft.data.models.Postulation
import com.eldraft.domain.repository.PostulationRepository
import com.eldraft.domain.usecase.postulation.ApplyToConvocatoryUseCase
import com.eldraft.domain.usecase.postulation.ApproveApplicantUseCase
import com.eldraft.domain.usecase.postulation.GetApplicantsUseCase
import com.eldraft.domain.usecase.postulation.RejectApplicantUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private class FakePostulationRepository : PostulationRepository {
    var appliedTo: String? = null
    var appliedPosition: String? = null
    var approvedId: String? = null
    var rejectedId: String? = null

    override suspend fun apply(convocatoryId: String, position: String): Postulation {
        appliedTo = convocatoryId
        appliedPosition = position
        return Postulation(id = "p1", convocatoryId = convocatoryId, playerId = "u1", position = position, status = "pending")
    }

    override suspend fun getMine(): List<com.eldraft.data.models.MyPostulation> = emptyList()

    override suspend fun getApplicants(convocatoryId: String): List<Postulation> =
        listOf(Postulation(id = "p1", convocatoryId = convocatoryId, playerId = "u1", status = "pending"))

    override suspend fun approve(postulationId: String): Postulation {
        approvedId = postulationId
        return Postulation(id = postulationId, convocatoryId = "c1", playerId = "u1", status = "approved")
    }

    override suspend fun reject(postulationId: String): Postulation {
        rejectedId = postulationId
        return Postulation(id = postulationId, convocatoryId = "c1", playerId = "u1", status = "rejected")
    }

    // Añadido a PostulationRepository después de escribirse estos tests; ningún caso
    // de uso de este archivo lo ejercita.
    override suspend fun withdraw(postulationId: String) = error("no usado")
}

class PostulationUseCasesTest {

    @Test
    fun apply_delega_al_repo() = runTest {
        val repo = FakePostulationRepository()
        val result = ApplyToConvocatoryUseCase(repo)("c1", "Defensa")
        assertEquals("c1", repo.appliedTo)
        assertEquals("Defensa", repo.appliedPosition)
        assertEquals("pending", result.status)
    }

    @Test
    fun apply_con_id_vacio_falla_sin_llamar_repo() = runTest {
        val repo = FakePostulationRepository()
        assertFailsWith<IllegalArgumentException> { ApplyToConvocatoryUseCase(repo)("", "Defensa") }
        assertNull(repo.appliedTo)
    }

    @Test
    fun apply_sin_posicion_falla_sin_llamar_repo() = runTest {
        val repo = FakePostulationRepository()
        assertFailsWith<IllegalArgumentException> { ApplyToConvocatoryUseCase(repo)("c1", "") }
        assertNull(repo.appliedTo)
    }

    @Test
    fun get_applicants_devuelve_lista() = runTest {
        val list = GetApplicantsUseCase(FakePostulationRepository())("c1")
        assertEquals(1, list.size)
    }

    @Test
    fun approve_marca_aprobado() = runTest {
        val repo = FakePostulationRepository()
        val result = ApproveApplicantUseCase(repo)("p1")
        assertEquals("p1", repo.approvedId)
        assertEquals("approved", result.status)
    }

    @Test
    fun reject_marca_rechazado() = runTest {
        val repo = FakePostulationRepository()
        val result = RejectApplicantUseCase(repo)("p1")
        assertEquals("p1", repo.rejectedId)
        assertEquals("rejected", result.status)
    }

    @Test
    fun approve_con_id_vacio_falla() = runTest {
        val repo = FakePostulationRepository()
        assertFailsWith<IllegalArgumentException> { ApproveApplicantUseCase(repo)("") }
        assertNull(repo.approvedId)
    }
}
