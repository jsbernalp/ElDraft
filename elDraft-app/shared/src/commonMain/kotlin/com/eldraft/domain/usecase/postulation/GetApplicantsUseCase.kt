package com.eldraft.domain.usecase.postulation

import com.eldraft.data.models.Postulation
import com.eldraft.domain.repository.PostulationRepository

/** Lista los postulantes de una convocatoria (uso del organizador). */
class GetApplicantsUseCase(
    private val repository: PostulationRepository,
) {
    suspend operator fun invoke(convocatoryId: String): List<Postulation> {
        require(convocatoryId.isNotBlank()) { "Convocatoria inválida" }
        return repository.getApplicants(convocatoryId)
    }
}
