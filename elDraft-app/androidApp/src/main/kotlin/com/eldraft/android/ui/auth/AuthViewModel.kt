package com.eldraft.android.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eldraft.android.data.GoogleAuthException
import com.eldraft.domain.usecase.auth.RegisterFcmTokenUseCase
import com.eldraft.domain.usecase.auth.SignInDevUseCase
import com.eldraft.domain.usecase.auth.SignInWithGoogleUseCase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
    private val registerFcmToken: RegisterFcmTokenUseCase,
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
                syncFcmToken()
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
                syncFcmToken()
                _state.value = AuthUiState.Success(needsOnboarding = response.needsOnboarding)
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(e.message ?: "No se pudo conectar con el servidor")
            }
        }
    }

    /**
     * Obtiene el token FCM del dispositivo y lo registra en el backend.
     * Best-effort: cualquier fallo se loguea pero no afecta el login.
     */
    private suspend fun syncFcmToken() {
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            registerFcmToken(token)
        } catch (e: Exception) {
            Log.w("AuthViewModel", "No se pudo registrar el token FCM: ${e.message}")
        }
    }

    fun resetError() {
        if (_state.value is AuthUiState.Error) _state.value = AuthUiState.Idle
    }
}
