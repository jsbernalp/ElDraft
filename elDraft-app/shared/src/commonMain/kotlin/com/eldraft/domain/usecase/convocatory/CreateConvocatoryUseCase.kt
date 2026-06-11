package com.eldraft.domain.usecase.convocatory

import com.eldraft.data.models.Convocatory
import com.eldraft.data.models.CreateConvocatoryRequest
import com.eldraft.domain.repository.ConvocatoryRepository

/**
 * Crea una convocatoria. Valida localmente antes de llegar al backend para dar
 * feedback inmediato en la UI (el backend revalida de todas formas).
 */
class CreateConvocatoryUseCase(
    private val repository: ConvocatoryRepository,
) {
    suspend operator fun invoke(request: CreateConvocatoryRequest): Convocatory {
        require(request.positionSlots.isNotEmpty()) { "Agrega al menos una posición con sus cupos" }
        require(request.positionSlots.all { it.position.isNotBlank() }) { "Cada posición debe tener nombre" }
        require(request.positionSlots.all { it.slots >= 1 }) { "Cada posición necesita al menos 1 cupo" }
        val positions = request.positionSlots.map { it.position }
        require(positions.size == positions.toSet().size) { "No repitas posiciones" }
        val total = request.positionSlots.sumOf { it.slots }
        require(total in 1..30) { "El total de cupos debe estar entre 1 y 30" }
        require(request.format.isNotBlank()) { "Selecciona el formato" }
        require(request.ambiente.isNotBlank()) { "Selecciona el ambiente" }
        require(request.fee >= 0.0) { "La cuota no puede ser negativa" }
        require(request.scheduledAt.isNotBlank()) { "Selecciona fecha y hora" }
        return repository.create(request)
    }
}
