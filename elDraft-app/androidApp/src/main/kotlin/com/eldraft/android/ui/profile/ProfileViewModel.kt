package com.eldraft.android.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.android.data.SessionManager
import com.eldraft.data.remote.AuthApi
import com.eldraft.data.remote.PlayerApi
import com.eldraft.data.models.PlayerProfile
import com.eldraft.data.models.UpdateProfileRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado del guardado de la ficha técnica (onboarding). */
sealed interface OnboardingUiState {
    data object Idle : OnboardingUiState
    data object Saving : OnboardingUiState
    data object Saved : OnboardingUiState
    data class Error(val message: String) : OnboardingUiState
}

/** Estado de carga del Cromo de un jugador. */
sealed interface CromoUiState {
    data object Loading : CromoUiState
    data class Loaded(val profile: PlayerProfile) : CromoUiState
    data class Error(val message: String) : CromoUiState
}

class ProfileViewModel(
    private val authApi: AuthApi,
    private val playerApi: PlayerApi,
    private val session: SessionManager
) : ViewModel() {

    private val _onboarding = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Idle)
    val onboarding: StateFlow<OnboardingUiState> = _onboarding.asStateFlow()

    private val _cromo = MutableStateFlow<CromoUiState>(CromoUiState.Loading)
    val cromo: StateFlow<CromoUiState> = _cromo.asStateFlow()

    /** Guarda la ficha técnica del usuario autenticado + opcionalmente su teléfono. */
    fun saveProfile(
        positionPrimary: String,
        positionSecondary: String?,
        dominantFoot: String,
        height: Int?,
        build: String?,
        phone: String?
    ) {
        if (_onboarding.value is OnboardingUiState.Saving) return
        _onboarding.value = OnboardingUiState.Saving

        viewModelScope.launch {
            try {
                val userId = session.currentUserId()
                    ?: throw IllegalStateException("No hay sesión activa")

                if (!phone.isNullOrBlank()) {
                    authApi.updatePhone(phone)
                }

                playerApi.updateProfile(
                    playerId = userId,
                    request = UpdateProfileRequest(
                        positionPrimary = positionPrimary,
                        positionSecondary = positionSecondary?.ifBlank { null },
                        dominantFoot = dominantFoot,
                        height = height,
                        build = build?.ifBlank { null }
                    )
                )
                _onboarding.value = OnboardingUiState.Saved
            } catch (e: Exception) {
                _onboarding.value = OnboardingUiState.Error(
                    e.message ?: "No se pudo guardar tu ficha técnica"
                )
            }
        }
    }

    /** Carga el Cromo de un jugador (propio o ajeno). */
    fun loadCromo(playerId: String) {
        _cromo.value = CromoUiState.Loading
        viewModelScope.launch {
            try {
                val profile = playerApi.getProfile(playerId)
                _cromo.value = CromoUiState.Loaded(profile)
            } catch (e: Exception) {
                _cromo.value = CromoUiState.Error(
                    e.message ?: "No se pudo cargar la ficha técnica"
                )
            }
        }
    }

    fun resetOnboardingError() {
        if (_onboarding.value is OnboardingUiState.Error) _onboarding.value = OnboardingUiState.Idle
    }
}
