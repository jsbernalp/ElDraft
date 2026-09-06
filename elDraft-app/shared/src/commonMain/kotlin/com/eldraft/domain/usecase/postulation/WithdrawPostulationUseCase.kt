package com.eldraft.domain.usecase.postulation

import com.eldraft.domain.repository.PostulationRepository

class WithdrawPostulationUseCase(private val postulationRepository: PostulationRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(postulationId: String) =
        postulationRepository.withdraw(postulationId)
}
