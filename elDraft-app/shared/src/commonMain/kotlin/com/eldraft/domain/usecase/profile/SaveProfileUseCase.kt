package com.eldraft.domain.usecase.profile

import com.eldraft.data.models.PlayerProfile
import com.eldraft.data.models.UpdateProfileRequest
import com.eldraft.domain.repository.AuthRepository
import com.eldraft.domain.repository.ProfileRepository

/** Datos de entrada del onboarding (ficha técnica). */
data class SaveProfileInput(
    val positionPrimary: String,
    val positionSecondary: String?,
    val dominantFoot: String,
    val height: Int?,
    val build: String?,
    val phone: String?,
)

/**
 * Guarda la ficha técnica del usuario autenticado. Orquesta: resolver el
 * userId de la sesión, actualizar el teléfono (si viene) y luego el perfil.
 */
class SaveProfileUseCase(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(input: SaveProfileInput): PlayerProfile {
        val userId = authRepository.currentUserId()
            ?: throw IllegalStateException("No hay sesión activa")

        if (!input.phone.isNullOrBlank()) {
            authRepository.updatePhone(input.phone)
        }

        return profileRepository.updateProfile(
            playerId = userId,
            request = UpdateProfileRequest(
                positionPrimary = input.positionPrimary,
                positionSecondary = input.positionSecondary?.ifBlank { null },
                dominantFoot = input.dominantFoot,
                height = input.height,
                build = input.build?.ifBlank { null },
            ),
        )
    }
}
