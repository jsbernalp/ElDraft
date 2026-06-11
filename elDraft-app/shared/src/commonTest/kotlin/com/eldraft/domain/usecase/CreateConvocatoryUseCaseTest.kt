package com.eldraft.domain.usecase

import com.eldraft.data.models.Convocatory
import com.eldraft.data.models.CreateConvocatoryRequest
import com.eldraft.data.models.MapEvent
import com.eldraft.data.models.PositionSlot
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
            slotsNeeded = request.positionSlots.sumOf { it.slots },
            positionRequired = request.positionSlots.firstOrNull()?.position ?: "",
            positionSlots = request.positionSlots,
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
    positionSlots: List<PositionSlot> = listOf(PositionSlot("Delantero", 3)),
    format: String = "Fútbol 5",
    ambiente: String = "Recocha",
    fee: Double = 0.0,
    scheduledAt: String = "2026-06-10T19:00:00",
) = CreateConvocatoryRequest(
    lat = 6.24, lng = -75.58, addressText = null,
    positionSlots = positionSlots, fee = fee,
    format = format, ambiente = ambiente, scheduledAt = scheduledAt,
)

class CreateConvocatoryUseCaseTest {

    @Test
    fun crea_convocatoria_valida() = runTest {
        val repo = FakeConvocatoryRepository()
        val result = CreateConvocatoryUseCase(repo)(
            request(positionSlots = listOf(PositionSlot("Arquero", 1), PositionSlot("Defensa", 2))),
        )
        assertEquals("conv-1", result.id)
        // El total de cupos se deriva de la suma de posiciones.
        assertEquals(3, result.slotsNeeded)
    }

    @Test
    fun cupos_invalidos_fallan_sin_llamar_repo() = runTest {
        val repo = FakeConvocatoryRepository()
        val useCase = CreateConvocatoryUseCase(repo)
        // Lista vacía, cupo < 1 y suma > 30 son inválidos.
        assertFailsWith<IllegalArgumentException> { useCase(request(positionSlots = emptyList())) }
        assertFailsWith<IllegalArgumentException> { useCase(request(positionSlots = listOf(PositionSlot("Delantero", 0)))) }
        assertFailsWith<IllegalArgumentException> { useCase(request(positionSlots = listOf(PositionSlot("Delantero", 99)))) }
        assertTrue(repo.created == null)
    }

    @Test
    fun posiciones_duplicadas_fallan() = runTest {
        val repo = FakeConvocatoryRepository()
        val useCase = CreateConvocatoryUseCase(repo)
        assertFailsWith<IllegalArgumentException> {
            useCase(request(positionSlots = listOf(PositionSlot("Defensa", 1), PositionSlot("Defensa", 2))))
        }
        assertTrue(repo.created == null)
    }

    @Test
    fun campos_obligatorios_vacios_fallan() = runTest {
        val useCase = CreateConvocatoryUseCase(FakeConvocatoryRepository())
        assertFailsWith<IllegalArgumentException> { useCase(request(positionSlots = listOf(PositionSlot("", 2)))) }
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
