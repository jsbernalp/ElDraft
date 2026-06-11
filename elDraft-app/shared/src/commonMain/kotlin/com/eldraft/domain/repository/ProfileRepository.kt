package com.eldraft.domain.repository

import com.eldraft.data.models.PlayerProfile
import com.eldraft.data.models.UpdateProfileRequest

/** Operaciones sobre la ficha técnica (El Cromo) de los jugadores. */
interface ProfileRepository {

    /** Carga el Cromo de un jugador (propio o ajeno). */
    suspend fun getProfile(playerId: String): PlayerProfile

    /** Actualiza la ficha técnica de un jugador y devuelve el resultado. */
    suspend fun updateProfile(playerId: String, request: UpdateProfileRequest): PlayerProfile
}
