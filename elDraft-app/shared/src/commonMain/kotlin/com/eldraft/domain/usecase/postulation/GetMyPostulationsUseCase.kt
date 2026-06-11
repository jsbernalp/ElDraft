package com.eldraft.domain.usecase.postulation

import com.eldraft.data.models.MyPostulation
import com.eldraft.domain.repository.PostulationRepository

/** Lista las postulaciones del jugador autenticado (sus partidos como jugador). */
class GetMyPostulationsUseCase(
    private val repository: PostulationRepository,
) {
    suspend operator fun invoke(): List<MyPostulation> = repository.getMine()
}
