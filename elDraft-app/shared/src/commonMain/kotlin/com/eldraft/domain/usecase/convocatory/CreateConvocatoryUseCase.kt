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
        require(request.slotsNeeded in 1..30) { "Los cupos deben estar entre 1 y 30" }
        require(request.positionRequired.isNotBlank()) { "Selecciona la posición requerida" }
        require(request.format.isNotBlank()) { "Selecciona el formato" }
        require(request.ambiente.isNotBlank()) { "Selecciona el ambiente" }
        require(request.fee >= 0.0) { "La cuota no puede ser negativa" }
        require(request.scheduledAt.isNotBlank()) { "Selecciona fecha y hora" }
        return repository.create(request)
    }
}
