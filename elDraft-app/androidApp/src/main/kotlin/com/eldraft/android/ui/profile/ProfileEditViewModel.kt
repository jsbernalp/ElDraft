package com.eldraft.android.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.core.network.userMessage
import com.eldraft.data.models.PlayerProfile
import com.eldraft.data.models.UpdateProfileRequest
import com.eldraft.data.models.User
import com.eldraft.domain.repository.AuthRepository
import com.eldraft.domain.repository.ProfileRepository
import com.eldraft.domain.usecase.auth.GetMyAccountUseCase
import com.eldraft.domain.usecase.auth.LogoutUseCase
import com.eldraft.domain.usecase.auth.UpdateAccountUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileEditUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    /** Datos de cuenta (nombre, avatar, email, phone). */
    val user: User? = null,
    /** Ficha técnica actual (puede ser null si no hizo onboarding aún). */
    val profile: PlayerProfile? = null,
    /** true una vez que guardado OK — la pantalla debe hacer popBackStack. */
    val saved: Boolean = false,
    /** true cuando el logout fue exitoso — navegar a Login. */
    val loggedOut: Boolean = false,
    val error: String? = null,
)

class ProfileEditViewModel(
    private val getMyAccount: GetMyAccountUseCase,
    private val updateAccount: UpdateAccountUseCase,
    private val logout: LogoutUseCase,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileEditUiState())
    val state: StateFlow<ProfileEditUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val userDeferred = async { getMyAccount() }
                val userId = authRepository.currentUserId()
                val profileDeferred = async {
                    userId?.let { runCatching { profileRepository.getProfile(it) }.getOrNull() }
                }
                val user = userDeferred.await()
                val profile = profileDeferred.await()
                _state.update { it.copy(isLoading = false, user = user, profile = profile) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.userMessage("No se pudo cargar tu perfil")
                    )
                }
            }
        }
    }

    /**
     * Guarda nombre + avatar (cuenta) Y ficha técnica en paralelo.
     * Si la ficha es null (usuario sin onboarding) solo guarda la cuenta.
     */
    fun save(
        name: String,
        avatarUrl: String?,
        phone: String?,
        positionPrimary: String,
        positionSecondary: String?,
        dominantFoot: String,
        height: Int?,
        build: String?,
    ) {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val updatedUser = updateAccount(
                    name = name.trim(),
                    avatarUrl = avatarUrl?.trim()?.ifBlank { null },
                )

                // Actualizar teléfono si cambió
                if (!phone.isNullOrBlank()) {
                    authRepository.updatePhone(phone.trim())
                }

                val userId = authRepository.currentUserId()
                val updatedProfile = userId?.let {
                    runCatching {
                        profileRepository.updateProfile(
                            playerId = it,
                            request = UpdateProfileRequest(
                                positionPrimary = positionPrimary,
                                positionSecondary = positionSecondary?.ifBlank { null },
                                dominantFoot = dominantFoot,
                                height = height,
                                build = build?.ifBlank { null },
                            )
                        )
                    }.getOrNull()
                }

                _state.update {
                    it.copy(
                        isSaving = false,
                        saved = true,
                        user = updatedUser,
                        profile = updatedProfile ?: it.profile,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSaving = false,
                        error = e.userMessage("No se pudo guardar el perfil"),
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                logout.invoke()
                _state.update { it.copy(loggedOut = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.userMessage("No se pudo cerrar la sesión")) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearSaved() = _state.update { it.copy(saved = false) }
}
