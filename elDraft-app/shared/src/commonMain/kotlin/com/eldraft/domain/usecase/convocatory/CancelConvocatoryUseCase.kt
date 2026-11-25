package com.eldraft.domain.usecase.convocatory

import com.eldraft.domain.repository.ConvocatoryRepository

class CancelConvocatoryUseCase(
    private val convocatoryRepository: ConvocatoryRepository,
) {
    @Throws(Exception::class)
    suspend operator fun invoke(id: String, reason: String) =
        convocatoryRepository.cancel(id, reason)
}
