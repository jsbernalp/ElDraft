package com.eldraft.domain.usecase.postulation

import com.eldraft.domain.repository.PostulationRepository

class WithdrawPostulationUseCase(private val postulationRepository: PostulationRepository) {
    suspend operator fun invoke(postulationId: String) =
        postulationRepository.withdraw(postulationId)
}
