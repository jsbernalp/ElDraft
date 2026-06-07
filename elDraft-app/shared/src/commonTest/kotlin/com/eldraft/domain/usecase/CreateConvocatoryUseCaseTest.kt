package com.eldraft.domain.usecase

import com.eldraft.data.models.Convocatory
import com.eldraft.data.models.CreateConvocatoryRequest
import com.eldraft.data.models.MapEvent
import com.eldraft.domain.repository.ConvocatoryRepository
import com.eldraft.domain.usecase.convocatory.CreateConvocatoryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class FakeConvocatoryRepository : ConvocatoryRepository {
    var created: CreateConvocatoryRequest? = null
    override suspend fun create(request: CreateConvocatoryRequest): Convocatory {
        created = request
        return Convocatory(
            id = "conv-1",
            organizerId = "org-1",
            lat = request.lat,
            lng = request.lng,
            addressText = request.addressText,
            slotsNeeded = request.slotsNeeded,
            positionRequired = request.positionRequired,
            fee = request.fee,
            format = request.format,
            ambiente = request.ambiente,
            scheduledAt = request.scheduledAt,
        )
    }
    override suspend fun getNearby(lat: Double, lng: Double, radius: Double) = emptyList<Convocatory>()
    override suspend fun getMine() = emptyList<Convocatory>()
    override suspend fun getById(id: String): Convocatory = error("no usado")
    override fun observeMapEvents(lat: Double, lng: Double, radius: Double): Flow<MapEvent> = emptyFlow()
}

private fun request(
    slots: Int = 3,
    position: String = "Delantero",
    format: String = "Fútbol 5",
    ambiente: String = "Recocha",
    fee: Double = 0.0,
    scheduledAt: String = "2026-06-10T19:00:00",
) = CreateConvocatoryRequest(
    lat = 6.24, lng = -75.58, addressText = null,
    slotsNeeded = slots, positionRequired = position, fee = fee,
    format = format, ambiente = ambiente, scheduledAt = scheduledAt,
)

class CreateConvocatoryUseCaseTest {

    @Test
    fun crea_convocatoria_valida() = runTest {
        val repo = FakeConvocatoryRepository()
        val result = CreateConvocatoryUseCase(repo)(request())
        assertEquals("conv-1", result.id)
        assertEquals(3, repo.created?.slotsNeeded)
    }

    @Test
    fun cupos_invalidos_fallan_sin_llamar_repo() = runTest {
        val repo = FakeConvocatoryRepository()
        val useCase = CreateConvocatoryUseCase(repo)
        assertFailsWith<IllegalArgumentException> { useCase(request(slots = 0)) }
        assertFailsWith<IllegalArgumentException> { useCase(request(slots = 99)) }
        assertTrue(repo.created == null)
    }

    @Test
    fun campos_obligatorios_vacios_fallan() = runTest {
        val useCase = CreateConvocatoryUseCase(FakeConvocatoryRepository())
        assertFailsWith<IllegalArgumentException> { useCase(request(position = "")) }
        assertFailsWith<IllegalArgumentException> { useCase(request(format = "")) }
        assertFailsWith<IllegalArgumentException> { useCase(request(ambiente = "")) }
        assertFailsWith<IllegalArgumentException> { useCase(request(scheduledAt = "")) }
    }

    @Test
    fun fee_negativo_falla() = runTest {
        val useCase = CreateConvocatoryUseCase(FakeConvocatoryRepository())
        assertFailsWith<IllegalArgumentException> { useCase(request(fee = -1.0)) }
    }
}
