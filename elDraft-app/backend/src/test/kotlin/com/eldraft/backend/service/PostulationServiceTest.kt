package com.eldraft.backend.service

import com.eldraft.backend.repository.ConvocatoryRecord
import com.eldraft.backend.repository.ConvocatoryRepository
import com.eldraft.backend.repository.PostulationRecord
import com.eldraft.backend.repository.PostulationRepository
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Verifica la lógica de autorización y conflictos de PostulationService usando
 * repos falsos (sin base de datos). Las clases de repo se hicieron `open` para
 * poder sustituir solo los métodos que el servicio consulta.
 */
class PostulationServiceTest {

    private val organizerId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()
    private val convocatoryId = UUID.randomUUID()
    private val postulationId = UUID.randomUUID()

    private fun convocatory(status: String = "active", organizer: UUID = organizerId) =
        ConvocatoryRecord(
            id = convocatoryId, organizerId = organizer, lat = 6.2, lng = -75.5,
            addressText = null, slotsNeeded = 3, positionRequired = "Delantero",
            fee = 0.0, format = "Fútbol 5", ambiente = "Recocha", status = status,
            scheduledAt = "2026-06-10T19:00:00",
        )

    private fun postulation(status: String = "pending") = PostulationRecord(
        id = postulationId, convocatoryId = convocatoryId, playerId = playerId,
        status = status, createdAt = "2026-06-07T10:00:00", player = null,
    )

    private class FakeConvocatoryRepo(val record: ConvocatoryRecord?) : ConvocatoryRepository() {
        override fun findById(id: UUID): ConvocatoryRecord? = record
    }

    private class FakePostulationRepo(
        val createResult: PostulationRecord? = null,
        val byId: PostulationRecord? = null,
    ) : PostulationRepository() {
        var lastStatus: String? = null
        override fun create(convocatoryId: UUID, playerId: UUID): PostulationRecord? = createResult
        override fun findById(id: UUID): PostulationRecord? = byId
        override fun updateStatus(id: UUID, status: String): Boolean {
            lastStatus = status
            return true
        }
    }

    @Test
    fun apply_exitoso_crea_postulacion() {
        val service = PostulationService(
            FakePostulationRepo(createResult = postulation()),
            FakeConvocatoryRepo(convocatory()),
        )
        val result = service.apply(convocatoryId, playerId)
        assertEquals("pending", result.status)
    }

    @Test
    fun apply_a_convocatoria_inexistente_falla() {
        val service = PostulationService(FakePostulationRepo(), FakeConvocatoryRepo(null))
        assertFailsWith<PostulationNotFound> { service.apply(convocatoryId, playerId) }
    }

    @Test
    fun apply_a_convocatoria_cerrada_falla() {
        val service = PostulationService(
            FakePostulationRepo(createResult = postulation()),
            FakeConvocatoryRepo(convocatory(status = "full")),
        )
        assertFailsWith<PostulationConflict> { service.apply(convocatoryId, playerId) }
    }

    @Test
    fun organizador_no_puede_postularse_a_su_convocatoria() {
        val service = PostulationService(
            FakePostulationRepo(createResult = postulation()),
            FakeConvocatoryRepo(convocatory()),
        )
        assertFailsWith<PostulationConflict> { service.apply(convocatoryId, organizerId) }
    }

    @Test
    fun postulacion_duplicada_falla() {
        // El repo devuelve null cuando ya existe.
        val service = PostulationService(
            FakePostulationRepo(createResult = null),
            FakeConvocatoryRepo(convocatory()),
        )
        assertFailsWith<PostulationConflict> { service.apply(convocatoryId, playerId) }
    }

    @Test
    fun solo_organizador_ve_postulantes() {
        val service = PostulationService(FakePostulationRepo(), FakeConvocatoryRepo(convocatory()))
        assertFailsWith<PostulationForbidden> { service.getApplicants(convocatoryId, playerId) }
    }

    @Test
    fun solo_organizador_aprueba() {
        val service = PostulationService(
            FakePostulationRepo(byId = postulation()),
            FakeConvocatoryRepo(convocatory()),
        )
        assertFailsWith<PostulationForbidden> { service.approve(postulationId, playerId) }
    }

    @Test
    fun organizador_aprueba_correctamente() {
        val postRepo = FakePostulationRepo(byId = postulation())
        val service = PostulationService(postRepo, FakeConvocatoryRepo(convocatory()))
        service.approve(postulationId, organizerId)
        assertEquals("approved", postRepo.lastStatus)
    }

    @Test
    fun organizador_rechaza_correctamente() {
        val postRepo = FakePostulationRepo(byId = postulation())
        val service = PostulationService(postRepo, FakeConvocatoryRepo(convocatory()))
        service.reject(postulationId, organizerId)
        assertEquals("rejected", postRepo.lastStatus)
    }
}
