package com.eldraft.backend.service

import com.eldraft.backend.repository.PlayerProfileRecord
import com.eldraft.backend.repository.ProfileUpsert
import com.eldraft.backend.repository.UserRepository
import java.util.UUID

/**
 * Lógica de la ficha técnica (El Cromo): consulta y upsert con validación.
 * Antes la validación vivía suelta en PlayerRoutes.
 */
class PlayerService(
    private val userRepository: UserRepository,
) {
    /** Ficha técnica de un jugador, o null si aún no la tiene. */
    fun getProfile(userId: UUID): PlayerProfileRecord? =
        userRepository.getProfile(userId)

    /** Crea o actualiza la ficha técnica tras validar los campos. */
    fun upsertProfile(userId: UUID, data: ProfileUpsert): PlayerProfileRecord {
        validate(data)
        return userRepository.upsertProfile(userId, data)
    }

    private fun validate(data: ProfileUpsert) {
        if (data.positionPrimary.isBlank()) {
            throw IllegalArgumentException("La posición primaria es obligatoria")
        }
        if (data.dominantFoot.isBlank()) {
            throw IllegalArgumentException("La pierna dominante es obligatoria")
        }
        data.height?.let {
            if (it !in 100..250) throw IllegalArgumentException("La altura debe estar entre 100 y 250 cm")
        }
    }
}
