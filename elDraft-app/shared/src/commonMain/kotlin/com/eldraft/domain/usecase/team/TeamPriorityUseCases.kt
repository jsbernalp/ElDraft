package com.eldraft.domain.usecase.team

import com.eldraft.data.models.Convocatory
import com.eldraft.data.models.TeamPriorityInvite
import com.eldraft.domain.repository.TeamPriorityRepository

private const val INVALID_CONVOCATORY = "Convocatoria inválida"

/** Convocatorias de mis equipos que esperan mi "Voy" / "No puedo". */
class GetTeamPriorityInvitesUseCase(private val repository: TeamPriorityRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(): List<TeamPriorityInvite> = repository.getPriorityInvites()
}

/** Responde a la prioridad del equipo. "Voy" exige elegir posición. */
class RespondTeamPriorityUseCase(private val repository: TeamPriorityRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(convocatoryId: String, accept: Boolean, position: String?) {
        require(convocatoryId.isNotBlank()) { INVALID_CONVOCATORY }
        if (accept) require(!position.isNullOrBlank()) { "Elige tu posición" }
        // "No puedo" no lleva posición: no se manda aunque la UI la conserve.
        repository.respond(convocatoryId, accept, if (accept) position else null)
    }
}

/** "Abrir ya al público": el organizador salta lo que falta de la ventana. */
class OpenConvocatoryToPublicUseCase(private val repository: TeamPriorityRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(convocatoryId: String): Convocatory {
        require(convocatoryId.isNotBlank()) { INVALID_CONVOCATORY }
        return repository.openToPublic(convocatoryId)
    }
}
