package com.eldraft.domain.usecase.postulation

import com.eldraft.data.models.Postulation
import com.eldraft.domain.repository.PostulationRepository

/** El organizador aprueba una postulación. */
class ApproveApplicantUseCase(
    private val repository: PostulationRepository,
) {
    suspend operator fun invoke(postulationId: String): Postulation {
        require(postulationId.isNotBlank()) { "Postulación inválida" }
        return repository.approve(postulationId)
    }
}

/** El organizador rechaza una postulación. */
class RejectApplicantUseCase(
    private val repository: PostulationRepository,
) {
    suspend operator fun invoke(postulationId: String): Postulation {
        require(postulationId.isNotBlank()) { "Postulación inválida" }
        return repository.reject(postulationId)
    }
}
