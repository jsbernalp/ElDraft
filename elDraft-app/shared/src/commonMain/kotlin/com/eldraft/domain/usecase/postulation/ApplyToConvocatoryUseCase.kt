package com.eldraft.domain.usecase.postulation

import com.eldraft.data.models.Postulation
import com.eldraft.domain.repository.PostulationRepository

/** El jugador autenticado se postula a una convocatoria. */
class ApplyToConvocatoryUseCase(
    private val repository: PostulationRepository,
) {
    suspend operator fun invoke(convocatoryId: String, position: String): Postulation {
        require(convocatoryId.isNotBlank()) { "Convocatoria inválida" }
        require(position.isNotBlank()) { "Elige la posición a la que te postulas" }
        return repository.apply(convocatoryId, position)
    }
}
