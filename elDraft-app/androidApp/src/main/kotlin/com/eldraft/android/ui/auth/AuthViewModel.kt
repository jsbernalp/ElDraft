package com.eldraft.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.android.data.GoogleAuthException
import com.eldraft.domain.usecase.auth.SignInDevUseCase
import com.eldraft.domain.usecase.auth.SignInWithGoogleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado del flujo de autenticación, observado por LoginScreen. */
sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    /** Login OK; needsOnboarding decide a qué pantalla navegar. */
    data class Success(val needsOnboarding: Boolean) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel(
    private val signInWithGoogle: SignInWithGoogleUseCase,
    private val signInDev: SignInDevUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    /** Inicia Google Sign-In, intercambia el token con el backend y guarda la sesión. */
    fun signInWithGoogle() {
        if (_state.value is AuthUiState.Loading) return
        _state.value = AuthUiState.Loading

        viewModelScope.launch {
            try {
                val response = signInWithGoogle.invoke()
                _state.value = AuthUiState.Success(needsOnboarding = response.needsOnboarding)
            } catch (e: GoogleAuthException) {
                _state.value = AuthUiState.Error(e.message ?: "Error al iniciar sesión con Google")
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(e.message ?: "No se pudo conectar con el servidor")
            }
        }
    }

    /**
     * Login de desarrollo SIN Google. Útil para probar el flujo en el emulador
     * sin pasar por el selector de cuentas de Google.
     */
    fun signInDev() {
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val response = signInDev.invoke()
                _state.value = AuthUiState.Success(needsOnboarding = response.needsOnboarding)
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(e.message ?: "No se pudo conectar con el servidor")
            }
        }
    }

    fun resetError() {
        if (_state.value is AuthUiState.Error) _state.value = AuthUiState.Idle
    }
}
